package com.plumejade.lensouls.client.render;

import com.github.L_Ender.cataclysm.client.animation.Netherite_Monstrosity_Animation;
import com.github.L_Ender.cataclysm.client.model.CMModelLayers;
import com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model;
import com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model;
import com.github.L_Ender.cataclysm.client.model.entity.Netherite_Monstrosity_Model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.entity.BossPhantomEntity;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 灾变（Cataclysm）兼容层——所有灾变/LionfishAPI 代码隔离在此类中。
 * <p>
 * 此类仅在 {@code ModList.get().isLoaded("cataclysm")} 为 true 时才会被加载，
 * 由 {@link BossPhantomRenderer} 通过 {@code Class.forName()} 动态加载。
 * 主渲染器无任何灾变 import，避免无灾变模组时类加载崩溃。
 */
public final class CataclysmCompat {

    private CataclysmCompat() {}

    // ========== 模型创建 ==========

    public static EntityModel<?> createIgnisModel() {
        return new Ignis_Model();
    }

    public static EntityModel<?> createEnderGuardianModel() {
        return new Ender_Guardian_Model();
    }

    /** Netherite Monstrosity 原生模型 + 技能动画（HierarchicalModel） */
    public static Object createMonstrosityAnim(EntityRendererProvider.Context context) {
        ModelPart p = context.bakeLayer(CMModelLayers.NETHERITE_MONSTROSITY_MODEL);
        return new BossPhantomModelIntegration.ModelWithAnimation(
                new Netherite_Monstrosity_Model(p),
                Netherite_Monstrosity_Animation.SMASH);
    }

    // ========== 动画器创建 ==========

    public static Object createIgnisAnimator(EntityModel<?> model) {
        return new IgnisAnimator((Ignis_Model) model);
    }

    public static Object createEnderGuardianAnimator(EntityModel<?> model) {
        return new EnderGuardianAnimator((Ender_Guardian_Model) model);
    }

    // ========== 渲染 ==========

    /** 渲染 Ignis / Ender Guardian 模型（手动动画驱动） */
    public static void renderModel(EntityModel<?> model, PoseStack poseStack,
                                   MultiBufferSource buffer, ResourceLocation texture,
                                   float r, float g, float b, int alpha) {
        RenderType rt = RenderType.entityTranslucentEmissive(texture);
        VertexConsumer vc = buffer.getBuffer(rt);
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(1.15f, 1.15f, 1.15f);
        model.renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY,
                packColor(r, g, b, alpha));
        poseStack.popPose();
    }

    /** 触发动画帧更新（Ignis / Ender Guardian） */
    public static void animateAndRender(Object animator, BossPhantomEntity entity,
                                        EntityModel<?> model, PoseStack poseStack,
                                        MultiBufferSource buffer, ResourceLocation texture,
                                        float r, float g, float b, int alpha) {
        if (animator == null) return;
        if (animator instanceof IgnisAnimator ia) {
            ia.animate(entity);
        } else if (animator instanceof EnderGuardianAnimator ea) {
            ea.animate(entity);
        }
        renderModel(model, poseStack, buffer, texture, r, g, b, alpha);
    }

    // ========== 动画同步 ==========

    /** 缓存 LionfishAPI Animation 引用（从实体类静态字段反射获取） */
    public static Object resolveLionfishAnim(BossPhantomType type) {
        String className, fieldName;
        if (type == BossPhantomType.IGNIS) {
            className = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity";
            fieldName = "SPIN_ATTACK";
        } else if (type == BossPhantomType.ENDER_GUARDIAN) {
            className = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity";
            fieldName = "GUARDIAN_BLACKHOLE";
        } else {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 修复 LionfishAPI 动画同步：客户端实体缺失 animation 引用时从缓存补上 */
    public static void syncEntityAnimation(BossPhantomEntity entity, Object cachedAnim) {
        if (cachedAnim == null) return;
        if (entity.getAnimation() != null) return;
        try {
            com.github.L_Ender.lionfishapi.server.animation.Animation anim =
                    (com.github.L_Ender.lionfishapi.server.animation.Animation) cachedAnim;
            entity.setAnimation(anim);
            entity.setAnimationTick(Math.min(entity.tickCount, anim.getDuration() - 1));
        } catch (Exception ignored) {}
    }

    // ========== 工具方法 ==========

    private static int packColor(float r, float g, float b, int alpha) {
        return (alpha << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
