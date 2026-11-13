// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import android.app.Application;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CapturePorts.*;

/** Build-composed finite helper. Public source has no vendor/custody provider and fails closed. */
public final class FiniteShellController {
    private FiniteShellController(){}
    public interface OriginalCustodyRecorder {
        /** Private executor must durably retain original invocation/process/pipe custody before this returns. */
        void beforeInvocation(CaptureAdmission admission)throws Exception;
        void localObservation(ControllerStatus status)throws Exception;
    }
    public interface RuntimeSelection {
        String vendorPermission();
        /** Closed private input parser and exact live provider binding; never an arbitrary class/path loader. */
        RuntimeBindings bind(Application frameworkShell,File closedAdmissionInput)throws Exception;
    }
    public static final class RuntimeBindings {
        public final CaptureAdmission admission;public final Driver driver;public final SinkClient sink;public final OwnerProbe probe;public final OriginalCustodyRecorder custody;
        public RuntimeBindings(CaptureAdmission a,Driver d,SinkClient s,OwnerProbe p,OriginalCustodyRecorder c){if(a==null||d==null||s==null||p==null||c==null)throw new IllegalArgumentException("actual private runtime/custody bindings required");admission=a;driver=d;sink=s;probe=p;custody=c;}
    }
    // A private source/build composition replaces this absent binding with one reviewed typed provider.
    // No argument, property, JSON class name, native path or reflection selector can select code.
    private static final RuntimeSelection BUILD_SELECTED_PROVIDER=null;
    private static volatile Session originalSession;
    public static void main(String[] args)throws Exception{
        if(args==null||args.length!=1)throw new IllegalArgumentException("one closed private admission data file required");
        RuntimeSelection selected=BUILD_SELECTED_PROVIDER;if(selected==null)throw new IllegalStateException("no reviewed vendor and durable original-custody provider compiled in");
        File data=new File(args[0]);if(!data.isFile())throw new IllegalArgumentException("private admission data absent");
        final String permission=selected.vendorPermission();
        BootstrapResult<Looper,Application> environment=CapturePorts.bootstrap(new Bootstrap<Looper,Application>(){
            public Looper currentLooper(){return Looper.myLooper();}
            public Looper prepareLooper(){Looper.prepare();return Looper.myLooper();}
            public Application createFrameworkApplication(Looper coordinator)throws Exception{return GenuineShellApplication.create(permission);}
        });
        Application app=environment.application;RuntimeBindings bindings=selected.bind(app,data);
        if(!permission.equals(bindings.admission.compatibility.vendorPermission))throw new IllegalArgumentException("selected permission/compatibility join differs");
        CaptureAdmission admission=bindings.admission.withShell(GenuineShellApplication.identity(app,permission));admission.validate();
        if(!"android_elapsed_realtime".equals(admission.scope.clockDomain))throw new IllegalArgumentException("owner observations must use helper's declared monotonic domain");
        bindings.custody.beforeInvocation(admission); // Missing/unknown durable custody blocks all capture work.
        Session session=new Session(bindings,admission,environment.looper);originalSession=session;session.begin();Looper.loop();
    }
    private static final class Session {
        final RuntimeBindings bindings;final CaptureAdmission admission;final Handler coordinator;final Looper loop;final FiniteCaptureController controller;boolean recorderUnknown;
        Session(RuntimeBindings bindings,CaptureAdmission admission,Looper loop){this.bindings=bindings;this.admission=admission;this.loop=loop;coordinator=new Handler(loop);controller=new FiniteCaptureController(bindings.driver,bindings.sink,bindings.probe,SystemClock::elapsedRealtime,new HandlerWorker());}
        void begin(){controller.begin(admission);coordinator.post(this::tick);}
        void tick(){controller.tick();ControllerStatus status=controller.status();
            if(!recorderUnknown)try{bindings.custody.localObservation(status);}catch(Exception unknown){recorderUnknown=true;controller.requestStop(StopReason.REQUESTED);}
            if(status.localRetired&&!recorderUnknown){loop.quitSafely();return;}
            // HELD deliberately retains this exact original session/worker/Surface/provider.
            coordinator.postDelayed(this::tick,Math.min(250,admission.budget.observationMillis));
        }
    }
    private static final class HandlerWorker implements OriginalWorker {
        final HandlerThread thread=new HandlerThread("neutral-capture-original");final Handler handler;
        HandlerWorker(){thread.start();handler=new Handler(thread.getLooper());}
        public void post(Runnable action){if(!handler.post(action))throw new IllegalStateException("original worker queue rejected");}
        public void postBarrier(Runnable completion){post(completion);}
        public boolean quitAndJoin(long wait)throws InterruptedException{if(Thread.currentThread()==thread)throw new IllegalStateException("cannot join original worker from itself");if(!thread.quitSafely())return false;thread.join(wait);return !thread.isAlive();}
    }
}
