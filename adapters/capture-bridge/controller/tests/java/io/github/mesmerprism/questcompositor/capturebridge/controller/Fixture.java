// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import io.github.mesmerprism.questcompositor.capturebridge.*;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CapturePorts.*;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CaptureAdmission.*;

final class Fixture {
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void rejects(Runnable action,String m){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError(m+": forbidden operation accepted");}
    static String hash(char c){char[] a=new char[64];Arrays.fill(a,c);return "sha256:"+new String(a);}
    final Object binder=new Object(),publisher=new Object();final List<String> events=Collections.synchronizedList(new ArrayList<String>());
    final Clock clock=new Clock();final Worker worker=new Worker();final DriverPort driver=new DriverPort();final SinkPort sink=new SinkPort();final Probe probe=new Probe();
    final StereoSinkDescriptor descriptor=new StereoSinkDescriptor(41,2560,1280,1,768,4,new StereoSinkDescriptor.Rect(0,0,1280,1280),new StereoSinkDescriptor.Rect(1280,0,1280,1280),StereoSinkDescriptor.Origin.TOP_LEFT,0,false,false);
    final CompatibilityExpectation compatibility=new CompatibilityExpectation("synthetic-firmware","synthetic-api","synthetic-abi",hash('a'),new Artifact(hash('b'),123),new Service("example.fixture.driver","example.fixture.driver.DriverService","example.fixture.CAPTURE",1001,true,true),"example.fixture.TypedCapture",1,2,"example.fixture.CAPTURE","typed-loader-v1","signature-v1","defaults-v1","normal-privacy-v1",LocalWrapperPolicy.ACCEPTED_STOP_GRACE_OR_ORIGINAL_DEATH);
    final FiniteCaptureController controller=new FiniteCaptureController(driver,sink,probe,clock,worker);
    long runMillis=100,actionUntil=5000,cleanupUntil=8000;
    CaptureAdmission admission(){return new CaptureAdmission(compatibility,new SinkExpectation(new Service("example.fixture.app","example.fixture.app.Sink","android.permission.DUMP",1002,true,true),new Artifact(hash('c'),456),77,88,new int[]{1002,1002,1002,1002},("example.fixture.app\0").getBytes(java.nio.charset.StandardCharsets.UTF_8),descriptor,publisher),new Scope("fixture-target","fixture-boot","fixture-lease","fixture-monotonic",5,100,10,actionUntil,cleanupUntil,binder,"fixture-request","fixture-session","0123456789abcdef0123456789abcdef",true,true,true),new Budget(runMillis,200,100,50,LocalWrapperPolicy.ACCEPTED_STOP_GRACE_OR_ORIGINAL_DEATH),new ShellIdentity(2000,2000,2000,"com.android.shell","com.android.shell","com.android.shell",true,true,true));}
    void begin(){controller.begin(admission());worker.all();}
    void finish(){clock.now+=60;controller.tick();worker.all();controller.tick();}
    static final class Clock implements MonotonicClock {volatile long now=1000;public long nowMillis(){return now;}}
    final class Worker implements OriginalWorker {
        final ArrayDeque<Runnable> queue=new ArrayDeque<>();boolean joinOk=true,interruptJoin,dropBarrier,throwJoin;
        public synchronized void post(Runnable action){queue.add(action);}
        public void postBarrier(final Runnable action){events.add("barrier-queued");if(!dropBarrier)post(()->{events.add("barrier");action.run();});}
        void one(){Runnable action; synchronized(this){action=queue.poll();}if(action!=null)action.run();}
        void all(){for(int i=0;i<50;i++){synchronized(this){if(queue.isEmpty())return;}one();}throw new AssertionError("unbounded queue");}
        public boolean quitAndJoin(long wait)throws InterruptedException{events.add("join");if(interruptJoin)throw new InterruptedException("synthetic join");if(throwJoin)throw new IllegalStateException("synthetic unknown join");return joinOk;}
    }
    final class Watch implements Monitor {final String name;boolean ok=true,throwsRetire;Runnable death;
        Watch(String name){this.name=name;}public boolean retire()throws Exception{events.add("unlink-"+name);if(throwsRetire)throw new Exception("synthetic unlink");return ok;}}
    final class DriverPort implements Driver {
        volatile int starts,stops,inspects;volatile boolean privacy=true,unknownStart,unknownStop,alive=true;CallResult startResult=CallResult.ACCEPTED,stopResult=CallResult.ACCEPTED;
        CountDownLatch startEntered,startRelease,stopEntered,stopRelease;final Watch watch=new Watch("driver");Runnable duringInspect;
        public CompatibilityObservation inspect(){inspects++;if(duringInspect!=null)duringInspect.run();return new CompatibilityObservation(compatibility,binder,alive,true,true,true,true,privacy,true);}
        public Monitor watchOriginal(Object original,Runnable callback){check(original==binder,"watch wrong binder");watch.death=callback;return watch;}
        public CallResult startVideo(SinkLease lease,String request)throws Exception{starts++;events.add("start");if(startEntered!=null){startEntered.countDown();startRelease.await();}if(unknownStart)throw new Exception("synthetic partial Start");return startResult;}
        public CallResult stopGlobal(String request)throws Exception{stops++;events.add("stop");if(stopEntered!=null){stopEntered.countDown();stopRelease.await();}if(unknownStop)throw new Exception("synthetic unknown Stop");return stopResult;}
        void die(){alive=false;if(watch.death!=null)watch.death.run();}
    }
    final class Lease implements SinkLease {
        final Object invocation;int closes,identityReads;boolean badDescriptor,valid=true,closeThrows;
        Lease(Object token){invocation=token;}
        public StereoSinkDescriptor descriptor(){if(!badDescriptor)return descriptor;long[] w=descriptor.toWords();w[1]++;return StereoSinkDescriptor.fromWords(w);}
        public Object publisherIdentity(){return publisher;}public Object invocationIdentity(){identityReads++;if(identityReads==sink.identityHookAt&&sink.identityHook!=null)sink.identityHook.run();return invocation;}public boolean valid(){if(sink.duringValidity!=null){Runnable hook=sink.duringValidity;sink.duringValidity=null;hook.run();}return valid;}
        public void close(){closes++;events.add("close");if(closeThrows)throw new IllegalStateException("synthetic unknown close");}
    }
    final class SinkPort implements SinkClient {
        Lease lease;final Watch watch=new Watch("sink");int phase=1,identityHookAt;boolean wrongLifetime;Runnable duringStatus,duringValidity,identityHook;
        public SinkLease acquire(StereoSinkDescriptor expected,Object invocation){lease=new Lease(wrongLifetime?new Object():invocation);return lease;}
        public SinkStatus currentStatus(StereoSinkDescriptor expected){if(duringStatus!=null)duringStatus.run();return SinkStatus.fromWords(new long[]{1,41,phase,phase==1?1:0,0,0,0,0});}
        public Monitor watchOriginal(SinkLease owned,Runnable callback){watch.death=callback;return watch;}
        void die(){phase=2;lease.valid=false;watch.death.run();}
    }
    final class Probe implements OwnerProbe {long serial=10;boolean foreign,quiet=true,wrongBoot,repeat,wrongNonce;Object observedBinder=binder;
        public OwnerObservation observe(){if(!repeat)serial++;return new OwnerObservation("fixture-target",wrongBoot?"replacement-boot":"fixture-boot","fixture-lease","fixture-monotonic",5,100,serial,clock.now,8000,observedBinder,true,true,foreign,quiet,true,true,"fixture-request","fixture-session",wrongNonce?"fedcba9876543210fedcba9876543210":"0123456789abcdef0123456789abcdef");}}
}
