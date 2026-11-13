// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CapturePorts.*;

public final class FiniteCaptureControllerTest {
    private FiniteCaptureControllerTest(){}
    private static void finalStartActionExpiry(){
        Fixture f=new Fixture();f.runMillis=10000;f.actionUntil=1050;f.sink.identityHookAt=3;f.sink.identityHook=()->f.clock.now=1051;f.begin();
        Fixture.check(f.driver.starts==0&&f.driver.stops==0,"final action expiry below run deadline must prevent Start");
    }
    private static void finalStopCleanupExpiry(){
        Fixture f=new Fixture();f.actionUntil=1050;f.cleanupUntil=1050;f.begin();f.sink.identityHookAt=5;f.sink.identityHook=()->f.clock.now=1051;f.controller.requestStop(StopReason.REQUESTED);f.worker.all();
        Fixture.check(f.driver.stops==0&&f.sink.lease.closes==0&&f.controller.status().stopDecisionAtMillis==0,"final cleanup expiry while observation is fresh must prevent global Stop and retain custody");
    }
    private static void finalStopObservationExpiry(){
        Fixture f=new Fixture();f.begin();f.sink.identityHookAt=5;f.sink.identityHook=()->f.clock.now=1201;f.controller.requestStop(StopReason.REQUESTED);f.worker.all();
        Fixture.check(f.driver.stops==0&&f.sink.lease.closes==0&&f.controller.status().stopDecisionAtMillis==0,"final observation age must prevent global Stop and retain custody");
    }
    private static void finalPublisherDeath(){
        Fixture f=new Fixture();final boolean[] atomic={false};f.sink.identityHookAt=3;
        f.sink.identityHook=()->{f.controller.onOriginalPublisherDeath(f.publisher);ControllerStatus s=f.controller.status();atomic[0]=s.originalPublisherDied&&s.stopQueued;};f.begin();
        Fixture.check(f.driver.starts==0,"known publisher death after final lease check must prevent Start");
        Fixture.check(atomic[0],"publisher invalidation and Stop latch must be one observable transition");
        Fixture.check(f.driver.stops==0&&f.controller.status().startDecisionAtMillis==0,"no invented attempt/decision after death");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1){switch(args[0]){case "action":finalStartActionExpiry();break;case "cleanup":finalStopCleanupExpiry();break;case "age":finalStopObservationExpiry();break;case "publisher":finalPublisherDeath();break;default:throw new IllegalArgumentException("unknown regression");}return;}
        finalStartActionExpiry();finalStopCleanupExpiry();finalStopObservationExpiry();finalPublisherDeath();
        Fixture rejected=new Fixture();rejected.driver.startResult=CallResult.REJECTED;rejected.begin();
        Fixture.check(rejected.driver.starts==1&&rejected.driver.stops==1,"rejected/possibly partial Start still has one own Stop");
        rejected.controller.requestStop(StopReason.REQUESTED);rejected.worker.all();Fixture.check(rejected.driver.stops==1,"duplicate stop request");
        rejected.finish();ControllerStatus done=rejected.controller.status();
        Fixture.check(done.localRetired&&done.start==CaptureAttempt.Outcome.REJECTED&&!done.vendorRetirementAcknowledged&&!done.externalHostCustodyResolved&&!done.appReaderDrainConfirmed,"local facts cannot become native/host/app retirement");
        Fixture.check(rejected.events.indexOf("unlink-driver")<rejected.events.indexOf("barrier")&&rejected.events.indexOf("barrier")<rejected.events.indexOf("join")&&rejected.events.indexOf("join")<rejected.events.indexOf("close"),"actual retirement order");

        Fixture unknown=new Fixture();unknown.driver.unknownStart=true;unknown.begin();unknown.finish();
        Fixture.check(unknown.driver.starts==1&&unknown.driver.stops==1&&unknown.controller.status().start==CaptureAttempt.Outcome.OUTCOME_UNKNOWN,"throwing Start retained as unknown");
        Fixture betweenCalls=new Fixture();betweenCalls.sink.duringValidity=()->{betweenCalls.controller.requestStop(StopReason.REQUESTED);betweenCalls.controller.tick();Fixture.check(!betweenCalls.controller.status().monitorsRetired,"unfinished original admission task cannot retire monitors between RPCs");};betweenCalls.begin();

        Fixture blocked=new Fixture();blocked.driver.startEntered=new CountDownLatch(1);blocked.driver.startRelease=new CountDownLatch(1);
        blocked.controller.begin(blocked.admission());Thread original=new Thread(blocked.worker::one);original.start();
        Fixture.check(blocked.driver.startEntered.await(2,TimeUnit.SECONDS),"original Start entered");
        blocked.clock.now=1200;blocked.controller.tick();blocked.controller.requestStop(StopReason.REQUESTED);
        Fixture.check(blocked.driver.starts==1&&blocked.driver.stops==0&&blocked.sink.lease.closes==0&&blocked.controller.status().start==CaptureAttempt.Outcome.IN_FLIGHT,"blocked original retains Surface/worker and queued Stop");
        blocked.driver.startRelease.countDown();original.join(2000);Fixture.check(!original.isAlive(),"original returned");blocked.worker.all();blocked.finish();Fixture.check(blocked.driver.stops==1,"same original queue Stop after Start return");

        for(boolean thrown:new boolean[]{false,true}){
            Fixture stop=new Fixture();stop.driver.stopResult=CallResult.REJECTED;stop.driver.unknownStop=thrown;stop.begin();stop.controller.requestStop(StopReason.REQUESTED);stop.worker.all();stop.clock.now=4000;stop.controller.tick();
            Fixture.check(!stop.controller.status().localRetired&&stop.sink.lease.closes==0,"rejected/unknown Stop has no timed success path");
            stop.driver.die();stop.controller.tick();stop.worker.all();stop.controller.tick();Fixture.check(stop.driver.stops==1&&!stop.controller.status().vendorRetirementAcknowledged,"original death never retries/ACKs");
        }
        Fixture stoppedBlock=new Fixture();stoppedBlock.begin();stoppedBlock.driver.stopEntered=new CountDownLatch(1);stoppedBlock.driver.stopRelease=new CountDownLatch(1);stoppedBlock.controller.requestStop(StopReason.REQUESTED);
        Thread stopWorker=new Thread(stoppedBlock.worker::one);stopWorker.start();Fixture.check(stoppedBlock.driver.stopEntered.await(2,TimeUnit.SECONDS),"original Stop entered");
        stoppedBlock.driver.die();stoppedBlock.clock.now=3000;stoppedBlock.controller.tick();Fixture.check(stoppedBlock.sink.lease.closes==0,"death cannot replace blocked Stop completion");
        stoppedBlock.driver.stopRelease.countDown();stopWorker.join(2000);stoppedBlock.controller.tick();stoppedBlock.worker.all();stoppedBlock.controller.tick();

        Fixture foreign=new Fixture();foreign.probe.foreign=true;foreign.begin();Fixture.check(foreign.driver.starts==0&&foreign.driver.stops==0,"foreign capture before Start");
        Fixture wrongInvocation=new Fixture();wrongInvocation.probe.wrongNonce=true;wrongInvocation.begin();Fixture.check(wrongInvocation.driver.starts==0,"owner observation from another invocation cannot authorize Start");
        Fixture adjacent=new Fixture();adjacent.sink.duringStatus=()->adjacent.probe.wrongBoot=true;adjacent.begin();Fixture.check(adjacent.driver.starts==0,"owner rechecked after sink RPC");
        Fixture stale=new Fixture();stale.sink.phase=2;stale.begin();Fixture.check(stale.driver.starts==0,"non-ACTIVE sink prevents Start");
        Fixture lifetime=new Fixture();lifetime.sink.wrongLifetime=true;lifetime.begin();Fixture.check(lifetime.driver.starts==0,"foreign invocation lifetime prevents Start");
        Fixture privacy=new Fixture();privacy.driver.privacy=false;privacy.begin();Fixture.check(privacy.driver.starts==0&&privacy.driver.stops==0,"privacy defaults reject before capture calls");
        Fixture owner=new Fixture();owner.begin();owner.probe.wrongBoot=true;owner.controller.requestStop(StopReason.REQUESTED);owner.worker.all();owner.finish();Fixture.check(owner.driver.stops==0&&owner.sink.lease.closes==0,"owner changed before global Stop retains custody");
        Fixture serial=new Fixture();serial.begin();serial.probe.repeat=true;serial.controller.requestStop(StopReason.REQUESTED);serial.worker.all();Fixture.check(serial.driver.stops==0,"duplicate observation serial prevents later Stop");
        Fixture changed=new Fixture();changed.begin();changed.sink.lease.badDescriptor=true;changed.controller.requestStop(StopReason.REQUESTED);changed.worker.all();Fixture.check(changed.driver.stops==0,"descriptor replacement blocks Stop");
        Fixture noQuietAtStop=new Fixture();noQuietAtStop.begin();noQuietAtStop.probe.quiet=false;noQuietAtStop.controller.requestStop(StopReason.REQUESTED);noQuietAtStop.worker.all();Fixture.check(noQuietAtStop.driver.stops==1,"own Stop does not require quiet native capture");
        Fixture publisherDeath=new Fixture();publisherDeath.begin();publisherDeath.sink.die();publisherDeath.worker.all();Fixture.check(publisherDeath.driver.stops==1,"original publisher death latches own cleanup");
        Fixture wrongDeath=new Fixture();wrongDeath.begin();wrongDeath.controller.onOriginalDriverDeath(new Object());Fixture.check(!wrongDeath.controller.status().stopQueued,"replacement Binder death ignored");

        for(String failure:new String[]{"unlink","barrier","join","join-exception","interrupt","close"}){
            Fixture held=new Fixture();held.begin();if(failure.equals("unlink"))held.driver.watch.ok=false;if(failure.equals("barrier"))held.worker.dropBarrier=true;if(failure.equals("join"))held.worker.joinOk=false;if(failure.equals("join-exception"))held.worker.throwJoin=true;if(failure.equals("interrupt"))held.worker.interruptJoin=true;if(failure.equals("close"))held.sink.lease.closeThrows=true;
            held.controller.requestStop(StopReason.REQUESTED);held.worker.all();held.finish();Thread.interrupted();held.driver.die();held.controller.tick();held.worker.all();held.controller.tick();Thread.interrupted();
            Fixture.check(!held.controller.status().localRetired,"unknown "+failure+" is not erased by later death");
            Fixture.check(held.sink.lease.closes==(failure.equals("close")?1:0),"no early/replayed close for "+failure);
        }
        System.out.println("PASS FiniteCaptureControllerTest: partial/blocked calls, adjacent guards, sole Stop and retained retirement custody");
    }
}
