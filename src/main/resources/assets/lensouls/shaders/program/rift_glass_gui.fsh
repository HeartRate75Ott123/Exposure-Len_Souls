#version 150
// RiftGlass 玻璃合成片元（liquid_glass_gui 精简版，只保留圆角遮罩 + 模糊 + 色调 + 高光）
// Sampler0 = 清晰主画面拷贝；Sampler1 = 预计算模糊纹理（与主画面同尺寸）
// Rect = 圆角矩形（物理像素，y 已按 GL 坐标系翻转）；RadiusPx = 圆角半径
// FbSize = 主画面物理尺寸；Tint = 叠加色调

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec4 Rect;      // (x0, y0, x1, y1)
uniform float RadiusPx; // 圆角半径
uniform vec4 Tint;      // rgb + a（a = 色调混合强度）
uniform vec2 FbSize;

out vec4 fragColor;

float rrectSdf(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 coord = gl_FragCoord.xy;

    vec2 halfSize = vec2(Rect.z - Rect.x, Rect.w - Rect.y) * 0.5;
    vec2 center = vec2(Rect.x, Rect.y) + halfSize;

    float d = rrectSdf(coord - center, halfSize, RadiusPx);
    float s = 1.0; // 抗锯齿 1px
    float coverage = 1.0 - smoothstep(-s, s, d);

    vec2 uv = coord / FbSize;
    vec3 base = texture(Sampler0, uv).rgb;

    if (coverage <= 0.0) {
        fragColor = vec4(base.rgb, 1.0); // 面板外原样
        return;
    }

    vec3 blurred = texture(Sampler1, uv).rgb;
    vec3 col = mix(blurred, Tint.rgb, Tint.a * 0.85);

    // 顶部内高光
    float rectH = halfSize.y * 2.0;
    float localY = coord.y - Rect.y;
    float hl = smoothstep(0.0, RadiusPx + 8.0, localY)
             * (1.0 - smoothstep(0.0, rectH * 0.55, localY));
    col += vec3(0.07) * hl;

    // 边缘 Fresnel：在 SDF 距离靠近 0 时加一点边缘亮边
    float rim = exp(-abs(d) * 0.20) * 0.18;
    col += vec3(rim) * smoothstep(0.0, 1.0, coverage);

    // 抗锯齿融合：面板外用原色，面板内用玻璃色
    vec3 finalCol = mix(base.rgb, clamp(col, 0.0, 1.0), coverage);
    fragColor = vec4(finalCol, 1.0);
}
