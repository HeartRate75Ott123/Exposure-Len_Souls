#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

in vec2 texCoord;

out vec4 fragColor;

void main()
{
    vec4 color = texture(Sampler0, texCoord);
    float sceneDepth = texture(Sampler1, texCoord).r;
    float starDepth = texture(Sampler2, texCoord).r;
    float playerDepth = texture(Sampler3, texCoord).r;

    // 深度豁免：星空/玩家比场景近（未被遮挡）时保持彩色，其余灰阶
    float starVisible = step(starDepth, sceneDepth + 0.0005);
    float playerVisible = step(playerDepth, sceneDepth + 0.0005);
    float keepColor = max(starVisible, playerVisible);

    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 result = mix(vec3(luminance), color.rgb, keepColor);
    fragColor = vec4(result, color.a);
}