#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(DiffuseSampler, texCoord0);
    // 附魔闪烁纹理为灰度图，任意通道获取强度
    float alpha = texColor.r;

    // 红色脉冲：0.3 ~ 1.0，~2Hz
    float pulse = 0.5 + 0.5 * sin(Time * 4.0);
    float intensity = alpha * (0.3 + 0.7 * pulse) * 0.5;

    // 正红色，alpha 随脉冲变化
    fragColor = vec4(1.0, 0.12, 0.15, intensity);
}
