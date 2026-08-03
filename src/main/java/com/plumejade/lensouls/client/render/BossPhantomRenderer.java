package com.plumejade.lensouls.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.plumejade.lensouls.LenSouls;
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
 * <p>
 * 灾变（Cataclysm）兼容代码已隔离到 {@link CataclysmCompat}，通过 Class.forName() 动态加载，
 * 避免无灾变模组时类加载崩溃。
 */
public class BossPhantomRenderer extends EntityRenderer<BossPhantomEntity> {

    public static final ModelLayerLocation PHANTOM_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "boss_phantom"), "main");

    // 内置后备模型（仅当 LionfishAPI 模型不可用时的 Ignis/Ender Guardian 降级）
    private final BossPhantomModel<BossPhantomEntity> fallbackModel;

    // 各 BOSS 的动画模型捆绑（Legendary Monsters）
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation cloudGolemAnim;
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation paladinAnim;
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation obliteratorAnim;
    @Nullable private BossPhantomModelIntegration.ModelWithAnimation monstrosityAnim;

    // LionfishAPI 原生模型（Cataclysm）——类型擦除为 Object 避免类加载
    @Nullable private Object ignisModel;
    @Nullable private Object ignisAnim;
    @Nullable private Object enderGuardianModel;
    @Nullable private Object enderGuardianAnim;

    // 客户端缓存的 Animation 引用（用于修复同步）
    @Nullable private Object cachedIgnisAnim;
    @Nullable private Object cachedEnderGuardianAnim;

    // 动画状态（Legendary Monsters）
    private final AnimationState cloudGolemState = new AnimationState();
    private final AnimationState paladinState = new AnimationState();
    private final AnimationState obliteratorState = new AnimationState();
    private final AnimationState monstrosityState = new AnimationState();

    // 灾变兼容层（延迟加载，仅灾变存在时非空）
    @Nullable private final Class<?> cataclysmCompat;

    public BossPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.fallbackModel = new BossPhantomModel<>(context.bakeLayer(PHANTOM_LAYER));

        // Legendary Monsters 兼容（直接引用，模组不存在时为 null）
        if (ModList.get().isLoaded("legendary_monsters")) {
            cloudGolemAnim = BossPhantomModelIntegration.loadAnimatedModel(context, BossPhantomType.CLOUD_GOLEM);
            paladinAnim = BossPhantomModelIntegration.loadAnimatedModel(context, BossPhantomType.POSSESSED_PALADIN);
            obliteratorAnim = BossPhantomModelIntegration.loadAnimatedModel(context, BossPhantomType.OBLITERATOR);
        }

        // Cataclysm 兼容（动态加载，避免无灾变时类加载崩溃）
        if (ModList.get().isLoaded("cataclysm")) {
            cataclysmCompat = loadCataclysmCompat();
            if (cataclysmCompat != null) {
                try {
                    monstrosityAnim = (BossPhantomModelIntegration.ModelWithAnimation)
                            cataclysmCompat.getMethod("createMonstrosityAnim", EntityRendererProvider.Context.class)
                                    .invoke(null, context);
                    ignisModel = cataclysmCompat.getMethod("createIgnisModel").invoke(null);
                    ignisAnim = cataclysmCompat.getMethod("createIgnisAnimator", EntityModel.class)
                            .invoke(null, ignisModel);
                    enderGuardianModel = cataclysmCompat.getMethod("createEnderGuardianModel").invoke(null);
                    enderGuardianAnim = cataclysmCompat.getMethod("createEnderGuardianAnimator", EntityModel.class)
                            .invoke(null, enderGuardianModel);
                    cachedIgnisAnim = cataclysmCompat.getMethod("resolveLionfishAnim", BossPhantomType.class)
                            .invoke(null, BossPhantomType.IGNIS);
                    cachedEnderGuardianAnim = cataclysmCompat.getMethod("resolveLionfishAnim", BossPhantomType.class)
                            .invoke(null, BossPhantomType.ENDER_GUARDIAN);
                } catch (Exception e) {
                    LenSouls.LOGGER.warn("[幻灵] CataclysmCompat 初始化失败", e);
                }
            }
        } else {
            cataclysmCompat = null;
        }
    }

    /** 动态加载 CataclysmCompat 类 */
    @Nullable
    private static Class<?> loadCataclysmCompat() {
        try {
            return Class.forName("com.plumejade.lensouls.client.render.CataclysmCompat");
        } catch (ClassNotFoundException e) {
            return null;
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
            float chargeElapsed = Math.max(0, entity.tickCount - 20);
            float chargeProgress = Math.min(1.0f, chargeElapsed / 10.0f);
            brightnessScale = 0.5f + 0.5f * chargeProgress;
        } else if (phase == BossPhantomEntity.PHASE_EXECUTE) {
            brightnessScale = 1.5f;
        } else if (phase == BossPhantomEntity.PHASE_DECAY) {
            float decayElapsed = Math.max(0, entity.tickCount - 38);
            float decayProgress = Math.min(1.0f, decayElapsed / 22.0f);
            brightnessScale = Math.max(0.2f, 1.0f - decayProgress * 0.5f);
            baseAlpha = Math.max(12, (int)(102 * (1.0f - decayProgress)));
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

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

        model.root().getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
        AnimationState animState = selectAnimState(type);
        if (!animState.isStarted()) {
            animState.start((int) age);
        }
        float speed = 1.0f;
        float animOffset = (type == BossPhantomType.POSSESSED_PALADIN) ? -13.0f : 0.0f;
        animateModel4(model, animState, animPair.skillAnim(), age + animOffset, speed);

        RenderType renderType = RenderType.entityTranslucentEmissive(getTextureLocation(entity));
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(1.15f, 1.15f, 1.15f);
        poseStack.translate(0, -1.5f, 0);
        model.root().render(poseStack, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY,
                packColor(r, g, b, alpha));
        poseStack.popPose();
    }

    /** 渲染 LionfishAPI 模型（Ignis / Ender Guardian），手动驱动动画 */
    @SuppressWarnings("unchecked")
    private void renderFallback(BossPhantomEntity entity, BossPhantomType type, float age,
                                PoseStack poseStack, MultiBufferSource bufferSource,
                                int alpha, float brightnessScale) {
        int color = type.getColor();
        float r = Math.min(1.0f, ((color >> 16) & 0xFF) / 255.0f * brightnessScale);
        float g = Math.min(1.0f, ((color >> 8) & 0xFF) / 255.0f * brightnessScale);
        float b = Math.min(1.0f, (color & 0xFF) / 255.0f * brightnessScale);

        ResourceLocation texture = getTextureLocation(entity);

        if (type == BossPhantomType.IGNIS && ignisModel != null && cataclysmCompat != null) {
            try {
                // 同步动画
                cataclysmCompat.getMethod("syncEntityAnimation", BossPhantomEntity.class, Object.class)
                        .invoke(null, entity, cachedIgnisAnim);
                // 触发动画并渲染
                cataclysmCompat.getMethod("animateAndRender", Object.class, BossPhantomEntity.class,
                                EntityModel.class, PoseStack.class, MultiBufferSource.class,
                                ResourceLocation.class, float.class, float.class, float.class, int.class)
                        .invoke(null, ignisAnim, entity, ignisModel, poseStack, bufferSource,
                                texture, r, g, b, alpha);
            } catch (Exception e) {
                renderFallbackModel(entity, type, age, poseStack, bufferSource, r, g, b, alpha);
            }
        } else if (type == BossPhantomType.ENDER_GUARDIAN && enderGuardianModel != null && cataclysmCompat != null) {
            try {
                cataclysmCompat.getMethod("syncEntityAnimation", BossPhantomEntity.class, Object.class)
                        .invoke(null, entity, cachedEnderGuardianAnim);
                cataclysmCompat.getMethod("animateAndRender", Object.class, BossPhantomEntity.class,
                                EntityModel.class, PoseStack.class, MultiBufferSource.class,
                                ResourceLocation.class, float.class, float.class, float.class, int.class)
                        .invoke(null, enderGuardianAnim, entity, enderGuardianModel, poseStack, bufferSource,
                                texture, r, g, b, alpha);
            } catch (Exception e) {
                renderFallbackModel(entity, type, age, poseStack, bufferSource, r, g, b, alpha);
            }
        } else {
            renderFallbackModel(entity, type, age, poseStack, bufferSource, r, g, b, alpha);
        }
    }

    /** 降级渲染——根据 DATA_PHASE 驱动手臂动画 */
    private void renderFallbackModel(BossPhantomEntity entity, BossPhantomType type, float age,
                                     PoseStack poseStack, MultiBufferSource bufferSource,
                                     float r, float g, float b, int alpha) {
        int phase = entity.getPhantomPhase();
        float skillSwing = 0;
        if (phase == BossPhantomEntity.PHASE_CHARGE) {
            float t = Math.min(1.0f, Math.max(0, entity.tickCount - 20) / 10.0f);
            skillSwing = t * 0.5f;
        } else if (phase == BossPhantomEntity.PHASE_EXECUTE) {
            float t = Math.max(0, entity.tickCount - 30) / 2.0f;
            if (t <= 1.0f) skillSwing = 1.2f * (float) Math.sin(t * Math.PI);
        } else if (phase == BossPhantomEntity.PHASE_DECAY) {
            skillSwing = Math.max(0, 0.3f - (entity.tickCount - 38) * 0.015f);
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
            case IGNIS -> ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/ignis/ignis_idle_0.png");
            case ENDER_GUARDIAN -> ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/ender_guardian.png");
            case NETHERITE_MONSTROSITY -> ResourceLocation.fromNamespaceAndPath("cataclysm", "textures/entity/monstrosity/netherite_monstrosity.png");
            case CLOUD_GOLEM -> ResourceLocation.fromNamespaceAndPath("legendary_monsters", "textures/entity/cloud_golem/cloud_golem.png");
            case POSSESSED_PALADIN -> ResourceLocation.fromNamespaceAndPath("legendary_monsters", "textures/entity/posessed_paladin/new_posessed_paladin.png");
            case OBLITERATOR -> ResourceLocation.fromNamespaceAndPath("legendary_monsters", "textures/entity/the_warped_one/the_warped_one.png");
            default -> ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/entity/boss_phantom.png");
        };
    }
}
