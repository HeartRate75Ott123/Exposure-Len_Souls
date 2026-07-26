#version 150

uniform vec4 ColorModulator;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float alpha = vertexColor.a;
    if (alpha < 0.01) discard;
    // 颜色完全由 Java 侧每帧计算，着色器做增亮和输出
    fragColor = vec4(vertexColor.rgb * 2.4, alpha) * ColorModulator;
}
