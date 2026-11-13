// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import static io.github.mesmerprism.questcompositor.capturebridge.controller.CapturePorts.*;

/** Immutable original-operation facts. Tokens never manufacture resource completion. */
public final class CaptureAttempt {
    public enum Outcome { NOT_ATTEMPTED,IN_FLIGHT,ACCEPTED,REJECTED,OUTCOME_UNKNOWN }
    public final Outcome start,stop;public final boolean stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed;
    private final Object originalBinder;
    private CaptureAttempt(Object binder,Outcome start,Outcome stop,boolean latch,boolean died,boolean monitors,boolean barrier,boolean joined,boolean closed){originalBinder=binder;this.start=start;this.stop=stop;stopLatched=latch;originalDriverDied=died;monitorsRetired=monitors;barrierCompleted=barrier;workerJoined=joined;surfaceClosed=closed;}
    public static CaptureAttempt create(Object binder){return new CaptureAttempt(binder,Outcome.NOT_ATTEMPTED,Outcome.NOT_ATTEMPTED,false,false,false,false,false,false);}
    private CaptureAttempt copy(Outcome s,Outcome t,boolean latch,boolean died,boolean monitors,boolean barrier,boolean joined,boolean closed){return new CaptureAttempt(originalBinder,s,t,latch,died,monitors,barrier,joined,closed);}
    private static void require(boolean v){if(!v)throw new IllegalStateException("original attempt/retirement ordering violation");}
    public CaptureAttempt markStart(){require(start==Outcome.NOT_ATTEMPTED&&!stopLatched&&!originalDriverDied);return copy(Outcome.IN_FLIGHT,stop,stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt returnStart(CallResult value){require(start==Outcome.IN_FLIGHT&&value!=null);return copy(value==CallResult.ACCEPTED?Outcome.ACCEPTED:Outcome.REJECTED,stop,stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt unknownStart(){require(start==Outcome.IN_FLIGHT);return copy(Outcome.OUTCOME_UNKNOWN,stop,stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt latchStop(){return copy(start,stop,true,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt markStop(){require(start!=Outcome.NOT_ATTEMPTED&&start!=Outcome.IN_FLIGHT&&stop==Outcome.NOT_ATTEMPTED&&!originalDriverDied);return copy(start,Outcome.IN_FLIGHT,true,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt returnStop(CallResult value){require(stop==Outcome.IN_FLIGHT&&value!=null);return copy(start,value==CallResult.ACCEPTED?Outcome.ACCEPTED:Outcome.REJECTED,stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt unknownStop(){require(stop==Outcome.IN_FLIGHT);return copy(start,Outcome.OUTCOME_UNKNOWN,stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt driverDied(Object binder){return binder!=originalBinder?this:copy(start,stop,stopLatched,true,monitorsRetired,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt monitorsRetired(){require(start!=Outcome.IN_FLIGHT&&stop!=Outcome.IN_FLIGHT);return copy(start,stop,stopLatched,originalDriverDied,true,barrierCompleted,workerJoined,surfaceClosed);}
    public CaptureAttempt barrierCompleted(){require(monitorsRetired);return copy(start,stop,stopLatched,originalDriverDied,monitorsRetired,true,workerJoined,surfaceClosed);}
    public CaptureAttempt workerJoined(){require(barrierCompleted);return copy(start,stop,stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,true,surfaceClosed);}
    public CaptureAttempt surfaceClosed(){require(workerJoined&&!surfaceClosed);return copy(start,stop,stopLatched,originalDriverDied,monitorsRetired,barrierCompleted,workerJoined,true);}
}
