#version 150

in vec3 Position;
in vec2 UV0;

out vec2 texCoord0;

void main() {
    // 全屏四边形：直接输出 NDC 坐标，不经过任何矩阵变换
    gl_Position = vec4(Position, 1.0);
    texCoord0 = UV0;
}
