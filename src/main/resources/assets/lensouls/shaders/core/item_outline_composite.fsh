#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 ScreenSize;
uniform vec3 OutlineColor;
uniform float OutlineWidth;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // mask 的 alpha 通道即物品剪影覆盖度；物品自身像素直接保留（不覆盖可见手持物）
    float center = texture(DiffuseSampler, texCoord0).a;
    if (center > 0.15) discard;

    int r = int(OutlineWidth + 0.5);
    int maxR = 16;
    int best = maxR * maxR + 1;

    // 距离场搜索：在 OutlineWidth 像素半径内找最近的物品像素（种子）
    for (int dy = -maxR; dy <= maxR; dy++) {
        int dySq = dy * dy;
        if (dySq >= best) continue;
        for (int dx = -maxR; dx <= maxR; dx++) {
            int distSq = dx * dx + dySq;
            if (distSq >= best) continue;
            if (distSq > r * r) continue;
            vec2 off = vec2(float(dx), float(dy)) / ScreenSize;
            float a = texture(DiffuseSampler, texCoord0 + off).a;
            if (a > 0.15) {
                best = distSq;
            }
        }
    }

    if (best > r * r) discard;

    // 实心亮环：dn=0 贴物品，dn=1 环外缘；内 80% 整圈高亮，外 20% 柔和渐隐
    // 可见宽度 ≈ 0.8 * r，随 OutlineWidth 线性变化，粗细直观可控
    float dn = sqrt(float(best)) / float(r);
    float alpha = 0.95 * (1.0 - smoothstep(0.8, 1.0, dn));
    fragColor = vec4(OutlineColor, alpha);
}
