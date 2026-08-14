#version 150

// 对齐原版 rendertype_outline 语义：纹理 alpha==0 的面不写入 mask。
// Sampler0 由 dispatcher RETURN 绑定当前实体的纹理（反射提取，白像素兜底：
// 兜底纹理 alpha=1，不剔除任何面，退化为旧行为）。
uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a == 0.0) discard;
    fragColor = vec4(1.0);
}
