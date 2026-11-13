// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

/** One publisher object can never be rebound to another reader generation. */
public final class PublisherBinding {
    public final StereoSinkDescriptor descriptor;
    private final boolean closed;
    private PublisherBinding(StereoSinkDescriptor descriptor, boolean closed) { this.descriptor=descriptor; this.closed=closed; }
    public static PublisherBinding bind(SinkAdmission state) {
        if (state == null || !state.isCurrent(state.descriptor)) throw new IllegalStateException("live sink required");
        return new PublisherBinding(state.descriptor,false);
    }
    public void requireRequest(StereoSinkDescriptor expected) {
        if (closed || !descriptor.equals(expected)) throw new IllegalStateException("exact original publisher generation required");
    }
    public PublisherBinding close() { return new PublisherBinding(descriptor,true); }
}
