package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.entity.ModEntities;
import com.plumejade.lensouls.entity.TwitcherEntity;
import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.network.TwistSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.UUID;

/**
 * 羽·扭曲之人效果处理器。
 * <p>
 * 佩戴检测：Curios 任意槽位（findFirstCurio 遍历所有槽）。
 * 机制：
 * <ul>
 *   <li>受到伤害 +100%、造成伤害 -25%（随扭曲值线性增幅，最高 +250%）</li>
 *   <li>扭曲值：0 起，复制之魂合成按实际消耗 +N，死亡 +10，封顶 100</li>
 *   <li>扭曲值满 100 死亡：生成扭曲者（死亡点 32 格内无存活扭曲者且 64 格内无 BOSS 时），
 *       扭曲值清零，本次死亡强制掉落（由 PlayerDropEquipmentMixin 依据 FORCE_DROP 标记执行）</li>
 *   <li>任一归属扭曲者死亡 → 扭曲值清零</li>
 * </ul>
 */
public class FeatherTwitcherHandler {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("lensouls.twitcher");

    public static final String KEY_TWIST = "lensouls:twist_value";
    /** 死亡强制掉落的单次标记（扭曲值满 100 死亡时写入，掉落流程消费后清除） */
    public static final String KEY_FORCE_DROP = "lensouls:force_drop";

    public static final float DAMAGE_TAKEN_MULTIPLIER = 2.0f;
    /** 造成伤害基础倍率（-25%） */
    public static final float BASE_DEALT_MULTIPLIER = 0.75f;
    /** 每点扭曲值的伤害增幅（+3%，满值 +300%） */
    public static final float DEALT_PER_TWIST = 0.03f;

    public static final int MAX_TWIST = 100;
    /** 存活扭曲者判定半径 */
    public static final int SPAWN_RANGE = 32;
    /** BOSS 判定半径 */
    public static final int BOSS_RANGE = 64;

    /** [Twist] 日志降频：变化量 ≥20 或距上次日志 ≥100 tick 才打 */
    private static int lastLoggedTwist = -1;
    private static long lastTwistLogTick = 0;

    /** 佩戴检测：Curios 任意槽位持有扭曲羽毛 */
    public static boolean hasTwitcher(Player player) {
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.FEATHER_TWITCHER.get())).isPresent())
                .orElse(false);
    }

    /**
     * 跨死亡持久化子键：NeoForge 复活（restoreFrom）只复制 persistentData 的
     * PlayerPersisted 子键，直接放根上的键死亡复活后会丢失（实测 twist 变 0）。
     */
    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** 读取扭曲值（0..100） */
    public static int getTwist(Player player) {
        if (player == null) return 0;
        return Math.max(0, Math.min(MAX_TWIST, persisted(player).getInt(KEY_TWIST)));
    }

    /** 设置扭曲值（封顶 100）并同步客户端 */
    public static void setTwist(ServerPlayer player, int value) {
        int clamped = Math.max(0, Math.min(MAX_TWIST, value));
        CompoundTag tag = persisted(player);
        tag.putInt(KEY_TWIST, clamped);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
        long now = player.level().getGameTime();
        if (Math.abs(clamped - lastLoggedTwist) >= 20 || now - lastTwistLogTick >= 100) {
            LOGGER.info("[Twist] {} twist={}", player.getName().getString(), clamped);
            lastLoggedTwist = clamped;
            lastTwistLogTick = now;
        }
        TwistSyncPacket.send(player, clamped);
    }

    /** 增加扭曲值（封顶 100）并同步客户端 */
    public static void addTwist(ServerPlayer player, int delta) {
        setTwist(player, getTwist(player) + delta);
    }

    /** 玩家登录时同步扭曲值到客户端槽（大退重进后立即恢复显示，避免客户端缓存停留在 0） */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int twist = getTwist(player);
            LOGGER.info("[LoginSync] Player {} logged in, sending twist={}", player.getName().getString(), twist);
            TwistSyncPacket.send(player, twist);
        }
    }

    /** 受到伤害 +100% */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && hasTwitcher(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_TAKEN_MULTIPLIER);
        }
    }

    /** 造成伤害：-25% 基础 + 扭曲线性增幅（最高 +250%） */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && hasTwitcher(player)) {
            float multiplier = BASE_DEALT_MULTIPLIER + DEALT_PER_TWIST * getTwist(player);
            event.setNewDamage(event.getNewDamage() * multiplier);
        }
    }

    /**
     * 死亡结算（触发点在死亡，不是累加）：
     * <ul>
     *   <li>扭曲值满 100 时<b>尝试召唤扭曲者</b>——去重逻辑保留：
     *       附近 32 格内已有存活的归属扭曲者、或 64 格内有 BOSS 时不召唤；</li>
     *   <li><b>无论这次有没有成功召唤，一律清零</b>（不再把 100 留给下次重试）；</li>
     *   <li>扭曲值不足 100 时同样清零（「死亡就清0」）。</li>
     * </ul>
     * 满 100 时死亡仍保留「本次死亡强制掉落」标记（由掉落 mixin 消费）。
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!hasTwitcher(player)) return;

        if (getTwist(player) >= MAX_TWIST) {
            // 去重：附近已有归属扭曲者 / 有 BOSS → 不召唤（但下面照样清零）
            if (canSpawnTwitcher(player)) {
                spawnTwitcher(player);
            }
            player.getPersistentData().putBoolean(KEY_FORCE_DROP, true);
        }
        setTwist(player, 0);
    }

    /** 生成条件：死亡点 32 格内无存活归属扭曲者，且 64 格内无 BOSS */
    private static boolean canSpawnTwitcher(ServerPlayer player) {
        List<TwitcherEntity> near = player.serverLevel().getEntitiesOfClass(TwitcherEntity.class,
                player.getBoundingBox().inflate(SPAWN_RANGE),
                e -> e.isAlive() && e.isOwnedBy(player.getUUID()));
        if (!near.isEmpty()) return false;
        return !hasBossNearby(player, BOSS_RANGE);
    }

    /** 反射检测死亡点半径内是否存在持有可见 BOSS 血条的实体（复用 CopySoulDropHandler 判定） */
    private static boolean hasBossNearby(ServerPlayer player, int range) {
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range))) {
            if (entity == player) continue;
            if (CopySoulDropHandler.hasBossBar(entity)) return true;
        }
        return false;
    }

    /** 生成扭曲者：死亡点 3~8 格随机位置，属性按玩家状态配置 */
    private static void spawnTwitcher(ServerPlayer player) {
        var level = player.serverLevel();
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist = 3 + level.random.nextInt(6);
        Vec3 pos = player.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

        TwitcherEntity twitcher = ModEntities.TWITCHER.get().create(level);
        if (twitcher == null) return;
        twitcher.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
        twitcher.setOwner(player.getUUID());
        twitcher.initFromPlayer(player);
        level.addFreshEntity(twitcher);
    }

    /**
     * 扭曲者死亡 → 清零<b>击杀者</b>的扭曲值。
     * <p>
     * 需求：「杀死扭曲者就清击杀者的（如果佩戴了羽毛）」——只看击杀者，不看归属者；
     * 击杀者必须佩戴着扭曲之人（否则他本来就没有扭曲值）；
     * 该扭曲者是否归属自己不影响结算。
     */
    @SubscribeEvent
    public static void onTwitcherDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof TwitcherEntity twitcher)) return;
        if (event.getEntity().level().isClientSide) return;
        LOGGER.info("[TwitchDeath] twitcher died at {}, owner={}", twitcher.blockPosition(), twitcher.getOwnerUuid());

        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (!hasTwitcher(killer)) return;
        if (getTwist(killer) > 0) {
            LOGGER.info("[TwitchDeath] clear twist of killer {}", killer.getName().getString());
            setTwist(killer, 0);
        }
    }
}
