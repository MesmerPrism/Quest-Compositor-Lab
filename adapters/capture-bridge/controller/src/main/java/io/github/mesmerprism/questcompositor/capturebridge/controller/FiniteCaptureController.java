// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import io.github.mesmerprism.questcompositor.capturebridge.SinkStatus;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CapturePorts.*;

/** One invocation for its entire lifetime. No replacement, cancellation or replay API. */
public final class FiniteCaptureController {
    private final Driver driver;private final SinkClient sink;private final OwnerProbe probe;private final MonotonicClock clock;private final OriginalWorker worker;
    private final Object invocation=new Object();private final Set<String> hardHolds=new LinkedHashSet<>(),history=new LinkedHashSet<>();
    private CaptureAdmission admission;private CaptureAttempt attempt=CaptureAttempt.create(new Object());private SinkLease lease;private Monitor driverMonitor,sinkMonitor;
    private String phase="NEW",rpc;private boolean begun,stopQueued,publisherDied,retirementStarted,joinAttempted,localRetired,beginTaskPending,stopTaskPending;
    private long deadline,lastNow,lastSerial,stopReturnedAt,startDecisionAt,stopDecisionAt;
    public FiniteCaptureController(Driver d,SinkClient s,OwnerProbe p,MonotonicClock c,OriginalWorker w){if(d==null||s==null||p==null||c==null||w==null)throw new IllegalArgumentException("all typed ports required");driver=d;sink=s;probe=p;clock=c;worker=w;}
    public synchronized void begin(CaptureAdmission value){
        synchronized(this){if(begun)throw new IllegalStateException("invocation cannot rearm");begun=true;admission=value;
            try{if(value==null)throw new IllegalArgumentException("admission required");value.validate();lastNow=clock.nowMillis();if(lastNow<=0)throw new IllegalArgumentException("positive helper monotonic observation required");deadline=Math.addExact(lastNow,value.budget.runMillis);lastSerial=value.scope.initialObservationSerial;attempt=CaptureAttempt.create(value.scope.originalBinderIdentity);phase="ADMITTING";}catch(RuntimeException failure){hardHolds.add("INVALID_ADMISSION");phase="HELD";throw failure;}}
        synchronized(this){beginTaskPending=true;}
        try{worker.post(()->{try{doBegin();}finally{synchronized(FiniteCaptureController.this){beginTaskPending=false;}}});}catch(RuntimeException unknown){hold("START_QUEUE_UNKNOWN");}
    }
    public void requestStop(StopReason reason){
        boolean post;synchronized(this){post=latchStopLocked(reason);}if(post)postStop();
    }
    private boolean latchStopLocked(StopReason reason){
        if(!begun||localRetired)return false;attempt=attempt.latchStop();history.add("STOP_"+reason.name());if(stopQueued)return false;stopQueued=true;stopTaskPending=true;phase="STOP_LATCHED";return true;
    }
    private void postStop(){
        try{worker.post(()->{try{doStop();}finally{synchronized(FiniteCaptureController.this){stopTaskPending=false;}}});}catch(RuntimeException unknown){hold("STOP_QUEUE_UNKNOWN");}
    }
    private <T>T call(String name,Callable<T> action)throws Exception{
        synchronized(this){if(rpc!=null)throw new IllegalStateException("original worker must serialize calls");rpc=name;}
        try{return action.call();}catch(Exception unknown){synchronized(this){history.add(name+"_OUTCOME_UNKNOWN");if(!name.equals("START")&&!name.equals("STOP"))hardHolds.add(name+"_UNKNOWN");}throw unknown;}
        finally{synchronized(this){rpc=null;}}
    }
    private void compatibility(CompatibilityObservation observation){
        CaptureAdmission.need(observation!=null&&admission.compatibility.equals(observation.values)&&observation.originalBinderIdentity==admission.scope.originalBinderIdentity&&observation.alive&&observation.loaderVerified&&observation.proxyVerified&&observation.signatureVerified&&observation.defaultsVerified&&observation.privacyVerified&&observation.permissionGranted,"exact original compatibility/privacy/permission required");
    }
    private void leaseIdentity(boolean start){
        CaptureAdmission.need(lease!=null&&admission.sink.descriptor.equals(lease.descriptor())&&lease.publisherIdentity()==admission.sink.originalPublisherIdentity&&lease.invocationIdentity()==invocation&&(!start||lease.valid()),"original exact sink/invocation required");
    }
    private void ownerFactsAt(OwnerObservation o,boolean start,long now){
        CaptureAdmission.Scope s=admission.scope;
        CaptureAdmission.need(o!=null&&s.requestId.equals(o.requestId)&&s.sessionId.equals(o.sessionId)&&s.nonce.equals(o.invocationNonce)&&s.targetId.equals(o.targetId)&&s.bootId.equals(o.bootId)&&s.leaseId.equals(o.leaseId)&&s.clockDomain.equals(o.clockDomain)&&s.nativePid==o.nativePid&&s.nativeStartTicks==o.nativeStartTicks&&s.originalBinderIdentity==o.originalBinderIdentity&&o.observedAtMillis>0&&o.observedAtMillis<=now&&now-o.observedAtMillis<=admission.budget.observationMillis&&o.cleanupValidUntilMillis>=now&&s.cleanupValidUntilMillis>=now&&o.priorCustodyResolved&&o.soleOperatorDeclared&&!o.foreignCaptureObserved&&o.ownAttemptStopAuthorized&&(!start||o.nativeQuiet&&o.startAuthorized&&s.actionValidUntilMillis>=now),"fresh adjacent original-owner/boot/lease/invocation observation required");
    }
    private synchronized void owner(OwnerObservation o,boolean start){
        long now=clock.nowMillis();CaptureAdmission.need(now>=lastNow,"helper clock regressed");ownerFactsAt(o,start,now);
        CaptureAdmission.need(o.observationSerial>lastSerial,"owner observation serial must advance");lastNow=now;lastSerial=o.observationSerial;
    }
    /** Called only under this monitor, immediately with the attempt marker and no external observation RPC. */
    private long decisionTimeLocked(OwnerObservation finalOwner,boolean start){
        long now=clock.nowMillis();CaptureAdmission.need(now>=lastNow,"helper clock regressed");
        ownerFactsAt(finalOwner,start,now);CaptureAdmission.need(finalOwner.observationSerial==lastSerial,"final observation no longer current");lastNow=now;return now;
    }
    private OwnerObservation guard(boolean start)throws Exception{
        owner(call("OWNER_BEFORE",probe::observe),start);
        compatibility(call("COMPATIBILITY",driver::inspect));leaseIdentity(start);
        boolean dead;synchronized(this){dead=publisherDied;}
        if(!dead){SinkStatus status=call("SINK_STATUS",()->sink.currentStatus(admission.sink.descriptor));long[] words=status.toWords();CaptureAdmission.need(words[1]==admission.sink.descriptor.generation&&(!start||words[2]==1&&words[3]==1),"exact current sink status required");}
        else CaptureAdmission.need(!start,"dead publisher forbids Start");
        // Potentially blocking compatibility/sink calls cannot reuse the earlier observation.
        OwnerObservation finalOwner=call("OWNER_AFTER",probe::observe);owner(finalOwner,start);leaseIdentity(start);return finalOwner;
    }
    private void doBegin(){
        try{
            compatibility(call("INITIAL_COMPATIBILITY",driver::inspect));
            driverMonitor=call("DRIVER_WATCH",()->driver.watchOriginal(admission.scope.originalBinderIdentity,()->onOriginalDriverDeath(admission.scope.originalBinderIdentity)));
            if(driverMonitor==null){hold("DRIVER_WATCH_UNKNOWN");throw new IllegalStateException("actual monitor required");}
            lease=call("SINK_ACQUIRE",()->sink.acquire(admission.sink.descriptor,invocation));leaseIdentity(true);
            sinkMonitor=call("SINK_WATCH",()->sink.watchOriginal(lease,()->onOriginalPublisherDeath(admission.sink.originalPublisherIdentity)));
            if(sinkMonitor==null){hold("SINK_WATCH_UNKNOWN");throw new IllegalStateException("actual monitor required");}
            OwnerObservation finalOwner=guard(true);boolean postBudgetStop=false,marked=false;
            synchronized(this){
                long now=decisionTimeLocked(finalOwner,true);
                if(publisherDied||attempt.originalDriverDied||attempt.stopLatched)return;
                CaptureAdmission.need(hardHolds.isEmpty(),"unresolved original custody blocks dispatch");
                if(now>=deadline)postBudgetStop=latchStopLocked(StopReason.BUDGET_EXPIRED);
                else{attempt=attempt.markStart();startDecisionAt=now;phase="START_RPC";marked=true;}
            }
            if(postBudgetStop)postStop();if(!marked)return;
            try{CallResult result=call("START",()->driver.startVideo(lease,admission.scope.requestId));synchronized(this){attempt=attempt.returnStart(result);phase=attempt.stopLatched?"STOP_LATCHED":"RUNNING_OR_POSSIBLY_RUNNING";}if(result==CallResult.REJECTED)requestStop(StopReason.START_FAILED);}
            catch(Exception unknown){synchronized(this){if(attempt.start==CaptureAttempt.Outcome.IN_FLIGHT)attempt=attempt.unknownStart();}requestStop(StopReason.START_FAILED);}
        }catch(Exception rejected){synchronized(this){history.add("ADMISSION_FAILED");}requestStop(StopReason.ADMISSION_FAILED);}
    }
    private void doStop(){
        synchronized(this){if(attempt.start==CaptureAttempt.Outcome.NOT_ATTEMPTED||attempt.originalDriverDied){phase="LOCAL_RETIRING";return;}}
        try{OwnerObservation finalOwner=guard(false);synchronized(this){long now=decisionTimeLocked(finalOwner,false);if(attempt.originalDriverDied){phase="LOCAL_RETIRING";return;}CaptureAdmission.need(attempt.stopLatched&&hardHolds.isEmpty(),"unresolved original custody blocks dispatch");if(publisherDied)history.add("ORIGINAL_PUBLISHER_DEAD_CLEANUP_ONLY");attempt=attempt.markStop();stopDecisionAt=now;phase="STOP_RPC";}
            try{CallResult result=call("STOP",()->driver.stopGlobal(admission.scope.requestId));synchronized(this){attempt=attempt.returnStop(result);stopReturnedAt=clock.nowMillis();phase="LOCAL_RETIRING";}}
            catch(Exception unknown){synchronized(this){if(attempt.stop==CaptureAttempt.Outcome.IN_FLIGHT)attempt=attempt.unknownStop();phase="HELD";}}
        }catch(Exception rejected){hold("STOP_GUARD_FAILED");}
    }
    public void onOriginalDriverDeath(Object identity){
        boolean post;synchronized(this){if(admission==null||identity!=admission.scope.originalBinderIdentity)return;attempt=attempt.driverDied(identity);history.add("ORIGINAL_DRIVER_DIED");post=latchStopLocked(StopReason.DRIVER_DIED);}if(post)postStop();
    }
    public void onOriginalPublisherDeath(Object identity){boolean post;synchronized(this){if(admission==null||identity!=admission.sink.originalPublisherIdentity)return;publisherDied=true;post=latchStopLocked(StopReason.SINK_INVALIDATED);}if(post)postStop();}
    private synchronized void hold(String reason){hardHolds.add(reason);history.add(reason);phase="HELD";}
    public void tick(){
        boolean expire=false;
        synchronized(this){if(!begun||admission==null||localRetired)return;long now=clock.nowMillis();if(now<lastNow){hold("CLOCK_REGRESSED");return;}lastNow=now;if(now>=deadline&&!attempt.stopLatched)expire=true;}
        if(expire)requestStop(StopReason.BUDGET_EXPIRED);
        boolean retire=false,join=false;
        synchronized(this){
            if(!hardHolds.isEmpty()||rpc!=null||beginTaskPending||stopTaskPending||attempt.start==CaptureAttempt.Outcome.IN_FLIGHT||attempt.stop==CaptureAttempt.Outcome.IN_FLIGHT||!attempt.stopLatched)return;
            boolean localPolicy=attempt.start==CaptureAttempt.Outcome.NOT_ATTEMPTED||attempt.originalDriverDied||attempt.stop==CaptureAttempt.Outcome.ACCEPTED&&clock.nowMillis()>=stopReturnedAt&&clock.nowMillis()-stopReturnedAt>=admission.budget.localGraceMillis;
            if(!localPolicy){phase="HELD";return;}
            if(!retirementStarted){retirementStarted=true;retire=true;phase="LOCAL_RETIRING";}
            else if(attempt.barrierCompleted&&!joinAttempted){joinAttempted=true;join=true;}
        }
        if(retire){
            boolean known=true;
            for(Monitor monitor:new Monitor[]{driverMonitor,sinkMonitor})if(monitor!=null)try{if(!monitor.retire())known=false;}catch(Exception unknown){known=false;}
            if(!known){hold("MONITOR_RETIREMENT_UNKNOWN");return;}
            synchronized(this){attempt=attempt.monitorsRetired();}
            try{worker.postBarrier(()->{synchronized(FiniteCaptureController.this){attempt=attempt.barrierCompleted();}});}catch(RuntimeException unknown){hold("BARRIER_UNKNOWN");}
        }
        if(join){
            try{if(!worker.quitAndJoin(admission.budget.joinMillis)){hold("WORKER_JOIN_UNKNOWN");return;}}
            catch(InterruptedException unknown){Thread.currentThread().interrupt();hold("WORKER_JOIN_INTERRUPTED");return;}
            catch(RuntimeException unknown){hold("WORKER_JOIN_UNKNOWN");return;}
            synchronized(this){attempt=attempt.workerJoined();}
            try{if(lease!=null){lease.close();synchronized(this){attempt=attempt.surfaceClosed();}}synchronized(this){localRetired=true;phase="LOCAL_RETIRED";}}
            catch(RuntimeException unknown){hold("SURFACE_CLOSE_UNKNOWN");}
        }
    }
    public synchronized ControllerStatus status(){
        Set<String> holds=new LinkedHashSet<>(hardHolds);if(rpc!=null)holds.add("ORIGINAL_RPC_PENDING");
        if(beginTaskPending||stopTaskPending)holds.add("ORIGINAL_WORK_PENDING");
        if(begun&&!localRetired){if(!attempt.monitorsRetired)holds.add("ORIGINAL_MONITORS_HELD");if(!attempt.barrierCompleted)holds.add("ORIGINAL_BARRIER_PENDING");if(!attempt.workerJoined)holds.add("ORIGINAL_WORKER_HELD");if(lease!=null&&!attempt.surfaceClosed)holds.add("ORIGINAL_SURFACE_HELD");}
        return new ControllerStatus(phase,rpc,attempt,stopQueued,lease!=null,localRetired,holds,history,publisherDied,startDecisionAt,stopDecisionAt);
    }
}
