// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

/** Closed app-side status. The last word is an explicitly unavailable vendor ACK. */
public final class SinkStatus {
    public static final int WORD_COUNT=8;
    private final long[] words;
    private SinkStatus(long[] words) { this.words=words.clone(); }
    public long[] toWords() { return words.clone(); }
    public static SinkStatus fromState(SinkAdmission state) {
        if (state == null || state.descriptor == null) throw new IllegalArgumentException("bound sink required");
        int code;
        switch (state.phase) {
            case ACTIVE: code=1; break;
            case INVALIDATED: code=2; break;
            case DRAINED: code=3; break;
            case RETIRED: code=4; break;
            default: throw new IllegalArgumentException("bound lifecycle required");
        }
        return fromWords(new long[] {1,state.descriptor.generation,code,state.readerAlive?1:0,
                state.callbackCount(),state.imageCount(),state.fenceCount(),0});
    }
    public static SinkStatus fromWords(long[] words) {
        if (words == null || words.length != WORD_COUNT || words[0] != 1 || words[1] <= 0
                || words[2] < 1 || words[2] > 4 || words[3] < 0 || words[3] > 1 || words[7] != 0
                || words[4] < 0 || words[4] > Integer.MAX_VALUE
                || words[5] < 0 || words[5] > Integer.MAX_VALUE
                || words[6] < 0 || words[6] > Integer.MAX_VALUE
                || words[6] > 0 && words[5] == 0
                || words[2] == 1 && words[3] != 1
                || words[2] >= 3 && (words[3] != 0 || words[4] != 0 || words[5] != 0 || words[6] != 0)) {
            throw new IllegalArgumentException("invalid closed app-side sink status");
        }
        return new SinkStatus(words);
    }
}
