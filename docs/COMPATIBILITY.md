# Compatibility and evidence

`gles-body-es310` describes shader bodies consumed after a runtime prefix. The
host prefixes model `eye`, packed SBS input, `tex` and the vertex orientation
transform only. They are authored test interfaces, not extracted vendor source.
The experimental compatibility record supports at most five indexed passes.
It does not identify a universally compatible firmware or grant loader access.

The vertex maps each draw to one packed eye. Sampling clamps inside that eye's
half-texel bounds. Blur is conventional separable Gaussian RGBA processing and
does not linearize colour or reinterpret alpha as a validity mask. Clean RGB
uses the public bounded configuration with an independently documented sampling
policy. See the example README for exact subset and alpha semantics.

Evidence levels remain distinct: source/hash checks; CPU numerical checks;
compiler/link checks against synthetic prefixes; host-driver synthetic pixels;
native loader construction; distinguishable adoption; physical scanout; wearer
assessment. A result at one level is not a result at another. No headset,
Adreno, native binding, source timing, orientation or sustained behavior is
qualified by this milestone.

Property names are case-sensitive. `debug.oculus.postProcess.enable` differs
in capitalization from `debug.oculus.postprocess.*`. Selection plans clear
inherited selectors and size/filter overrides through index 4. Requested
properties are not an effective loaded-program witness. Changing a file does
not prove a cached native program was reconstructed.

No persistent shader history is exposed by the observed discarded intermediate
attachments. Shader temporal smoothing cannot be assumed. Existing tearing,
abrupt carrier-driven gain and optical-grid visibility remain unresolved.

Capture lifetime/permissions and any future Shell adapter have a separate owner.
No vendor AIDL, firmware dump, capture operation or device mutation is included.

The neutral capture sink module supplies a local 19-word descriptor and
app-side callback/image/fence accounting. Its Android Binder classes are only
API-compiled here; Shell/DUMP enforcement, Surface transfer, Binder death and
Service lifecycle need real runtime evidence. Local retirement is not a vendor
retirement acknowledgment. This module does not provide global capture-idle,
peer quiescence, output exclusion, live pixels or restart qualification.
