#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 ScreenSize;
uniform float Time;

uniform vec4 BossColor1;
uniform vec4 BossColor2;
uniform vec4 BossColor3;
uniform vec4 BossColor4;
uniform float BossGlowStrength;
uniform float BossOutlineWidth;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float centerAlpha = texture(DiffuseSampler, texCoord0).a;
    if (centerAlpha > 0.1) discard;

    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(DiffuseSampler, 0);

    int maxRadius = 4;
    int maxRadiusSq = maxRadius * maxRadius;
    int bestDistSq = maxRadiusSq + 1;

    // 距离场搜索：找最近的 seed，且 seed 的编码半径必须覆盖搜索距离
    for (int dy = -maxRadius; dy <= maxRadius; dy++) {
        int dySq = dy * dy;
        if (dySq >= bestDistSq) continue;

        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            if (dx == 0 && dy == 0) continue;
            int distSq = dx * dx + dySq;
            if (distSq >= bestDistSq) continue;

            ivec2 sp = pixel + ivec2(dx, dy);
            if (sp.x < 0 || sp.y < 0 || sp.x >= size.x || sp.y >= size.y) continue;

            vec4 seed = texelFetch(DiffuseSampler, sp, 0);
            if (seed.a <= 0.1) continue;

            // seed 的 alpha 编码了搜索半径（纹理 alpha → 半径比例）
            // 低 alpha 的噪点 seed 只能覆盖短距离，远处透明像素找不到它
            float seedRadius = seed.a;                     // 0.01 ~ 1.0
            int seedRadiusPx = int(seedRadius * float(maxRadius));
            if (distSq > seedRadiusPx * seedRadiusPx) continue;

            bestDistSq = distSq;
        }
    }

    if (bestDistSq > maxRadiusSq) discard;

    // ── BOSS 多色渐变 ──
    float t = Time * 36.0;
    float b1 = sin(texCoord0.x * 3.0 + texCoord0.y * 2.0 + t * 0.15) * 0.5 + 0.5;
    float b2 = sin(texCoord0.y * 3.5 - texCoord0.x * 1.5 + t * 0.20) * 0.5 + 0.5;
    float b3 = sin((texCoord0.x + texCoord0.y) * 2.0 + t * 0.10) * 0.5 + 0.5;

    vec3 mixed = mix(BossColor1.rgb, BossColor2.rgb, b1);
    mixed = mix(mixed, BossColor3.rgb, b2 * 0.6);
    mixed = mix(mixed, BossColor4.rgb, b3 * 0.4);

    float breathe = sin(Time * 0.9) * 0.25 + 0.75;
    vec3 color = mixed * breathe;

    float brightness = 0.6 + 1.2;
    float alpha = clamp(BossGlowStrength, 0.0, 0.95);
    if (alpha < 0.01) discard;

    fragColor = vec4(color * brightness, alpha);
}
