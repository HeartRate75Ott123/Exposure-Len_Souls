#version 150

// 时间定格天空 — 全屏四边形顶点着色器。
//
// 顶点直接输出到裁剪空间（不经过 ModelViewMat / ProjMat）：
//   * z = 1.0 → 远平面深度。主目标深度在该像素仍为 clear 值时（=无地形遮挡的天空）
//     才通过 LEQUAL 深度测试，因此这一个 pass 天然只覆盖"天空区域"，
//     地形 / 实体 / 半透明方块照旧遮挡——不需要采样深度纹理。
//   * 视射线在片元着色器中用相机基向量重建（见 fsh），所以天空是"世界锚定"的。

in vec3 Position;

out vec2 vScreen;

void main() {
    vScreen = Position.xy;
    gl_Position = vec4(Position.xy, 1.0, 1.0);
}
