package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.PhantomRemnantEntity;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 虚影核心 / 虚影残像的死亡结算。
 * <p>
 * <b>为什么整件事放在 {@link LivingDeathEvent} 而不是 {@link LivingDropsEvent}：</b>
 * <pre>
 * ServerPlayer.die
 *   offset   9: CommonHooks.onLivingDeath   → LivingDeathEvent（本类在这里干活，背包完好）
 *   offset 218: dropAllDeathLoot
 *                 offset 59: dropEquipment()      ← 背包被清空、Curios 饰品被取走
 *                 offset 67: dropExperience(...)  ← 原版经验球在此爆出
 *                 offset 93: onLivingDrops        ← LivingDropsEvent（此时背包已空）
 * </pre>
 * 曾把「是否佩戴虚影核心」的判断放在 {@code LivingDropsEvent}，那时背包与饰品栏都已被清空，
 * 于是查不到核心、容器根本不生成（实测症状）。现在改为在背包完好的那一刻<b>直接搬空</b>：
 * 随后的 {@code dropEquipment()} 面对空背包自然什么都掉不出来，不需要再去清掉落列表。
 * <p>
 * 与 Enigmatic Legacy 的差异（按需求）：额外抓取 <b>Curios 全部槽位</b>并原样归位；
 * <b>经验全额</b>收进容器（并掐掉原版经验球，避免翻倍）；开启 {@code keepInventory} 时完全不生效。
 */
public class PhantomRemnantHandler {

    /**
     * 死亡瞬间（背包尚完好）完成全部结算。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (level.isClientSide) return;

            if (level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                LenSouls.LOGGER.debug("[PhantomRemnant] 跳过：已开启死亡不掉落");
                return;
            }
            if (!hasCore(player)) {
                LenSouls.LOGGER.debug("[PhantomRemnant] 跳过：未佩戴/未携带虚影核心");
                return;
            }

            // 1) 掐掉原版经验球（否则与本模组的全额返还叠加成经验翻倍）
            player.skipDropExperience();

            // 2) 此刻 Inventory（含主背包/盔甲/副手）与 Curios 都还在身上 → 全部搬进容器并清空槽位
            ItemStack remnant = PhantomRemnantData.capture(player, null);

            // 3) 在死亡点生成永存容器
            PhantomRemnantEntity entity = new PhantomRemnantEntity(level,
                    player.getX(), player.getY() + 1.5, player.getZ(), remnant, player.getUUID());
            level.addFreshEntity(entity);

            LenSouls.LOGGER.debug("[PhantomRemnant] 已为 {} 生成虚影残像 @ {} {} {}",
                    player.getName().getString(), player.getX(), player.getY(), player.getZ());
        } catch (Exception e) {
            LenSouls.LOGGER.error("[PhantomRemnant] 死亡结算失败", e);
        }
    }

    /**
     * 兜底：任何在本模组打包<b>之后</b>才进入掉落列表的东西（例如某些模组用自己的
     * 死亡处理器把物品加进 drops），一并并入附近的容器，避免散落一地。
     * <p>
     * 正常情况下这里捡不到任何东西——背包与饰品栏在第 2 步就已经被清空了。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        try {
            if (event.getDrops().isEmpty()) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (level.isClientSide) return;

            var remnantOpt = level.getEntitiesOfClass(PhantomRemnantEntity.class,
                            player.getBoundingBox().inflate(4.0),
                            e -> player.getUUID().equals(e.getOwnerUuid()))
                    .stream().findFirst();
            if (remnantOpt.isEmpty()) return;

            ItemStack stack = remnantOpt.get().getItem();
            int moved = PhantomRemnantData.append(stack, event.getDrops());
            if (moved > 0) {
                event.getDrops().clear();
                LenSouls.LOGGER.info("[PhantomRemnant] 兜底并入 {} 件残留掉落", moved);
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[PhantomRemnant] 掉落兜底失败", e);
        }
    }

    /** 佩戴在任意 Curios 栏位，或放在背包/快捷栏里，都算携带 */
    public static boolean hasCore(Player player) {
        if (player == null) return false;
        boolean inCurios = CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.PHANTOM_CORE.get())).isPresent())
                .orElse(false);
        if (inCurios) return true;
        return player.getInventory().contains(s -> s.is(ModItems.PHANTOM_CORE.get()));
    }
}
