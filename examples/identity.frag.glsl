// Linear RGBA, no gamma conversion or alpha reassociation.
layout(location = 0) in highp vec2 texCoord;
layout(location = 0) out highp vec4 outColor;
void main() {
    highp vec2 size = vec2(textureSize(tex, 0));
    highp float side = step(0.5, texCoord.x);
    highp vec2 lo = vec2(side * 0.5, 0.0) + 0.5 / size;
    highp vec2 hi = vec2((side + 1.0) * 0.5, 1.0) - 0.5 / size;
    outColor = texture(tex, clamp(texCoord, lo, hi));
}
