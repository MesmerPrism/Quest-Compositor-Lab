// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pure immutable app-side lifecycle reducer. No clock, GPU, capture RPC or retirement polling. */
public final class SinkAdmission {
    public enum Phase { UNBOUND, ACTIVE, INVALIDATED, DRAINED, RETIRED }
    public static final class Transition {
        public final SinkAdmission state;
        public final long token;
        public final boolean publishable;
        private Transition(SinkAdmission state, long token, boolean publishable) {
            this.state=state; this.token=token; this.publishable=publishable;
        }
    }
    public final Phase phase;
    public final boolean readerAlive;
    public final StereoSinkDescriptor descriptor;
    private final long highWatermark, nextToken;
    private final Set<Long> callbacks;
    private final Map<Long, Boolean> images;
    private final Map<Long, Long> fences;

    private SinkAdmission(Phase phase, boolean readerAlive, StereoSinkDescriptor descriptor,
            long highWatermark, long nextToken, Set<Long> callbacks, Map<Long, Boolean> images,
            Map<Long, Long> fences) {
        this.phase=phase; this.readerAlive=readerAlive; this.descriptor=descriptor;
        this.highWatermark=highWatermark; this.nextToken=nextToken;
        this.callbacks=Collections.unmodifiableSet(new HashSet<>(callbacks));
        this.images=Collections.unmodifiableMap(new HashMap<>(images));
        this.fences=Collections.unmodifiableMap(new HashMap<>(fences));
    }
    public static SinkAdmission empty() {
        return new SinkAdmission(Phase.UNBOUND,false,null,0,1,
                Collections.emptySet(),Collections.emptyMap(),Collections.emptyMap());
    }
    private SinkAdmission copy(Phase nextPhase, boolean alive, long serial,
            Set<Long> nextCallbacks, Map<Long, Boolean> nextImages, Map<Long, Long> nextFences) {
        return new SinkAdmission(nextPhase,alive,descriptor,highWatermark,serial,
                nextCallbacks,nextImages,nextFences);
    }
    public SinkAdmission attach(StereoSinkDescriptor next) {
        if (next == null || phase != Phase.UNBOUND && phase != Phase.RETIRED
                || next.generation <= highWatermark) {
            throw new IllegalStateException("new generation requires prior app-side retirement");
        }
        return new SinkAdmission(Phase.ACTIVE,true,next,next.generation,nextToken,
                Collections.emptySet(),Collections.emptyMap(),Collections.emptyMap());
    }
    public boolean isCurrent(StereoSinkDescriptor expected) {
        return phase == Phase.ACTIVE && readerAlive && descriptor != null && descriptor.equals(expected);
    }
    public void requireCurrent(StereoSinkDescriptor expected) {
        if (!isCurrent(expected)) throw new IllegalStateException("current live descriptor required");
    }
    public void requireDescriptor(StereoSinkDescriptor expected) {
        if (descriptor == null || !descriptor.equals(expected)) {
            throw new IllegalStateException("exact bound descriptor required");
        }
    }
    private void requireGeneration(long generation) {
        if (descriptor == null || generation != descriptor.generation || phase == Phase.RETIRED) {
            throw new IllegalStateException("current nonretired generation required");
        }
    }
    private long token() {
        if (nextToken <= 0 || nextToken == Long.MAX_VALUE) throw new IllegalStateException("ticket space exhausted");
        return nextToken;
    }
    public Transition beginCallback(StereoSinkDescriptor expected) {
        requireCurrent(expected);
        long token=token();
        Set<Long> next=new HashSet<>(callbacks); next.add(token);
        return new Transition(copy(phase,readerAlive,token+1,next,images,fences),token,true);
    }
    public Transition imageAcquired(long callback) {
        if (!callbacks.contains(callback)) throw new IllegalStateException("actual entered callback required");
        long token=token();
        boolean publishable=phase == Phase.ACTIVE && readerAlive;
        Map<Long,Boolean> next=new HashMap<>(images); next.put(token,publishable);
        return new Transition(copy(phase,readerAlive,token+1,callbacks,next,fences),token,publishable);
    }
    public Transition beginFence(long image) {
        if (phase != Phase.ACTIVE || !readerAlive || !Boolean.TRUE.equals(images.get(image))) {
            throw new IllegalStateException("live admitted image required before GPU submission");
        }
        long token=token();
        Map<Long,Long> next=new HashMap<>(fences); next.put(token,image);
        return new Transition(copy(phase,readerAlive,token+1,callbacks,images,next),token,true);
    }
    public void requireImageCurrent(StereoSinkDescriptor expected, long image) {
        requireCurrent(expected);
        if (!Boolean.TRUE.equals(images.get(image))) throw new IllegalStateException("current admitted image ticket required");
    }
    public SinkAdmission finishCallback(long token) {
        Set<Long> next=new HashSet<>(callbacks);
        if (!next.remove(token)) throw new IllegalStateException("unknown/replayed callback ticket");
        return copy(phase,readerAlive,nextToken,next,images,fences);
    }
    /** Caller must own an actual completed fence observation; there is no timeout path. */
    public SinkAdmission fenceCompleted(long token) {
        Map<Long,Long> next=new HashMap<>(fences);
        if (next.remove(token) == null) throw new IllegalStateException("unknown/replayed GPU ticket");
        return copy(phase,readerAlive,nextToken,callbacks,images,next);
    }
    /** Called only after actual AImage/import lease release, never on intent to release. */
    public SinkAdmission releaseImage(long token) {
        if (fences.containsValue(token)) throw new IllegalStateException("GPU still retains image");
        Map<Long,Boolean> next=new HashMap<>(images);
        if (next.remove(token) == null) throw new IllegalStateException("unknown/replayed image ticket");
        return copy(phase,readerAlive,nextToken,callbacks,next,fences);
    }
    public SinkAdmission invalidate(long generation) {
        requireGeneration(generation);
        return phase == Phase.ACTIVE
                ? copy(Phase.INVALIDATED,readerAlive,nextToken,callbacks,images,fences) : this;
    }
    /** Actual reader/callback admission destruction observation; not native capture Stop. */
    public SinkAdmission readerDestroyed(long generation) {
        requireGeneration(generation);
        return copy(phase == Phase.DRAINED ? phase : Phase.INVALIDATED,false,nextToken,callbacks,images,fences);
    }
    public SinkAdmission observeDrained() {
        if (phase != Phase.INVALIDATED || readerAlive || !callbacks.isEmpty() || !images.isEmpty() || !fences.isEmpty()) {
            throw new IllegalStateException("app reader, callbacks, image leases and GPU uses remain undrained");
        }
        return copy(Phase.DRAINED,false,nextToken,callbacks,images,fences);
    }
    public SinkAdmission retire() {
        if (phase != Phase.DRAINED) throw new IllegalStateException("observed app-side drain required");
        return copy(Phase.RETIRED,false,nextToken,callbacks,images,fences);
    }
    public int callbackCount() { return callbacks.size(); }
    public int imageCount() { return images.size(); }
    public int fenceCount() { return fences.size(); }
    public boolean vendorRetirementAcknowledged() { return false; }
}
