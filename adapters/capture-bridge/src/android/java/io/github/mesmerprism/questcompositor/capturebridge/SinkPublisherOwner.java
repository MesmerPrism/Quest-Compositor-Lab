// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

import android.os.IBinder;
import android.os.RemoteException;
import android.view.Surface;
import java.util.NoSuchElementException;

/** Serializes the pure reducer with publication. Owns no reader, GPU queue or capture service. */
public final class SinkPublisherOwner {
    public interface SurfaceFactory {
        /** Fresh owned wrapper for the exact app-owned reader. Must be bounded and nonblocking. */
        Surface acquireOwnedWrapper(StereoSinkDescriptor expected);
    }
    public static final class ImageAdmission {
        public final long token;
        public final boolean publishable;
        private ImageAdmission(long token, boolean publishable) {
            this.token=token; this.publishable=publishable;
        }
    }
    private SinkAdmission state=SinkAdmission.empty();
    private SurfaceFactory factory;
    private IBinder client;
    private IBinder.DeathRecipient death;
    private StereoSinkDescriptor clientDescriptor;

    public synchronized void attach(StereoSinkDescriptor descriptor, SurfaceFactory surfaceFactory) {
        if (surfaceFactory == null) throw new IllegalArgumentException("app-owned surface factory required");
        SinkAdmission next=state.attach(descriptor);
        detachClient();
        state=next; factory=surfaceFactory;
    }
    public synchronized SinkAdmission snapshot() { return state; }
    public synchronized long beginCallback(StereoSinkDescriptor observed) {
        SinkAdmission.Transition next=state.beginCallback(observed); state=next.state; return next.token;
    }
    public synchronized ImageAdmission imageAcquired(long callback) {
        SinkAdmission.Transition next=state.imageAcquired(callback); state=next.state;
        return new ImageAdmission(next.token,next.publishable);
    }
    /** Admission plus bounded host publication use the same monitor as invalidation. */
    public synchronized void publishCurrent(StereoSinkDescriptor expected, long image, Runnable publication) {
        if (publication == null) throw new IllegalArgumentException("bounded publication required");
        state.requireImageCurrent(expected,image);
        publication.run();
    }
    public synchronized long beginFence(long image) {
        SinkAdmission.Transition next=state.beginFence(image); state=next.state; return next.token;
    }
    public synchronized void finishCallback(long callback) { state=state.finishCallback(callback); }
    public synchronized void fenceCompleted(long fence) { state=state.fenceCompleted(fence); }
    public synchronized void imageReleased(long image) { state=state.releaseImage(image); }
    public synchronized void invalidate(long generation) {
        state=state.invalidate(generation); detachClient();
    }
    public synchronized void readerDestroyed(long generation) {
        state=state.readerDestroyed(generation); factory=null; detachClient();
    }
    public synchronized void observeDrained() { state=state.observeDrained(); }
    public synchronized void retire() { state=state.retire(); factory=null; detachClient(); }
    public synchronized void publisherDestroyed(StereoSinkDescriptor expected) {
        // A delayed old Service/Parcel failure never invalidates a replacement reader.
        if (state.isCurrent(expected)) { state=state.invalidate(expected.generation); detachClient(); }
    }
    synchronized SinkStatus status(StereoSinkDescriptor expected) {
        state.requireDescriptor(expected); return SinkStatus.fromState(state);
    }
    synchronized StereoSinkPublisher.Snapshot acquire(StereoSinkDescriptor expected, IBinder lifetime)
            throws RemoteException {
        state.requireCurrent(expected);
        if (lifetime == null) throw new IllegalArgumentException("publisher-client lifetime Binder required");
        bindClient(expected,lifetime);
        Surface surface=null;
        try {
            surface=factory.acquireOwnedWrapper(expected);
            state.requireCurrent(expected); // Also guards reentrant factory invalidation.
            if (surface == null || !surface.isValid()) throw new IllegalStateException("current app-owned Surface unavailable");
            return new StereoSinkPublisher.Snapshot(expected,surface);
        } catch (RuntimeException failure) {
            if (surface != null) surface.release();
            if (state.isCurrent(expected)) state=state.invalidate(expected.generation);
            if (client == lifetime && expected.equals(clientDescriptor)) detachClient();
            throw failure;
        }
    }
    private void bindClient(final StereoSinkDescriptor expected, final IBinder lifetime) throws RemoteException {
        if (client != null && client != lifetime) throw new SecurityException("different publisher client already bound");
        if (client == null) {
            client=lifetime;
            clientDescriptor=expected;
            death=new IBinder.DeathRecipient() {
                @Override public void binderDied() { clientDied(expected,lifetime); }
            };
            try { lifetime.linkToDeath(death,0); }
            catch (RemoteException failure) {
                state=state.invalidate(expected.generation); detachClient(); throw failure;
            }
        }
        if (!lifetime.isBinderAlive()) {
            state=state.invalidate(expected.generation); detachClient();
            throw new RemoteException("publisher client died");
        }
    }
    private synchronized void clientDied(StereoSinkDescriptor expected, IBinder lifetime) {
        if (client == lifetime && expected.equals(clientDescriptor)) {
            if (state.isCurrent(expected)) state=state.invalidate(expected.generation);
            detachClient();
        }
    }
    private void detachClient() {
        IBinder held=client; IBinder.DeathRecipient recipient=death;
        client=null; death=null; clientDescriptor=null;
        if (held != null && recipient != null) {
            try { held.unlinkToDeath(recipient,0); }
            catch (NoSuchElementException ignored) { /* Death may already have removed the registration. */ }
        }
    }
}
