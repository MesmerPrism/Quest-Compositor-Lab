// Conventional five-tap binomial Gaussian approximation; see horizontal pass.
layout(location = 0) in highp vec2 texCoord;
layout(location = 0) out highp vec4 outColor;
void main() {
    highp vec2 size = vec2(textureSize(tex, 0));
    highp float side = step(0.5, texCoord.x);
    highp vec2 lo = vec2(side * 0.5, 0.0) + 0.5 / size;
    highp vec2 hi = vec2((side + 1.0) * 0.5, 1.0) - 0.5 / size;
    const highp float weights[5] = float[5](0.0625, 0.25, 0.375, 0.25, 0.0625);
    outColor = vec4(0.0);
    for (int tap = 0; tap < 5; ++tap) {
        highp vec2 uv = texCoord + vec2(0.0, float(tap - 2) / size.y);
        outColor += weights[tap] * texture(tex, clamp(uv, lo, hi));
    }
}
