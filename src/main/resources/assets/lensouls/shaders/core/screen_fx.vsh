#version 150

// 屏幕后处理全屏四边形：顶点直接输出到裁剪空间（不使用矩阵）

in vec3 Position;

out vec2 vUV;

void main() {
    vUV = Position.xy * 0.5 + 0.5;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
