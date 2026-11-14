// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral

import io.github.mesmerprism.questcompositor.audioneutral.gles.*
import io.github.mesmerprism.synedelica.core.AudioClockDomain
import io.github.mesmerprism.synedelica.core.AudioSnapshot
import org.junit.Assert.*
import org.junit.Test

class AudioUniformValuesTest {
    private val source = AudioSnapshot(4, 9, 1_000_000_000L, AudioClockDomain.BootTime, true, .75f, .25f, 0)
    private val key = AudioSourceKey(4, 9, 1_000_000_000L, AudioClockDomain.BootTime)
    private val state = AudioDrawState(source, key)
    private val neutral = AudioUniformValues(0, 0f, 0f, 0f, 0f)

    @Test fun rgbAndBlueUseRealMapperValuesAndBlurUsesFreshRms() {
        val rgb = AudioUniformValues.forDraw(state, 1_010_000_000L, NeutralPass.CleanRgb)
        assertEquals(1, rgb.active)
        assertEquals(.0075f, rgb.rgbRed, 1e-8f)
        assertEquals(0f, rgb.rgbGreen, 0f)
        assertEquals(.0025f, rgb.rgbBlue, 1e-8f)
        assertEquals(0f, rgb.blurMix, 0f)
        val blue = AudioUniformValues.forDraw(state, 1_010_000_000L, NeutralPass.BlueOnly)
        assertEquals(0f, blue.rgbRed, 0f)
        assertEquals(.0025f, blue.rgbBlue, 1e-8f)
        for (pass in listOf(NeutralPass.BlurHorizontal, NeutralPass.BlurVertical)) {
            val blur = AudioUniformValues.forDraw(state, 1_010_000_000L, pass)
            assertEquals(AudioUniformValues(1, 0f, 0f, 0f, .25f), blur)
        }
        assertEquals(neutral, AudioUniformValues.forDraw(state, 1_010_000_000L, NeutralPass.Identity))
    }

    @Test fun everyDrawExpiresWithoutNewAudioAndResetsAllEffects() {
        for (pass in NeutralPass.values()) {
            val boundary = AudioUniformValues.forDraw(state, 1_250_000_000L, pass)
            if (pass != NeutralPass.Identity) assertEquals(1, boundary.active)
            assertEquals(neutral, AudioUniformValues.forDraw(state, 1_250_000_001L, pass))
            assertEquals(neutral, AudioUniformValues.forDraw(state.copy(selected = null), 1_010_000_000L, pass))
            assertEquals(neutral, AudioUniformValues.forDraw(state.copy(source = null), 1_010_000_000L, pass))
            assertEquals(neutral, AudioUniformValues.forDraw(state.copy(selected = key.copy(generation = 5)), 1_010_000_000L, pass))
        }
    }

    @Test fun invalidFutureAndQuietInputsNeverLatchOldStrengths() {
        for (pass in NeutralPass.values()) {
            assertEquals(neutral, AudioUniformValues.forDraw(state, 999_999_999L, pass))
            for (bad in listOf(source.copy(valid = false), source.copy(amplitude = Float.NaN), source.copy(rms = 2f))) {
                assertEquals(neutral, AudioUniformValues.forDraw(state.copy(source = bad), 1_010_000_000L, pass))
            }
            val quiet = AudioUniformValues.forDraw(state.copy(source = source.copy(amplitude = 0f, rms = 0f)), 1_010_000_000L, pass)
            assertEquals(0f, quiet.rgbRed, 0f); assertEquals(0f, quiet.rgbBlue, 0f); assertEquals(0f, quiet.blurMix, 0f)
        }
    }
}
