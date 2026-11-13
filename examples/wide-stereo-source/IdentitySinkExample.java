// SPDX-License-Identifier: AGPL-3.0-or-later
import io.github.mesmerprism.questcompositor.capturebridge.SinkAdmission;
import io.github.mesmerprism.questcompositor.capturebridge.StereoSinkDescriptor;

/** Inert authored fixture. No reader or media resource exists. */
public final class IdentitySinkExample {
    private IdentitySinkExample() {}
    public static void main(String[] args) {
        StereoSinkDescriptor fixture=new StereoSinkDescriptor(1,2560,1280,1,768,4,
                new StereoSinkDescriptor.Rect(0,0,1280,1280),
                new StereoSinkDescriptor.Rect(1280,0,1280,1280),
                StereoSinkDescriptor.Origin.TOP_LEFT,0,false,false);
        // Only validate the prospective descriptor. Do not attach a nonexistent reader.
        if (SinkAdmission.empty().phase != SinkAdmission.Phase.UNBOUND) throw new AssertionError("initial state");
        System.out.println("SOURCE-ONLY identity fixture: "+fixture.width+"x"+fixture.height+"; reader=absent; capture=not-started");
    }
}
