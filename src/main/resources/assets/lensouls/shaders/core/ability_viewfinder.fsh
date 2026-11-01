#version 150

// ============================================================================
//  Len Souls - 能力取景框色晕（整屏椭圆羽化，顶在所有东西之上）
//
//  触发：手持相机选中的能力 + 右键打开 Exposure 取景框。
//  位置：帧末 GuiGraphics.flush() 之后的全屏 pass —— Exposure 的取景框是在
//        Gui.render 的 HEAD 画的，所以这一层必然压在取景框之上（含四角臂/画幅/刻度）。
//  做法：不做任何采样（不读 DiffuseSampler）。画面保持原样，只叠一层 alpha 色晕：
//        **整屏椭圆羽化** —— 一个宽而长的过渡（FEATHER_IN..FEATHER_OUT，跨了整个半径），
//        中心几乎为 0、越往外越浓、四角最浓。核心区不是被"挡住"，而是被这条长渐变
//        自然让开：羽化越宽，中心越干净。
//  为什么不做画幅矩形保护（用户纠正过）：把画幅当硬边界去裁，会得到"两条平色带"，
//        既不像参考图的椭圆、画幅边还会留一圈生硬界线。全屏羽化 + 宽过渡才是对的。
//  颜色：由 Java 侧按当前能力从能力球贴图取色传进来（uniform FxColor），
//        换能力时在两色之间插值，不会一帧跳色。
// ============================================================================

in vec2 vUV;
out vec4 fragColor;

uniform vec2  FxScreenSize;   // 只有诊断用；形状按 uv 归一化，不随分辨率变
uniform float FxTime;

uniform vec3  FxColor;        // 色晕主色（sRGB 0..1，来自能力球贴图）
uniform float FxAlpha;        // 0..1 总不透明度（入场 / 退场的主通道）
uniform float FxReach;        // 0..1 前沿：0 = 椭圆还很大（只在四角），1 = 定型
uniform vec2  FxEllipse;      // 椭圆半轴（uv：0.5 = 半屏）

float saturate(float x) { return clamp(x, 0.0, 1.0); }

// 羽化窗口（椭圆归一化半径）：0 = 屏心，1 = 椭圆边界。
// 窗口越宽 → 渐变越长 → 中心越干净、边缘越早开始上色。这是唯一的形状旋钮。
const float FEATHER_IN  = 0.30;
const float FEATHER_OUT = 1.12;
const float PEAK_ALPHA  = 0.60;

void main() {
    vec2 e = (vUV - 0.5) / max(FxEllipse, vec2(1.0e-3));
    float me = length(e) * mix(1.45, 1.0, saturate(FxReach));

    float t = saturate((me - FEATHER_IN) / (FEATHER_OUT - FEATHER_IN));
    float field = t * t * (3.0 - 2.0 * t);

    // 慢呼吸（1.7 rad/s，约 3.7 秒一轮）：不要一闪一闪
    float breath = 0.94 + 0.06 * sin(FxTime * 1.7);
    float a = FxAlpha * breath * PEAK_ALPHA * field;

    // 抖一下最低位，避免大面积渐变出现色带
    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    a = saturate(a + dither);

    // 往白提亮 34%：保住色相的同时把"彩色玻璃"变成"雾"
    vec3 col = mix(FxColor, vec3(1.0), 0.34) * (0.94 + 0.12 * field);

    fragColor = vec4(col, a);
}
