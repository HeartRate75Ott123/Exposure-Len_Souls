package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.ability.util.TimeFreezeManager;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 时间定格触发（拍照触发）。
 * <p>
 * 实体定身语义：只定身拍摄瞬间画面内的生物
 * （{@code FrameAddedEvent.getEntitiesInFrame()}），玩家完全正常 tick。
 */
public class TimeFreezeHandler {

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            Entity entity = event.getCameraHolderEntity();
            if (!(entity instanceof ServerPlayer player)) return;
            if (CameraAbilityStore.getSelected(player) != AbilityType.TIME_STOP) return;
            if (!hasSoulPhotography(player)) return;
            triggerTimeFreeze(player, event.getEntitiesInFrame());
        } catch (Exception ex) {
            LenSouls.LOGGER.error("[TimeFreezeHandler] error", ex);
        }
    }

    private static boolean hasSoulPhotography(ServerPlayer player) {
        var enchReg = player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var soulPhoto = enchReg.get(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ENCHANTMENT,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "soul_photography")));
        if (soulPhoto.isEmpty()) return false;
        // 以「当前使用的相机」为准：CameraInputHandler.getWieldedCamera 也会落回正在取景的
        // 饰品槽相机。只查主/副手的话，快捷取景框一开两手都是空的 ⇒ 时间定格永远不触发。
        return com.plumejade.lensouls.enchantment.ModEnchantments
                .getSoulPhotographyLevel(player.registryAccess(),
                        CameraInputHandler.getWieldedCamera(player)) > 0;
    }

    private static void triggerTimeFreeze(ServerPlayer player,
                                          java.util.List<net.minecraft.world.entity.LivingEntity> entitiesInFrame) {
        TimeFreezeManager manager = TimeFreezeManager.getInstance();
        if (manager.isFrozen()) {
            // 走最上层提示弹窗（FeatherToast）：新提示进来时旧提示走快速淡出，和其它提示同一条管线
            com.plumejade.lensouls.network.HudHintPacket.send(player,
                    Component.translatable("message.lensouls.freeze_already_active")
                            .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        manager.freeze(player.server, player, entitiesInFrame);
    }
}