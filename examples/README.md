# Neutral shader bodies

These examples were independently authored for this repository. No starter
shader or downstream private implementation supplied their code. The public
configuration reference is `rusty-quest/docs/RGB_CHANNEL_TRANSFORM.md` at
revision `fb5781031a51bd0709a5941e0a271360b8db49d7`.

The loader supplies ES310 declarations: vertex `float eye` (0/1) and
`vec4 screenOrientationAdjustment`, fragment binding-0 `sampler2D tex` and
`int frameId`. Bodies own location-0 `texCoord` and `outColor`. The triangle
uses `gl_VertexID`, and each eye draws into its own output viewport. Input is
packed side-by-side RGBA with an even width and equal eye dimensions. All
sampling clamps to the current eye's half-texel limits; no seam tap can read
the other eye. The fixture interprets orientation as a column-major 2x2 matrix
on clip XY with identity `(1,0,0,1)`; native integration remains unqualified.

Identity samples linear RGBA without gamma conversion or alpha reassociation.
Blur applies the conventional separable five-tap binomial approximation
`[1,4,6,4,1]/16`: horizontal, vertical, then identity. Each component, including
alpha, is filtered independently. Straight-alpha inputs can therefore show
colour fringes; use premultiplied data when the consumer requires it.

Clean RGB implements an authored consumer formula: scale eye-local coordinates
about the image centre and add a constant per-channel directional offset. It
does not derive strength from image content. Alpha always comes from the
undisplaced source. Explicit highp bilinear interpolation avoids relying on
the driver's fixed-function subtexel interpolation precision. The public
normalization ranges apply to direction turns, rate, strength, scale and
coverage. Unknown mode/edge codes become bypass/clamp; nonfinite channel
values use zero direction/rate/strength and unit scale/coverage. Linked mode
uses normalized red controls for all channels.

Consumers can prepend `LAB_RGB_MODE`, `LAB_RGB_EDGE`, `LAB_RGB_DIRECTION`,
`LAB_RGB_RATE`, `LAB_RGB_STRENGTH`, `LAB_RGB_SCALE`, `LAB_RGB_COVERAGE` and
`LAB_RGB_TIME_SECONDS` definitions. Vector controls are `vec3` expressions.
The body defaults provide a bounded neutral sample: independent mode, clamp,
directions `(0,0,0.5)` turns, strengths `(0.01,0,0.01)` in eye-local UV,
zero rates and unit scale/coverage. Red samples 0.01 UV to the right and blue
samples 0.01 UV to the left; visible features move in the opposite directions.
green and alpha remain undisplaced. Explicitly setting strength `vec3(0.0)`
with unit scale is identity for every edge mode.
Nonunit image scale is an independent transform. Coverage is a centred square
of the configured eye-local width; pixels outside retain the source channel.
Clamp repeats boundary texels; mirror reflects at normalized image boundaries;
fade multiplies RGB by linear edge weights over one texel outside the image,
reaching black beyond that interval. Fade preserves source alpha.

Time is an explicit finite common seconds value, default zero. Rates are
frozen until the caller supplies time, equally for both eyes. These static
examples provide no runtime control transport; editing definitions requires
recompilation. They do not convert `frameId` to a clock or claim timing
precision, source timestamps or monotonicity. Very large time values are not
qualified; callers should supply bounded elapsed intervals.

Run `tools/Check-NeutralSource.ps1 -Compiler <local-compiler-path>`. The compiler
may be glslc or glslangValidator; its local path is configuration only. A missing
compiler fails. glslc validates OpenGL SPIR-V stages; glslangValidator also
checks stage linking. The Windows host fixture separately compiles and links
the actual ES310 sources through desktop OpenGL and renders synthetic RGBA32F
cases against an independent CPU reference at 0.00002 absolute tolerance.
Tested Python dependencies are NumPy 2.5.2 and PyOpenGL 3.1.10. The shader tests
in normal unittest discovery include actual driver rendering, so both are
required for that command as well as this wrapper. Missing dependencies or an
unsupported host driver fail the fixture. Optional `-Output local/<receipt>.json`
retains host details in ignored local evidence. The authored test prefixes
are a narrow harness, not an extracted or equivalent native preamble.

Desktop results establish no Quest/Adreno, actual native binding/orientation,
intermediate attachment, scanout, reload, clock or wearer acceptance.
