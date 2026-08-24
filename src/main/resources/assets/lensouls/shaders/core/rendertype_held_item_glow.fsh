#version 150

// 第一人称手持物品发光 composite：读 mask FBO（物品形状），元素主色单色半透明发光

uniform sampler2D DiffuseSampler;
uniform vec4 GlowColor;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float mask = texture(DiffuseSampler, texCoord0).a;
    if (mask < 0.01) discard;
    fragColor = vec4(GlowColor.rgb, mask * 0.65);
}
