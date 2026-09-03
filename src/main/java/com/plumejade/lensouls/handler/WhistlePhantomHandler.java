package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 灵魂口哨效果：
 * <ul>
 *   <li>佩戴者造成的一切伤害 -80%（玩家本人造成，含照片弹幕；幻灵本体伤害不属于玩家造成，不受影响）；</li>
 *   <li>玩家每受一次伤害，幻灵伤害 +50%/层（cap 10，刷新式，每层持续 10s），作用于借体本体+召唤物的全部伤害；</li>
 *   <li>幻灵攻击命中非玩家时 40% 概率把该次伤害替换为目标最大生命 30%（魔法穿透），且受叠层倍率放大。</li>
 * </ul>
 * 与法师胸针互斥（由物品 canEquip 保证）。
 */
public class WhistlePhantomHandler {

    private static final int MAX_STACKS = 10;
    /** 层持续时间（tick）：10s */
    private static final int STACK_TICKS = 200;
    private static final float PERCENT_TRIGGER = 0.4f;
    private static final float PERCENT_DAMAGE = 0.30f;
    /** 百分比一击内置冷却（tick）：3s——触发后 3s 内不再触发替换伤害，仍走正常幻灵伤害 */
    private static final int PERCENT_COOLDOWN_TICKS = 60;

    /** 玩家 UUID → 每层的到期 gameTime（≤ MAX_STACKS 条） */
    private static final Map<UUID, List<Long>> STACKS = new HashMap<>();
    /** 玩家 UUID → 下一次允许触发百分比一击的 gameTime */
    private static final Map<UUID, Long> PERCENT_COOLDOWN = new HashMap<>();

    /** 该玩家是否佩戴灵魂口哨 */
    public static boolean hasWhistle(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.SOUL_WHISTLE.get())).isPresent())
                .orElse(false);
    }

    // ========== 1. 佩戴者自身造成伤害 -80% ==========

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        Entity caster = event.getSource().getEntity();
        if (!(caster instanceof ServerPlayer player)) return;
        if (event.getEntity() == caster) return;
        if (!hasWhistle(player)) return;
        event.setNewDamage(event.getNewDamage() * 0.2f);
    }

    // ========== 2. 佩戴者受击 → 幻灵增伤叠层（cap 10，刷新式 10s）==========

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerHurt(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!hasWhistle(player)) return;
        if (event.getNewDamage() <= 0f) return;

        long now = player.level().getGameTime();
        List<Long> times = STACKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        prune(times, now);
        // 刷新式：已存在层全部续至 now+STACK_TICKS
        for (int i = 0; i < times.size(); i++) {
            times.set(i, now + STACK_TICKS);
        }
        // 不满 cap 时补一层
        if (times.size() < MAX_STACKS) {
            times.add(now + STACK_TICKS);
        }
    }

    // ========== 3. 幻灵伤害增强 + 40% 百分比替换 ==========

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPhantomDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity target = event.getEntity();
        if (target instanceof Player) return;               // 幻灵不会打玩家，双保险
        if (event.getOriginalDamage() <= 0f) return;

        Entity direct = event.getSource().getDirectEntity();
        if (direct == null) return;
        if (!isPhantomSource(direct)) return;

        UUID owner = phantomOwnerOf(direct);
        if (owner == null) return;
        ServerPlayer player = ((ServerLevel) target.level()).getServer().getPlayerList().getPlayer(owner);
        if (player == null) return;
        if (!hasWhistle(player)) return;

        int stacks = currentStacks(player);
        float mult = 1f + 0.5f * stacks;

        long now = player.level().getGameTime();
        Long cdUntil = PERCENT_COOLDOWN.get(player.getUUID());
        boolean onCooldown = cdUntil != null && cdUntil > now;

        if (!onCooldown && player.getRandom().nextFloat() < PERCENT_TRIGGER) {
            // 触发：替换为目标最大生命百分比伤害，并进入 3s 内置冷却
            PERCENT_COOLDOWN.put(player.getUUID(), now + PERCENT_COOLDOWN_TICKS);
            event.setNewDamage(target.getMaxHealth() * PERCENT_DAMAGE * mult);
        } else {
            event.setNewDamage(event.getNewDamage() * mult);
        }
    }

    /** 登出清理叠层与百分比冷却 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof Player player) {
            STACKS.remove(player.getUUID());
            PERCENT_COOLDOWN.remove(player.getUUID());
        }
    }

    // ========== 工具 ==========

    private static void prune(List<Long> times, long now) {
        Iterator<Long> it = times.iterator();
        while (it.hasNext()) {
            if (it.next() <= now) it.remove();
        }
    }

    private static int currentStacks(Player player) {
        List<Long> times = STACKS.get(player.getUUID());
        if (times == null || times.isEmpty()) return 0;
        long now = player.level().getGameTime();
        prune(times, now);
        return times.size();
    }

    /** 是否幻灵来源：实体本身（本体/召唤物）或弹幕 owner 链 */
    private static boolean isPhantomSource(Entity e) {
        if (e == null) return false;
        if (isPhantomEntity(e)) return true;
        if (e instanceof net.minecraft.world.entity.projectile.Projectile proj) {
            return isPhantomSource(proj.getOwner());
        }
        return false;
    }

    private static boolean isPhantomEntity(Entity e) {
        return e.getPersistentData().getBoolean("lensouls:phantom")
                || e.getPersistentData().getBoolean("lensouls:phantom_minion");
    }

    /** 沿直接实体→owner 链向上，取第一个带 phantom_owner 的 UUID */
    private static UUID phantomOwnerOf(Entity e) {
        Entity cur = e;
        int guard = 0;
        while (cur != null && guard++ < 6) {
            UUID uuid = cur.getPersistentData().hasUUID("lensouls:phantom_owner")
                    ? cur.getPersistentData().getUUID("lensouls:phantom_owner") : null;
            if (uuid != null) return uuid;
            if (cur instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                cur = proj.getOwner();
            } else {
                break;
            }
        }
        return null;
    }

    private WhistlePhantomHandler() {}
}
