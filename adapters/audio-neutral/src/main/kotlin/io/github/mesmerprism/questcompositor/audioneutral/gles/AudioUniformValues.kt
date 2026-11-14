// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral.gles

import io.github.mesmerprism.synedelica.core.AudioSnapshot
import io.github.mesmerprism.questcompositor.audioneutral.AudioSourceKey
import io.github.mesmerprism.questcompositor.audioneutral.AudioNeutralMapper
import io.github.mesmerprism.questcompositor.audioneutral.AudioTarget

enum class NeutralPass(val bodyName: String) {
    Identity("identity.frag.glsl"), BlurHorizontal("blur-horizontal.frag.glsl"),
    BlurVertical("blur-vertical.frag.glsl"), CleanRgb("clean-rgb.frag.glsl"),
    BlueOnly("clean-rgb.frag.glsl")
}

/** One immutable observation from the existing host audio owner. */
data class AudioDrawState(val source: AudioSnapshot?, val selected: AudioSourceKey?)

data class AudioUniformValues(val active: Int, val rgbRed: Float, val rgbGreen: Float,
                             val rgbBlue: Float, val blurMix: Float) {
    companion object {
        fun forDraw(state: AudioDrawState, nowNanos: Long, pass: NeutralPass): AudioUniformValues {
            val frame = AudioNeutralMapper.prepare(state.source, state.selected, nowNanos,
                if (pass == NeutralPass.BlueOnly) AudioTarget.BlueOnly else AudioTarget.CleanRgb)
            if (!frame.valid || pass == NeutralPass.Identity) return AudioUniformValues(0, 0f, 0f, 0f, 0f)
            return when (pass) {
                NeutralPass.BlurHorizontal, NeutralPass.BlurVertical ->
                    AudioUniformValues(1, 0f, 0f, 0f, frame.source!!.rms)
                else -> AudioUniformValues(1, frame.redUv, frame.greenUv, frame.blueUv, 0f)
            }
        }
    }
}
