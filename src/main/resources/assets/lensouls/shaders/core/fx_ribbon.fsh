#version 150

// ============================================================================
//  Len Souls - 世界空间发光带 / 圆环（自定义着色器版）
//
//  这一版能"漂亮"，靠的是 UV 做柔边 + 着色器里的能量脉冲；
//  Iris 兼容靠的是<b>绘制时机</b>：整条世界特效在 AFTER_LEVEL（Iris 的
//  finalizeLevelRendering 之后）手动绘制，此时 Iris 的
//  shouldOverrideShaders() == false，不会把帧缓冲抢回 gbuffer，
//  自定义 ShaderInstance 正常落在主目标上。
//
//  UV0.x = 沿线参数（0..1；圆环上就是角度/TAU）
//  UV0.y = 横向（0..1，0.5 = 芯）
//  Color = 逐顶点颜色（rgb 色调 + a 不透明度）
//
//  动画：
//    * FxGrow   0..1  沿线"生长"（超过生长点直接丢弃）
//    * FxTaper  0..1  越靠末端越细越淡（消散/离开）
//    * FxAlpha  0..1  整体渐入淡出
// ============================================================================

in vec2 vUV;
in vec4 vColor;
out vec4 fragColor;

uniform float FxTime;
uniform float FxPulseSpeed;
uniform float FxPulseSharp;
uniform float FxPulseCount;
uniform float FxEdgeSoft;
uniform float FxCoreBoost;
uniform float FxFlicker;
uniform float FxGrow;
uniform float FxTaper;
uniform float FxAlpha;

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

void main() {
    float along  = vUV.x;
    float across = vUV.y;

    // 只有正在"生长"（FxGrow < 1）时才做末端淡出与裁切；
    // 静态线（FxGrow = 1）必须两端一样亮 —— 否则闭合环会在接缝处出现
    // "末端淡出、起始全亮"的硬接，也就是"绕一圈回来不连续"的观感。
    float growFade = 1.0;
    if (FxGrow < 0.999) {
        if (along > FxGrow) discard;
        growFade = 1.0 - smoothstep(max(0.0, FxGrow - 0.08), FxGrow, along);
    }

    float taperMix = clamp(FxTaper, 0.0, 1.0);
    float thin = mix(1.0, 1.0 - along * 0.85, taperMix);

    // 横向剖面：中心最亮、两侧平滑收到 0。
    // 再乘一道"内侧归零带"（最外 6% 恒为 0）：可见的羽化必须完全发生在四边形**内部**，
    // 这样几何边界 / 光栅化永远切不到可见像素 —— 两侧都是羽化，不会出现"一侧硬边"。
    float edge = 1.0 - abs(across * 2.0 - 1.0);
    edge = pow(clamp(edge, 0.0, 1.0), FxEdgeSoft);
    edge *= smoothstep(0.0, 0.06, across) * smoothstep(1.0, 0.94, across);
    edge *= thin;

    float core = pow(clamp(edge * 1.55 - 0.55, 0.0, 1.0), 2.2) * FxCoreBoost;

    float count = max(1.0, floor(FxPulseCount + 0.5));
    float sharp = max(0.008, FxPulseSharp);
    float p0 = fract(along * count - FxTime * FxPulseSpeed);
    float p1 = fract(along * count - FxTime * FxPulseSpeed + 0.5);
    // 注意：fract 在回绕处是 1 → 0 的<b>跳变</b>，直接拿它做高斯，脉冲带就会变成
    // "一侧陡壁（0→满亮）、一侧缓落"的锯齿条 —— 用户反复报的"它的一侧是硬边缘、另一侧有淡化"
    // 就是这个，不是几何问题。改成"周期内的最短距离" min(p, 1-p)：回绕处两侧都连续，
    // 每条脉冲带左右对称羽化。
    float d0 = min(p0, 1.0 - p0);
    float d1 = min(p1, 1.0 - p1);
    float pulse = exp(-pow(d0 / sharp, 2.0)) + exp(-pow(d1 / sharp, 2.0)) * 0.55;

    // 闪烁频率刻意压低：along 高频噪声在浅视角下会转成锯齿。
    // 第三维取 |across - 0.5|（镜像），保证左右两半拿到的闪烁完全一致 —— 否则两侧淡出不等，
    // 观感就是"一边硬一边软"。
    float flick = 1.0 + FxFlicker * (vnoise(vec3(along * 9.0, FxTime * 7.0, abs(across - 0.5) * 4.0)) - 0.5);

    vec3 tint = vColor.rgb;
    vec3 col = tint * (0.42 + 0.58 * edge);
    col += mix(tint, vec3(1.0), 0.50) * pulse * (0.30 + 0.70 * edge) * 1.20;
    col += vec3(1.0) * core * (0.35 + 0.65 * pulse) * 0.42;

    float alpha = edge * vColor.a * flick * FxAlpha * growFade;
    fragColor = vec4(max(col, 0.0) * clamp(alpha, 0.0, 1.0), 1.0);
}
