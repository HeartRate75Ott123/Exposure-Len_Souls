#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 ScreenSize;
uniform vec3 PaletteColor0;
uniform vec3 PaletteColor1;
uniform vec3 PaletteColor2;
uniform vec3 PaletteColor3;
uniform float PaletteSize;
uniform float GlowStrength;
uniform float Time;
uniform float DebugMode;

in vec2 texCoord;

out vec4 fragColor;

float saturate(float x) { return clamp(x, 0.0, 1.0); }

vec3 paletteColor(float i) {
    if (i < 0.5) return PaletteColor0;
    if (i < 1.5) return PaletteColor1;
    if (i < 2.5) return PaletteColor2;
    return PaletteColor3;
}

vec3 gradientColor(vec2 uv) {
    float size = max(PaletteSize, 2.0);
    float flow = uv.x * 8.0 + uv.y * 6.0 + Time * 6.0;
    float pos = fract(flow / size);
    float scaled = pos * size;
    float idx0 = floor(scaled);
    float idx1 = mod(idx0 + 1.0, size);
    float blend = fract(scaled);
    return mix(paletteColor(idx0), paletteColor(idx1), blend);
}

void main() {
    // === 调试模式：显示 mask 的 alpha（黑=背景，白=物品）===
    if (DebugMode > 0.5) {
        float mask = texture(DiffuseSampler, texCoord).a;
        fragColor = vec4(mask > 0.01 ? vec3(1.0) : vec3(0.0), 1.0);
        return;
    }

    // Sobel 3×3 边缘检测（在 mask alpha 通道上）
    vec2 s = 2.0 / ScreenSize;

    float tl = texture(DiffuseSampler, texCoord + vec2(-s.x,  s.y)).a;
    float tc = texture(DiffuseSampler, texCoord + vec2( 0.0,  s.y)).a;
    float tr = texture(DiffuseSampler, texCoord + vec2( s.x,  s.y)).a;
    float ml = texture(DiffuseSampler, texCoord + vec2(-s.x,  0.0)).a;
    float mr = texture(DiffuseSampler, texCoord + vec2( s.x,  0.0)).a;
    float bl = texture(DiffuseSampler, texCoord + vec2(-s.x, -s.y)).a;
    float bc = texture(DiffuseSampler, texCoord + vec2( 0.0, -s.y)).a;
    float br = texture(DiffuseSampler, texCoord + vec2( s.x, -s.y)).a;

    float gx = -tl - 2.0*tc - tr + bl + 2.0*bc + br;
    float gy = -tl - 2.0*ml - bl + tr + 2.0*mr + br;
    float edge = sqrt(gx*gx + gy*gy);

    if (edge < 0.04) discard;

    // Glow 扩散带
    float glow = smoothstep(0.01, 0.20, edge) * (1.0 - smoothstep(0.20, 0.50, edge));
    // 核心边缘
    float core = smoothstep(0.04, 0.40, edge);
    // 微光闪烁
    float spark = smoothstep(0.25, 0.50, edge) * 0.12 *
        (0.7 + 0.5 * sin(Time * 4.0 + texCoord.x * 300.0 + texCoord.y * 200.0));

    float intensity = core * 0.6 + glow * 0.4 * (0.5 + GlowStrength * 1.2) + spark * GlowStrength;

    // 调色板颜色
    vec3 color = gradientColor(texCoord);
    color *= 1.0 + GlowStrength * 0.5;
    color = max(color, vec3(0.25));

    float brightness = 0.6 + intensity * 1.2;
    vec3 finalColor = color * brightness;
    float alpha = clamp(intensity * 1.5, 0.2, 0.95);

    fragColor = vec4(finalColor, alpha);
}
