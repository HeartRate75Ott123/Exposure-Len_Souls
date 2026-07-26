package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 时空回溯处理器。
 * <p>
 * 被动：LivingDamageEvent.Pre 检测致命伤，消耗回溯照片保命。
 * 主动：由 CameraInputHandler → TemporalRecallTriggerPacket 触发。
 */
public class TemporalRecallHandler {

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity().level().isClientSide) return;

        // 不致命 → 跳过
        if (event.getNewDamage() < player.getHealth()) return;

        // 当前能力必须是时空回溯
        if (AbilityManager.getInstance().getEnabled(player) != AbilityType.TEMPORAL_RECALL) return;


        // 扫描背包找回溯照片
        ItemStack photo = findTemporalPhoto(player);
        if (photo.isEmpty()) {
            return;
        }

        // 抵消伤害
        event.setNewDamage(0);

        // 读取快照
        TemporalSnapshot snapshot = TemporalSnapshot.fromPhoto(photo);
        if (snapshot != null) {
            photo.shrink(1);
            snapshot.apply(player);
            player.displayClientMessage(
                    Component.translatable("ability.lensouls.temporal_recall.triggered")
                            .copy().withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
    }

    private static ItemStack findTemporalPhoto(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (TemporalSnapshot.hasSnapshot(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
