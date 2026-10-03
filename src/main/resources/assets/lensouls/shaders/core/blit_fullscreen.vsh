#version 150
// 全屏三角带：texCoord 即顶点 xy（单位化）；NDC 映射由 shader 完成。
// 照抄 ReGlass 的 blit_fullscreen.vsh。
in vec3 Position;
out vec2 texCoord;
void main() {
    texCoord = Position.xy;
    vec2 ndc = Position.xy * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);
}
