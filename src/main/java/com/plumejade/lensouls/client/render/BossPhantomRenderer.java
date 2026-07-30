package com.plumejade.lensouls.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.plumejade.lensouls.LenSouls;
import com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model;
import com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model;
import com.plumejade.lensouls.client.model.BossPhantomModel;
import com.plumejade.lensouls.entity.BossPhantomEntity;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * BOSS 虚影幻灵渲染器。
 * <p>
 * HierarchicalModel 模型（Cloud Golem / Possessed Paladin / Obliterator / Netherite Monstrosity）
 * 使用原生模型 + 技能 AnimationDefinition 驱动完整动画。
 * LionfishAPI 模型（Ignis / Ender Guardian）使用内置人形模型 + 手动动画。
 */
public class BossPhantomRenderer extends EntityRenderer<BossPhantomEntity> {

    public static final ModelLayerLocation PHANTOM_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "boss_phantom"), "main");

    // 内置后备模型（仅当 LionfishAPI 模型不可用时的 Ignis/Ender Guardian 降级）
    private final BossPhantomModel<BossPhantomEntity> fallbackModel;

    // 各 BOSS 的动画模型捆绑
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation cloudGolemAnim;
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation paladinAnim;
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation obliteratorAnim;
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation monstrosityAnim;

    // LionfishAPI 原生模型 + 动画器
    @Nullable private Ignis_Model ignisModel;
    @Nullable private IgnisAnimator ignisAnim;
    @Nullable private Ender_Guardian_Model enderGuardianModel;
    @Nullable private EnderGuardianAnimator enderGuardianAnim;

    // 客户端缓存的 Animation 引用（用于修复同步）
    @Nullable private com.github.L_Ender.lionfishapi.server.animation.Animation cachedIgnisAnim;
    @Nullable private com.github.L_Ender.lionfishapi.server.animation.Animation cachedEnderGuardianAnim;

    // 动画状态
    private final AnimationState cloudGolemState = new AnimationState();
    private final AnimationState paladinState = new AnimationState();
    private final AnimationState obliteratorState = new AnimationState();
    private final AnimationState monstrosityState = new AnimationState();

    public BossPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.fallbackModel = new BossPhantomModel<>(context.bakeLayer(PHANTOM_LAYER));

        if (ModList.get().isLoaded("legendary_monsters")) {
            cloudGolemAnim = BossPhantomModelIntegration.loadAnimatedModel(context, BossPhantomType.CLOUD_GOLEM);
            paladinAnim = BossPhantomModelIntegration.loadAnimatedModel(context, BossPhantomType.POSSESSED_PALADIN);
            obliteratorAnim = BossPhantomModelIntegration.loadAnimatedModel(context, BossPhantomType.OBLITERATOR);
        }
        if (ModList.get().isLoaded("cataclysm")) {
            monstrosityAnim = BossPhantomModelIntegration.loadAnimatedModel(context, BossPhantomType.NETHERITE_MONSTROSITY);
            ignisModel = new Ignis_Model();
            ignisAnim = new IgnisAnimator(ignisModel);
            enderGuardianModel = new Ender_Guardian_Model();
            enderGuardianAnim = new EnderGuardianAnimator(enderGuardianModel);
            // 缓存 LionfishAPI Animation 引用（单例，客户端侧反射获取）
            cachedIgnisAnim = resolveLionfishAnim(BossPhantomType.IGNIS);
            cachedEnderGuardianAnim = resolveLionfishAnim(BossPhantomType.ENDER_GUARDIAN);
        }
    }

    @Override
    public void render(@NotNull BossPhantomEntity entity, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        BossPhantomType type = entity.getBossType();
        float age = entity.tickCount + partialTick;

        // 计算阶段渲染参数
        int phase = entity.getPhantomPhase();
        int baseAlpha = 102; // 默认 0.4 不透明度
        float brightnessScale = 1.0f;

        if (phase == BossPhantomEntity.PHASE_CHARGE) {
            // 蓄力：亮度从 0.5 线性递增到 1.0（蓄力期约 10 tick）
            float chargeElapsed = Math.max(0, entity.tickCount - 20);
            float chargeProgress = Math.min(1.0f, chargeElapsed / 10.0f);
            brightnessScale = 0.5f + 0.5f * chargeProgress;
        } else if (phase == BossPhantomEntity.PHASE_EXECUTE) {
            // 爆发：全白闪烁 1 tick + 最大亮度
            brightnessScale = 1.5f; // 过亮产生闪烁感
        } else if (phase == BossPhantomEntity.PHASE_DECAY) {
            // 余辉：透明度线性下降到 0.05（约 22 tick 内完成）
            float decayElapsed = Math.max(0, entity.tickCount - 38);
            float decayProgress = Math.min(1.0f, decayElapsed / 22.0f);
            brightnessScale = Math.max(0.2f, 1.0f - decayProgress * 0.5f); // 亮度最多降到 20%
            baseAlpha = Math.max(12, (int)(102 * (1.0f - decayProgress)));
        }

        // 应用实体 yaw 旋转：180 - yaw 是标准实体渲染公式
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // 选择模型和渲染方式
        if (type == BossPhantomType.IGNIS || type == BossPhantomType.ENDER_GUARDIAN) {
            renderFallback(entity, type, age, poseStack, bufferSource, baseAlpha, brightnessScale);
        } else {
            renderAnimated(entity, type, age, poseStack, bufferSource, baseAlpha, brightnessScale);
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** 渲染有完整动画的原生模型（HierarchicalModel） */
    private void renderAnimated(BossPhantomEntity entity, BossPhantomType type, float age,
                                PoseStack poseStack, MultiBufferSource bufferSource,
                                int alpha, float brightnessScale) {
        var animPair = selectAnimPair(type);
        if (animPair == null) { renderFallback(entity, type, age, poseStack, bufferSource, alpha, brightnessScale); return; }

        HierarchicalModel<?> model = animPair.model();
        int color = type.getColor();
        float r = Math.min(1.0f, ((color >> 16) & 0xFF) / 255.0f * brightnessScale);
        float g = Math.min(1.0f, ((color >> 8) & 0xFF) / 255.0f * brightnessScale);
        float b = Math.min(1.0f, (color & 0xFF) / 255.0f * brightnessScale);

        // 重置姿势 + 播放技能动画
        model.root().getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
        AnimationState animState = selectAnimState(type);
        if (!animState.isStarted()) {
            animState.start((int) age);
        }
        float speed = 1.0f;
        // 堕落圣骑动画整体偏移 5 帧，使关键动作对齐技能触发
        float animOffset = (type == BossPhantomType.POSSESSED_PALADIN) ? -13.0f : 0.0f;
        animateModel4(model, animState, animPair.skillAnim(), age + animOffset, speed);

        RenderType renderType = RenderType.entityTranslucentEmissive(getTextureLocation(entity));
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(1.15f, 1.15f, 1.15f);
        poseStack.translate(0, -1.5f, 0);
        // 绕过部分 LM 模型 renderToBuffer 重写（漏传 color 参数），
        // 直接调用 root.render() 的 5 参数版本确保 color alpha 正确生效
        model.root().render(poseStack, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY,
                packColor(r, g, b, alpha));
        poseStack.popPose();
    }

    /** 渲染 LionfishAPI 模型（Ignis / Ender Guardian），手动驱动动画 */
    private void renderFallback(BossPhantomEntity entity, BossPhantomType type, float age,
                                PoseStack poseStack, MultiBufferSource bufferSource,
                                int alpha, float brightnessScale) {
        int color = type.getColor();
        float r = Math.min(1.0f, ((color >> 16) & 0xFF) / 255.0f * brightnessScale);
        float g = Math.min(1.0f, ((color >> 8) & 0xFF) / 255.0f * brightnessScale);
        float b = Math.min(1.0f, (color & 0xFF) / 255.0f * brightnessScale);

        if (type == BossPhantomType.IGNIS && ignisAnim != null) {
            // 修复动画同步：客户端实体 animation 为 null，从缓存补上
            syncEntityAnimation(entity, cachedIgnisAnim);
            ignisAnim.animate(entity);
            renderModelDirect(ignisModel, poseStack, bufferSource, entity, r, g, b, alpha);
        } else if (type == BossPhantomType.ENDER_GUARDIAN && enderGuardianAnim != null) {
            syncEntityAnimation(entity, cachedEnderGuardianAnim);
            enderGuardianAnim.animate(entity);
            renderModelDirect(enderGuardianModel, poseStack, bufferSource, entity, r, g, b, alpha);
        } else {
            // 降级到后备——根据 DATA_PHASE 驱动手臂动画
            int phase = entity.getPhantomPhase();
            float skillSwing = 0;
            if (phase == BossPhantomEntity.PHASE_CHARGE) {
                float t = Math.min(1.0f, Math.max(0, entity.tickCount - 20) / 10.0f);
                skillSwing = t * 0.5f; // 手臂缓慢上抬
            } else if (phase == BossPhantomEntity.PHASE_EXECUTE) {
                float t = Math.max(0, entity.tickCount - 30) / 2.0f;
                if (t <= 1.0f) skillSwing = 1.2f * (float) Math.sin(t * Math.PI); // 快速下劈
            } else if (phase == BossPhantomEntity.PHASE_DECAY) {
                skillSwing = Math.max(0, 0.3f - (entity.tickCount - 38) * 0.015f); // 缓慢下垂
            }
            float breath = (float) Math.sin(age * 0.08f) * 0.1f;
            fallbackModel.leftArm.xRot = -0.2f + skillSwing;
            fallbackModel.rightArm.xRot = -0.2f - skillSwing;
            fallbackModel.leftArm.yRot = skillSwing * 0.3f;
            fallbackModel.rightArm.yRot = -skillSwing * 0.3f;
            fallbackModel.head.xRot = breath * 0.5f;
            RenderType rt = RenderType.entityTranslucentEmissive(getTextureLocation(entity));
            VertexConsumer vc = bufferSource.getBuffer(rt);
            poseStack.pushPose();
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.scale(1.15f, 1.15f, 1.15f);
            fallbackModel.renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    packColor(r, g, b, alpha));
            poseStack.popPose();
        }
    }

    /** 修复 LionfishAPI 动画同步：客户端实体缺失 animation 引用时从缓存补上 */
    private static void syncEntityAnimation(BossPhantomEntity entity,
                                             @Nullable com.github.L_Ender.lionfishapi.server.animation.Animation cached) {
        if (cached != null && entity.getAnimation() == null) {
            entity.setAnimation(cached);
            entity.setAnimationTick(Math.min(entity.tickCount, cached.getDuration() - 1));
        }
    }

    /** 客户端侧反射获取 LionfishAPI Animation 单例引用 */
    @Nullable
    private static com.github.L_Ender.lionfishapi.server.animation.Animation resolveLionfishAnim(BossPhantomType type) {
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
            return (com.github.L_Ender.lionfishapi.server.animation.Animation) f.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderModelDirect(EntityModel<?> model, PoseStack poseStack,
                                   MultiBufferSource buffer, BossPhantomEntity entity,
                                   float r, float g, float b, int alpha) {
        RenderType rt = RenderType.entityTranslucentEmissive(getTextureLocation(entity));
        VertexConsumer vc = buffer.getBuffer(rt);
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(1.15f, 1.15f, 1.15f);
        model.renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY,
                packColor(r, g, b, alpha));
        poseStack.popPose();
    }

    private AnimationState selectAnimState(BossPhantomType type) {
        return switch (type) {
            case CLOUD_GOLEM -> cloudGolemState;
            case POSSESSED_PALADIN -> paladinState;
            case OBLITERATOR -> obliteratorState;
            case NETHERITE_MONSTROSITY -> monstrosityState;
            default -> throw new IllegalArgumentException("No anim state for " + type);
        };
    }

    @Nullable
    private BossPhantomModelIntegration.ModelWithAnimation selectAnimPair(BossPhantomType type) {
        return switch (type) {
            case CLOUD_GOLEM -> cloudGolemAnim;
            case POSSESSED_PALADIN -> paladinAnim;
            case OBLITERATOR -> obliteratorAnim;
            case NETHERITE_MONSTROSITY -> monstrosityAnim;
            default -> null;
        };
    }

    /** 反射调用 HierarchicalModel.animate(AnimationState, AnimationDefinition, float, float) */
    private static void animateModel4(HierarchicalModel<?> model, AnimationState state, Object animDef, float ageInTicks, float speed) {
        try {
            java.lang.reflect.Method m = HierarchicalModel.class.getDeclaredMethod(
                    "animate", AnimationState.class, animDef.getClass(), float.class, float.class);
            m.setAccessible(true);
            m.invoke(model, state, animDef, ageInTicks, speed);
        } catch (Exception e) {
        }
    }

    private static int packColor(float r, float g, float b, int alpha) {
        return (alpha << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(@NotNull BossPhantomEntity entity) {
        BossPhantomType type = entity.getBossType();
        return switch (type) {
            // Ignis 使用多帧动画贴图，取第一帧作为静态引用
            case IGNIS -> ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/ignis/ignis_idle_0.png");
            case ENDER_GUARDIAN -> ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/ender_guardian.png");
            case NETHERITE_MONSTROSITY -> ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/monstrosity/netherite_monstrosity.png");
            case CLOUD_GOLEM -> ResourceLocation.fromNamespaceAndPath("legendary_monsters", "textures/entity/cloud_golem/cloud_golem.png");
            case POSSESSED_PALADIN -> ResourceLocation.fromNamespaceAndPath("legendary_monsters", "textures/entity/posessed_paladin/new_posessed_paladin.png");
            // Obliterator 贴图在 the_warped_one 目录下
            case OBLITERATOR -> ResourceLocation.fromNamespaceAndPath("legendary_monsters", "textures/entity/the_warped_one/the_warped_one.png");
            // 新 BOSS 使用降级模型/贴图
            default -> ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/entity/boss_phantom.png");
        };
    }
}
