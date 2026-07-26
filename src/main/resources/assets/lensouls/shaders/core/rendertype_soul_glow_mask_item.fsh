#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    ivec2 texel = ivec2(texCoord0 * textureSize(Sampler0, 0));
    float alphaMask = texelFetch(Sampler0, texel, 0).a;
    if (alphaMask <= 0.15) discard;
    fragColor = vec4(1.0);
}
