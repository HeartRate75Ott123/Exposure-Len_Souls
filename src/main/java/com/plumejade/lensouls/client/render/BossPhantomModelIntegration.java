package com.plumejade.lensouls.client.render;

import com.github.L_Ender.cataclysm.client.model.CMModelLayers;
import com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model;
import com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model;
import com.github.L_Ender.cataclysm.client.model.entity.Netherite_Monstrosity_Model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.miauczel.legendary_monsters.entity.AnimatedMonster.Animations.TheObliterator.TheObliteratorAnimations;
import net.miauczel.legendary_monsters.entity.animations.CloudGolemAnimations;
import net.miauczel.legendary_monsters.entity.animations.PosessedPaladinAnimations;
import net.miauczel.legendary_monsters.entity.client.ModModelLayers;
import net.miauczel.legendary_monsters.entity.client.Model.Cloud_GolemModel;
import net.miauczel.legendary_monsters.entity.client.Model.NewPossessedPaladinModel;
import net.miauczel.legendary_monsters.entity.client.Model.TheObliteratorModel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * BOSS 幻灵模型集成。
 * <p>
 * HierarchicalModel 模型：加载原生模型+技能动画定义，反射调用 animate()
 * LionfishAPI 模型（Ignis / Ender Guardian）：加载原生模型，wrapStaticModel 包装
 */
public class BossPhantomModelIntegration {

    /** HierarchicalModel 模型 + 技能动画 */
    @Nullable
    public static ModelWithAnimation loadAnimatedModel(EntityRendererProvider.Context context, BossPhantomType type) {
        return switch (type) {
            case CLOUD_GOLEM -> loadIfLoaded("legendary_monsters", () -> {
                ModelPart p = context.bakeLayer(ModModelLayers.CLOUD_GOLEM_LAYER);
                return new ModelWithAnimation(new Cloud_GolemModel<>(p), CloudGolemAnimations.laser4);
            });
            case POSSESSED_PALADIN -> loadIfLoaded("legendary_monsters", () -> {
                ModelPart p = context.bakeLayer(ModModelLayers.NEW_POSSESSED_PALADIN_LAYER);
                return new ModelWithAnimation(new NewPossessedPaladinModel<>(p), PosessedPaladinAnimations.ATTACK3);
            });
            case OBLITERATOR -> loadIfLoaded("legendary_monsters", () -> {
                ModelPart p = context.bakeLayer(ModModelLayers.THE_OBLITERATOR_LAYER);
                return new ModelWithAnimation(new TheObliteratorModel<>(p), TheObliteratorAnimations.rightArmSpinSmash);
            });
            case NETHERITE_MONSTROSITY -> loadIfLoaded("cataclysm", () -> {
                ModelPart p = context.bakeLayer(CMModelLayers.NETHERITE_MONSTROSITY_MODEL);
                return new ModelWithAnimation(
                        new Netherite_Monstrosity_Model(p),
                        com.github.L_Ender.cataclysm.client.animation.Netherite_Monstrosity_Animation.SMASH);
            });
            case IGNIS, ENDER_GUARDIAN -> null;
        };
    }

    /** LionfishAPI 模型——原生模型 + 静态姿势包装 */
    @Nullable
    public static EntityModel<?> loadStaticModel(EntityRendererProvider.Context context, BossPhantomType type) {
        return switch (type) {
            case IGNIS -> loadIfLoaded("cataclysm", () -> wrapStatic(new Ignis_Model()));
            case ENDER_GUARDIAN -> loadIfLoaded("cataclysm", () -> wrapStatic(new Ender_Guardian_Model()));
            default -> null;
        };
    }

    /** 包装为静态姿势（setupAnim 无操作，renderToBuffer 委托） */
    @SuppressWarnings("unchecked")
    private static <T extends Entity> EntityModel<T> wrapStatic(EntityModel<?> inner) {
        return new EntityModel<T>() {
            @Override
            public void setupAnim(T e, float a, float b, float c, float d, float f) {}
            @Override
            public void renderToBuffer(PoseStack p, VertexConsumer v, int i, int j, int k) {
                inner.renderToBuffer(p, v, i, j, k);
            }
        };
    }

    // ========== 通用加载 ==========

    @Nullable
    private static <T> T loadIfLoaded(String modId, SafeSupplier<T> supplier) {
        if (!ModList.get().isLoaded(modId)) return null;
        try { return supplier.get(); }
        catch (Exception e) { LenSouls.LOGGER.warn("[幻灵] 加载 {} 失败", modId, e); return null; }
    }

    public record ModelWithAnimation(HierarchicalModel<?> model, AnimationDefinition skillAnim) {}

    @FunctionalInterface
    interface SafeSupplier<T> { T get() throws Exception; }
}
