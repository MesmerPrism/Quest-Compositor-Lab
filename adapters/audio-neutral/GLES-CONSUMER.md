# App-owned per-frame GLES consumer

This repository has no production Android application/render loop. Its existing
graphics harness is a desktop synthetic test. The isolated contribution here
uses the concrete Android `GLSurfaceView.Renderer` callbacks and direct `GLES31`
compile, link, uniform and draw APIs. It supplies reusable source and an actual
SDK hookup; no Activity, APK, microphone/capture owner or running pipeline is
introduced.

The [renderer](../../examples/audio-reactive/android/AudioNeutralSurfaceRenderer.kt)
constructs a [GlesNeutralPass](src/android/kotlin/io/github/mesmerprism/questcompositor/audioneutral/gles/GlesNeutralPass.kt)
on the GL thread. On every draw it obtains the host-owned packed stereo
`GL_TEXTURE_2D`, binds the view's default framebuffer and then reads one immutable
`AudioDrawState` from the existing audio owner. `SystemClock.elapsedRealtimeNanos`
is sampled after that read. The original source identity and BOOTTIME age are
checked using the already pinned mapper before direct `glUniform3f`,
`glUniform1i` and `glUniform1f` calls and two eye-local `glDrawArrays` calls.
Programs are compiled once per context, not once per audio sample.

The standalone build's `compileAndroidConsumerKotlin` task compiles both Android
sources against the pinned API34 jar; it does not execute SDK stubs. This source
set is separate from the JVM distribution. An embedding Android app must compile
the module's main and Android source roots, the exact external `AudioSnapshot.kt`
and the renderer example. Its existing audio owner supplies the current source
and selected key together through a thread-safe immutable observation.

## Minimal host hookup

Run `stageNeutralShaderAssets` and include the resulting ignored build directory
`local/audio-neutral-gradle/neutral-assets` as an Android assets source directory,
or package the five exact repository `examples/*.glsl` files under the assets
folder `quest-compositor-lab`. The stage task copies those authored bodies without
editing them. With a dedicated `GLSurfaceView`, install before any other renderer:

```kotlin
val renderer = AudioNeutralSurfaceRenderer.install(
    view = glView,
    shaderBody = { name ->
        context.assets.open("quest-compositor-lab/$name")
            .bufferedReader().use { it.readText() }
    },
    inputProvider = { textureOwner.currentPackedTextureOnGlThread() },
    audioProvider = { audioOwner.currentDrawState() },
    pass = NeutralPass.CleanRgb,
)
```

`textureOwner` and `audioOwner` above are the embedding app's existing owners,
not supplied implementations or new authority. Providers run on the view's GL
thread and must preserve its current EGL context. The audio provider only reads
immutable data; it must not alter GL state. The input provider must finish
producer synchronization before returning a live, complete, same-share-group
2D texture. Metadata must describe the actual even-width packed left/right
storage. Do not sample a texture attached to the active output framebuffer.
External/OES textures need the host's existing conversion. Input texture storage,
reuse/fences, focus/Stop and Android lifecycle remain host-owned.

Installation requests an ES3 context and continuously renders while the view is
active; the ES310 sources require actual GLES3.1 support and fail explicitly if
unsupported. This continued draw cadence evaluates expiry without new audio.
Invalid/missing/stopped/mismatched/future or older-than-250ms audio writes zero
effect controls; fresh silence also gives zero displacement/mix. Exactly 250ms
is accepted. Missing/invalid input or output dimensions clear black, and callback
or GL failures disable that context's pass, expose `failure` and log the error.
Ordinary provider/asset exceptions also disable the pass; release and black-clear
cleanup are attempted separately and cleanup failures remain suppressed on the
original exception. This error handling supplies no physical cleanup ACK.
The host must address failures; there is no success substitute.

The host queues `renderer.releaseOnGlThread()` and observes its completion before
pausing the view. A queued callback, successful draw, context loss or GL object
deletion does not ACK GPU retirement, producer release, microphone cleanup or
audio-v2/Whole closure. If the render thread stalls or the view is paused, this
source cannot guarantee immediate physical removal of a previously displayed
frame. The host's presentation/lifecycle and retirement contracts still apply.

## Pass and uniform contract

[GLES-ABI.json](GLES-ABI.json) versions this separate app-owned prefix as
`quest.compositor.audio-neutral.gles-pass.v1`. The original static shader bodies
and retail/native compositor ABI remain unchanged. Do not insert this prefix
into an unrelated compositor loader.

| Pass | Per-draw effect values |
| --- | --- |
| Identity | All zero; samples the source. |
| CleanRgb | Active when valid; red = 0.01 amplitude, blue = 0.01 RMS, green = 0. |
| BlueOnly | Active when valid; red/green = 0, blue = 0.01 RMS. |
| BlurHorizontal / BlurVertical | Mix original source with the existing fixed five-tap pass by RMS. |

All inactive controls reset on every draw, including cross-generation/key
changes and Stop. RGB preserves the authored red-right/blue-left sampling
directions. Blur mixes RGBA and clamps both source and kernel samples to the eye.
Each blur enum is one pass. A complete separable blur needs host-owned
intermediate textures/FBOs and synchronization; this example does not claim to
assemble that graph. Uniforms optimized out for a selected pass may have location
-1; all uniforms required by its declared ABI must be present at actual linking.
The pass owns only its program, VAO and sampler. It requires its original thread
and EGL context, changes GL state on a dedicated target and does not restore a
shared renderer's state.

## Headless source conformance

```text
python adapters/audio-neutral/run_gles_conformance.py --supplier /path/to/Synedelica-Spatial --gradle-home /path/to/gradle-9.4.1 --android-jar /path/to/android-34/android.jar --compiler /path/to/glslc
```

Use JDK17 and the module's pinned Kotlin2.1.0/JUnit4.13.2 dependencies. The runner
uses hidden Windows child processes, verifies exact supplier/SDK hashes, runs the
15 JVM cases, compiles the real Android sources, stages exact assets and executes
the same Kotlin shader assembler used by the consumer for all five passes. Each
vertex/fragment stage is compiled by glslc; required scalar/vector uniform types
are checked from compiler-emitted SPIR-V. The receipt records actual execution or
Gradle's exact up-to-date reuse, source/tool hashes and individual commands.

This is source, JVM, SDK declaration and shader-stage/interface evidence. It
does not execute Android or GLES, link a real driver program, upload a uniform,
produce GPU pixels, establish resource retirement or qualify headset display.
Generated classes, distributions, assets, SPIR-V and receipts remain ignored.
No private effect formulas, recovered palettes or assets are included.
