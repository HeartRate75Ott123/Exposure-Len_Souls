package com.plumejade.lensouls.event;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;

/**
 * 附魔移除/武器销毁时自动弹出照片。
 * <p>
 * 注意：不再使用 {@code @EventBusSubscriber(bus = Bus.GAME)}，
 * 改为在 {@link com.plumejade.lensouls.LenSouls} 构造器中手动注册。
 */
public class EnchantmentRemovalListener {

    /**
     * 当物品被修改（如砂轮祛魔）时检查。
     * NeoForge 中 ItemStack 被修改后会触发此事件。
     */
    @SubscribeEvent
    public static void onItemDestroyed(PlayerDestroyItemEvent event) {
        ItemStack original = event.getOriginal();
        if (original.isEmpty()) return;

        // 检查是否曾经有摄魂附魔
        if (original.getEnchantmentLevel(
                ModEnchantments.SOUL_PHOTOGRAPHY) <= 0) return;

        // 弹出照片
        ejectPhotograph(event.getEntity(), original);
    }

    /**
     * 从武器中弹出存储的照片到玩家背包。
     */
    private static void ejectPhotograph(Player player, ItemStack weaponStack) {
        CustomData data = weaponStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        var tag = data.copyTag();
        if (!tag.contains("SoulPhotoStack")) return;

        var access = player.registryAccess();
        ItemStack photo = ItemStack.parseOptional(access, tag.getCompound("SoulPhotoStack"));
        if (photo.isEmpty()) return;

        // 返还到玩家背包
        if (!player.getInventory().add(photo)) {
            player.drop(photo, false);
        }

        // 清除武器上的照片数据
        tag.remove("SoulPhotoStack");
        tag.remove("SoulPhotoEntityId");
        weaponStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

    }
}
