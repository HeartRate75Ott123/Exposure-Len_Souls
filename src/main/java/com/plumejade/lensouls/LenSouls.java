package com.plumejade.lensouls;

import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.gui.AbilityGuiHolder;
import com.plumejade.lensouls.ability.handler.PhotoInjectionHandler;
import com.plumejade.lensouls.ability.handler.SpatialWarpHandler;
import com.plumejade.lensouls.ability.handler.TemporalRecallHandler;
import com.plumejade.lensouls.ability.handler.FreezeCleanupHandler;
import com.plumejade.lensouls.ability.util.TimeFreezeManager;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.damage.ArmorPenHandler;
import com.plumejade.lensouls.damage.DamageHandler;
import com.plumejade.lensouls.damage.PhotoDamageHandler;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.entity.BossPhantomManager;
import com.plumejade.lensouls.entity.ModEntities;
import com.plumejade.lensouls.event.EnchantmentRemovalListener;
import com.plumejade.lensouls.event.GunKillHandler;
import com.plumejade.lensouls.event.VillagerTradeHandler;
import com.plumejade.lensouls.gui.ModMenus;
import com.plumejade.lensouls.item.ModCreativeTabs;
import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.network.PacketHandler;
import com.plumejade.lensouls.timer.TimerService;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(LenSouls.MODID)
public class LenSouls {
    public static final String MODID = "lensouls";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LenSouls(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModEffects.register(modEventBus);
        com.plumejade.lensouls.attribute.ModAttributes.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModEnchantments.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModEntities.register(modEventBus);
        com.plumejade.lensouls.recipe.DimensionalGunRecipes.register(modEventBus);
        com.plumejade.lensouls.recipe.CopySoulRecipes.register(modEventBus);
        com.plumejade.lensouls.recipe.PotionGlassPaneRecipes.register(modEventBus);
        com.plumejade.lensouls.particle.ModParticleTypes.register(modEventBus);
        com.plumejade.lensouls.sound.ModSounds.register(modEventBus);
        PacketHandler.register(modEventBus);

        // 能力选择 GUI（LDLib2 服务端菜单，客户端与服务端共同构建 UI 树）
        AbilityGuiHolder.register();

        modEventBus.addListener(this::registerConditions);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(DamageHandler.class);
        NeoForge.EVENT_BUS.register(PhotoDamageHandler.class);
        NeoForge.EVENT_BUS.register(ArmorPenHandler.class);
        NeoForge.EVENT_BUS.register(EnchantmentRemovalListener.class);
        NeoForge.EVENT_BUS.register(VillagerTradeHandler.class);
        NeoForge.EVENT_BUS.register(GunKillHandler.class);
        NeoForge.EVENT_BUS.register(BossPhantomManager.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.entity.PhantomDamageHandler.class);

        // ---- 能力系统 ----
        NeoForge.EVENT_BUS.register(AbilityManager.class);
        NeoForge.EVENT_BUS.register(PhotoInjectionHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.FilterPhotoHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.damage.FilterDamageHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.FilterTickHandler.class);
        NeoForge.EVENT_BUS.register(SpatialWarpHandler.class);
        NeoForge.EVENT_BUS.register(TemporalRecallHandler.class);
        NeoForge.EVENT_BUS.register(FreezeCleanupHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.ability.command.LensoulsCommand.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.ability.handler.VitalStrikeHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.ability.handler.SoulSeverHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.ability.handler.CameraLoginHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.integration.PhotographEffectRegistry.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.integration.TrophyModifierHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.integration.PhotoSpecialEffects.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.integration.PhotoSetEffects.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.SuppressHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.IgnisBrandHandler.class);

        // Curios 照片饰品槽（运行时检测）
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.integration.CuriosIntegration.class);

        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.ability.handler.TimeFreezeHandler.class);

        // BOSS 韧性系统（注册实例，所有 @SubscribeEvent 用非静态方法）
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.boss.ToughnessPhotoHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.boss.BossToughnessManager.getInstance());

        // ---- 元素系统 ----
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.AnvilUpgradeHandler.class);

        // ---- 物品获取方式（掉落+配方监听） ----
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.AcquisitionHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.CopySoulDropHandler.class);

        // ---- 羽·元素觉醒者效果 ----
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.FeatherElementRiseHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.FeatherTwitcherHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.FeatherHardmanHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.FeatherAbyssHandler.class);
        // ---- 元素活性 tooltip（客户端物品 hover 提示） ----
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.handler.ElementActivityTooltipHandler.class);

        // BOSS 韧性 — 伤害减免 + 自动注册事件处理器
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.boss.ToughnessDamageHandler.class);
        NeoForge.EVENT_BUS.register(com.plumejade.lensouls.boss.StunResistanceCleanupHandler.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    private void registerConditions(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.CONDITION_CODECS,
                com.plumejade.lensouls.config.ConfigRecipeCondition.ID,
                () -> com.plumejade.lensouls.config.ConfigRecipeCondition.CODEC);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    /**
     * 注册数据包重载监听器，支持 {@code /reload} 热加载实体弱点配置。
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DataPackLoader());
        event.addListener(new com.plumejade.lensouls.config.ItemElementActivityLoader());
        event.addListener(new com.plumejade.lensouls.config.DamageTypeElementLoader());
        event.addListener(new com.plumejade.lensouls.config.AttackerElementLoader());
        event.addListener(new com.plumejade.lensouls.config.BossEntityLoader());
        event.addListener(new com.plumejade.lensouls.config.CopySoulFilter());
        event.addListener(new com.plumejade.lensouls.config.PhotoSetLoader());
        event.addListener(new com.plumejade.lensouls.config.PhotoSetDefs());
    }

    /**
     * 玩家加入 / /reload 时，把服务端解析的数据包结果同步到客户端。
     * <p>
     * 这些 {@code SimpleJsonResourceReloadListener} 只在服务端触发 {@link AddReloadListenerEvent}，
     * 多人客机进程从不执行，需经 S2C 包填充客户端静态缓存，
     * 否则 Jade 面板弱点/活性/韧性与照片套装 Shift tooltip 在客机为空。
     * <p>
     * 该事件在登录时序下存在局域网投递不可靠的情况，因此另加
     * {@link #onPlayerLoggedIn}（登录显式推送）与客户端 C2S 拉取（{@code DatapackSyncRequestPacket}）双保险。
     */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        com.plumejade.lensouls.network.DatapackSyncPacket packet =
                com.plumejade.lensouls.network.DatapackSyncPacket.build();
        event.getRelevantPlayers().forEach(player ->
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet));
    }

    /**
     * 玩家登录完成（play 阶段）显式推送全量数据包结果，规避 OnDatapackSyncEvent 登录时序不可靠。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player, com.plumejade.lensouls.network.DatapackSyncPacket.build());
        }
    }

    /**
     * 服务端每 tick 驱动计时器过期清理和虚影幻灵序列。
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        TimerService.getInstance().tick();
        BossPhantomManager.getInstance().tick();
        TimeFreezeManager.getInstance().tick();
        com.plumejade.lensouls.boss.BossToughnessManager.getInstance().tick();
    }
}
