// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

import java.util.Arrays;

/** Immutable local reader contract, independent of capture pose, clock or effect semantics. */
public final class StereoSinkDescriptor {
    public static final int VERSION = 1;
    public static final int WORD_COUNT = 19;
    public enum Origin { TOP_LEFT, BOTTOM_LEFT }
    public static final class Rect {
        public final int x, y, width, height;
        public Rect(int x, int y, int width, int height) {
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                    || (long)x + width > Integer.MAX_VALUE || (long)y + height > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("nonempty bounded half-open rectangle required");
            }
            this.x=x; this.y=y; this.width=width; this.height=height;
        }
    }
    public final long generation, usage;
    public final int width, height, imageFormat, maxImages, quarterTurns;
    public final Rect left, right;
    public final Origin origin;
    public final boolean reflectX, reflectY;
    public StereoSinkDescriptor(long generation, int width, int height, int imageFormat, long usage,
            int maxImages, Rect left, Rect right, Origin origin, int quarterTurns,
            boolean reflectX, boolean reflectY) {
        if (generation <= 0 || width < 2 || width > 16384 || (width & 1) != 0
                || height <= 0 || height > 16384 || imageFormat != 1 || usage != 768L
                || maxImages < 2 || maxImages > 64 || left == null || right == null
                || origin == null || quarterTurns < 0 || quarterTurns > 3
                || left.x != 0 || left.y != 0 || left.width != width/2 || left.height != height
                || right.x != width/2 || right.y != 0 || right.width != width/2 || right.height != height) {
            throw new IllegalArgumentException("unsupported v1 RGBA/SBS reader descriptor");
        }
        this.generation=generation; this.width=width; this.height=height; this.imageFormat=imageFormat;
        this.usage=usage; this.maxImages=maxImages; this.left=left; this.right=right;
        this.origin=origin; this.quarterTurns=quarterTurns; this.reflectX=reflectX; this.reflectY=reflectY;
    }
    public long[] toWords() {
        return new long[] {VERSION, generation, width, height, imageFormat, usage, maxImages,
                left.x, left.y, left.width, left.height, right.x, right.y, right.width, right.height,
                origin == Origin.TOP_LEFT ? 0 : 1, quarterTurns, reflectX ? 1 : 0, reflectY ? 1 : 0};
    }
    public static StereoSinkDescriptor fromWords(long[] words) {
        if (words == null || words.length != WORD_COUNT || words[0] != VERSION
                || words[15] < 0 || words[15] > 1
                || words[17] < 0 || words[17] > 1 || words[18] < 0 || words[18] > 1) {
            throw new IllegalArgumentException("closed sink descriptor version/shape required");
        }
        return new StereoSinkDescriptor(words[1], integer(words[2]), integer(words[3]), integer(words[4]),
                words[5], integer(words[6]),
                new Rect(integer(words[7]), integer(words[8]), integer(words[9]), integer(words[10])),
                new Rect(integer(words[11]), integer(words[12]), integer(words[13]), integer(words[14])),
                words[15] == 0 ? Origin.TOP_LEFT : Origin.BOTTOM_LEFT, integer(words[16]), words[17] == 1, words[18] == 1);
    }
    private static int integer(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("descriptor integer overflow");
        }
        return (int)value;
    }
    @Override public boolean equals(Object other) {
        return other instanceof StereoSinkDescriptor
                && Arrays.equals(toWords(), ((StereoSinkDescriptor)other).toWords());
    }
    @Override public int hashCode() {
        return Arrays.hashCode(toWords());
    }
}
