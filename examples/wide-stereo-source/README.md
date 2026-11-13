# Inert identity sink example

This is a host-only descriptor/lifecycle recipe. It opens no Surface, reader,
camera, capture service, queue, socket or device connection. It performs no
colour/effect processing and makes no live stereo, FOV, timing, pose,
presentation or retirement claim.

From the destination repository root, after selecting a JDK:

```text
javac --release 8 -d /path/to/disposable-output adapters/capture-bridge/src/main/java/io/github/mesmerprism/questcompositor/capturebridge/*.java examples/wide-stereo-source/IdentitySinkExample.java
java -cp /path/to/disposable-output IdentitySinkExample
```

The printed dimensions are an authored host fixture, not selected firmware or
calibration. Real adoption must create its own reader, validate its exact
descriptor, implement real resource observations and prove native underlay and
effective output exclusion in the actual presentation carrier. A future Shell
controller is separate work and must preserve genuine Shell identity/attribution.

The [consumer-seams guide](CONSUMER-SEAMS.md) maps the selected packed AImage/AHB
path to existing accounting and carrier-owned resources. It is documentation,
with the actual provider source pin still pending; this fixture remains inert.
