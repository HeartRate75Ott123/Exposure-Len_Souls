package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.feather.CurseDefs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 脱离 Curios 的登录迁移（{@code docs/诅咒系统-重设计方案.md} §6 方案 A 的第三步）。
 * <p>
 * 旧存档里诅咒是 {@code ICurioItem}，玩家会把它们戴在 Curios 的饰品槽上。现在：
 * 道具不再是 {@code ICurioItem}、也从 {@code curios:curio} tag 里移除了，
 * 而 {@code FeatherEquip} 只认我们自己的 7 个界面槽 —— 于是旧存档里"戴在身上"的诅咒
 * 会**静默失效**。
 * <p>
 * 所以登录时扫一遍 Curios，把任何属于本系统的诅咒道具**取下来放回背包**
 * （背包满则掉在脚下，不吞物品）并给一条提示。迁移是幂等的：Curios 里没有就什么都不做。
 */
public final class CurseMigrationHandler {

    private CurseMigrationHandler() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            var curios = CuriosApi.getCuriosInventory(player);
            if (curios.isEmpty()) return;

            int moved = 0;
            for (var entry : curios.get().getCurios().entrySet()) {
                var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty() || CurseDefs.byItem(stack.getItem()) == null) continue;
                    ItemStack taken = stacks.extractItem(i, stack.getCount(), false);
                    if (taken.isEmpty()) continue;
                    if (!player.getInventory().add(taken)) player.drop(taken, false);
                    moved += taken.getCount();
                }
            }

            if (moved > 0) {
                player.displayClientMessage(
                        Component.translatable("message.lensouls.curse.migrated", moved), false);
                LenSouls.LOGGER.info("[Curse] 脱离 Curios：从饰品栏迁回 {} 件诅咒道具", moved);
            }
        } catch (Throwable t) {
            // 迁移失败绝不能影响登录
            LenSouls.LOGGER.error("[Curse] Curios 迁移失败（已忽略）", t);
        }
    }
}
