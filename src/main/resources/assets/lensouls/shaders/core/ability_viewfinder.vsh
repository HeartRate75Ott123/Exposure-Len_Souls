#version 150

// 能力取景框色晕：全屏四边形，顶点直接输出到裁剪空间（不使用矩阵，
// 与 screen_fx.vsh 同一套写法 —— 画在 GUI 之后，投影矩阵是 GUI 正交矩阵，这里绕开它）

in vec3 Position;

out vec2 vUV;

void main() {
    vUV = Position.xy * 0.5 + 0.5;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
