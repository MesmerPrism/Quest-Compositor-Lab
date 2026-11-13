// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CapturePorts.*;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CaptureAdmission.*;

public final class CaptureAttemptTest {
    private CaptureAttemptTest(){}
    private static final class RecordingBootstrap implements Bootstrap<Object,String>{
        Object looper;int prepares;final java.util.List<String> order=new java.util.ArrayList<>();
        RecordingBootstrap(boolean existing){if(existing)looper=new Object();}
        public Object currentLooper(){return looper;}
        public Object prepareLooper(){Fixture.check(looper==null,"duplicate prepare forbidden");prepares++;order.add("prepare");looper=new Object();return looper;}
        public String createFrameworkApplication(Object expected){Fixture.check(looper!=null&&expected==looper,"framework bootstrap requires an already prepared/reused coordinator Looper");order.add("framework");return "actual-factory-result";}
    }
    private static void bootstrapOrder()throws Exception{
        RecordingBootstrap fresh=new RecordingBootstrap(false);BootstrapResult<Object,String> created=CapturePorts.bootstrap(fresh);
        Fixture.check(fresh.prepares==1&&created.looper==fresh.looper&&fresh.order.equals(java.util.Arrays.asList("prepare","framework")),"prepare precedes framework exactly once");
        RecordingBootstrap existing=new RecordingBootstrap(true);Object original=existing.looper;BootstrapResult<Object,String> reused=CapturePorts.bootstrap(existing);
        Fixture.check(existing.prepares==0&&reused.looper==original&&existing.order.equals(java.util.Arrays.asList("framework")),"existing coordinator reused without duplicate prepare");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("bootstrap")){bootstrapOrder();return;}
        bootstrapOrder();
        Object binder=new Object();CaptureAttempt empty=CaptureAttempt.create(binder);
        Fixture.rejects(empty::markStop,"Stop before own attempted Start");
        CaptureAttempt a=empty.markStart();Fixture.check(a.start==CaptureAttempt.Outcome.IN_FLIGHT,"attempt marker before call");
        Fixture.rejects(a::markStart,"no Start replay");Fixture.rejects(a::markStop,"blocked Start cannot be replaced");
        a=a.unknownStart().latchStop().markStop().returnStop(CallResult.REJECTED);
        Fixture.check(a.start==CaptureAttempt.Outcome.OUTCOME_UNKNOWN&&a.stop==CaptureAttempt.Outcome.REJECTED,"unknown Start distinct from rejected Stop");
        Fixture.rejects(a::markStop,"no Stop replay");Fixture.rejects(a::workerJoined,"join requires actual barrier");
        Fixture.check(!a.driverDied(new Object()).originalDriverDied,"replacement death ignored");
        a=a.driverDied(binder).monitorsRetired().barrierCompleted().workerJoined().surfaceClosed();
        Fixture.check(a.surfaceClosed&&!empty.surfaceClosed,"immutable ordered local observations");
        Fixture f=new Fixture();CaptureAdmission accepted=f.admission();accepted.validate();
        for(int uid:new int[]{0,1000,10001}){
            ShellIdentity bad=new ShellIdentity(uid,uid,uid,"com.android.shell","com.android.shell","com.android.shell",true,true,true);
            Fixture.rejects(()->accepted.withShell(bad).validate(),"genuine Shell UID/eUID");
        }
        Fixture.rejects(()->accepted.withShell(new ShellIdentity(2000,2000,2000,"com.android.shell","example.other","com.android.shell",true,true,true)).validate(),"op-package mismatch");
        Fixture.rejects(()->accepted.withShell(new ShellIdentity(2000,2000,2000,"com.android.shell","com.android.shell","com.android.shell",true,false,true)).validate(),"DUMP absent");
        Fixture.rejects(()->accepted.withShell(new ShellIdentity(2000,2000,2000,"com.android.shell","com.android.shell","com.android.shell",true,true,false)).validate(),"vendor permission absent");
        int[] row=accepted.sink.uidRow();row[0]=0;Fixture.check(accepted.sink.uidRow()[0]==1002,"defensive identity rows");
        System.out.println("PASS CaptureAttemptTest: exact identity, one-attempt outcomes and ordered local retirement");
    }
}
