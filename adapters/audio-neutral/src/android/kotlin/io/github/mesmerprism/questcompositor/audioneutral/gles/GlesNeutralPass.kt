// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral.gles

import android.opengl.EGL14
import android.opengl.GLES31
import android.os.SystemClock

/** An already owned, same-share-group GL_TEXTURE_2D containing packed left/right eyes. */
data class PackedTexture(val textureId: Int, val width: Int, val height: Int) {
    init {
        require(textureId > 0) { "A live, host-owned texture name is required" }
        require(width >= 2 && width % 2 == 0 && height > 0) {
            "Packed stereo requires an even width >= 2 and positive height"
        }
    }
}

/**
 * One real GLES 3.1 pass on the current target, owned by a dedicated GL surface.
 * Construct, draw and release on the same GL thread and context. The host owns
 * input texture storage, producer synchronization, framebuffer selection and
 * physical retirement. BlurHorizontal/BlurVertical are individual passes;
 * chaining them requires host-owned intermediate textures and framebuffers.
 */
class GlesNeutralPass(
    private val pass: NeutralPass,
    shaderBody: (String) -> String,
) {
    private val ownerThread = Thread.currentThread()
    private val ownerContext = EGL14.eglGetCurrentContext()
    private var program = 0
    private var vertexArray = 0
    private var sampler = 0
    private var released = false
    private var texLocation = -1
    private var eyeLocation = -1
    private var orientationLocation = -1
    private var rgbLocation = -1
    private var activeLocation = -1
    private var blurLocation = -1

    init {
        check(ownerContext != EGL14.EGL_NO_CONTEXT) { "A current GLES context is required" }
        try {
            program = linkProgram(
                NeutralRuntimeShaders.vertex(shaderBody("fullscreen.vert.glsl")),
                NeutralRuntimeShaders.fragment(pass, shaderBody(pass.bodyName)),
            )
            texLocation = requiredUniform("tex")
            eyeLocation = requiredUniform("eye")
            orientationLocation = requiredUniform("screenOrientationAdjustment")
            rgbLocation = GLES31.glGetUniformLocation(program, NeutralRuntimeShaders.RGB)
            activeLocation = GLES31.glGetUniformLocation(program, NeutralRuntimeShaders.ACTIVE)
            blurLocation = GLES31.glGetUniformLocation(program, NeutralRuntimeShaders.BLUR)
            when (pass) {
                NeutralPass.CleanRgb, NeutralPass.BlueOnly -> {
                    requireLocation(rgbLocation, NeutralRuntimeShaders.RGB)
                    requireLocation(activeLocation, NeutralRuntimeShaders.ACTIVE)
                }
                NeutralPass.BlurHorizontal, NeutralPass.BlurVertical ->
                    requireLocation(blurLocation, NeutralRuntimeShaders.BLUR)
                NeutralPass.Identity -> Unit
            }
            val names = IntArray(1)
            GLES31.glGenVertexArrays(1, names, 0)
            vertexArray = names[0]
            check(vertexArray != 0) { "GLES failed to allocate the pass vertex array" }
            names[0] = 0
            GLES31.glGenSamplers(1, names, 0)
            sampler = names[0]
            check(sampler != 0) { "GLES failed to allocate the pass sampler" }
            GLES31.glSamplerParameteri(sampler, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
            GLES31.glSamplerParameteri(sampler, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
            GLES31.glSamplerParameteri(sampler, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
            GLES31.glSamplerParameteri(sampler, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
            checkGl("pass initialization")
        } catch (failure: Exception) {
            attemptCleanup(failure) { deleteOwnedObjects() }
            throw failure
        }
    }

    /**
     * Upload fresh controls on every draw, even when no new audio arrives.
     * [audioState] reads the real owner's current snapshot and selection together;
     * its result is evaluated after texture setup against Android BOOTTIME.
     * Missing input/invalid output dimensions clear the target and zero controls.
     * An invalid/expired audio value instead renders the source with zero effect.
     */
    fun draw(input: PackedTexture?, width: Int, height: Int, audioState: () -> AudioDrawState) {
        checkOwner()
        check(!released) { "Pass has been released" }
        try {
            prepareTargetState()
            GLES31.glUseProgram(program)
            GLES31.glBindVertexArray(vertexArray)
            if (input == null || width < 2 || width % 2 != 0 || height <= 0 ||
                !GLES31.glIsTexture(input.textureId)) {
                uploadZeroControls()
                clearBlack()
                return
            }
            GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, input.textureId)
            GLES31.glBindSampler(0, sampler)
            GLES31.glUniform1i(texLocation, 0)
            GLES31.glUniform4f(orientationLocation, 1f, 0f, 0f, 1f)
            checkGl("input binding")

            // Texture/provider work may block. Read selection and the clock last;
            // never substitute frame-arrival time or System.nanoTime for BOOTTIME.
            val state = audioState()
            val nowNanos = SystemClock.elapsedRealtimeNanos()
            val values = AudioUniformValues.forDraw(state, nowNanos, pass)
            GLES31.glUniform3f(rgbLocation, values.rgbRed, values.rgbGreen, values.rgbBlue)
            GLES31.glUniform1i(activeLocation, values.active)
            GLES31.glUniform1f(blurLocation, values.blurMix)
            for (eye in 0..1) {
                GLES31.glViewport(eye * (width / 2), 0, width / 2, height)
                GLES31.glUniform1f(eyeLocation, eye.toFloat())
                GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, 3)
            }
            checkGl("packed stereo draw")
        } catch (failure: Exception) {
            // Do not leave the previous image visible after a failed callback/bind/draw.
            attemptCleanup(failure) { uploadZeroControls() }
            attemptCleanup(failure) { clearBlack() }
            throw failure
        }
    }

    /** Deletes only this pass's objects; returning is not a GPU retirement ACK. */
    fun releaseOnGlThread() {
        checkOwner()
        if (released) return
        GLES31.glUseProgram(0)
        GLES31.glBindVertexArray(0)
        GLES31.glBindSampler(0, 0)
        deleteOwnedObjects()
        released = true
    }

    private fun checkOwner() {
        check(Thread.currentThread() === ownerThread && EGL14.eglGetCurrentContext() == ownerContext) {
            "Pass requires its original GL thread and context"
        }
    }

    private fun prepareTargetState() {
        GLES31.glDisable(GLES31.GL_SCISSOR_TEST)
        GLES31.glDisable(GLES31.GL_BLEND)
        GLES31.glDisable(GLES31.GL_DEPTH_TEST)
        GLES31.glDisable(GLES31.GL_STENCIL_TEST)
        GLES31.glDisable(GLES31.GL_CULL_FACE)
        GLES31.glDisable(GLES31.GL_RASTERIZER_DISCARD)
        GLES31.glColorMask(true, true, true, true)
    }

    private fun uploadZeroControls() {
        GLES31.glUniform3f(rgbLocation, 0f, 0f, 0f)
        GLES31.glUniform1i(activeLocation, 0)
        GLES31.glUniform1f(blurLocation, 0f)
    }

    private fun clearBlack() {
        GLES31.glClearColor(0f, 0f, 0f, 1f)
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
    }

    private fun requiredUniform(name: String): Int =
        GLES31.glGetUniformLocation(program, name).also { requireLocation(it, name) }

    private fun requireLocation(location: Int, name: String) {
        check(location >= 0) { "Required uniform '$name' missing from $pass" }
    }

    private fun compileShader(stage: Int, source: String): Int {
        val shader = GLES31.glCreateShader(stage)
        check(shader != 0) { "GLES failed to create shader stage $stage" }
        try {
            GLES31.glShaderSource(shader, source)
            GLES31.glCompileShader(shader)
            val status = IntArray(1)
            GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { "Shader stage $stage failed: ${GLES31.glGetShaderInfoLog(shader)}" }
            return shader
        } catch (failure: Exception) {
            attemptCleanup(failure) { GLES31.glDeleteShader(shader) }
            throw failure
        }
    }

    private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES31.GL_VERTEX_SHADER, vertexSource)
        var fragment = 0
        var linked = 0
        try {
            fragment = compileShader(GLES31.GL_FRAGMENT_SHADER, fragmentSource)
            linked = GLES31.glCreateProgram()
            check(linked != 0) { "GLES failed to create program" }
            GLES31.glAttachShader(linked, vertex)
            GLES31.glAttachShader(linked, fragment)
            GLES31.glLinkProgram(linked)
            val status = IntArray(1)
            GLES31.glGetProgramiv(linked, GLES31.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Program link failed: ${GLES31.glGetProgramInfoLog(linked)}" }
            return linked
        } catch (failure: Exception) {
            if (linked != 0) attemptCleanup(failure) { GLES31.glDeleteProgram(linked) }
            throw failure
        } finally {
            GLES31.glDeleteShader(vertex)
            if (fragment != 0) GLES31.glDeleteShader(fragment)
        }
    }

    private fun deleteOwnedObjects() {
        if (program != 0) GLES31.glDeleteProgram(program)
        if (vertexArray != 0) GLES31.glDeleteVertexArrays(1, intArrayOf(vertexArray), 0)
        if (sampler != 0) GLES31.glDeleteSamplers(1, intArrayOf(sampler), 0)
        program = 0
        vertexArray = 0
        sampler = 0
    }

    private fun checkGl(operation: String) {
        val error = GLES31.glGetError()
        check(error == GLES31.GL_NO_ERROR) { "GLES error 0x${error.toString(16)} during $operation" }
    }

    private inline fun attemptCleanup(failure: Exception, action: () -> Unit) {
        try {
            action()
        } catch (cleanup: Exception) {
            if (cleanup !== failure) failure.addSuppressed(cleanup)
        }
    }
}
