#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.01) {
        discard;
    }

    // 动态渐变：UV + 时间驱动，多维度动画
    // 色1: 亮金 (1.0, 0.84, 0.0)  色2: 暖橙 (1.0, 0.65, 0.1)
    vec3 gold1 = vec3(1.0, 0.84, 0.0);
    vec3 gold2 = vec3(1.0, 0.65, 0.1);
    vec3 gold3 = vec3(1.0, 0.92, 0.3);  // 亮白金色高光

    // ① 慢速渐变色流动（~3.5秒完整周期，肉眼可见波动的流动）
    float flow = texCoord0.x * 3.0 + texCoord0.y * 2.0 + GameTime * 0.06;
    float blend = sin(flow) * 0.5 + 0.5;
    vec3 warmColor = mix(gold1, gold2, blend);

    // ② 独立的高光扫掠（沿 UV 方向移动的光带）
    float highlight = sin(texCoord0.x * 6.0 + texCoord0.y * 4.0 + GameTime * 0.1);
    highlight = clamp(highlight * 0.5 + 0.5, 0.0, 1.0);
    vec3 finalColor = mix(warmColor, gold3, highlight * 0.4);

    // ③ 柔和呼吸式亮度脉冲
    float breathe = sin(GameTime * 0.05) * 0.06 + 0.94;

    finalColor *= breathe;
    float alpha = color.a * vertexColor.a;
    fragColor = vec4(finalColor, alpha) * ColorModulator;
}
