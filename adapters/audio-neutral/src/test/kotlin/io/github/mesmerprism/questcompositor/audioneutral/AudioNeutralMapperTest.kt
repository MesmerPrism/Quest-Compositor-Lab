// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral

import io.github.mesmerprism.synedelica.core.AudioClockDomain
import io.github.mesmerprism.synedelica.core.AudioSnapshot
import org.junit.Assert.*
import org.junit.Test

class AudioNeutralMapperTest {
    private val source = AudioSnapshot(2, 7, 1_000_000_000L, AudioClockDomain.BootTime,
        true, 0.75f, 0.25f, 3)
    private val key = AudioSourceKey(2, 7, 1_000_000_000L, AudioClockDomain.BootTime)

    private fun defines(frame: AudioNeutralFrame, selected: AudioSourceKey? = key,
                        now: Long = 1_000_000_000L): Map<String, String> =
        AudioNeutralMapper.shaderDefines(frame, selected, now).lineSequence()
            .filter { it.isNotBlank() }.associate {
                val parts = it.split(' ', limit = 3)
                assertEquals("#define", parts[0])
                parts[1] to parts[2]
            }

    private fun assertNeutral(frame: AudioNeutralFrame, reason: NeutralReason) {
        assertFalse(frame.valid)
        assertEquals(reason, frame.neutralReason)
        assertEquals(0f, frame.redUv, 0f)
        assertEquals(0f, frame.greenUv, 0f)
        assertEquals(0f, frame.blueUv, 0f)
        val output = defines(frame)
        assertEquals("0", output["LAB_RGB_MODE"])
        assertEquals("vec3(0.0, 0.0, 0.0)", output["LAB_RGB_STRENGTH"])
    }

    @Test fun freshControlsMapToBoundedOffsetsAndKeepOriginalSource() {
        val frame = AudioNeutralMapper.prepare(source, key, 1_010_000_000L)
        assertTrue(frame.valid)
        assertNull(frame.neutralReason)
        assertEquals(source, frame.source)
        assertEquals(key, frame.sourceKey)
        assertEquals(AudioTarget.CleanRgb, frame.target)
        assertEquals(0.0075f, frame.redUv, 0.000000001f)
        assertEquals(0f, frame.greenUv, 0f)
        assertEquals(0.0025f, frame.blueUv, 0.000000001f)
        val output = defines(frame)
        assertEquals(setOf("LAB_RGB_MODE", "LAB_RGB_EDGE", "LAB_RGB_DIRECTION", "LAB_RGB_RATE",
            "LAB_RGB_STRENGTH", "LAB_RGB_SCALE", "LAB_RGB_COVERAGE", "LAB_RGB_TIME_SECONDS"), output.keys)
        assertEquals("1", output["LAB_RGB_MODE"])
        assertEquals("0", output["LAB_RGB_EDGE"])
        assertEquals("vec3(0.0, 0.0, 0.5)", output["LAB_RGB_DIRECTION"])
        assertEquals("vec3(0.0)", output["LAB_RGB_RATE"])
        assertEquals("vec3(1.0)", output["LAB_RGB_SCALE"])
        assertEquals("vec3(1.0)", output["LAB_RGB_COVERAGE"])
        assertEquals("0.0", output["LAB_RGB_TIME_SECONDS"])
        assertEquals("vec3(0.0075, 0.0, 0.0025)", output["LAB_RGB_STRENGTH"])
    }

    @Test fun blueOnlyLeavesRedAndGreenAtIdentity() {
        val frame = AudioNeutralMapper.prepare(source, key, 1_000_000_000L, AudioTarget.BlueOnly)
        assertTrue(frame.valid)
        assertEquals(0f, frame.redUv, 0f)
        assertEquals(0f, frame.greenUv, 0f)
        assertEquals(0.0025f, frame.blueUv, 0.000000001f)
        assertEquals("vec3(0.0, 0.0, 0.0025)", defines(frame)["LAB_RGB_STRENGTH"])
    }

    @Test fun silenceIsFreshWithIdentityOffsets() {
        val frame = AudioNeutralMapper.prepare(source.copy(amplitude = 0f, rms = 0f, onsetCount = 0),
            key, 1_000_000_000L)
        assertTrue(frame.valid)
        assertEquals("1", defines(frame)["LAB_RGB_MODE"])
        assertEquals("vec3(0.0, 0.0, 0.0)", defines(frame)["LAB_RGB_STRENGTH"])
    }

    @Test fun maximumControlsStayWithinAuthoredOnePercentBound() {
        val frame = AudioNeutralMapper.prepare(source.copy(amplitude = 1f, rms = 1f, onsetCount = 255),
            key, 1_000_000_000L)
        assertTrue(frame.valid)
        assertEquals("vec3(0.01, 0.0, 0.01)", defines(frame)["LAB_RGB_STRENGTH"])
    }

    @Test fun sameExactIdentityCanBePreparedAndDrawnRepeatedlyUntilInclusiveBoundary() {
        for (now in listOf(1_000_000_000L, 1_100_000_000L, 1_250_000_000L)) {
            val frame = AudioNeutralMapper.prepare(source, key, now)
            assertTrue(frame.valid)
            assertEquals("1", defines(frame, now = now)["LAB_RGB_MODE"])
            assertEquals("1", defines(frame, now = now)["LAB_RGB_MODE"])
        }
        assertNeutral(AudioNeutralMapper.prepare(source, key, 1_250_000_001L), NeutralReason.Expired)
    }

    @Test fun cachedFreshFrameIsRecheckedAtShaderEmissionWithoutRetimestamping() {
        val frame = AudioNeutralMapper.prepare(source, key, 1_100_000_000L)
        assertEquals("1", defines(frame, now = 1_250_000_000L)["LAB_RGB_MODE"])
        assertEquals("0", defines(frame, now = 1_250_000_001L)["LAB_RGB_MODE"])
        assertEquals("vec3(0.0, 0.0, 0.0)",
            defines(frame, now = 1_250_000_001L)["LAB_RGB_STRENGTH"])
        assertEquals(1_000_000_000L, frame.source!!.capturedAtNanos)
    }

    @Test fun stopOrUnselectionNeutralizesBothPreparationAndCachedEmission() {
        assertNeutral(AudioNeutralMapper.prepare(source, null, 1_000_000_000L), NeutralReason.Unselected)
        val frame = AudioNeutralMapper.prepare(source, key, 1_000_000_000L)
        assertEquals("0", defines(frame, selected = null)["LAB_RGB_MODE"])
        assertEquals("vec3(0.0, 0.0, 0.0)", defines(frame, selected = null)["LAB_RGB_STRENGTH"])
    }

    @Test fun changedGenerationSequenceOrOriginalTimeCannotReuseCachedFrame() {
        val frame = AudioNeutralMapper.prepare(source, key, 1_000_000_000L)
        for (changed in listOf(key.copy(generation = 3), key.copy(sequence = 8),
            key.copy(capturedAtNanos = 1_000_000_001L))) {
            assertNeutral(AudioNeutralMapper.prepare(source, changed, 1_010_000_000L),
                NeutralReason.IdentityMismatch)
            assertEquals("0", defines(frame, selected = changed)["LAB_RGB_MODE"])
        }
    }

    @Test fun absentInvalidAndMalformedControlsFailClosedAndPreserveSuppliedLineage() {
        assertNeutral(AudioNeutralMapper.prepare(null, key, 1_000_000_000L), NeutralReason.MissingSource)
        assertNeutral(AudioNeutralMapper.prepare(source.copy(valid = false), key, 1_000_000_000L),
            NeutralReason.InvalidSource)
        val malformed = listOf(
            source.copy(amplitude = Float.NaN), source.copy(amplitude = Float.POSITIVE_INFINITY),
            source.copy(amplitude = Float.NEGATIVE_INFINITY), source.copy(amplitude = -0.01f),
            source.copy(amplitude = 1.01f), source.copy(rms = Float.NaN),
            source.copy(rms = Float.POSITIVE_INFINITY), source.copy(rms = Float.NEGATIVE_INFINITY),
            source.copy(rms = -0.01f), source.copy(rms = 1.01f), source.copy(onsetCount = -1),
            source.copy(onsetCount = 256), source.copy(onsetCount = Long.MAX_VALUE))
        for (bad in malformed) {
            val frame = AudioNeutralMapper.prepare(bad, key, 1_000_000_000L)
            assertNeutral(frame, NeutralReason.InvalidControls)
            assertEquals(bad, frame.source)
            assertEquals(key, frame.sourceKey)
        }
    }

    @Test fun futureAndInvalidOriginalTimesFailClosed() {
        assertNeutral(AudioNeutralMapper.prepare(source, key, 999_999_999L), NeutralReason.FutureSource)
        val futureFrame = AudioNeutralMapper.prepare(source, key, 1_000_000_000L)
        assertEquals("0", defines(futureFrame, now = 999_999_999L)["LAB_RGB_MODE"])
        for (time in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNeutral(AudioNeutralMapper.prepare(source.copy(capturedAtNanos = time),
                key.copy(capturedAtNanos = time), 1_000_000_000L), NeutralReason.InvalidIdentity)
        }
        for (bad in listOf(source.copy(generation = -1), source.copy(sequence = -1))) {
            val badKey = AudioSourceKey(bad.generation, bad.sequence, bad.capturedAtNanos, bad.clockDomain)
            assertNeutral(AudioNeutralMapper.prepare(bad, badKey, 1_000_000_000L), NeutralReason.InvalidIdentity)
        }
    }

    @Test fun zeroGenerationAndSequenceAreAdmittedAndLargeTimesDoNotOverflowAge() {
        val zero = source.copy(generation = 0, sequence = 0)
        assertTrue(AudioNeutralMapper.prepare(zero, key.copy(generation = 0, sequence = 0),
            1_000_000_000L).valid)
        assertNeutral(AudioNeutralMapper.prepare(source, key, Long.MAX_VALUE), NeutralReason.Expired)
        assertNeutral(AudioNeutralMapper.prepare(source, key, Long.MIN_VALUE), NeutralReason.FutureSource)
    }

    @Test fun rejectedFrameCannotBecomeFreshAtEmission() {
        val frame = AudioNeutralMapper.prepare(source, key, 1_250_000_001L)
        assertEquals("0", defines(frame, now = 1_000_000_000L)["LAB_RGB_MODE"])
    }
}
