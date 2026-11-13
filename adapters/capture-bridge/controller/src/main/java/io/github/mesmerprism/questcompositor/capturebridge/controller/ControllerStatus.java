// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.capturebridge.controller;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Helper-local facts only; external host/process custody and vendor ACK stay unresolved. */
public final class ControllerStatus {
    public final String phase,rpcInFlight;public final CaptureAttempt.Outcome start,stop;
    public final boolean stopQueued,monitorsRetired,barrierCompleted,workerJoined,surfaceAcquired,surfaceClosed,localRetired;
    public final boolean originalPublisherDied;public final long startDecisionAtMillis,stopDecisionAtMillis;
    public final boolean vendorRetirementAcknowledged=false,externalHostCustodyResolved=false,appReaderDrainConfirmed=false;
    public final Set<String> holds,history;
    ControllerStatus(String phase,String rpc,CaptureAttempt attempt,boolean queued,boolean acquired,boolean retired,Set<String> holds,Set<String> history,boolean publisherDied,long startDecision,long stopDecision){this.phase=phase;rpcInFlight=rpc;start=attempt.start;stop=attempt.stop;stopQueued=queued;monitorsRetired=attempt.monitorsRetired;barrierCompleted=attempt.barrierCompleted;workerJoined=attempt.workerJoined;surfaceAcquired=acquired;surfaceClosed=attempt.surfaceClosed;localRetired=retired;this.holds=Collections.unmodifiableSet(new LinkedHashSet<>(holds));this.history=Collections.unmodifiableSet(new LinkedHashSet<>(history));originalPublisherDied=publisherDied;startDecisionAtMillis=startDecision;stopDecisionAtMillis=stopDecision;}
}
