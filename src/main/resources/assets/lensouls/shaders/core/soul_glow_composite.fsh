#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 ScreenSize;
uniform vec4 Color1;
uniform vec4 Color2;
uniform vec4 Color3;
uniform vec4 Color4;
uniform float GlowStrength;
uniform float OutlineWidth;
uniform float GameTime;

in vec2 texCoord;

out vec4 fragColor;

float sampleAlpha(vec2 offset) {
    vec2 uv = texCoord + offset / ScreenSize;
    return texture(DiffuseSampler, uv).a;
}

void main() {
    vec2 px = vec2(1.0) / ScreenSize;
    float dx = OutlineWidth * px.x;
    float dy = OutlineWidth * px.y;

    float center = sampleAlpha(vec2(0.0));
    float top    = sampleAlpha(vec2(0.0, -dy));
    float bottom = sampleAlpha(vec2(0.0,  dy));
    float left   = sampleAlpha(vec2(-dx, 0.0));
    float right  = sampleAlpha(vec2( dx, 0.0));
    float tl     = sampleAlpha(vec2(-dx, -dy));
    float tr     = sampleAlpha(vec2( dx, -dy));
    float bl     = sampleAlpha(vec2(-dx,  dy));
    float br     = sampleAlpha(vec2( dx,  dy));

    float edgeX = left + tl + bl - right - tr - br;
    float edgeY = top + tl + tr - bottom - bl - br;
    float edge = sqrt(edgeX * edgeX + edgeY * edgeY);
    edge = clamp(edge * 1.5, 0.0, 1.0);

    float t = GameTime * 0.02;

    vec4 color;
    if (center > 0.01) {
        vec4 c1 = mix(Color1, Color2, 0.5 + 0.5 * sin(t * 1.3));
        vec4 c2 = mix(Color3, Color4, 0.5 + 0.5 * cos(t * 1.7 + 1.0));

        vec4 interiorColor = mix(c1, c2, 0.5 + 0.5 * sin(texCoord.x * 8.0 + texCoord.y * 6.0 + t * 0.5));
        interiorColor.a *= GlowStrength * 0.15;

        vec4 edgeColor = mix(c1, c2, 0.5 + 0.5 * sin(t * 2.0 + texCoord.x * texCoord.y * 4.0));
        edgeColor.a *= edge * GlowStrength * 0.6;

        color = vec4(
            interiorColor.rgb * (1.0 - edge) + edgeColor.rgb * edge,
            interiorColor.a + edgeColor.a
        );
    } else {
        vec4 c1 = mix(Color1, Color2, 0.5 + 0.5 * sin(t * 1.3));
        vec4 c2 = mix(Color3, Color4, 0.5 + 0.5 * cos(t * 1.7 + 1.0));
        vec4 edgeColor = mix(c1, c2, 0.5 + 0.5 * sin(t * 2.0));
        edgeColor.a *= edge * GlowStrength * 0.6;
        color = edgeColor;
    }

    fragColor = color;
}
