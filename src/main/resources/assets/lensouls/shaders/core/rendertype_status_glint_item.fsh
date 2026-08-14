#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 itemColor = texture(Sampler1, texCoord0);
    if (itemColor.a <= 0.15) discard;
    vec4 color = texture(Sampler0, texCoord0);
    fragColor = color * ColorModulator;
}