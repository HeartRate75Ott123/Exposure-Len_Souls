#version 150

// SDF 圆角矩形：真正的矢量圆角（每像素计算有符号距离场，边缘按像素抗锯齿）。
// 不用任何贴图、不用网格细分 —— 圆角在任何分辨率下都是解析精确的。
//
// 顶点属性：
//   UV0.x -> 矩形内归一化 x（0..1，仅用于渐变）
//   UV0.y -> 不参与插值的占位（保留给以后用）

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vLocal;
out float vGradT;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vLocal = UV0;
    vGradT = UV0.x;
}
