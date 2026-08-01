package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.gui.PhotoGuiMenu;
import com.plumejade.lensouls.network.ElementSpiralPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 摄魂照片增伤处理器。
 * <p>
 * 当玩家持有附有 {@link ModEnchantments#SOUL_PHOTOGRAPHY} 的武器
 * 且武器中已存入目标实体的照片时，追加伤害 = 原伤害 × photoBonus。
 * <p>
 * 注意：不再使用 {@code @EventBusSubscriber(bus = Bus.GAME)}，
 * 改为在 {@link com.plumejade.lensouls.LenSouls} 构造器中手动注册。
 */
public class PhotoDamageHandler {

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        float originalDamage = event.getOriginalDamage();

        if (originalDamage <= 0f) return;

        // 检查攻击者是否为玩家
        if (!(source.getEntity() instanceof Player player)) return;

        ItemStack weapon = player.getMainHandItem();

        // 检查武器是否有摄魂附魔
        if (weapon.getEnchantmentLevel(
                ModEnchantments.SOUL_PHOTOGRAPHY) <= 0) return;

        // 从武器 CustomData 读取已存储的实体 ID
        String storedEntityId = PhotoGuiMenu.getEntityId(weapon);
        if (storedEntityId == null) return;

        // 验证存储的照片含有摄魂术数据（防御性检查，防止遗留无标记照片造成增伤）
        if (!PhotoGuiMenu.hasSoulDataInWeapon(weapon, player.registryAccess())) return;

        // 检查目标实体类型是否匹配
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (!targetId.toString().equals(storedEntityId)) return;

        // 应用增伤 + 弱点透镜螺旋粒子
        float bonus = Config.PHOTO_BONUS.get().floatValue();
        if (bonus > 0f) {
            // 基于当前总伤害（已含元素追加）叠加，而非覆盖原始伤害
            float currentDamage = event.getNewDamage();
            event.setNewDamage(currentDamage + originalDamage * bonus);
            if (!target.level().isClientSide) {
                PacketDistributor.sendToPlayersTrackingEntity(target,
                        new ElementSpiralPacket(target.getId(), 0, true));
            }
        }
    }
}
