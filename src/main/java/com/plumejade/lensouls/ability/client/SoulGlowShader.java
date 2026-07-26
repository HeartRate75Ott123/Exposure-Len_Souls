package com.plumejade.lensouls.ability.client;

import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.renderer.ShaderInstance;

public class SoulGlowShader {

    private static ShaderInstance shader;

    public static void setShader(ShaderInstance instance) {
        shader = instance;
    }

    public static ShaderInstance getShader() {
        return shader;
    }

    public static void setBossColors(BossOutlineColors colors) {
        if (shader == null) return;
        setVec3(shader, "BossColor1", colors.color1());
        setVec3(shader, "BossColor2", colors.color2());
        setVec3(shader, "BossColor3", colors.color3());
        setVec3(shader, "BossColor4", colors.color4());
    }

    public static void setGlowIntensity(float intensity) {
        if (shader == null) return;
        setFloat(shader, "GlowIntensity", intensity);
    }

    public static void setUseTextureAlpha(boolean use) {
        if (shader == null) return;
        setFloat(shader, "UseTextureAlpha", use ? 1f : 0f);
    }

    public static void setOutlineWidth(float width) {
        if (shader == null) return;
        setFloat(shader, "OutlineWidth", width);
    }

    public static void setUseGlowExpansion(boolean use) {
        if (shader == null) return;
        setFloat(shader, "UseGlowExpansion", use ? 1f : 0f);
    }

    private static void setVec3(ShaderInstance inst, String name, float[] v) {
        var uniform = inst.getUniform(name);
        if (uniform != null && v != null && v.length >= 3) {
            uniform.set(v[0], v[1], v[2]);
        }
    }

    private static void setFloat(ShaderInstance inst, String name, float v) {
        var uniform = inst.getUniform(name);
        if (uniform != null) uniform.set(v);
    }

    private SoulGlowShader() {}
}
