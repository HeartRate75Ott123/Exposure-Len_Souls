#version 150

// 玩家 BOSS 镜魂全身描边：复用原版 outline buffer（entityTarget）的边缘逻辑，
// 检测 MarkerColor 标记色轮廓，替换为四元素四色渐变描边。
// - Marker 轮廓内部 → 透明（主渲染身体可见），描边由边缘渐变承担
// - Marker 边缘距离内 → 渐变描边（四色流动 + 呼吸）
// - 其他区域（原版其他实体 outline / 背景）→ 原样透传

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform vec4 BossColor1;
uniform vec4 BossColor2;
uniform vec4 BossColor3;
uniform vec4 BossColor4;
uniform vec4 MarkerColor;
uniform float BossGlowStrength;

in vec2 texCoord0;

out vec4 fragColor;

bool isMarker(vec4 texel) {
    return abs(texel.r - MarkerColor.r) < 0.02
        && abs(texel.g - MarkerColor.g) < 0.02
        && abs(texel.b - MarkerColor.b) < 0.02
        && texel.a > 0.5;
}

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord0);

    // 玩家轮廓内部 → 透明（身体主渲染可见）
    if (isMarker(texel)) {
        fragColor = vec4(0.0);
        return;
    }

    // distance-field 找 Marker 边缘
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(DiffuseSampler, 0);
    int maxRadius = 4;
    int bestDistSq = maxRadius * maxRadius + 1;
    bool found = false;

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
            if (isMarker(seed)) {
                bestDistSq = distSq;
                found = true;
            }
        }
    }

    if (found) {
        // 四色渐变流动
        float t = Time * 36.0;
        float b1 = sin(texCoord0.x * 3.0 + texCoord0.y * 2.0 + t * 0.15) * 0.5 + 0.5;
        float b2 = sin(texCoord0.y * 3.5 - texCoord0.x * 1.5 + t * 0.20) * 0.5 + 0.5;
        float b3 = sin((texCoord0.x + texCoord0.y) * 2.0 + t * 0.10) * 0.5 + 0.5;
        vec3 mixed = mix(BossColor1.rgb, BossColor2.rgb, b1);
        mixed = mix(mixed, BossColor3.rgb, b2 * 0.6);
        mixed = mix(mixed, BossColor4.rgb, b3 * 0.4);
        float breathe = sin(Time * 0.9) * 0.25 + 0.75;
        float alpha = clamp(BossGlowStrength, 0.0, 0.95);
        fragColor = vec4(mixed * breathe, alpha);
        return;
    }

    // 其他区域原样透传（保留原版其他 outline / 背景）
    fragColor = texel;
}
