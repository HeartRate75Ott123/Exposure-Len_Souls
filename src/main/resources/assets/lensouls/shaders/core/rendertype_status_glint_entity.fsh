#version 150

// Sampler0 = 状态光效纹理（红/白/蓝，setup 时由 textureState 绑定）
// Sampler1 = 实体模型纹理（alpha==0 剔除透明面，setup 时绑定最后记录的实体纹理，
//            反射提取失败时为白像素，alpha=1 不剔除任何面）
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec2 texCoord1;

out vec4 fragColor;

void main() {
    vec4 entityColor = texture(Sampler1, texCoord1);
    if (entityColor.a == 0.0) discard;
    fragColor = texture(Sampler0, texCoord0);
}