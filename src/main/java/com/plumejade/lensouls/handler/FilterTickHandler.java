package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 滤镜效果每 tick 动态结算：属性类效果（护甲转伤害、血量转攻速、减速、护甲/韧性加成、debuff 转韧性）
 * 通过 transient 修饰符实时重算；火中回春（#11）维持再生。
 */
public class FilterTickHandler {

    private static final ResourceLocation BLOBS_DMG = ResourceLocation.parse("lensouls:filter_blobs_dmg");
    private static final ResourceLocation BLOBS_ARMOR = ResourceLocation.parse("lensouls:filter_blobs_armor");
    private static final ResourceLocation CC_SPEED = ResourceLocation.parse("lensouls:filter_cc_speed");
    private static final ResourceLocation PENCIL_SPD = ResourceLocation.parse("lensouls:filter_pencil_spd");
    private static final ResourceLocation BUMPY_ARMOR = ResourceLocation.parse("lensouls:filter_bumpy_armor");
    private static final ResourceLocation BUMPY_SPD = ResourceLocation.parse("lensouls:filter_bumpy_spd");
    private static final ResourceLocation FLIP_TGH = ResourceLocation.parse("lensouls:filter_flip_tgh");
    private static final ResourceLocation FLIP_SPD = ResourceLocation.parse("lensouls:filter_flip_spd");
    private static final ResourceLocation DESAT_TGH = ResourceLocation.parse("lensouls:filter_desat_tgh");

    private static final Map<UUID, Double> lastBlobsArmor = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // #1 护甲转伤害（实时）：护甲点转等量攻击伤害，并抵消护甲（失去护甲）
        if (player.hasEffect(ModEffects.FILTER_BLOBS)) {
            AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
            double current = armor == null ? 0 : armor.getValue();
            double last = lastBlobsArmor.getOrDefault(player.getUUID(), 0.0);
            double natural = current + last;
            double n = natural;
            setMod(player, Attributes.ATTACK_DAMAGE, BLOBS_DMG, n, AttributeModifier.Operation.ADD_VALUE);
            setMod(player, Attributes.ARMOR, BLOBS_ARMOR, -n, AttributeModifier.Operation.ADD_VALUE);
            lastBlobsArmor.put(player.getUUID(), n);
        } else {
            removeMod(player, Attributes.ATTACK_DAMAGE, BLOBS_DMG);
            removeMod(player, Attributes.ARMOR, BLOBS_ARMOR);
            lastBlobsArmor.remove(player.getUUID());
        }

        // #3 血量转攻速（实时）：超出 20 的血量每点 +2% 攻速
        if (player.hasEffect(ModEffects.FILTER_COLOR_CONVOLVE)) {
            double excess = Math.max(0, player.getHealth() - 20);
            setMod(player, Attributes.ATTACK_SPEED, CC_SPEED, 0.02 * excess, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            removeMod(player, Attributes.ATTACK_SPEED, CC_SPEED);
        }

        // #5 每失 10% 血 -7% 移速（伤害加成在 FilterDamageHandler）
        if (player.hasEffect(ModEffects.FILTER_PENCIL)) {
            float missing = 1f - player.getHealth() / player.getMaxHealth();
            int tiers = (int) (missing / 0.1f);
            setMod(player, Attributes.MOVEMENT_SPEED, PENCIL_SPD, -0.07 * tiers, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            removeMod(player, Attributes.MOVEMENT_SPEED, PENCIL_SPD);
        }

        // #8 +10 甲 / -50% 移速
        if (player.hasEffect(ModEffects.FILTER_BUMPY)) {
            setMod(player, Attributes.ARMOR, BUMPY_ARMOR, 10, AttributeModifier.Operation.ADD_VALUE);
            setMod(player, Attributes.MOVEMENT_SPEED, BUMPY_SPD, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            removeMod(player, Attributes.ARMOR, BUMPY_ARMOR);
            removeMod(player, Attributes.MOVEMENT_SPEED, BUMPY_SPD);
        }

        // #9 +8 韧性 / -50% 移速
        if (player.hasEffect(ModEffects.FILTER_FLIP)) {
            setMod(player, Attributes.ARMOR_TOUGHNESS, FLIP_TGH, 8, AttributeModifier.Operation.ADD_VALUE);
            setMod(player, Attributes.MOVEMENT_SPEED, FLIP_SPD, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            removeMod(player, Attributes.ARMOR_TOUGHNESS, FLIP_TGH);
            removeMod(player, Attributes.MOVEMENT_SPEED, FLIP_SPD);
        }

        // #14 每负面效果 +1 韧性
        if (player.hasEffect(ModEffects.FILTER_DESATURATE)) {
            int debuffs = countDebuffs(player);
            setMod(player, Attributes.ARMOR_TOUGHNESS, DESAT_TGH, debuffs, AttributeModifier.Operation.ADD_VALUE);
        } else {
            removeMod(player, Attributes.ARMOR_TOUGHNESS, DESAT_TGH);
        }

        // #11 火中生命恢复 3
        if (player.hasEffect(ModEffects.FILTER_WOBBLE) && player.isOnFire()) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 2, false, false, false));
        }
    }

    private static void setMod(LivingEntity e, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance ai = e.getAttribute(attr);
        if (ai == null) return;
        ai.removeModifier(id);
        if (amount != 0) ai.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeMod(LivingEntity e, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id) {
        AttributeInstance ai = e.getAttribute(attr);
        if (ai != null) ai.removeModifier(id);
    }

    private static int countDebuffs(LivingEntity entity) {
        int n = 0;
        for (var inst : entity.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) n++;
        }
        return n;
    }
}
