#version 150

// SDF 圆角矩形：真正的矢量圆角。每像素解析计算有符号距离场 + 抗锯齿，
// 不依赖贴图或网格细分，任意分辨率下圆角都精确。
//
// 两种用法：
//   1) UseTexture = 0 —— 纯色/渐变填充（条目、按钮底、滚动条）
//   2) UseTexture = 1 —— 采样 Sampler0（模糊后的背景）做磨砂玻璃面板
//
// 顶点侧通过 UV0 传入「矩形内局部像素坐标（0..w, 0..h）」，片元侧据此求 SDF。

in vec2 vLocal;

uniform vec2      Size;         // 矩形宽高（GUI 像素）
uniform float     Radius;       // 圆角半径
uniform vec4      FillColor;    // 底色 / 渐变上端
uniform vec4      FillColor2;   // 渐变下端；等于 FillColor 时为纯色
uniform vec4      BorderColor;  // 描边色
uniform float     BorderWidth;  // 描边宽度（0 = 不画）
uniform float     EdgeSoftness; // 抗锯齿过渡宽度（约 1px）
uniform sampler2D Sampler0;     // 模糊后的背景（UseTexture = 1 时使用）
uniform float     UseTexture;   // 0 = 纯色，1 = 采样纹理
uniform vec4      Tint;         // 采样纹理时叠加的色调

out vec4 fragColor;

float rrectSdf(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 halfSize = Size * 0.5;
    float d = rrectSdf(vLocal - halfSize, halfSize, Radius);

    float s = max(EdgeSoftness, 0.0001);
    float coverage = 1.0 - smoothstep(-s, s, d);
    if (coverage <= 0.0) discard;

    vec4 base;
    if (UseTexture > 0.5) {
        vec3 tex = texture(Sampler0, vLocal / max(Size, vec2(1.0))).rgb;
        base = vec4(mix(tex, Tint.rgb, Tint.a), FillColor.a);
    } else {
        float t = clamp(vLocal.y / max(Size.y, 0.0001), 0.0, 1.0);
        base = mix(FillColor, FillColor2, t);
    }

    // 描边
    if (BorderWidth > 0.0) {
        float borderMask = 1.0 - smoothstep(0.0, s, abs(d + BorderWidth * 0.5) - BorderWidth * 0.5);
        base = mix(base, BorderColor, borderMask);
    }

    // 顶部内高光：玻璃受光面
    float hl = smoothstep(0.0, Radius + 6.0, vLocal.y) * (1.0 - smoothstep(0.0, Size.y * 0.5, vLocal.y));
    base.rgb += vec3(0.06) * hl;

    fragColor = vec4(base.rgb, base.a * coverage);
}
