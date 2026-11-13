// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

import java.util.Arrays;

public final class StereoSinkDescriptorTest {
    private StereoSinkDescriptorTest() {}
    static StereoSinkDescriptor descriptor(long generation) {
        return descriptor(generation, 2560, 1280, 1, 768, 4,
                new StereoSinkDescriptor.Rect(0, 0, 1280, 1280),
                new StereoSinkDescriptor.Rect(1280, 0, 1280, 1280));
    }
    static StereoSinkDescriptor descriptor(long generation, int width, int height, int format,
            long usage, int images, StereoSinkDescriptor.Rect left, StereoSinkDescriptor.Rect right) {
        return new StereoSinkDescriptor(generation, width, height, format, usage, images,
                left, right, StereoSinkDescriptor.Origin.TOP_LEFT, 0, false, false);
    }
    public static void main(String[] args) {
        TestSupport.rejects(IllegalArgumentException.class, () -> descriptor(0), "zero generation");
        TestSupport.rejects(IllegalArgumentException.class, () -> descriptor(-1), "negative generation");
        StereoSinkDescriptor d = descriptor(41);
        long[] words = {1, 41, 2560, 1280, 1, 768, 4, 0, 0, 1280, 1280, 1280, 0, 1280, 1280, 0, 0, 0, 0};
        TestSupport.check(Arrays.equals(words, d.toWords()), "literal ABI vector");
        TestSupport.check(StereoSinkDescriptor.fromWords(words).equals(d), "descriptor round trip");
        TestSupport.check(!descriptor(42).equals(d), "generation changes exact identity");
        long[] mutable = d.toWords(); mutable[1] = 7;
        TestSupport.check(d.generation == 41 && d.toWords()[1] == 41, "no mutable backing array");
        for (int index : new int[] {0, 2, 3, 4, 5, 6, 7, 9, 11, 13, 15, 16, 17, 18}) {
            long[] bad = words.clone();
            bad[index] = index == 0 ? 2 : index == 5 ? 256 : index == 15 ? 2
                    : index == 16 ? 4 : index == 17 || index == 18 ? 2 : -1;
            TestSupport.rejects(IllegalArgumentException.class,
                    () -> StereoSinkDescriptor.fromWords(bad), "invalid word " + index);
        }
        long[] overflow = words.clone(); overflow[2] = 4294969856L;
        TestSupport.rejects(IllegalArgumentException.class,
                () -> StereoSinkDescriptor.fromWords(overflow), "lossy integer cast");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> StereoSinkDescriptor.fromWords(new long[18]), "truncated descriptor");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> StereoSinkDescriptor.fromWords(new long[20]), "extra descriptor fields");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> StereoSinkDescriptor.fromWords(null), "missing descriptor");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2560,1280,2,768,4,d.left,d.right), "unsupported format");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2560,1280,1,769,4,d.left,d.right), "changed usage");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2560,1280,1,768,1,d.left,d.right), "insufficient reader capacity");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2560,1280,1,768,65,d.left,d.right), "unbounded reader capacity");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2561,1280,1,768,4,d.left,d.right), "odd packed extent");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2560,1280,1,768,4,d.right,d.left), "reversed eyes");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2560,1280,1,768,4,d.left,d.left), "overlapping eyes");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> descriptor(41,2560,1280,1,768,4,d.left,new StereoSinkDescriptor.Rect(1281,0,1280,1280)), "out of bounds");
        TestSupport.rejects(IllegalArgumentException.class,
                () -> new StereoSinkDescriptor.Rect(Integer.MAX_VALUE,0,1280,1280), "overflow rectangle");
        StereoSinkDescriptor oriented = new StereoSinkDescriptor(41,2560,1280,1,768,4,d.left,d.right,
                StereoSinkDescriptor.Origin.BOTTOM_LEFT, 2, true, false);
        TestSupport.check(!oriented.equals(d), "orientation part of exact identity");
        TestSupport.check(StereoSinkDescriptor.fromWords(oriented.toWords()).equals(oriented), "orientation preserved");
        System.out.println("PASS StereoSinkDescriptorTest: closed layout, exact format/usage, bounds, immutable ABI");
    }
}
