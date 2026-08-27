package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 滤镜效果伤害结算：攻击者持有滤镜效果时按协议加成（严格基于 {@code getNewDamage()} 护甲后基数）。
 * 受害者侧处理敌人易伤（+35% 受伤）。
 */
public class FilterDamageHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        // 受害者侧：敌人易伤 +35%
        if (victim.hasEffect(ModEffects.FILTER_SPIDER)) {
            event.setNewDamage(event.getNewDamage() * 1.35f);
        }

        // 攻击者侧：玩家持有滤镜效果 → 伤害加成
        if (event.getSource().getEntity() instanceof Player player) {
            float base = event.getNewDamage();
            float add = 0f;
            float mult = 1f;

            // #2 移速比率 ×伤害
            if (player.hasEffect(ModEffects.FILTER_SOBEL)) {
                double baseSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
                double curSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                if (baseSpeed > 0) mult *= (float) (curSpeed / baseSpeed);
            }
            // #4 空手无甲 +70
            if (player.hasEffect(ModEffects.FILTER_BLUR)) {
                if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty() && !hasArmor(player)) {
                    add += 70f;
                }
            }
            // #5 每失 10% 血 +25%
            if (player.hasEffect(ModEffects.FILTER_PENCIL)) {
                float missing = 1f - player.getHealth() / player.getMaxHealth();
                int tiers = (int) (missing / 0.1f);
                mult *= 1f + 0.25f * tiers;
            }
            // #6 满血 +10
            if (player.hasEffect(ModEffects.FILTER_ANTIALIAS)) {
                if (player.getHealth() >= player.getMaxHealth()) add += 10f;
            }
            // #10 水中 175%
            if (player.hasEffect(ModEffects.FILTER_NTSC)) {
                if (player.isInWater()) mult *= 1.75f;
            }
            // #12 每 debuff +10%
            if (player.hasEffect(ModEffects.FILTER_SCAN_PINCUSHION)) {
                int debuffs = countDebuffs(player);
                mult *= 1f + 0.10f * debuffs;
            }
            // #13 每 64 复制之魂 +3
            if (player.hasEffect(ModEffects.FILTER_BITS)) {
                int count = countCopySoul(player);
                add += 3f * (count / 64);
            }
            // #16 跳跃高度每格 +5
            if (player.hasEffect(ModEffects.FILTER_DECONVERGE)) {
                double jump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
                add += 5f * (float) jump;
            }

            event.setNewDamage((base + add) * mult);
        }
    }

    private static boolean hasArmor(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private static int countDebuffs(LivingEntity entity) {
        int n = 0;
        for (var inst : entity.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) n++;
        }
        return n;
    }

    private static int countCopySoul(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == ModItems.COPY_SOUL.get()) n += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() == ModItems.COPY_SOUL.get()) n += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() == ModItems.COPY_SOUL.get()) n += stack.getCount();
        }
        return n;
    }
}
