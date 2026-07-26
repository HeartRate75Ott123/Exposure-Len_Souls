#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 ScreenSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 ts = vec2(1.0) / ScreenSize;

    // Sobel 3×3 on alpha channel (mask has entity in opaque, bg transparent)
    float tl = texture(Sampler0, texCoord0 + vec2(-1.0, -1.0) * ts).a;
    float tc = texture(Sampler0, texCoord0 + vec2( 0.0, -1.0) * ts).a;
    float tr = texture(Sampler0, texCoord0 + vec2( 1.0, -1.0) * ts).a;
    float ml = texture(Sampler0, texCoord0 + vec2(-1.0,  0.0) * ts).a;
    float mr = texture(Sampler0, texCoord0 + vec2( 1.0,  0.0) * ts).a;
    float bl = texture(Sampler0, texCoord0 + vec2(-1.0,  1.0) * ts).a;
    float bc = texture(Sampler0, texCoord0 + vec2( 0.0,  1.0) * ts).a;
    float br = texture(Sampler0, texCoord0 + vec2( 1.0,  1.0) * ts).a;

    float gx = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
    float gy = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
    float edge = sqrt(gx * gx + gy * gy);

    if (edge < 0.2) discard;

    // 金色描边 + GameTime 动画
    float pulse = sin(GameTime * 0.3) * 0.5 + 0.5;
    float warm = sin(GameTime * 0.15) * 0.2 + 0.8;
    vec3 gold = vec3(1.0, 0.75 + pulse * 0.15, 0.1);
    float alpha = clamp(edge * 0.8, 0.0, 0.8) * warm;

    fragColor = vec4(gold * ColorModulator.rgb, alpha);
}
