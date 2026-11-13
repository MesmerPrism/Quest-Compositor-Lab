// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;

import io.github.mesmerprism.questcompositor.capturebridge.StereoSinkDescriptor;
import io.github.mesmerprism.questcompositor.capturebridge.SinkStatus;

/** Typed ports only; no address, command bag, runtime loader or vendor ABI. */
public final class CapturePorts {
    private CapturePorts(){}
    /** Actual entry seam: establish/reuse this thread's coordinator before framework Application creation. */
    public interface Bootstrap<L,A> {
        L currentLooper();L prepareLooper();A createFrameworkApplication(L coordinator)throws Exception;
    }
    public static final class BootstrapResult<L,A> {
        public final L looper;public final A application;
        private BootstrapResult(L looper,A application){this.looper=looper;this.application=application;}
    }
    public static <L,A> BootstrapResult<L,A> bootstrap(Bootstrap<L,A> port)throws Exception{
        if(port==null)throw new IllegalArgumentException("actual bootstrap port required");
        L looper=port.currentLooper();if(looper==null)looper=port.prepareLooper();
        if(looper==null||port.currentLooper()!=looper)throw new IllegalStateException("prepared current coordinator Looper required");
        A application=port.createFrameworkApplication(looper);
        if(application==null||port.currentLooper()!=looper)throw new IllegalStateException("framework bootstrap must retain that coordinator Looper");
        return new BootstrapResult<>(looper,application);
    }
    public enum CallResult { ACCEPTED, REJECTED }
    public enum StopReason { REQUESTED,BUDGET_EXPIRED,SINK_INVALIDATED,DRIVER_DIED,START_FAILED,ADMISSION_FAILED }
    public interface Monitor { boolean retire() throws Exception; }
    public interface Driver {
        CompatibilityObservation inspect() throws Exception;
        Monitor watchOriginal(Object originalBinderIdentity,Runnable onDeath) throws Exception;
        CallResult startVideo(SinkLease sink,String requestId) throws Exception;
        CallResult stopGlobal(String requestId) throws Exception;
    }
    public interface SinkLease extends AutoCloseable {
        StereoSinkDescriptor descriptor();Object publisherIdentity();Object invocationIdentity();boolean valid();
        @Override void close();
    }
    public interface SinkClient {
        SinkLease acquire(StereoSinkDescriptor expected,Object invocationIdentity) throws Exception;
        SinkStatus currentStatus(StereoSinkDescriptor expected) throws Exception;
        Monitor watchOriginal(SinkLease sink,Runnable onDeath) throws Exception;
    }
    public interface OwnerProbe { OwnerObservation observe() throws Exception; }
    public interface MonotonicClock { long nowMillis(); }
    public interface OriginalWorker {
        /** Enqueue FIFO, never execute inline or replace a blocked worker. */
        void post(Runnable action);void postBarrier(Runnable completion);
        /** Coordinator/finisher only, after the original barrier. */
        boolean quitAndJoin(long boundedWaitMillis) throws InterruptedException;
    }
    public static final class CompatibilityObservation {
        public final CaptureAdmission.CompatibilityExpectation values;public final Object originalBinderIdentity;
        public final boolean alive,loaderVerified,proxyVerified,signatureVerified,defaultsVerified,privacyVerified,permissionGranted;
        public CompatibilityObservation(CaptureAdmission.CompatibilityExpectation values,Object binder,boolean alive,boolean loader,boolean proxy,boolean signature,boolean defaults,boolean privacy,boolean permission){this.values=values;originalBinderIdentity=binder;this.alive=alive;loaderVerified=loader;proxyVerified=proxy;signatureVerified=signature;defaultsVerified=defaults;privacyVerified=privacy;permissionGranted=permission;}
    }
    public static final class OwnerObservation {
        public final String targetId,bootId,leaseId,clockDomain,requestId,sessionId,invocationNonce;public final long nativePid,nativeStartTicks,observationSerial,observedAtMillis,cleanupValidUntilMillis;public final Object originalBinderIdentity;
        public final boolean priorCustodyResolved,soleOperatorDeclared,foreignCaptureObserved,nativeQuiet,startAuthorized,ownAttemptStopAuthorized;
        public OwnerObservation(String target,String boot,String lease,String domain,long pid,long ticks,long serial,long observed,long cleanup,Object binder,boolean prior,boolean sole,boolean foreign,boolean quiet,boolean start,boolean stop,String request,String session,String nonce){targetId=target;bootId=boot;leaseId=lease;clockDomain=domain;nativePid=pid;nativeStartTicks=ticks;observationSerial=serial;observedAtMillis=observed;cleanupValidUntilMillis=cleanup;originalBinderIdentity=binder;priorCustodyResolved=prior;soleOperatorDeclared=sole;foreignCaptureObserved=foreign;nativeQuiet=quiet;startAuthorized=start;ownAttemptStopAuthorized=stop;requestId=request;sessionId=session;invocationNonce=nonce;}
    }
}
