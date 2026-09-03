package com.plumejade.lensouls;

import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import com.plumejade.lensouls.ability.client.GravityTetherRenderer;
import com.plumejade.lensouls.ability.client.SpatialWarpOutlineRenderer;

import com.plumejade.lensouls.client.screen.ScreenShakeApplier;
import com.plumejade.lensouls.boss.ToughnessBarRenderer;
import com.plumejade.lensouls.client.render.BossPhantomRenderer;
import com.plumejade.lensouls.client.tabs.PhotoTabRegistry;
import com.plumejade.lensouls.entity.ModEntities;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.effect.FilterEffect;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.gui.ConverterScreen;
import com.plumejade.lensouls.gui.ModMenus;
import com.plumejade.lensouls.gui.PhotoGuiScreen;
import com.plumejade.lensouls.gui.SoulSelectOverlay;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import com.plumejade.lensouls.client.itemoutline.ItemOutlineShaders;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import java.util.List;
import java.util.function.Consumer;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = LenSouls.MODID, dist = Dist.CLIENT)
public class LenSoulsClient {

    public LenSoulsClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 注册到 Mod 事件总线
        modEventBus.addListener(LenSoulsClient::registerScreens);
        modEventBus.addListener(LenSoulsClient::registerClientExtensions);
        modEventBus.addListener(LenSoulsClient::registerEntityRenderers);
        modEventBus.addListener(LenSoulsClient::registerLayerDefinitions);
        modEventBus.addListener(LenSoulsClient::registerLayers);
        modEventBus.addListener(LenSoulsClient::registerShaders);
        modEventBus.addListener(LenSoulsClient::registerParticleProviders);

        // 客户端游戏事件（RenderLevelStageEvent）
        NeoForge.EVENT_BUS.register(SpatialWarpOutlineRenderer.class);
        NeoForge.EVENT_BUS.register(FrozenOutlineManager.class);
        NeoForge.EVENT_BUS.register(BossOutlineManager.class);
        NeoForge.EVENT_BUS.register(GravityTetherRenderer.class);
        NeoForge.EVENT_BUS.register(ToughnessBarRenderer.class);
        NeoForge.EVENT_BUS.register(ScreenShakeApplier.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.client.SanBarOverlay.class);
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.phantom.ClientPhantomHandler::onPlayerTick);
        // 客户端断线时清理幻灵状态
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.phantom.ClientPhantomHandler::onClientLogout);
        // 客户端断线时清空能力缓存，防止切换存档跨档污染
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.ability.client.ClientAbilityCache::onClientLogout);
        // 客户端元素螺旋粒子 Tick 驱动
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.particle.ClientElementSpiralHandler::onClientTick);
        // N公司2级员工 BGM 距离控制（客户端 tick）
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.sound.Level2StaffBossBgmHandler::onClientTick);
        // 客户端断线停止 BGM（幻灵已在上方注册）
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.sound.Level2StaffBossBgmHandler::reset);
        NeoForge.EVENT_BUS.addListener(LenSoulsClient::onGatherEffectTooltips);
        // 登录进世界后主动向服务端拉取数据包解析结果（弱点/活性/套装），
        // 兜底 OnDatapackSyncEvent 在局域网等环境下登录时序不可靠导致的客机缓存为空。
        NeoForge.EVENT_BUS.addListener(LenSoulsClient::onClientLoggingIn);
        // 兼容 Stylish Effects 的 tooltip 事件
        tryRegisterStylishEffectsListener();

        // 注册背包「照片效果」选项卡（L2 Library tabs 框架）
        PhotoTabRegistry.register();
    }

    /** 注册菜单界面到 RegisterMenuScreensEvent */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONVERTER.get(), ConverterScreen::new);
        event.register(ModMenus.CONVERTER_SELECT.get(), SoulSelectOverlay::new);
        event.register(ModMenus.PHOTO_GUI.get(), PhotoGuiScreen::new);
        event.register(ModMenus.PHOTO_ALBUM.get(), com.plumejade.lensouls.gui.AlbumScreen::new);
    }

    /** 注册客户端扩展（显示元素灌注图标，隐藏原版粒子） */
    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        IClientMobEffectExtensions visible = new IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(MobEffectInstance instance) { return true; }
            @Override
            public boolean isVisibleInGui(MobEffectInstance instance) { return true; }
        };
        event.registerMobEffect(visible, ModEffects.FIRE_INFUSION, ModEffects.WATER_INFUSION,
                ModEffects.EARTH_INFUSION, ModEffects.ENDER_INFUSION);
    }

    /** 注册幻灵渲染器（灾变/传奇怪物任一未装时整个幻灵渲染不启用——渲染器构造硬依赖两者模型，幻灵生成已被 isModLoaded 保护） */
    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (net.neoforged.fml.ModList.get().isLoaded("cataclysm")
                && net.neoforged.fml.ModList.get().isLoaded("legendary_monsters")) {
            event.registerEntityRenderer(
                    com.plumejade.lensouls.entity.ModEntities.BOSS_PHANTOM.get(),
                    BossPhantomRenderer::new);
        }
        event.registerEntityRenderer(ModEntities.GUN_BULLET.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<com.plumejade.lensouls.entity.GunBulletEntity>(ctx, 0.8f, false));
        event.registerEntityRenderer(ModEntities.GRAVITY_BULLET.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<com.plumejade.lensouls.entity.GravityBulletEntity>(ctx, 1.5f, false));
        event.registerEntityRenderer(ModEntities.TWITCHER.get(),
                com.plumejade.lensouls.client.render.TwitcherRenderer::new);
        // N公司2级员工（GeckoLib，运行时 geckolib 由 mod 提供）
        event.registerEntityRenderer(ModEntities.LEVEL2_STAFF_BOSS.get(),
                com.plumejade.lensouls.client.render.Level2StaffBossRenderer::new);
    }

    /** 注册幻灵模型层定义 */
    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                BossPhantomRenderer.PHANTOM_LAYER,
                com.plumejade.lensouls.client.model.BossPhantomModel::createLayer);
    }

    /** 冻结描边不再使用 RenderLayer（改用 EntityRenderDispatcherMixin + FrozenOutlineManager） */
    private static void registerLayers(EntityRenderersEvent.AddLayers event) {
        // 玩家身体金色光效调试层（自包含 RenderType，不依赖 mixin）
        for (var skin : net.minecraft.client.resources.PlayerSkin.Model.values()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                // 镜魂实体发光层（第三人称，Iris/PA/BC 兼容）
                pr.addLayer(new com.plumejade.lensouls.ability.client.SoulGlowLayer<>(pr));
            }
        }
    }

    /** 注册核心着色器（镜魂描边 + mask） */
    private static void registerShaders(RegisterShadersEvent event) {
        ResourceProvider provider = event.getResourceProvider();

        // 冻结描边 — 蒙版纯白着色器（NEW_ENTITY）
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "rendertype_mask_entity"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),
                    instance -> {
                        FrozenOutlineManager.maskShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[Outline] 蒙版着色器加载失败", e);
        }

        // 冻结描边 — 金边复合着色器（POSITION_TEX 全屏 quad）
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "rendertype_gold_outline"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX),
                    instance -> {
                        FrozenOutlineManager.goldOutlineShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[Outline] 金边复合着色器加载失败", e);
        }

        // BOSS 镜魂描边 composite（第一人称手部 mask 描边，distance field 无噪点）
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "boss_outline_composite"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX),
                    instance -> {
                        BossOutlineManager.bossCompositeShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[BossGlow] composite 着色器加载失败", e);
        }

        // 第一人称手持物单色描边 composite（独立 mask 目标，仿 yuyu，仅第一人称手持物）
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "item_outline_composite"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX),
                    instance -> {
                        ItemOutlineShaders.itemCompositeShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[ItemOutline] 第一人称描边 composite 着色器加载失败", e);
        }

        // FBO item mask 着色器 — alpha test + 纯白（手持物品用，避免透明区域方框）
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "rendertype_soul_glow_mask_item"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),
                    instance -> {
                        com.plumejade.lensouls.ability.client.FrozenOutlineManager.itemMaskShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[SoulGlow] FBO item mask 着色器加载失败", e);
        }

        // 状态光效 glint（物品版）— 采样物品图集做 alpha 测试，剔除透明像素
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "rendertype_status_glint_item"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX),
                    instance -> {
                        com.plumejade.lensouls.ability.client.StatusGlintItemRenderTypes.itemGlintShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[Glint] 物品状态光效着色器加载失败", e);
        }

        // 状态光效 glint（实体版）— Sampler1 采样实体纹理做 alpha 测试，剔除透明面
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "rendertype_status_glint_entity"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX),
                    instance -> {
                        com.plumejade.lensouls.ability.client.CaptureState.glintEntityShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[Glint] 实体状态光效着色器加载失败", e);
        }

        // 时间定格黑洞星空 — 天空球（POSITION_COLOR）
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "black_hole_ring"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR),
                    instance -> {
                        com.plumejade.lensouls.ability.client.GrayOutManager.blackHoleShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[GrayOut] 黑洞星空着色器加载失败", e);
        }
    }

    /** 注册粒子提供器 */
    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.FLYING_SPARK.get(),
                com.plumejade.lensouls.client.particle.FlyingSparkParticle.Provider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK.get(),
                com.plumejade.lensouls.client.particle.FlyingSparkParticle.GreenProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK_ORANGE.get(),
                com.plumejade.lensouls.client.particle.FlyingSparkParticle.Provider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK_PURPLE.get(),
                com.plumejade.lensouls.client.particle.FlyingSparkParticle.PurpleProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.GRASS_BLADE.get(),
                com.plumejade.lensouls.client.particle.GrassBladeParticle.Provider::new);

        // 韧性粒子
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.TOUGHNESS_HIT.get(),
                com.plumejade.lensouls.client.particle.ToughnessHitParticle.Provider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.TOUGHNESS_BREAK.get(),
                com.plumejade.lensouls.client.particle.ToughnessBreakParticle.Provider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.TOUGHNESS_SHOCKWAVE.get(),
                com.plumejade.lensouls.client.particle.ToughnessShockwaveParticle.Provider::new);

        // 时间定格拒绝粒子（天青色）
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.FREEZE_REJECT.get(),
                com.plumejade.lensouls.client.particle.FreezeRejectParticle.Provider::new);

        // 元素灌注环境粒子
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_PARTICLE_FIRE.get(),
                com.plumejade.lensouls.client.particle.ElementParticle.FireProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_PARTICLE_WATER.get(),
                com.plumejade.lensouls.client.particle.ElementParticle.WaterProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_PARTICLE_EARTH.get(),
                com.plumejade.lensouls.client.particle.ElementParticle.EarthProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_PARTICLE_ENDER.get(),
                com.plumejade.lensouls.client.particle.ElementParticle.EnderProvider::new);

        // 元素弱点螺旋粒子
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_FIRE.get(),
                com.plumejade.lensouls.client.particle.SpiralParticle.FireProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_WATER.get(),
                com.plumejade.lensouls.client.particle.SpiralParticle.WaterProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_EARTH.get(),
                com.plumejade.lensouls.client.particle.SpiralParticle.EarthProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_ENDER.get(),
                com.plumejade.lensouls.client.particle.SpiralParticle.EnderProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_PROJECTILE.get(),
                com.plumejade.lensouls.client.particle.SpiralParticle.ProjectileProvider::new);
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_WEAKNESS.get(),
                com.plumejade.lensouls.client.particle.SpiralParticle.WeaknessLensProvider::new);

        // 滤镜效果隐藏粒子：客户端不生成任何粒子（provider 返回 null），既满足「非 null 可被编码」又实现隐藏
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.FILTER_HIDDEN.get(),
                (net.minecraft.client.particle.SpriteSet sprites) ->
                        (type, level, x, y, z, vx, vy, vz) -> null);

        // 折翼沉渊·祸之可能性召唤粒子（精灵）
        event.registerSpriteSet(com.plumejade.lensouls.particle.ModParticleTypes.ABYSS_SUMMON.get(),
                com.plumejade.lensouls.client.particle.SummonSpiritParticle.Provider::new);
    }

    /** 登录进世界后，主动向服务端拉取一次数据包解析结果全量包（弱点/活性/套装兜底同步）。 */
    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                com.plumejade.lensouls.network.DatapackSyncRequestPacket.instance());
    }

    private static void onGatherEffectTooltips(GatherEffectScreenTooltipsEvent event) {
        MobEffectInstance inst = event.getEffectInstance();
        MobEffect effect = inst.getEffect().value();
        if (!(effect instanceof ElementInfusionEffect) && !(effect instanceof FilterEffect)) return;
        Component desc = Component.translatable(inst.getDescriptionId() + ".description");
        String raw = desc.getString();
        if (raw.isEmpty() || raw.equals(inst.getDescriptionId() + ".description")) return;
        String[] lines = raw.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            event.getTooltip().add(1 + i, Component.literal(lines[i]));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tryRegisterStylishEffectsListener() {
        try {
            Class<?> eventClass = Class.forName(
                    "fuzs.stylisheffects.neoforge.api.v1.client.NeoForgeMobEffectWidgetEvent$EffectTooltip");
            java.lang.reflect.Method addListenerMethod =
                    IEventBus.class.getMethod("addListener", Class.class, Consumer.class);
            java.lang.reflect.Method getContext = eventClass.getMethod("getContext");
            Class<?> contextClass = getContext.getReturnType();
            java.lang.reflect.Method getEffectInstance = contextClass.getMethod("getEffectInstance");
            Class<?> effectInstClass = getEffectInstance.getReturnType();
            java.lang.reflect.Method getEffect = effectInstClass.getMethod("getEffect");
            Class<?> holderClass = getEffect.getReturnType();
            java.lang.reflect.Method holderValue = holderClass.getMethod("value");
            java.lang.reflect.Method getTooltipLines = eventClass.getMethod("getTooltipLines");
            java.lang.reflect.Method getDescriptionId = effectInstClass.getMethod("getDescriptionId");
            Consumer handler = event -> {
                try {
                    Object mobEffectInst = getEffectInstance.invoke(getContext.invoke(event));
                    Object effect = holderValue.invoke(getEffect.invoke(mobEffectInst));
                    if (!(effect instanceof ElementInfusionEffect) && !(effect instanceof FilterEffect)) return;
                    List<Component> tooltip = (List<Component>) getTooltipLines.invoke(event);
                    String descKey = (String) getDescriptionId.invoke(mobEffectInst);
                    Component desc = Component.translatable(descKey + ".description");
                    String raw = desc.getString();
                    if (raw.isEmpty() || raw.equals(descKey + ".description")) return;
                    String[] lines = raw.split("\n", -1);
                    for (int i = 0; i < lines.length; i++) {
                        tooltip.add(1 + i, Component.literal(lines[i]));
                    }
                } catch (Exception ignored) {
                }
            };
            addListenerMethod.invoke(NeoForge.EVENT_BUS, eventClass, handler);
        } catch (Exception ignored) {
        }
    }
}
