// New generic offsets, independent of any application effect signal.
// Public config normalization: rusty.quest.rgb-channel-transform.v1.
// Host may prepend these compile-time controls. No extra native uniforms.
#ifndef LAB_RGB_MODE
#define LAB_RGB_MODE 1
#endif
#ifndef LAB_RGB_EDGE
#define LAB_RGB_EDGE 0
#endif
#ifndef LAB_RGB_DIRECTION
#define LAB_RGB_DIRECTION vec3(0.0, 0.0, 0.5)
#endif
#ifndef LAB_RGB_RATE
#define LAB_RGB_RATE vec3(0.0)
#endif
#ifndef LAB_RGB_STRENGTH
#define LAB_RGB_STRENGTH vec3(0.01, 0.0, 0.01)
#endif
#ifndef LAB_RGB_SCALE
#define LAB_RGB_SCALE vec3(1.0)
#endif
#ifndef LAB_RGB_COVERAGE
#define LAB_RGB_COVERAGE vec3(1.0)
#endif
#ifndef LAB_RGB_TIME_SECONDS
// Explicit caller-supplied common time; default freezes animated directions.
#define LAB_RGB_TIME_SECONDS 0.0
#endif
layout(location = 0) in highp vec2 texCoord;
layout(location = 0) out highp vec4 outColor;
highp float labFinite(highp float value, highp float fallback) {
    return (isnan(value) || isinf(value)) ? fallback : value;
}
highp vec4 labSample(highp vec2 localUv, highp float side) {
    // Explicit highp bilinear interpolation makes the synthetic numerical
    // contract independent of a driver's fixed-function subtexel precision.
    highp ivec2 fullSize = textureSize(tex, 0);
    highp ivec2 eyeSize = ivec2(fullSize.x / 2, fullSize.y);
    highp vec2 point = clamp(localUv * vec2(eyeSize) - 0.5,
                            vec2(0.0), vec2(eyeSize - 1));
    highp ivec2 a = ivec2(floor(point));
    highp ivec2 b = min(a + 1, eyeSize - 1);
    highp vec2 fraction = fract(point);
    highp int origin = int(side) * eyeSize.x;
    highp vec4 lower = mix(texelFetch(tex, ivec2(origin + a.x, a.y), 0),
                           texelFetch(tex, ivec2(origin + b.x, a.y), 0), fraction.x);
    highp vec4 upper = mix(texelFetch(tex, ivec2(origin + a.x, b.y), 0),
                           texelFetch(tex, ivec2(origin + b.x, b.y), 0), fraction.x);
    return mix(lower, upper, fraction.y);
}
void main() {
    highp float side = step(0.5, texCoord.x);
    highp vec2 uv = vec2(texCoord.x * 2.0 - side, texCoord.y);
    highp vec4 base = labSample(uv, side);
    outColor = base; // Preserve source alpha, including at fade edges.
    highp vec4 result = base;
    int mode = LAB_RGB_MODE;
    int edge = LAB_RGB_EDGE;
    if (mode < 0 || mode > 2) mode = 0;
    if (edge < 0 || edge > 2) edge = 0;
    if (mode == 0) return;
    highp vec3 directions = LAB_RGB_DIRECTION;
    highp vec3 rates = LAB_RGB_RATE;
    highp vec3 strengths = LAB_RGB_STRENGTH;
    highp vec3 scales = LAB_RGB_SCALE;
    highp vec3 coverages = LAB_RGB_COVERAGE;
    highp float seconds = labFinite(LAB_RGB_TIME_SECONDS, 0.0);
    for (int channel = 0; channel < 3; ++channel) {
        int index = mode == 2 ? 0 : channel;
        highp float direction = fract(labFinite(directions[index], 0.0));
        highp float rate = clamp(labFinite(rates[index], 0.0), -2.0, 2.0);
        highp float strength = clamp(labFinite(strengths[index], 0.0), 0.0, 0.08);
        highp float scale = clamp(labFinite(scales[index], 1.0), 0.5, 2.0);
        highp float coverage = clamp(labFinite(coverages[index], 1.0), 0.5, 1.0);
        if (max(abs(uv.x - 0.5), abs(uv.y - 0.5)) > 0.5 * coverage) continue;
        highp float angle = 6.283185307179586 * fract(direction + seconds * rate);
        highp vec2 moved = (uv - 0.5) / scale + 0.5
                          + strength * vec2(cos(angle), sin(angle));
        highp vec2 eyeSize = vec2(textureSize(tex, 0)) * vec2(0.5, 1.0);
        // Fade to black over one texel outside the normalized image boundary.
        highp vec2 edgeWeight = clamp(1.0 + min(moved, 1.0 - moved) * eyeSize, 0.0, 1.0);
        if (edge == 1) moved = 1.0 - abs(mod(moved, 2.0) - 1.0);
        highp float value = labSample(moved, side)[channel];
        result[channel] = edge == 2 ? value * edgeWeight.x * edgeWeight.y : value;
    }
    outColor = result;
}
