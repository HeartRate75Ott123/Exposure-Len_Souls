#version 150

// ============================================================================
//  Len Souls - 屏幕后处理特效（统一一张）
//
//  在 AFTER_LEVEL（Iris 合成之后、清深度之前）画全屏四边形，输入是主帧缓冲的
//  颜色拷贝（DiffuseSampler），输出直接覆盖主帧缓冲。子效果全部由 uniform 驱动：
//    · 破碎镜面（SHARD）—— Voronoi 裂片错位 + 彩虹色散裂纹 + 碎片亮度差
//    · 径向拉伸（WARP）—— UV 向中心推进 + 沿径向多 tap 拖尾（隧道感）
//    · 色散（CHROMA）
//    · 径向速度线（STREAK，弱化使用）
//    · 扩散圆环（RIPPLE，可走彩虹棱镜色）
//    · 暗角染色（TINT，带呼吸脉冲）
//    · RGB 脉冲（RGB，三通道错拍呼吸 + 朝外推的彩色脉冲环，边缘最强）
//    · 椭圆羽化蒙版（MASK，把上面这些子效果裁进一个边缘羽化的椭圆里，
//      前沿可推进/后退 —— 时空回溯"四周覆盖 / 中心褪去"用的就是这一层）
//    · 全屏闪光（FLASH，画在蒙版之后：白场始终整屏，负责盖住传送那一帧）
// ============================================================================

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D DiffuseSampler;

uniform vec2  FxScreenSize;
uniform float FxTime;

uniform float FxWarp;
uniform vec2  FxWarpCenter;
uniform float FxChroma;
uniform float FxStreak;

uniform float FxShard;       // 0..1 破碎镜面强度
uniform float FxShardPhase;  // 0..1 碎裂推进（0=完好，1=全脱落）
uniform float FxShardRestore; // 0..1 复位：碎块错位与裂缝平滑收回原位
uniform float FxPrism;       // 0..1 涟漪是否走彩虹棱镜色

uniform float FxRgb;           // 0..1 RGB 脉冲（三通道错拍呼吸 + 色相跑马灯，边缘最强）

uniform float FxBlurH;         // 0..1 横向失焦（只沿 X 方向扩散）
uniform float FxViewfinder;    // 0..1 取景框整体不透明度
uniform float FxViewfinderOpen; // 0..1 取景框开合（0 = 刚出现/外扩，1 = 定格收紧）


uniform float FxFlash;
uniform vec3  FxFlashColor;

uniform vec4  FxTint;
uniform vec4  FxRipple;      // xy=中心，z=半径，w=强度
uniform vec3  FxRippleColor;

// ── 椭圆羽化蒙版 ────────────────────────────────────────────────────────────
//  FxMask       0..1  蒙版强度（0 = 不裁切，整屏都是特效；1 = 完全按椭圆裁切）
//  FxMaskEdge         羽化带中心处的"椭圆归一化半径"：0 = 屏幕中心，1 = 椭圆边界
//  FxMaskSoft         羽化带半宽（以椭圆归一化半径为单位）
//  FxMaskAlpha  0..1  蒙版内特效的整体不透明度（0 = 只剩原画面，1 = 全实）
//  FxMaskRadius       椭圆半轴（uv 归一化：0.5 = 半屏）
//  FxMaskCenter       椭圆中心（uv）
//  前沿(FxMaskEdge)小 → 只剩外圈有特效；前沿大 → 圈外也退干净，整屏回到原画面。
//  因此同一个公式，前沿从大到小收 = 从四周向中心覆盖；从小到大推 = 中心先褪、逐圈向外退。
//  FxMaskAlpha 与前沿正交：前沿管"盖到哪"，它管"盖到的地方有多实"，入场两者一起走才不突兀。
uniform float FxMask;
uniform float FxMaskEdge;
uniform float FxMaskSoft;
uniform float FxMaskAlpha;
uniform vec2  FxMaskRadius;
uniform vec2  FxMaskCenter;

float saturate(float x) { return clamp(x, 0.0, 1.0); }

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

vec3 sceneAt(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

/** 棱镜彩虹（0..1 三通道相位错开） */
vec3 rainbow(float phase) {
    return 0.5 + 0.5 * cos(6.2831853 * (vec3(0.0, 0.3333, 0.6667) + phase));
}

// ── 取景框用的 SDF 线工具（屏幕中心坐标系，已做宽高比校正） ──

/** 一条线（d = 到线心的有符号距离），halfW = 半宽，边缘 0.0025 软化（抗锯齿） */
float vfLine(float d, float halfW) {
    return 1.0 - smoothstep(halfW, halfW + 0.0025, abs(d));
}

/** 沿某方向从 0 到 -len 的一段（两端都软化），用于取景框四角的"臂" */
float vfSpan(float v, float len) {
    return smoothstep(-len - 0.0035, -len + 0.0035, v) * (1.0 - smoothstep(-0.0035, 0.0035, v));
}

/**
 * 破碎镜面：把屏幕切成 Voronoi 裂片，每片按自身随机方向错开并轻微缩放，
 * 同时输出"裂纹边缘强度"与"碎片亮度差"。
 */
vec2 shatterUV(vec2 uv, float strength, float phase, float restore,
               out float crack, out float glint, out float detach) {
    vec2 aspect = vec2(FxScreenSize.x / max(FxScreenSize.y, 1.0), 1.0);
    vec2 sq = vec2(uv.x * aspect.x, uv.y);

    vec2 p = sq * 4.5;
    vec2 i = floor(p);
    vec2 f = fract(p);

    float d1 = 8.0;
    float d2 = 8.0;
    vec2 nearest = vec2(0.0);

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = hash22(i + g);
            vec2 r = g + o - f;
            float d = length(r);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                nearest = i + g;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }

    float edge = d2 - d1;

    vec2 h = hash22(nearest + 17.0);
    vec2 dir = h * 2.0 - 1.0;

    // 逐片脱落：每片有自己的起爆时刻（按 hash 排序），越晚的越晚飞走
    float order = h.y;
    float k = smoothstep(order * 0.72, order * 0.72 + 0.28, phase);

    // 复位：把错位、缩小、裂隙一起按 (1 - restore) 收回 —— 碎块"滑回原位"
    float keep = 1.0 - clamp(restore, 0.0, 1.0);

    // 脱落 = 沿自身方向飞出去 + 缩小；缩小时与邻片之间露出"时间裂隙"
    vec2 offset = dir * (0.020 * strength) * (1.0 + 7.0 * k) * keep;
    // phase > 1 的部分当作"下坠"：整块镜面向下掉，配合复位一起收回
    float drop = max(phase - 1.0, 0.0);
    offset.x += sign(dir.x) * 0.010 * drop * strength * keep;
    offset.y -= 0.42 * drop * strength * keep;
    float scale = (1.0 + (h.x - 0.5) * 0.04 * strength) * (1.0 - 0.22 * k * keep);

    vec2 center = vec2(0.5 * aspect.x, 0.5);
    vec2 local = (sq - center) * scale + center + offset;

    // 缝隙：脱落越多，边缘露出的黑缝越宽
    float gap = (0.014 + k * 0.045) * keep;
    // 裂纹线：细而亮
    crack = 1.0 - smoothstep(gap * 0.35, gap * 0.35 + 0.012, edge);
    // 脱落处：只有贴边的窄条露出裂隙，k 越大缝越宽（复位时一起收掉）
    detach = k * k * (1.0 - smoothstep(gap * 0.45, gap, edge)) * keep;

    glint = 0.72 + 0.28 * sin(h.x * 41.0 + h.y * 23.0);

    return vec2(local.x / aspect.x, local.y);
}

/** 径向拉伸 + 沿径向拖尾 */
vec3 warpedSample(vec2 uv, float warp, vec2 center) {
    if (warp <= 0.001) return sceneAt(uv);

    vec2 aspect = vec2(FxScreenSize.x / max(FxScreenSize.y, 1.0), 1.0);
    vec2 d = (uv - center) * aspect;
    vec2 dirv = normalize(d + vec2(1.0e-5));

    vec2 base = center + (uv - center) * (1.0 - 0.10 * warp);

    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i) / 5.0;
        vec2 suv = base + (dirv * fi * (warp * 0.075)) / aspect;
        float w = 1.0 - 0.72 * fi;
        acc += sceneAt(suv) * w;
        wsum += w;
    }
    return acc / max(wsum, 1.0e-3);
}

void main() {
    vec2 center = FxWarpCenter;
    vec2 aspect = vec2(FxScreenSize.x / max(FxScreenSize.y, 1.0), 1.0);

    // 屏幕抖动不走这里：{@code CameraShake} 直接抖相机角度（后处理里偏移采样太不明显，实测看不出）
    vec2 uv = vUV;

    // ---- 破碎镜面 ----
    float crack = 0.0;
    float glint = 1.0;
    float detach = 0.0;
    if (FxShard > 0.001) {
        // 传 uv（已含抖动/放大）而不是 vUV：削韧反馈里"抖屏"和"炸碎"是同时进行的
        uv = shatterUV(uv, FxShard, FxShardPhase, FxShardRestore, crack, glint, detach);
    }

    vec2 d = (uv - center) * aspect;
    float r = length(d);

    // ---- 拉伸 + 色散 ----
    vec3 col;
    if (FxChroma > 0.001) {
        float ca = 0.035 * FxChroma;
        vec2 uvR = center + (uv - center) * (1.0 - ca);
        vec2 uvB = center + (uv - center) * (1.0 + ca);
        col.r = warpedSample(uvR, FxWarp, center).r;
        col.g = warpedSample(uv, FxWarp, center).g;
        col.b = warpedSample(uvB, FxWarp, center).b;
    } else {
        col = warpedSample(uv, FxWarp, center);
    }

    // ---- 横向失焦（镜头散光/脱焦：只沿水平方向做多 tap 扩散，垂直方向不动） ----
    if (FxBlurH > 0.001) {
        // 峰值约 ±3% 屏宽（5 个 tap × 0.006），再大就成了"糊成一片"
        float amt = FxBlurH * 0.006;
        vec3 acc = col;
        float wsum = 1.0;
        for (int i = 1; i <= 5; i++) {
            float fi = float(i) / 5.0;
            float w = 1.0 - 0.68 * fi;
            acc += sceneAt(uv + vec2(amt * fi, 0.0)) * w;
            acc += sceneAt(uv - vec2(amt * fi, 0.0)) * w;
            wsum += 2.0 * w;
        }
        col = acc / wsum;
    }

    // ---- 碎片亮度差 + 裂纹（先压暗出厚度，再加彩虹色散） ----
    if (FxShard > 0.001) {
        col *= mix(1.0, glint, FxShard * 0.85);
        col *= 1.0 - 0.22 * crack * FxShard;
        vec3 crackColor = rainbow(crack * 1.6 + FxTime * 0.18 + vUV.x * 0.25);
        col += crackColor * crack * FxShard * 0.42;
        col += vec3(1.0) * pow(crack, 4.0) * FxShard * 0.10;

        // 脱落处露出"时间裂隙"：深蓝黑 + 冷光边缘
        vec3 rift = vec3(0.012, 0.022, 0.042) + crackColor * 0.10;
        col = mix(col, rift, clamp(detach * FxShard, 0.0, 1.0));
    }

    // ---- 径向速度线（弱化：只做极细的辅助动感） ----
    if (FxStreak > 0.001) {
        float ang = atan(d.y, d.x);
        float n1 = sin(ang * 74.0 + FxTime * 9.0) * 0.5 + 0.5;
        float n2 = sin(ang * 29.0 - FxTime * 5.0 + 1.7) * 0.5 + 0.5;
        float streak = pow(n1, 9.0) * 0.55 + pow(n2, 11.0) * 0.35;
        float mask = smoothstep(0.12, 0.55, r) * (1.0 - smoothstep(0.72, 1.05, r));
        col += vec3(0.62, 0.88, 1.00) * streak * mask * FxStreak * 0.32;
    }

    // ---- 扩散圆环（可切彩虹棱镜色） ----
    if (FxRipple.w > 0.001) {
        vec2 rd = (vUV - FxRipple.xy) * aspect;
        float rr = length(rd);
        float w = max(0.006, FxRipple.z * 0.10 + 0.012);
        // 平方写成乘法：GLSL 的 pow(x,y) 在 x<0 时未定义，而环内侧 (rr - 半径) 恒为负
        float rt = (rr - FxRipple.z) / w;
        float ring = exp(-rt * rt);
        vec3 ringColor = mix(FxRippleColor, rainbow(rr * 2.2 + FxTime * 0.35), saturate(FxPrism));
        col += ringColor * ring * FxRipple.w;
    }

    // ---- 暗角染色 ----
    if (FxTint.a > 0.001) {
        float vig = smoothstep(0.28, 1.02, r * 1.10);
        float pulse = 0.72 + 0.28 * sin(FxTime * 16.0);
        col = mix(col, FxTint.rgb, saturate(FxTint.a * vig * pulse));
    }

    // ---- RGB 脉冲（定身冲击：三通道错拍呼吸 + 朝外推的彩色脉冲环） ----
    //    放在暗角之后：暗角压在边缘，RGB 脉冲必须最后加，才不会被压成灰蓝。
    //    中心几乎不动（免得糊住准星），越靠边越明显。
    if (FxRgb > 0.001) {
        float rim = smoothstep(0.18, 1.02, r);
        // 从中心朝外连续推进的脉冲波（一亮一暗，节拍由 FxTime 决定）
        float wave = pow(0.5 + 0.5 * cos(r * 6.0 - FxTime * 15.0), 3.0);
        // 色相轮转：同一帧里不同半径是不同颜色，像 RGB 跑马灯
        vec3 tone = rainbow(FxTime * 0.8 - r * 0.35);
        float amt = FxRgb * wave * mix(0.12, 1.0, rim);
        col += tone * amt * 0.26;

        // 三通道各自呼吸（相位差 120°）：要的是"RGB"，不是单纯提亮
        vec3 breath = 0.5 + 0.5 * cos(FxTime * 11.0 + vec3(0.0, 2.0944, 4.1888));
        col *= 1.0 + (breath - 0.5) * FxRgb * 0.30 * mix(0.25, 1.0, rim);
    }

    // ---- 取景框（银白：四角臂 + 细画幅边框 + 中央圆环 + 圆内十字 + 环外刻度） ----
    if (FxViewfinder > 0.001) {
        vec2 p = (vUV - 0.5) * aspect;

        float bx = max(aspect.x * 0.5 - 0.072, 0.10);
        float by = max(0.5 - 0.072, 0.10);

        // 1) 四角臂（视觉主角：相机取景器的"括号"）
        float armLen = 0.090;
        float cw = 0.0017;
        float arms = 0.0;
        for (int i = 0; i < 4; i++) {
            float sx = (i == 0 || i == 2) ? 1.0 : -1.0;
            float sy = (i < 2) ? 1.0 : -1.0;
            vec2 d = p - vec2(sx * bx, sy * by);
            float ax = vfLine(d.y, cw) * vfSpan(d.x * sx, armLen);
            float ay = vfLine(d.x, cw) * vfSpan(d.y * sy, armLen);
            arms = max(arms, max(ax, ay));
        }

        // 2) 细画幅边框（很淡，只把"画幅"交代清楚）
        float insideX = 1.0 - smoothstep(bx - 0.004, bx + 0.004, abs(p.x));
        float insideY = 1.0 - smoothstep(by - 0.004, by + 0.004, abs(p.y));
        float border = max(vfLine(p.y - by, 0.0010) * insideX,
                           vfLine(p.x - bx, 0.0010) * insideY) * 0.34;

        // 3) 中央圆环（出现时略大 → 定格收紧）
        //    只保留<b>一个</b>圈：再加内圈会变成"同心双环"，不如单圈干净
        float ringR = mix(0.205, 0.150, saturate(FxViewfinderOpen));
        float rp = length(p);
        float ring = vfLine(rp - ringR, 0.0024);

        // 4) 圆内十字（端点收在圆环内侧，不穿出去）
        float reach = ringR * 0.82;
        float crossH = vfLine(p.y, 0.0016) * (1.0 - smoothstep(reach - 0.004, reach + 0.004, abs(p.x)));
        float crossV = vfLine(p.x, 0.0016) * (1.0 - smoothstep(reach - 0.004, reach + 0.004, abs(p.y)));

        // 5) 环外四个刻度
        float tickIn = ringR + 0.014;
        float tickOut = ringR + 0.040;
        float tickV = vfLine(p.x, 0.0013)
                * smoothstep(tickIn, tickIn + 0.004, abs(p.y))
                * (1.0 - smoothstep(tickOut - 0.004, tickOut, abs(p.y)));
        float tickH = vfLine(p.y, 0.0013)
                * smoothstep(tickIn, tickIn + 0.004, abs(p.x))
                * (1.0 - smoothstep(tickOut - 0.004, tickOut, abs(p.x)));

        float lines = max(max(arms, border),
                          max(ring, max(max(crossH, crossV), max(tickV, tickH))));

        // 画幅外轻微压暗（取景器里更亮、取景器外更沉）
        float outside = smoothstep(0.0, 0.045, max(abs(p.x) - bx, abs(p.y) - by));
        col *= 1.0 - 0.24 * FxViewfinder * outside;

        vec3 silver = vec3(0.86, 0.91, 1.00);
        float breathe = 0.90 + 0.10 * sin(FxTime * 3.4);
        col += silver * lines * FxViewfinder * 1.05 * breathe;
    }

    // ---- 椭圆羽化蒙版（时空回溯：进入从四周覆盖、退出从中心向四周褪去） ----
    //  做法：算一个"椭圆归一化半径" me（me=0 屏心、me=1 椭圆边界、>1 椭圆外），
    //  再用 smoothstep(前沿-羽化, 前沿+羽化, me) 得到"特效保留量"，
    //  羽化带以内的部分退回"未处理"的原画面（sceneAt(vUV)，即拷贝纹理的原始像素）。
    //  前沿由 Java 侧推进：进入时从大到小收（外圈先盖、逐圈漫向中心），
    //  退出时从小到大推（中心先褪、逐圈向外退干净），两端都不会出现硬边。
    if (FxMask > 0.001) {
        vec2 mq = (vUV - FxMaskCenter) / max(FxMaskRadius, vec2(1.0e-3));
        float me = length(mq);
        float msoft = max(FxMaskSoft, 1.0e-3);
        float keep = smoothstep(FxMaskEdge - msoft, FxMaskEdge + msoft, me);
        keep = mix(1.0, keep, saturate(FxMask));
        // 再乘一层整体不透明度：入场时前沿推进 + 由淡到浓同时进行（退出时恒为 1）
        keep *= saturate(FxMaskAlpha);
        col = mix(sceneAt(vUV), col, keep);
    }

    // ---- 全屏闪光 ----
    if (FxFlash > 0.001) {
        col = mix(col, FxFlashColor, saturate(FxFlash));
    }

    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    fragColor = vec4(max(col + dither, 0.0), 1.0);
}
