#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

uniform vec3 BossColor1;
uniform vec3 BossColor2;
uniform vec3 BossColor3;
uniform vec3 BossColor4;
uniform float GlowIntensity;
uniform float UseTextureAlpha;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    if (UseTextureAlpha > 0.5) {
        vec4 texColor = texture(Sampler0, clamp(texCoord0, 0.0, 1.0));
        if (texColor.a < 0.01) {
            discard;
        }
    }

    float t = GameTime * 6.0;
    float flow1 = texCoord0.x * 3.0 + texCoord0.y * 2.0 + t * 0.15;
    float flow2 = texCoord0.y * 3.5 - texCoord0.x * 1.5 + t * 0.20;
    float flow3 = (texCoord0.x + texCoord0.y) * 2.0 + t * 0.10;

    float blend1 = sin(flow1) * 0.5 + 0.5;
    float blend2 = sin(flow2) * 0.5 + 0.5;
    float blend3 = sin(flow3) * 0.5 + 0.5;

    vec3 mixed = mix(BossColor1, BossColor2, blend1);
    mixed = mix(mixed, BossColor3, blend2 * 0.6);
    mixed = mix(mixed, BossColor4, blend3 * 0.4);

    float breathe = sin(GameTime * 0.15) * 0.25 + 0.75;
    float glowStrength = (GlowIntensity + 0.5) * 1.2;

    float outAlpha = ColorModulator.a * breathe * 1.1;
    fragColor = vec4(mixed * glowStrength * breathe * 1.3, clamp(outAlpha, 0.0, 1.0));
}
