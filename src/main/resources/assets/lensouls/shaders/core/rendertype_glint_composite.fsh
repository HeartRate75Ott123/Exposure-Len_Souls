#version 150

uniform sampler2D DiffuseSampler;
uniform vec4 ColorModulator;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler, texCoord0) * ColorModulator;
}
