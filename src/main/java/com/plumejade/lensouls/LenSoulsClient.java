package com.plumejade.lensouls;

import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import com.plumejade.lensouls.ability.client.GravityTetherRenderer;
import com.plumejade.lensouls.ability.client.SpatialWarpOutlineRenderer;

import com.plumejade.lensouls.client.screen.ScreenShakeApplier;
import com.plumejade.lensouls.client.phantom.ClientPhantomHandler;
import com.plumejade.lensouls.boss.ToughnessBarRenderer;
import com.plumejade.lensouls.boss.ToughnessHitSoundPacket;
import com.plumejade.lensouls.boss.ToughnessParticlePacket;
import com.plumejade.lensouls.boss.ToughnessSyncPacket;
import com.plumejade.lensouls.client.render.BossPhantomRenderer;
import com.plumejade.lensouls.entity.ModEntities;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.entity.BossPhantomType;
import com.plumejade.lensouls.gui.ConverterScreen;
import com.plumejade.lensouls.gui.ModMenus;
import com.plumejade.lensouls.gui.PhotoGuiScreen;
import com.plumejade.lensouls.ability.network.AbilitySyncPacket;
import com.plumejade.lensouls.ability.network.FreezeSyncPacket;
import com.plumejade.lensouls.network.PhantomSkillPacket;
import com.plumejade.lensouls.network.PhantomStartPacket;
import com.plumejade.lensouls.network.PhantomStopPacket;
import com.plumejade.lensouls.network.PhantomTickPacket;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.List;
import java.util.function.Consumer;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = LenSouls.MODID, dist = Dist.CLIENT)
public class LenSoulsClient {

    private static final String PROTOCOL_VERSION = "1";

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
        modEventBus.addListener(LenSoulsClient::registerS2CPackets);

        // 客户端游戏事件（RenderLevelStageEvent）
        NeoForge.EVENT_BUS.register(SpatialWarpOutlineRenderer.class);
        NeoForge.EVENT_BUS.register(FrozenOutlineManager.class);
        NeoForge.EVENT_BUS.register(BossOutlineManager.class);
        NeoForge.EVENT_BUS.register(GravityTetherRenderer.class);
        NeoForge.EVENT_BUS.register(ToughnessBarRenderer.class);
        NeoForge.EVENT_BUS.register(ScreenShakeApplier.class);
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.phantom.ClientPhantomHandler::onPlayerTick);
        // 客户端断线时清理幻灵状态
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.phantom.ClientPhantomHandler::onClientLogout);
        // 客户端元素螺旋粒子 Tick 驱动
        NeoForge.EVENT_BUS.addListener(com.plumejade.lensouls.client.particle.ClientElementSpiralHandler::onClientTick);
        // 药水效果 tooltip 追加描述行
        NeoForge.EVENT_BUS.addListener(LenSoulsClient::onGatherEffectTooltips);
        // 兼容 Stylish Effects 的 tooltip 事件
        tryRegisterStylishEffectsListener();
    }

    /** 注册菜单界面到 RegisterMenuScreensEvent */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONVERTER.get(), ConverterScreen::new);
        event.register(ModMenus.PHOTO_GUI.get(), PhotoGuiScreen::new);
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

    /** 注册幻灵渲染器 */
    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                com.plumejade.lensouls.entity.ModEntities.BOSS_PHANTOM.get(),
                BossPhantomRenderer::new);
        event.registerEntityRenderer(ModEntities.GUN_BULLET.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<com.plumejade.lensouls.entity.GunBulletEntity>(ctx, 0.8f, false));
        event.registerEntityRenderer(ModEntities.GRAVITY_BULLET.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<com.plumejade.lensouls.entity.GravityBulletEntity>(ctx, 1.5f, false));
    }

    /** 注册幻灵模型层定义 */
    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                BossPhantomRenderer.PHANTOM_LAYER,
                com.plumejade.lensouls.client.model.BossPhantomModel::createLayer);
    }

    /** 冻结描边不再使用 RenderLayer（改用 FrozenEntityRenderMixin + FrozenOutlineManager） */
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

        // 镜魂物品发光着色器（NEW_ENTITY 格式，物品模型兼容）— 保持注册供 mask 使用
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "rendertype_soul_item_glow"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),
                    instance -> {
                        com.plumejade.lensouls.ability.client.SoulGlowShader.setShader(instance);
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[SoulGlow] 镜魂物品发光着色器加载失败", e);
        }

        // BOSS 镜魂 distance field composite 着色器
        try {
            event.registerShader(
                    new ShaderInstance(provider,
                            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "boss_outline_composite"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX),
                    instance -> {
                        com.plumejade.lensouls.ability.client.BossOutlineManager.bossCompositeShader = instance;
                    }
            );
        } catch (java.io.IOException e) {
            LenSouls.LOGGER.error("[BossGlow] distance field composite 着色器加载失败", e);
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
    }

    /** 注册 S2C 虚影包（仅客户端处理） */
    private static void registerS2CPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(
                PhantomStartPacket.TYPE,
                PhantomStartPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    BossPhantomType[] values = BossPhantomType.values();
                    if (packet.getBossTypeOrdinal() < 0 || packet.getBossTypeOrdinal() >= values.length) return;
                    BossPhantomType type = values[packet.getBossTypeOrdinal()];
                    ClientPhantomHandler.getInstance().startPhantom(
                            packet.getPlayerId(), type, packet.getLifetimeTicks(),
                            packet.getPhantomX(), packet.getPhantomY(), packet.getPhantomZ(),
                            packet.getPhantomYaw());
                    ClientPhantomHandler.addPhantomEntity(packet.getPhantomEntityId());
                })
        );

        registrar.playToClient(
                PhantomSkillPacket.TYPE,
                PhantomSkillPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    BossPhantomType[] values = BossPhantomType.values();
                    if (packet.getBossTypeOrdinal() < 0 || packet.getBossTypeOrdinal() >= values.length) return;
                    BossPhantomType type = values[packet.getBossTypeOrdinal()];
                    ClientPhantomHandler.getInstance().playSkill(type);
                })
        );

        registrar.playToClient(
                PhantomStopPacket.TYPE,
                PhantomStopPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    ClientPhantomHandler.getInstance().stopPhantom();
                })
        );

        registrar.playToClient(
                PhantomTickPacket.TYPE,
                PhantomTickPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    BossPhantomType[] values = BossPhantomType.values();
                    if (packet.getBossTypeOrdinal() < 0 || packet.getBossTypeOrdinal() >= values.length) return;
                    BossPhantomType type = values[packet.getBossTypeOrdinal()];
                    ClientPhantomHandler.getInstance().playPhase(type, packet.getPhase());
                })
        );

        // ---- 能力系统 S2C ----
        registrar.playToClient(
                AbilitySyncPacket.TYPE,
                AbilitySyncPacket.STREAM_CODEC,
                AbilitySyncPacket::handle
        );

        // ---- 冻结状态同步 S2C ----
        registrar.playToClient(
                FreezeSyncPacket.TYPE,
                FreezeSyncPacket.STREAM_CODEC,
                FreezeSyncPacket::handle
        );

        // ---- BOSS 韧性同步 S2C ----
        registrar.playToClient(
                ToughnessSyncPacket.TYPE,
                ToughnessSyncPacket.STREAM_CODEC,
                ToughnessSyncPacket::handle
        );

        // ---- BOSS 韧性削减音效 S2C ----
        registrar.playToClient(
                ToughnessHitSoundPacket.TYPE,
                ToughnessHitSoundPacket.STREAM_CODEC,
                ToughnessHitSoundPacket::handle
        );

        // ---- BOSS 韧性削减粒子 S2C ----
        registrar.playToClient(
                ToughnessParticlePacket.TYPE,
                ToughnessParticlePacket.STREAM_CODEC,
                ToughnessParticlePacket::handle
        );

        // ---- 时间定格拒绝粒子 S2C ----
        registrar.playToClient(
                com.plumejade.lensouls.boss.FreezeRejectParticlePacket.TYPE,
                com.plumejade.lensouls.boss.FreezeRejectParticlePacket.STREAM_CODEC,
                com.plumejade.lensouls.boss.FreezeRejectParticlePacket::handle
        );

        // ---- 元素弱点螺旋粒子 S2C ----
        registrar.playToClient(
                com.plumejade.lensouls.network.ElementSpiralPacket.TYPE,
                com.plumejade.lensouls.network.ElementSpiralPacket.STREAM_CODEC,
                com.plumejade.lensouls.network.ElementSpiralPacket::handle
        );
    }

    private static void onGatherEffectTooltips(GatherEffectScreenTooltipsEvent event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (!(inst.getEffect().value() instanceof ElementInfusionEffect)) return;
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
                    if (!(effect instanceof ElementInfusionEffect)) return;
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
