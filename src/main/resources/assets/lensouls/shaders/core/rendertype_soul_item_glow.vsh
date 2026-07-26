#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float OutlineWidth;
uniform float UseGlowExpansion;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position;
    if (UseGlowExpansion > 0.5) {
        pos += Normal * OutlineWidth;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
