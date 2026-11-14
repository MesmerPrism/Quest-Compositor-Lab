// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral.example

import io.github.mesmerprism.questcompositor.audioneutral.gles.NeutralPass
import io.github.mesmerprism.questcompositor.audioneutral.gles.NeutralRuntimeShaders
import java.io.File

/** Compiler fixture: assembles the exact source used by GlesNeutralPass, no GL context. */
object AudioUniformShaderExample {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "stage pass shader-body-file required" }
        val pass = NeutralPass.valueOf(args[1])
        val body = File(args[2])
        val expected = if (args[0] == "vertex") "fullscreen.vert.glsl" else pass.bodyName
        require(body.name == expected)
        print(when (args[0]) {
            "vertex" -> NeutralRuntimeShaders.vertex(body.readText())
            "fragment" -> NeutralRuntimeShaders.fragment(pass, body.readText())
            else -> error("Unknown shader stage")
        })
    }
}
