// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral.gles

/** App-owned GLES31 pass ABI. Never prepend this to a retail compositor loader. */
object NeutralRuntimeShaders {
    const val ABI = "quest.compositor.audio-neutral.gles-pass.v1"
    const val RGB = "labAudioRgb"
    const val ACTIVE = "labAudioActive"
    const val BLUR = "labAudioBlurMix"

    fun vertex(body: String): String = """
        #version 310 es
        precision highp float;
        precision highp int;
        uniform float eye;
        uniform vec4 screenOrientationAdjustment;
    """.trimIndent() + "\n" + body

    fun fragment(pass: NeutralPass, body: String): String {
        val prefix = """
            #version 310 es
            precision highp float;
            precision highp int;
            layout(binding = 0) uniform sampler2D tex;
            uniform int frameId;
            uniform highp vec3 $RGB;
            uniform int $ACTIVE;
            uniform highp float $BLUR;
        """.trimIndent() + "\n"
        return when (pass) {
            NeutralPass.CleanRgb, NeutralPass.BlueOnly -> prefix + """
                #define LAB_RGB_MODE $ACTIVE
                #define LAB_RGB_EDGE 0
                #define LAB_RGB_DIRECTION vec3(0.0, 0.0, 0.5)
                #define LAB_RGB_RATE vec3(0.0)
                #define LAB_RGB_STRENGTH $RGB
                #define LAB_RGB_SCALE vec3(1.0)
                #define LAB_RGB_COVERAGE vec3(1.0)
                #define LAB_RGB_TIME_SECONDS 0.0
            """.trimIndent() + "\n" + body
            NeutralPass.BlurHorizontal, NeutralPass.BlurVertical -> prefix +
                "#define main labKernelMain\n" + body + "\n#undef main\n" + """
                    void main() {
                        labKernelMain();
                        highp vec2 size = vec2(textureSize(tex, 0));
                        highp float side = step(0.5, texCoord.x);
                        highp vec2 lo = vec2(side * 0.5, 0.0) + 0.5 / size;
                        highp vec2 hi = vec2((side + 1.0) * 0.5, 1.0) - 0.5 / size;
                        highp vec4 sourceRgba = texture(tex, clamp(texCoord, lo, hi));
                        outColor = mix(sourceRgba, outColor, clamp($BLUR, 0.0, 1.0));
                    }
                """.trimIndent() + "\n"
            NeutralPass.Identity -> prefix + body
        }
    }
}
