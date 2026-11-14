// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.mesmerprism.questcompositor.audioneutral.example

import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.util.Log
import io.github.mesmerprism.questcompositor.audioneutral.gles.AudioDrawState
import io.github.mesmerprism.questcompositor.audioneutral.gles.GlesNeutralPass
import io.github.mesmerprism.questcompositor.audioneutral.gles.NeutralPass
import io.github.mesmerprism.questcompositor.audioneutral.gles.PackedTexture
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Concrete SDK render-loop seam for a dedicated GLES 3.1-capable surface.
 * The embedding app supplies shader bodies, an already owned packed 2D texture
 * and the single real audio owner's current snapshot/selection. Providers run
 * on this surface's GL thread; publish immutable state safely from other threads.
 * This sample neither creates a capture owner nor owns the supplied texture.
 */
class AudioNeutralSurfaceRenderer private constructor(
    private val shaderBody: (String) -> String,
    private val inputProvider: () -> PackedTexture?,
    private val audioProvider: () -> AudioDrawState,
    private val selectedPass: NeutralPass,
) : GLSurfaceView.Renderer {
    private var pass: GlesNeutralPass? = null
    private var width = 0
    private var height = 0

    /** A real initialization/draw error disables this context's pass and clears black. */
    @Volatile
    var failure: Exception? = null
        private set

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // A new callback may represent context loss. Old names belong to the old
        // context and must never be deleted against this newly current context.
        pass = null
        failure = null
        try {
            pass = GlesNeutralPass(selectedPass, shaderBody)
        } catch (error: Exception) {
            disableAfterFailure(error)
        }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun onDrawFrame(unused: GL10?) {
        val current = pass
        if (current == null) {
            clearBlack()
            return
        }
        try {
            val input = inputProvider()
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0)
            current.draw(input, width, height, audioProvider)
        } catch (error: Exception) {
            disableAfterFailure(error, current)
        }
    }

    /**
     * Host queues this on the GL thread and ensures it ran before pausing the
     * view. queueEvent submission alone does not establish completion or GPU
     * retirement. Host Stop/focus/lifecycle and producer synchronization remain
     * external. A later new context is initialized by onSurfaceCreated.
     */
    fun releaseOnGlThread() {
        pass?.releaseOnGlThread()
        pass = null
        clearBlack()
    }

    private fun disableAfterFailure(error: Exception, current: GlesNeutralPass? = null) {
        pass = null
        failure = error
        try {
            current?.releaseOnGlThread()
        } catch (cleanup: Exception) {
            if (cleanup !== error) error.addSuppressed(cleanup)
        }
        try {
            clearBlack()
        } catch (cleanup: Exception) {
            if (cleanup !== error) error.addSuppressed(cleanup)
        }
        Log.e("AudioNeutralSurface", "Neutral GLES pass disabled for this context", error)
    }

    private fun clearBlack() {
        // A provider may have selected its own FBO before throwing.
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0)
        GLES31.glDisable(GLES31.GL_SCISSOR_TEST)
        GLES31.glDisable(GLES31.GL_RASTERIZER_DISCARD)
        GLES31.glColorMask(true, true, true, true)
        GLES31.glClearColor(0f, 0f, 0f, 1f)
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
    }

    companion object {
        /** Install before any other setRenderer call; GLSL ES 3.1 is required. */
        fun install(
            view: GLSurfaceView,
            shaderBody: (String) -> String,
            inputProvider: () -> PackedTexture?,
            audioProvider: () -> AudioDrawState,
            pass: NeutralPass = NeutralPass.CleanRgb,
        ): AudioNeutralSurfaceRenderer {
            val renderer = AudioNeutralSurfaceRenderer(shaderBody, inputProvider, audioProvider, pass)
            view.setEGLContextClientVersion(3)
            view.setRenderer(renderer)
            // Expiry must still be evaluated when audio publication stops.
            view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            return renderer
        }
    }
}
