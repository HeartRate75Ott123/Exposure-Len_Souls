#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // 整数纹素采样：同一纹素的 alpha 值帧间不变，消除亚像素位移导致的 alpha 跳变
    ivec2 texSize = textureSize(Sampler0, 0);
    ivec2 texel = ivec2(texCoord0 * vec2(texSize));
    float alphaMask = texelFetch(Sampler0, texel, 0).a;
    if (alphaMask <= 0.01) {
        discard;
    }
    // 纯白输出，不依赖 ColorModulator（该 uniform 由渲染管线控制，mask 场景下不可靠）
    fragColor = vec4(1.0);
}
