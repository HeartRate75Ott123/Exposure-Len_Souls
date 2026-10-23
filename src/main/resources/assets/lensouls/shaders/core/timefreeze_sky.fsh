#version 150

// ============================================================================
//  Len Souls - 时间定格 · 冻结天穹 (Time-Frozen Sky)
//
//  纯客户端后处理 pass：世界渲染结束后（深度缓冲完好、手部渲染之前）画一张
//  全屏四边形，z = 远平面 + LEQUAL 深度测试 → 只覆盖"深度仍是 clear 值的像素"，
//  即真正的天空区域。地形/实体/半透明方块照旧遮挡，不需要采样深度纹理，
//  因此在 原版 / Sodium / Iris 光影 三条路径下行为完全一致。
//
//  画面要素（"时钟 + 蓝色风 + 高级着色器"）：
//    · 深蓝天穹底色 + 极光薄纱 + 星云 + 银河带（世界空间锚定，随视角旋转）
//    · 蓝色风：域扭曲的各向异性云带 + 丝状纤维，被冻结在半途，只做极缓慢的漂移
//    · 天穹时计：正前方天空中的巨表盘 —— 60 分刻度 / 12 时标 / 齿轮环 / 星盘环 /
//      玑镂纹 + 冰晶裂纹 / 停在 10:09:30 的三根指针（带"想走却走不动"的微颤）
//    · 冻结脉冲：从表盘圆心向外扩散、走到一半就凝住的时间涟漪
//    · 电影级收尾：亮部辉光 + 色调映射 + 暗角 + 抖动消除色带
//
//  所有强度由 SkyIntensity 控制（淡入淡出），alpha 混合回原画面。
// ============================================================================

in vec2 vScreen;
out vec4 fragColor;

uniform float SkyTime;          // 墙钟时间（秒，时停中依然流动 → 特效不死板）
uniform float SkyIntensity;     // 0..1 全局淡入淡出
uniform vec2  SkyScreenSize;    // 主帧缓冲尺寸（像素）
uniform float SkyTanHalfFov;    // tan(fovY/2)
uniform float SkyAspect;        // 宽高比
uniform float SkyDayBrightness; // 白天 1 → 夜晚约 0.45
uniform vec3  SkyFogColor;      // 当前地形雾色（视空间）
uniform vec3  SkyCamForward;
uniform vec3  SkyCamUp;
uniform vec3  SkyCamRight;
uniform vec3  SkyClockDir;      // 表盘圆心方向（世界空间，触发瞬间定住）

const float PI  = 3.141592653589793;
const float TAU = 6.283185307179586;

// ---------------------------------------------------------------------------
//  噪声 / 基础工具
// ---------------------------------------------------------------------------
float saturate(float x) { return clamp(x, 0.0, 1.0); }

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);

    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
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

float fbm2(vec3 p) {
    float s = 0.0;
    float a = 0.62;
    for (int i = 0; i < 2; i++) {
        s += a * vnoise(p);
        p = p.zxy * 2.11 + vec3(2.73, 1.19, 6.41);
        a *= 0.5;
    }
    return s * 1.26;
}

float fbm3(vec3 p) {
    float s = 0.0;
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p.zxy * 2.07 + vec3(1.37, 4.79, 2.11);
        a *= 0.5;
    }
    return s * 1.12;
}

float ridged3(vec3 p) {
    float s = 0.0;
    float a = 0.58;
    for (int i = 0; i < 3; i++) {
        float n = 1.0 - abs(vnoise(p) * 2.0 - 1.0);
        n *= n;
        s += n * a;
        p = p.zyx * 2.11 + vec3(5.31, 0.73, 3.97);
        a *= 0.5;
    }
    return s * 1.10;
}

float ridged2(vec3 p) {
    float s = 0.0;
    float a = 0.62;
    for (int i = 0; i < 2; i++) {
        float n = 1.0 - abs(vnoise(p) * 2.0 - 1.0);
        n *= n;
        s += n * a;
        p = p.zyx * 2.13 + vec3(5.31, 0.73, 3.97);
        a *= 0.5;
    }
    return s * 1.28;
}

/** 环形带遮罩（带 AA 宽度 w） */
float band(float x, float lo, float hi, float w) {
    return smoothstep(lo - w, lo + w, x) * (1.0 - smoothstep(hi - w, hi + w, x));
}

/** 细环：只保留"线"，软边 */
float ringLine(float x, float center, float halfWidth, float w) {
    return 1.0 - smoothstep(halfWidth - w, halfWidth + w, abs(x - center));
}

/** 齿轮齿廓（phase 让齿极缓慢转动） */
float gearTeeth(float az, float count, float phase) {
    return smoothstep(-0.20, 0.55, sin(az * count + phase));
}

// ---------------------------------------------------------------------------
//  星空
// ---------------------------------------------------------------------------
vec3 starLayer(vec3 d, float scale, float density, float size, float seed,
               float brightness, float pxAngle) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 rnd = hash33(cell + seed + 7.0);
    if (rnd.z > density) return vec3(0.0);

    // 星点吸附到单位球壳，避免大量星点落在视锥外
    vec3 starDir = normalize(cell + 0.5 + (hash33(cell + seed + 31.73) - 0.5) * 0.55);
    float dist = length(d - starDir) * scale;

    float px = max(pxAngle * scale, 0.0001);
    float radius = clamp(max(size, px * 0.9), 0.0005, 0.30);
    float star = 1.0 - smoothstep(radius * 0.15, radius, dist);
    star *= star;

    float power = pow(rnd.x, 3.2) * 0.85 + 0.15;
    float temp = hash13(cell + seed + 19.31);
    vec3 tint = mix(vec3(0.62, 0.80, 1.00), vec3(0.92, 0.97, 1.00),
                    smoothstep(0.20, 0.90, temp));
    tint = mix(tint, vec3(0.45, 0.95, 0.92), smoothstep(0.86, 1.00, temp) * 0.45);

    // 冻结的时间：闪烁几乎停住，只留一丝呼吸
    float twinkle = 0.92 + 0.08 * sin(SkyTime * 0.55 + rnd.x * 61.0);

    return tint * (star * power * brightness * twinkle);
}

// ---------------------------------------------------------------------------
//  蓝色风
//
//  域扭曲 + 各向异性（沿风向拉长）的 fbm 云带：柔和的长条风体，内部再叠细纤维。
//  刻意不用 ridged 噪声 —— 那会得到"闪电/树根"分叉，不是风。
//  返回 (风体 body, 亮芯 core, 细纤维 fibers)，均 0..1。
// ---------------------------------------------------------------------------
vec3 windField(vec3 dir, vec3 axis, float scale, float stretch, float t) {
    vec3 upRef = abs(axis.y) > 0.95 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    vec3 a = normalize(cross(upRef, axis));
    vec3 b = normalize(cross(axis, a));

    vec3 aniso = vec3(dot(dir, a), dot(dir, b), dot(dir, axis) * stretch) * scale;
    // 各向异性 + 各向同性混合：纯各向异性在"正对风向看"时会呈放射状扇形
    vec3 p = mix(dir * scale, aniso, 0.72);

    // 极缓慢漂移：时间被冻住，风只在原地流动
    p += vec3(t * 0.012, t * 0.006, t * 0.020);

    float w1 = fbm2(p * 0.62 + vec3(11.0, 3.0, 7.0));
    float w2 = fbm2(p * 0.62 + vec3(31.0, 17.0, 5.0));
    vec3 q = p * 1.35 + (vec3(w1, w2, (w1 + w2) * 0.5) - 0.5) * 2.0;

    float veil = fbm3(q * 0.82 + vec3(3.0, 9.0, 1.0));
    float fibers = fbm2(q * 3.10 + vec3(7.0, 2.0, 5.0));

    float body = smoothstep(0.44, 0.74, veil);
    float core = smoothstep(0.55, 0.86, veil + 0.24 * (fibers - 0.5));
    return vec3(body, core, fibers);
}

// ---------------------------------------------------------------------------
//  指针形状
// ---------------------------------------------------------------------------
float clockHand(float arc, float r, float len, float widthBase, float widthTip) {
    float t = mix(widthBase, widthTip, saturate(r / max(len, 1e-4)));
    float m = 1.0 - smoothstep(t * 0.55, t, arc);
    m *= 1.0 - smoothstep(len * 0.88, len, r);
    m *= smoothstep(0.0, 0.010, r);
    return m;
}

void main() {
    // ---- 视射线：相机基向量重建（世界锚定，不受镜头翻滚影响） ----
    vec2 ndc = gl_FragCoord.xy / SkyScreenSize * 2.0 - 1.0;
    vec3 dir = normalize(SkyCamForward
            + SkyCamRight * (ndc.x * SkyAspect * SkyTanHalfFov)
            + SkyCamUp    * (ndc.y * SkyTanHalfFov));

    float t = SkyTime;
    float px = clamp(max(max(fwidth(dir.x), fwidth(dir.y)), fwidth(dir.z)), 1e-5, 0.05);

    // ---- 天穹时计的局部坐标系（极坐标：半径角 r / 方位 az） ----
    vec3 n = normalize(SkyClockDir);
    vec3 upRef = abs(n.y) > 0.95 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    vec3 ax = normalize(cross(upRef, n));      // 表盘 3 点方向
    vec3 ay = normalize(cross(n, ax));         // 表盘 12 点方向

    float r  = acos(clamp(dot(dir, n), -1.0, 1.0));
    float az = atan(dot(dir, ay), dot(dir, ax));

    const float R_FACE = 0.62;                 // 表盘半径（弧度，约 35.5°）
    float faceMask = 1.0 - smoothstep(R_FACE - px * 1.6, R_FACE + px * 1.6, r);

    // =====================================================================
    //  1. 天穹底色
    // =====================================================================
    vec3 cZenith = vec3(0.008, 0.020, 0.068);
    vec3 cMid    = vec3(0.026, 0.088, 0.232);
    vec3 cHorizon= vec3(0.075, 0.300, 0.410);

    float g = pow(saturate(dir.y * 0.5 + 0.5), 1.30);
    vec3 col = mix(cHorizon, cMid, smoothstep(0.02, 0.45, g));
    col = mix(col, cZenith, smoothstep(0.45, 1.0, g));

    // 极光帘幕：在方位/高度上拉伸取样的细密竖帘，只占一侧天空
    float azA = atan(dir.z, dir.x);
    float elA = asin(clamp(dir.y, -1.0, 1.0));
    vec3 ac = vec3(cos(azA) * 1.15, elA * 5.2, sin(azA) * 1.15);
    float curtain = ridged3(ac * 1.9 + vec3(0.0, t * 0.004, 0.0));
    curtain = pow(smoothstep(0.46, 0.94, curtain), 1.6);
    float sideA = smoothstep(0.04, 0.20, elA) * (1.0 - smoothstep(0.30, 0.62, elA));
    float dAzA = abs(mod(azA - 2.45 + PI, TAU) - PI);   // 环绕角差，避免 ±PI 接缝
    float bandA = exp(-pow(dAzA / 1.15, 2.0));
    col += mix(vec3(0.010, 0.100, 0.230), vec3(0.020, 0.220, 0.250),
               smoothstep(0.06, 0.40, elA)) * curtain * sideA * bandA * 0.55;

    // 高空细碎星云
    float dust = fbm3(dir * 4.2 + vec3(1.7, 5.3, 9.1));
    col += vec3(0.022, 0.060, 0.125) * smoothstep(0.55, 0.86, dust) * (0.22 + 0.78 * g);

    // 银河带：一条倾斜的淡蓝星尘河
    vec3 mwN = normalize(vec3(0.62, 0.42, -0.66));
    float mwD = dot(dir, mwN) + 0.06 * (fbm2(dir * 2.2 + 4.0) - 0.5);
    float mw = exp(-mwD * mwD * 9.0);
    float mwTex = smoothstep(0.35, 0.72, fbm3(dir * 7.0 + vec3(2.0, 8.0, 5.0)));
    col += mix(vec3(0.055, 0.100, 0.225), vec3(0.135, 0.255, 0.420),
               smoothstep(0.30, 0.75, mwTex)) * mw * 0.40;

    // =====================================================================
    //  2. 星空（先铺，表盘会盖住中间一部分）
    // =====================================================================
    float starMask = smoothstep(-0.03, 0.10, dir.y) * (1.0 - 0.55 * mw);
    vec3 stars = vec3(0.0);
    stars += starLayer(dir, 140.0, 0.055, 0.060, 3.0, 1.55, px);
    stars += starLayer(dir, 74.0, 0.050, 0.085, 27.0, 2.90, px);
    stars += starLayer(dir, 33.0, 0.038, 0.110, 71.0, 5.20, px);
    stars += starLayer(dir, 15.0, 0.026, 0.028, 113.0, 9.00, px);
    stars *= starMask;
    col += stars;

    // =====================================================================
    //  3. 星盘大环 + 机芯齿轮（远景层）
    // =====================================================================
    // 星盘外环：与表盘同心的大圆环，把"时计"放大到整片天空
    {
        float bigRing = ringLine(r, R_FACE * 2.05, 0.0045, px * 1.5);
        float bigRing2 = ringLine(r, R_FACE * 2.55, 0.0030, px * 1.5);
        col += vec3(0.16, 0.42, 0.72) * bigRing * 0.42;
        col += vec3(0.12, 0.32, 0.58) * bigRing2 * 0.26;

        // 大环上的罗盘刻度：36 等分
        float markAng = abs(fract(az * 36.0 / TAU + 0.5) - 0.5) * (TAU / 36.0) * r;
        float mark = (1.0 - smoothstep(0.0035, 0.0035 + px * 1.4, markAng))
                   * band(r, R_FACE * 2.00, R_FACE * 2.10, px);
        col += vec3(0.30, 0.62, 0.95) * mark * 0.30;
    }

    // 外圈大齿轮（很淡，像巨型机芯）
    {
        float teeth = gearTeeth(az, 96.0, t * 0.010);
        float ringA = ringLine(r, R_FACE * 1.36, 0.010, px * 1.4);
        float ringB = ringLine(r, R_FACE * 1.46, 0.005, px * 1.4);
        float body  = band(r, R_FACE * 1.31, R_FACE * 1.38, px * 1.2) * teeth;
        col += vec3(0.20, 0.50, 0.78) * (ringA * 0.50 + ringB * 0.24) * 0.50;
        col += vec3(0.10, 0.30, 0.56) * body * 0.40;
    }
    // 内圈小齿轮（反向慢转）
    {
        float teeth = gearTeeth(az, 48.0, -t * 0.016);
        float ringA = ringLine(r, R_FACE * 1.13, 0.007, px * 1.4);
        col += vec3(0.16, 0.40, 0.68) * ringA * 0.36;
        col += vec3(0.08, 0.24, 0.48) * band(r, R_FACE * 1.10, R_FACE * 1.15, px) * teeth * 0.30;
    }

    // =====================================================================
    //  4. 蓝色风（背景层）
    // =====================================================================
    // 两层风：主风向的粗丝 + 反向斜风的细丝（共用一次域扭曲，开销可控）
    vec3 windAxis = normalize(vec3(0.90, 0.16, 0.40));       // 主风向（世界空间）
    vec3 windAxis2 = normalize(vec3(-0.35, 0.22, 0.91));     // 次风向（大致垂直）

    vec3 wA = windField(dir, windAxis, 2.4, 0.42, t);
    vec3 wB = windField(dir, windAxis2, 4.2, 0.62, t * 1.55);

    float bodyA = wA.x * (0.55 + 0.45 * smoothstep(0.28, 0.72, wA.z));
    float bodyB = wB.x * (0.55 + 0.45 * smoothstep(0.28, 0.72, wB.z)) * (0.25 + 0.75 * bodyA);
    float wind = saturate(bodyA + bodyB * 0.55 + wA.y * 0.55 * bodyA);

    float windH = mix(1.0, 0.55, saturate(dir.y));
    float windGlow = wind * windH;

    vec3 windDeep  = vec3(0.045, 0.200, 0.880);
    vec3 windBright= vec3(0.320, 0.860, 1.000);
    vec3 windCol = mix(windDeep, windBright, smoothstep(0.15, 0.95, wind));

    col += windCol * windGlow * 0.46;
    col += mix(windBright, vec3(0.72, 0.97, 1.0), 0.6) * pow(saturate(wA.y * bodyA), 2.2) * 0.62;
    col += vec3(0.06, 0.42, 0.82) * pow(saturate(wind), 1.7) * windH * 0.22;

    // =====================================================================
    //  5. 表盘：暗玻璃面 + 玑镂纹 + 冰裂
    // =====================================================================
    vec3 faceCol = mix(vec3(0.008, 0.024, 0.062), vec3(0.026, 0.100, 0.180),
                       smoothstep(0.0, R_FACE, r));

    // 玑镂纹（watch dial guilloché）：同心细密涟漪；频率随像素角自适应 → 永不摩尔纹
    float gFreq = clamp(0.34 / max(px, 1e-5), 40.0, 420.0);
    float guill = sin(r * gFreq - az * 2.0) * 0.5 + 0.5;
    faceCol += vec3(0.038, 0.092, 0.150) * guill * 0.42;

    // 冰晶裂纹：细、稀疏，只在表盘外缘区域
    float crack = ridged2(dir * 52.0 + 3.7);
    float crackMask = smoothstep(0.84, 0.995, crack)
                    * smoothstep(R_FACE * 1.02, R_FACE * 0.46, r);
    faceCol += vec3(0.16, 0.42, 0.62) * crackMask * 0.28;

    // 玻璃反光：表盘左上象限的一团柔光（单片玻璃斜光，不做同心环）
    vec2 lp = vec2(dot(dir, ax), dot(dir, ay)) / R_FACE;
    float sheen = exp(-dot(lp - vec2(-0.40, 0.52), lp - vec2(-0.40, 0.52)) / 0.30);
    faceCol += vec3(0.12, 0.30, 0.48) * sheen * 0.34;

    // 风在表盘上的投影：让风"掠过"表面，产生前后层次
    faceCol *= 1.0 - 0.22 * windGlow;

    // 内缘内阴影：让表盘像一块有厚度的玻璃/蓝宝石
    faceCol *= 1.0 - 0.55 * smoothstep(R_FACE * 0.72, R_FACE, r);

    col = mix(col, faceCol, faceMask * 0.88);

    // 表盘外缘双层环 + 外辉光
    float rimOuter = ringLine(r, R_FACE, px * 1.5, px * 1.7);
    float rimInner = ringLine(r, R_FACE * 0.952, 0.0035, px * 1.7);
    col += mix(vec3(0.42, 0.88, 1.00), vec3(0.95, 1.00, 1.00), 0.40) * rimOuter * 0.95;
    col += vec3(0.26, 0.60, 0.86) * rimInner * 0.40;

    float halo = exp(-pow((r - R_FACE) / 0.070, 2.0));
    col += vec3(0.09, 0.32, 0.58) * halo * 0.42;

    // ---- 刻度（换算到弧长做 AA） ----
    float minAng = abs(fract(az * 60.0 / TAU + 0.5) - 0.5) * (TAU / 60.0) * r;
    float minTick = (1.0 - smoothstep(0.0016, 0.0016 + px * 1.2, minAng))
                  * band(r, R_FACE * 0.872, R_FACE * 0.932, px);
    float hourAng = abs(fract(az * 12.0 / TAU + 0.5) - 0.5) * (TAU / 12.0) * r;
    float hourTick = (1.0 - smoothstep(0.0040, 0.0040 + px * 1.4, hourAng))
                   * band(r, R_FACE * 0.802, R_FACE * 0.932, px * 1.2);

    col += vec3(0.52, 0.86, 1.00) * minTick * 0.50;
    col += vec3(0.86, 0.97, 1.00) * hourTick * 1.00;

    // 12 点位置的菱形宝石
    {
        float dAz = abs(mod(az - PI * 0.5 + PI, TAU) - PI) * r;
        float dR  = (r - R_FACE * 0.745);
        float dia = 1.0 - smoothstep(0.0085, 0.0085 + px * 1.6, abs(dAz) * 1.6 + abs(dR));
        col += vec3(0.72, 0.96, 1.00) * dia * 1.25;
        col += vec3(0.22, 0.58, 0.90) * pow(dia, 0.42) * 0.40;
    }

    // 四个方位的宝石钉
    for (int i = 0; i < 4; i++) {
        float ang = float(i) * PI * 0.5 + PI * 0.25;
        float dAz = abs(mod(az - ang + PI, TAU) - PI) * r;
        vec2 p = vec2(dAz * 2.6, (r - R_FACE * 0.70));
        float d = length(p);
        col += vec3(0.44, 0.80, 1.00) * (1.0 - smoothstep(0.012, 0.012 + px * 2.0, d)) * 0.70;
    }

    // =====================================================================
    //  6. 指针（停在 10:09:30，带"想走却走不动"的微颤）
    // =====================================================================
    float hourAngle   = PI * 0.5 + (10.0 + 9.0 / 60.0) / 12.0 * TAU;
    float minuteAngle = PI * 0.5 + (9.0 + 30.0 / 60.0) / 60.0 * TAU;
    float secondAngle = PI * 0.5 + 30.0 / 60.0 * TAU;

    float trembleH = sin(t * 9.1) * 0.0020 + sin(t * 23.7) * 0.0008;
    float trembleM = sin(t * 7.3 + 1.7) * 0.0028 + sin(t * 31.1) * 0.0010;
    float trembleS = sin(t * 17.3 + 0.3) * 0.0045 + sin(t * 41.9) * 0.0016;

    float dH = abs(mod(az - (hourAngle   + trembleH) + PI, TAU) - PI) * r;
    float dM = abs(mod(az - (minuteAngle + trembleM) + PI, TAU) - PI) * r;
    float dS = abs(mod(az - (secondAngle + trembleS) + PI, TAU) - PI) * r;
    // 秒针尾配重（反向短针）
    float dS2 = abs(mod(az - (secondAngle + trembleS + PI) + PI, TAU) - PI) * r;

    float handH = clockHand(dH, r, R_FACE * 0.53, 0.019, 0.008);
    float handM = clockHand(dM, r, R_FACE * 0.79, 0.0105, 0.0045);
    float handS = clockHand(dS, r, R_FACE * 0.86, 0.0035, 0.0020) * 0.75;
    float tailS = clockHand(dS2, r * 3.0, R_FACE * 0.16, 0.0035, 0.0020) * 0.55;

    // 指针投影：沿"光来自左上方"的方向偏移一小段，做出悬浮层次
    float shH = clockHand(abs(mod(az - (hourAngle + trembleH + 0.0075) + PI, TAU) - PI) * r, r,
                          R_FACE * 0.53, 0.021, 0.010);
    float shM = clockHand(abs(mod(az - (minuteAngle + trembleM + 0.0075) + PI, TAU) - PI) * r, r,
                          R_FACE * 0.79, 0.0125, 0.0060);
    col *= 1.0 - 0.30 * (shH + shM);

    vec3 handCore = vec3(0.92, 0.98, 1.00);
    vec3 handWarm = vec3(1.00, 0.88, 0.66);
    col += mix(handWarm, handCore, 0.70) * handH * 1.10;
    col += handCore * handM * 1.40;
    col += mix(handCore, vec3(1.00, 0.62, 0.45), 0.55) * (handS + tailS) * 1.00;

    // 指针辉光（高级感来源）
    float glowH = exp(-pow(dH / 0.060, 2.0)) * (1.0 - smoothstep(R_FACE * 0.53, R_FACE * 0.60, r));
    float glowM = exp(-pow(dM / 0.048, 2.0)) * (1.0 - smoothstep(R_FACE * 0.79, R_FACE * 0.86, r));
    col += vec3(0.18, 0.52, 0.92) * (glowH * 0.28 + glowM * 0.38);

    // 中心轴：宝石 + 齿轮盘
    {
        float hub  = 1.0 - smoothstep(0.016, 0.020, r);
        float hubR = ringLine(r, 0.032, 0.0035, px * 1.5);
        col += vec3(0.88, 0.98, 1.00) * hub * 1.35;
        col += vec3(0.28, 0.64, 1.00) * hubR * 0.75;
        col += vec3(0.10, 0.34, 0.70) * exp(-pow(r / 0.045, 2.0)) * 0.20;
    }

    // 冻结脉冲：从圆心向外扩散的时间涟漪，走到一半就凝住
    {
        float phase = fract(t * 0.40);
        float rr = phase * R_FACE * 1.30;
        float waveWidth = 0.008 + 0.014 * phase;
        float pulse = exp(-pow((r - rr) / waveWidth, 2.0)) * (1.0 - phase) * (1.0 - phase);
        col += vec3(0.16, 0.50, 0.95) * pulse * 0.50;
    }

    // 冰晶飞屑：表盘外缘的细小亮尘（冻结在空中的雪）
    {
        vec3 dc = dir * 70.0;
        vec3 cell = floor(dc);
        vec3 rnd = hash33(cell + 5.0);
        float d = length(dc - (cell + 0.5 + (rnd - 0.5) * 0.8));
        float spark = (1.0 - smoothstep(0.05, 0.10 + px * 60.0, d)) * step(0.86, rnd.z);
        float zone = exp(-pow((r - R_FACE * 1.25) / 0.42, 2.0));
        col += vec3(0.60, 0.92, 1.00) * spark * zone * 0.55;
    }

    // =====================================================================
    //  7. 地平线雾化 + 仰望时脚下压暗
    // =====================================================================
    // 只在地平线附近做很淡的过渡，不做横向亮带；表盘范围内再减弱，避免切断时计
    float haze = exp(-pow(max(dir.y, 0.0) / 0.11, 2.0));
    col = mix(col, SkyFogColor, haze * 0.22 * (1.0 - faceMask * 0.78));

    // 仰望时脚下（悬崖外看到的空处）压成深色雾，避免"天空铺到脚底"
    vec3 belowCol = mix(SkyFogColor * 0.26, vec3(0.006, 0.012, 0.026), 0.58);
    col = mix(col, belowCol, saturate(-dir.y * 4.0) * 0.88);

    // =====================================================================
    //  8. 收尾：亮部辉光 / 冷色分级 / 色调映射 / 暗角 / 抖动
    // =====================================================================
    col *= SkyDayBrightness;

    vec3 bloom = pow(max(col - 0.60, 0.0), vec3(2.0));
    col += bloom * 0.50;

    // 冷色分级（蓝调）
    col = vec3(col.r * 0.94, col.g * 0.99, col.b * 1.06);

    // 软肩色调映射
    col = 1.0 - exp(-col * 1.25);
    col = pow(max(col, 0.0), vec3(0.96));

    // 暗角 + 镜头色散（分通道不同暗角）
    float rad = length(ndc * vec2(0.92, 1.0));
    col *= 1.0 - 0.26 * pow(saturate(rad * 0.72), 2.4);
    col.r *= mix(1.0, 0.986, saturate(rad * 0.7));
    col.b *= mix(1.0, 1.012, saturate(rad * 0.7));

    // 抖动，消除深蓝天穹的大面积色带
    float dither = (hash13(vec3(gl_FragCoord.xy, floor(t * 60.0))) - 0.5) / 255.0;
    col = max(col + dither, 0.0);

    fragColor = vec4(col, saturate(SkyIntensity));
}
