// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

public final class SinkAdmissionTest {
    private SinkAdmissionTest() {}
    static StereoSinkDescriptor descriptor(long generation) {
        return new StereoSinkDescriptor(generation, 2560,1280,1,768,4,
                new StereoSinkDescriptor.Rect(0,0,1280,1280),
                new StereoSinkDescriptor.Rect(1280,0,1280,1280),
                StereoSinkDescriptor.Origin.TOP_LEFT,0,false,false);
    }
    static void drainRejects(SinkAdmission state, String reason) {
        TestSupport.rejects(IllegalStateException.class, state::observeDrained, reason);
    }
    public static void main(String[] args) {
        StereoSinkDescriptor d = descriptor(10);
        SinkAdmission empty = SinkAdmission.empty();
        SinkAdmission active = empty.attach(d);
        TestSupport.check(active.phase == SinkAdmission.Phase.ACTIVE, "new actual reader must be active");
        TestSupport.check(empty.phase == SinkAdmission.Phase.UNBOUND, "reducer must not mutate old state");
        TestSupport.rejects(IllegalStateException.class, () -> active.attach(descriptor(11)), "live reader replacement");
        TestSupport.rejects(IllegalStateException.class, () -> active.beginCallback(descriptor(9)), "stale generation callback");
        StereoSinkDescriptor resized = new StereoSinkDescriptor(10,1280,1280,1,768,4,
                new StereoSinkDescriptor.Rect(0,0,640,1280),new StereoSinkDescriptor.Rect(640,0,640,1280),
                StereoSinkDescriptor.Origin.TOP_LEFT,0,false,false);
        TestSupport.rejects(IllegalStateException.class, () -> active.beginCallback(resized), "changed descriptor same generation");
        StereoSinkDescriptor reoriented = new StereoSinkDescriptor(10,2560,1280,1,768,4,d.left,d.right,
                StereoSinkDescriptor.Origin.BOTTOM_LEFT,0,false,false);
        TestSupport.rejects(IllegalStateException.class, () -> active.beginCallback(reoriented), "changed orientation");
        drainRejects(active, "live reader cannot drain");
        SinkAdmission.Transition callback = active.beginCallback(d);
        TestSupport.check(callback.state.callbackCount() == 1, "entered callback retained");
        SinkAdmission.Transition image = callback.state.imageAcquired(callback.token);
        TestSupport.check(image.publishable, "active acquired image admissible");
        SinkAdmission.Transition fence = image.state.beginFence(image.token);
        SinkAdmission held = fence.state.finishCallback(callback.token).invalidate(10).readerDestroyed(10);
        TestSupport.rejects(IllegalStateException.class,
                () -> held.requireImageCurrent(d,image.token), "earlier image admission cannot publish after invalidation");
        TestSupport.check(held.phase == SinkAdmission.Phase.INVALIDATED, "reader destruction disarms");
        TestSupport.check(!held.readerAlive, "reader destruction observed separately");
        drainRejects(held, "image and fence prevent drained");
        TestSupport.rejects(IllegalStateException.class, () -> held.releaseImage(image.token), "GPU fence retains image");
        TestSupport.rejects(IllegalStateException.class, () -> held.beginCallback(d), "callback after invalidation");
        TestSupport.rejects(IllegalStateException.class, () -> held.attach(descriptor(11)), "invalidation is not retirement");
        TestSupport.rejects(IllegalStateException.class, held::retire, "held image is not retired");
        SinkAdmission imageHeld = held.fenceCompleted(fence.token);
        drainRejects(imageHeld, "completed fence still has an app image lease");
        SinkAdmission drained = imageHeld.releaseImage(image.token).observeDrained();
        TestSupport.check(drained.phase == SinkAdmission.Phase.DRAINED, "all app resources drained");
        TestSupport.check(!drained.vendorRetirementAcknowledged(), "no invented vendor ACK");
        SinkAdmission retired = drained.retire();
        TestSupport.rejects(IllegalStateException.class, () -> retired.attach(descriptor(10)), "retired generation replay");
        TestSupport.rejects(IllegalStateException.class, () -> retired.attach(descriptor(9)), "older generation replay");
        SinkAdmission newer = retired.attach(descriptor(11));
        PublisherBinding oldPublisher=PublisherBinding.bind(active);
        oldPublisher.requireRequest(d);
        TestSupport.rejects(IllegalStateException.class,
                () -> oldPublisher.requireRequest(descriptor(11)), "old publisher cannot adopt new generation");
        PublisherBinding closed=oldPublisher.close();
        TestSupport.rejects(IllegalStateException.class, () -> closed.requireRequest(d), "destroyed publisher remains closed");
        TestSupport.rejects(IllegalStateException.class, () -> newer.finishCallback(callback.token), "late callback completion");
        TestSupport.rejects(IllegalStateException.class, () -> newer.fenceCompleted(fence.token), "late fence completion");
        TestSupport.rejects(IllegalStateException.class, () -> newer.invalidate(10), "old stop cannot invalidate replacement");

        // A callback entered before invalidation still owns cleanup, but can never publish/reopen.
        SinkAdmission.Transition racedCallback = newer.beginCallback(descriptor(11));
        SinkAdmission destroyed = racedCallback.state.readerDestroyed(11);
        drainRejects(destroyed, "entered callback prevents drain after reader destruction");
        SinkAdmission.Transition lateImage = destroyed.imageAcquired(racedCallback.token);
        TestSupport.check(!lateImage.publishable, "late acquisition is cleanup-only");
        TestSupport.rejects(IllegalStateException.class,
                () -> lateImage.state.beginFence(lateImage.token), "late image cannot submit GPU use");
        SinkAdmission racedClean = lateImage.state.releaseImage(lateImage.token).finishCallback(racedCallback.token);
        SinkAdmission end = racedClean.observeDrained().retire();
        TestSupport.rejects(IllegalStateException.class, () -> end.beginCallback(descriptor(11)), "retired publisher remains closed");
        TestSupport.rejects(IllegalStateException.class, () -> end.finishCallback(racedCallback.token), "callback completion replay");
        long[] expectedHeldStatus = {1,10,2,0,0,1,1,0};
        TestSupport.check(java.util.Arrays.equals(expectedHeldStatus,SinkStatus.fromState(held).toWords()),
                "wire status must expose held image/fence and no vendor ACK");
        TestSupport.check(java.util.Arrays.equals(expectedHeldStatus,
                SinkStatus.fromWords(expectedHeldStatus).toWords()), "status parse round trip");
        long[] forgedAck=expectedHeldStatus.clone(); forgedAck[7]=1;
        TestSupport.rejects(IllegalArgumentException.class,
                () -> SinkStatus.fromWords(forgedAck), "vendor ACK unavailable");
        long[] falseDrain=expectedHeldStatus.clone(); falseDrain[2]=3;
        TestSupport.rejects(IllegalArgumentException.class,
                () -> SinkStatus.fromWords(falseDrain), "wire drained with held images");
        long[] impossibleActive={1,10,1,0,0,0,0,0};
        TestSupport.rejects(IllegalArgumentException.class,
                () -> SinkStatus.fromWords(impossibleActive), "wire active with destroyed reader");
        long[] orphanFence={1,10,2,0,0,0,1,0};
        TestSupport.rejects(IllegalArgumentException.class,
                () -> SinkStatus.fromWords(orphanFence), "wire GPU hold without retained image");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> SinkStatus.fromWords(new long[9]), "wire extra status field");
        System.out.println("PASS SinkAdmissionTest: exact generations, descriptor guards, late callbacks, image/fence retirement");
    }
}
