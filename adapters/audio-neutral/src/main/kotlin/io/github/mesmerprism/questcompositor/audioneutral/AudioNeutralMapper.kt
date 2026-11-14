// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral

import io.github.mesmerprism.synedelica.core.AudioClockDomain
import io.github.mesmerprism.synedelica.core.AudioSnapshot

/** Exact source selected by the host, including the original capture time. */
data class AudioSourceKey(val generation: Long, val sequence: Long,
                          val capturedAtNanos: Long, val clockDomain: AudioClockDomain)

enum class AudioTarget { CleanRgb, BlueOnly }
enum class NeutralReason {
    MissingSource, Unselected, InvalidIdentity, IdentityMismatch, InvalidSource,
    InvalidControls, FutureSource, Expired
}

/** Immutable source lineage and the bounded controls prepared from it. */
class AudioNeutralFrame internal constructor(
    val source: AudioSnapshot?, val sourceKey: AudioSourceKey?, val target: AudioTarget,
    val valid: Boolean, val neutralReason: NeutralReason?,
    val redUv: Float, val greenUv: Float, val blueUv: Float,
)

/**
 * Stateless authored sample: amplitude drives red UV offset and RMS drives blue.
 * The host owns monotonic source admission, selection, Stop and physical retirement.
 * Repeated draws of the same selected source are allowed while it remains fresh.
 */
object AudioNeutralMapper {
    fun prepare(source: AudioSnapshot?, selected: AudioSourceKey?, nowNanos: Long,
                target: AudioTarget = AudioTarget.CleanRgb): AudioNeutralFrame {
        val sourceKey = source?.let {
            AudioSourceKey(it.generation, it.sequence, it.capturedAtNanos, it.clockDomain)
        }
        val reason = rejectionReason(source, sourceKey, selected, nowNanos)
        val fresh = reason == null
        val red = if (fresh && target == AudioTarget.CleanRgb) offset(source!!.amplitude) else 0f
        val blue = if (fresh) offset(source!!.rms) else 0f
        return AudioNeutralFrame(source, sourceKey, target, fresh, reason, red, 0f, blue)
    }

    /**
     * Emit controls for clean-rgb.frag.glsl, checking current selection and original
     * capture age immediately before emission. A cached frame never gains a new time.
     * These are compile-time defines; this function does not upload or render them.
     */
    fun shaderDefines(frame: AudioNeutralFrame, currentKey: AudioSourceKey?, nowNanos: Long): String {
        val checked = if (frame.valid) prepare(frame.source, currentKey, nowNanos, frame.target) else null
        val fresh = checked?.valid == true
        val red = if (fresh) checked!!.redUv else 0f
        val blue = if (fresh) checked!!.blueUv else 0f
        return """
            #define LAB_RGB_MODE ${if (fresh) 1 else 0}
            #define LAB_RGB_EDGE 0
            #define LAB_RGB_DIRECTION vec3(0.0, 0.0, 0.5)
            #define LAB_RGB_RATE vec3(0.0)
            #define LAB_RGB_STRENGTH vec3($red, 0.0, $blue)
            #define LAB_RGB_SCALE vec3(1.0)
            #define LAB_RGB_COVERAGE vec3(1.0)
            #define LAB_RGB_TIME_SECONDS 0.0
        """.trimIndent() + "\n"
    }

    private fun rejectionReason(source: AudioSnapshot?, sourceKey: AudioSourceKey?,
                                selected: AudioSourceKey?, nowNanos: Long): NeutralReason? {
        if (selected == null) return NeutralReason.Unselected
        if (source == null) return NeutralReason.MissingSource
        if (source.generation < 0 || source.sequence < 0 || source.capturedAtNanos <= 0 ||
            source.clockDomain != AudioClockDomain.BootTime) return NeutralReason.InvalidIdentity
        if (sourceKey != selected) return NeutralReason.IdentityMismatch
        if (!source.valid) return NeutralReason.InvalidSource
        if (!source.amplitude.isFinite() || source.amplitude !in 0f..1f ||
            !source.rms.isFinite() || source.rms !in 0f..1f || source.onsetCount !in 0L..255L) {
            return NeutralReason.InvalidControls
        }
        if (nowNanos < source.capturedAtNanos) return NeutralReason.FutureSource
        // Both values are now positive and ordered, so subtraction cannot overflow.
        if (nowNanos - source.capturedAtNanos > 250_000_000L) return NeutralReason.Expired
        return null
    }

    private fun offset(level: Float): Float = if (level == 0f) 0f else 0.01f * level
}
