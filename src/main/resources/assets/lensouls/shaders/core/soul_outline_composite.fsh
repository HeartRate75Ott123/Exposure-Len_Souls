#version 150

uniform sampler2D OutlineSampler;
uniform vec3 PaletteColor0;
uniform vec3 PaletteColor1;
uniform vec3 PaletteColor2;
uniform vec3 PaletteColor3;
uniform float PaletteSize;
uniform float GlowStrength;
uniform float Time;
uniform float AlphaThreshold;
uniform float MaxSearchRadius;
uniform float DebugMode;

out vec4 fragColor;

// ============================================================
// 196 个整数像素偏移，按距离从近到远排列（max radius² = 64）
// ============================================================
const int SAMPLE_COUNT = 196;
const ivec2 SAMPLES[SAMPLE_COUNT] = ivec2[](
    ivec2(-1, 0), ivec2(0, -1), ivec2(0, 1), ivec2(1, 0),
    ivec2(-1, -1), ivec2(-1, 1), ivec2(1, -1), ivec2(1, 1),
    ivec2(-2, 0), ivec2(0, -2), ivec2(0, 2), ivec2(2, 0),
    ivec2(-2, -1), ivec2(-2, 1), ivec2(-1, -2), ivec2(-1, 2),
    ivec2(1, -2), ivec2(1, 2), ivec2(2, -1), ivec2(2, 1),
    ivec2(-2, -2), ivec2(-2, 2), ivec2(2, -2), ivec2(2, 2),
    ivec2(-3, 0), ivec2(0, -3), ivec2(0, 3), ivec2(3, 0),
    ivec2(-3, -1), ivec2(-3, 1), ivec2(-1, -3), ivec2(-1, 3),
    ivec2(1, -3), ivec2(1, 3), ivec2(3, -1), ivec2(3, 1),
    ivec2(-3, -2), ivec2(-3, 2), ivec2(-2, -3), ivec2(-2, 3),
    ivec2(2, -3), ivec2(2, 3), ivec2(3, -2), ivec2(3, 2),
    ivec2(-4, 0), ivec2(0, -4), ivec2(0, 4), ivec2(4, 0),
    ivec2(-4, -1), ivec2(-4, 1), ivec2(-1, -4), ivec2(-1, 4),
    ivec2(1, -4), ivec2(1, 4), ivec2(4, -1), ivec2(4, 1),
    ivec2(-3, -3), ivec2(-3, 3), ivec2(3, -3), ivec2(3, 3),
    ivec2(-4, -2), ivec2(-4, 2), ivec2(-2, -4), ivec2(-2, 4),
    ivec2(2, -4), ivec2(2, 4), ivec2(4, -2), ivec2(4, 2),
    ivec2(-5, 0), ivec2(-4, -3), ivec2(-4, 3), ivec2(-3, -4), ivec2(-3, 4),
    ivec2(0, -5), ivec2(0, 5), ivec2(3, -4), ivec2(3, 4),
    ivec2(4, -3), ivec2(4, 3), ivec2(5, 0),
    ivec2(-5, -1), ivec2(-5, 1), ivec2(-1, -5), ivec2(-1, 5),
    ivec2(1, -5), ivec2(1, 5), ivec2(5, -1), ivec2(5, 1),
    ivec2(-5, -2), ivec2(-5, 2), ivec2(-2, -5), ivec2(-2, 5),
    ivec2(2, -5), ivec2(2, 5), ivec2(5, -2), ivec2(5, 2),
    ivec2(-4, -4), ivec2(-4, 4), ivec2(4, -4), ivec2(4, 4),
    ivec2(-5, -3), ivec2(-5, 3), ivec2(-3, -5), ivec2(-3, 5),
    ivec2(3, -5), ivec2(3, 5), ivec2(5, -3), ivec2(5, 3),
    ivec2(-6, 0), ivec2(0, -6), ivec2(0, 6), ivec2(6, 0),
    ivec2(-6, -1), ivec2(-6, 1), ivec2(-1, -6), ivec2(-1, 6),
    ivec2(1, -6), ivec2(1, 6), ivec2(6, -1), ivec2(6, 1),
    ivec2(-6, -2), ivec2(-6, 2), ivec2(-2, -6), ivec2(-2, 6),
    ivec2(2, -6), ivec2(2, 6), ivec2(6, -2), ivec2(6, 2),
    ivec2(-5, -4), ivec2(-5, 4), ivec2(-4, -5), ivec2(-4, 5),
    ivec2(4, -5), ivec2(4, 5), ivec2(5, -4), ivec2(5, 4),
    ivec2(-6, -3), ivec2(-6, 3), ivec2(-3, -6), ivec2(-3, 6),
    ivec2(3, -6), ivec2(3, 6), ivec2(6, -3), ivec2(6, 3),
    ivec2(-7, 0), ivec2(0, -7), ivec2(0, 7), ivec2(7, 0),
    ivec2(-7, -1), ivec2(-7, 1), ivec2(-5, -5), ivec2(-5, 5),
    ivec2(-1, -7), ivec2(-1, 7), ivec2(1, -7), ivec2(1, 7),
    ivec2(5, -5), ivec2(5, 5), ivec2(7, -1), ivec2(7, 1),
    ivec2(-6, -4), ivec2(-6, 4), ivec2(-4, -6), ivec2(-4, 6),
    ivec2(4, -6), ivec2(4, 6), ivec2(6, -4), ivec2(6, 4),
    ivec2(-7, -2), ivec2(-7, 2), ivec2(-2, -7), ivec2(-2, 7),
    ivec2(2, -7), ivec2(2, 7), ivec2(7, -2), ivec2(7, 2),
    ivec2(-7, -3), ivec2(-7, 3), ivec2(-3, -7), ivec2(-3, 7),
    ivec2(3, -7), ivec2(3, 7), ivec2(7, -3), ivec2(7, 3),
    ivec2(-6, -5), ivec2(-6, 5), ivec2(-5, -6), ivec2(-5, 6),
    ivec2(5, -6), ivec2(5, 6), ivec2(6, -5), ivec2(6, 5),
    ivec2(-8, 0), ivec2(0, -8), ivec2(0, 8), ivec2(8, 0)
);

// ============================================================
// 调色板
// ============================================================

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

// ============================================================
// 调试模式
// ============================================================
void debugOutput(ivec2 pixel) {
    float val = texelFetch(OutlineSampler, pixel, 0).a;
    fragColor = vec4(val > 0.01 ? vec3(1.0) : vec3(0.0, 0.0, 0.2), 1.0);
}

// ============================================================
// 主函数 — 距离场搜索（整数像素坐标，消除帧间抖动）
// ============================================================
void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(OutlineSampler, 0);

    if (DebugMode > 0.5) { debugOutput(pixel); return; }

    // 当前像素在 mask 内 → 不画描边
    vec4 centerSample = texelFetch(OutlineSampler, pixel, 0);
    if (centerSample.r > 0.001 || centerSample.g > 0.001 || centerSample.b > 0.001) discard;

    bool found = false;

    // 螺旋搜索最近 mask 像素（偏移量按距离排序，整数像素坐标）
    for (int i = 0; i < SAMPLE_COUNT; i++) {
        ivec2 samplePixel = pixel + SAMPLES[i];

        if (samplePixel.x < 0 || samplePixel.y < 0 ||
            samplePixel.x >= size.x || samplePixel.y >= size.y) continue;

        float a = texelFetch(OutlineSampler, samplePixel, 0).a;
        if (a > AlphaThreshold) {
            found = true;
            break;
        }
    }

    if (!found) discard;

    // UV 坐标从像素推导
    vec2 texC = (vec2(pixel) + 0.5) / vec2(size);

    // ======== 抗闪烁核心：纯色输出，距离只决定画不画 ========
    // 不依赖 nearestDist 控制亮度或透明度，帧间 1px 边界抖动不影响输出
    vec3 color = gradientColor(texC);
    color *= 1.0 + GlowStrength * 0.5;
    color = max(color, vec3(0.25));

    float glow = 0.5 + GlowStrength * 1.2;
    vec3 finalColor = color * (0.7 + glow * 0.6);

    fragColor = vec4(finalColor, 1.0);
}
