// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral.example

import io.github.mesmerprism.questcompositor.audioneutral.AudioNeutralMapper
import io.github.mesmerprism.questcompositor.audioneutral.AudioSourceKey
import io.github.mesmerprism.questcompositor.audioneutral.AudioTarget
import io.github.mesmerprism.synedelica.core.AudioClockDomain
import io.github.mesmerprism.synedelica.core.AudioSnapshot

/** Synthetic JVM-only cases. Writes only shader defines to standard output. */
object AudioNeutralExample {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("fresh", "blue", "expired", "generation-change", "stopped")) {
            "Expected exactly one case: fresh, blue, expired, generation-change, stopped"
        }
        val source = AudioSnapshot(2, 7, 1_000_000_000L, AudioClockDomain.BootTime,
            true, 0.75f, 0.25f, 3)
        val key = AudioSourceKey(source.generation, source.sequence, source.capturedAtNanos, source.clockDomain)
        val target = if (args[0] == "blue") AudioTarget.BlueOnly else AudioTarget.CleanRgb
        val frame = AudioNeutralMapper.prepare(source, key, 1_010_000_000L, target)
        val currentKey = when (args[0]) {
            "stopped" -> null
            "generation-change" -> key.copy(generation = 3)
            else -> key
        }
        val now = if (args[0] == "expired") 1_250_000_001L else 1_020_000_000L
        print(AudioNeutralMapper.shaderDefines(frame, currentKey, now))
    }
}
