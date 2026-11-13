// Independently authored triangle. Matrix column order is fixture-qualified;
// native orientation/binding equivalence requires separate qualification.
layout(location = 0) out highp vec2 texCoord;
void main() {
    highp vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    texCoord = vec2((p.x + clamp(eye, 0.0, 1.0)) * 0.5, p.y);
    highp vec2 clip = p * 2.0 - 1.0;
    gl_Position = vec4(mat2(screenOrientationAdjustment.xy,
                           screenOrientationAdjustment.zw) * clip, 0.0, 1.0);
}
