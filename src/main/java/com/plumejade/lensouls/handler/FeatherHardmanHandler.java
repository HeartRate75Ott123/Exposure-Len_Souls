package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 羽·荒厄遗咒效果处理器。
 * <p>
 * 佩戴检测：Curios 任意槽位（findFirstCurio 遍历所有槽）。
 * 效果：
 * <ul>
 *   <li>基础护甲值 +10（transient 属性修饰符，20 tick 幂等维持，摘下移除）</li>
 *   <li>受到伤害 +75%（LivingDamageEvent.Pre 受害者为佩戴者）</li>
 *   <li>造成伤害 +125%（LivingDamageEvent.Pre 伤害来源为佩戴者）</li>
 *   <li>药水活性无效：每 20 tick 清除全部水火土末影活性效果（含自己喝的）</li>
 *   <li>攻击对敌人有 35% 概率附加随机原版负面效果，等级 1~20 随机、持续 5 秒</li>
 *   <li>每 60 秒自身获得随机原版负面效果 10 秒（计时器持久化在 PlayerPersisted 子键，掉线不丢）</li>
 * </ul>
 * 佩戴者击杀 BOSS 不掉落复制之魂、无法使用复制之魂、拍照注入失败由
 * CopySoulDropHandler / CraftingMenuMixin / PhotoInjectionHandler 处理。
 */
public class FeatherHardmanHandler {

    /** 受到伤害倍率（+75%） */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 1.75f;
    /** 造成伤害倍率（+125%） */
    public static final float DAMAGE_DEALT_MULTIPLIER = 2.25f;
    /** 基础护甲值加成 */
    public static final int ARMOR_BONUS = 10;
    /** 攻击附加负面效果概率（35%） */
    public static final int DEBUFF_PROC_PERCENT = 35;
    /** 攻击附加负面效果时长：5 秒 */
    public static final int ATTACK_DEBUFF_DURATION = 100;
    /** 攻击附加负面效果最高等级（1~20 随机） */
    public static final int ATTACK_DEBUFF_MAX_LEVEL = 20;
    /** 自惩间隔：60 秒 */
    public static final int CURSE_INTERVAL_TICKS = 1200;
    /** 自惩持续：10 秒 */
    public static final int CURSE_DURATION_TICKS = 200;

    /** 自惩计时器持久化键（PlayerPersisted 子键，死亡/掉线保留） */
    public static final String KEY_NEXT_CURSE = "lensouls:hardman_next_curse";
    /** 护甲修饰符 ID（transient，可重复添加幂等替换） */
    private static final ResourceLocation ARMOR_MODIFIER_ID = ResourceLocation.parse("lensouls:hardman_armor");

    /** 原版负面效果池（攻击附加与自惩共用） */
    private static final Holder<MobEffect>[] DEBUFFS = new Holder[]{
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS,
            MobEffects.POISON,
            MobEffects.HUNGER,
            MobEffects.WITHER,
            MobEffects.BLINDNESS,
            MobEffects.CONFUSION,
            MobEffects.DIG_SLOWDOWN
    };

    private static final Holder<MobEffect>[] INFUSIONS = new Holder[]{
            ModEffects.FIRE_INFUSION,
            ModEffects.WATER_INFUSION,
            ModEffects.EARTH_INFUSION,
            ModEffects.ENDER_INFUSION
    };

    /** 佩戴检测：Curios 任意槽位持有荒厄羽毛 */
    public static boolean hasHardman(Player player) {
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.FEATHER_HARDMAN.get())).isPresent())
                .orElse(false);
    }

    /** 跨死亡持久化子键：NeoForge 复活（restoreFrom）只复制 persistentData 的 PlayerPersisted 子键 */
    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void writeBack(Player player, CompoundTag tag) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    /** 受到伤害 +75% */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && hasHardman(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_TAKEN_MULTIPLIER);
        }
    }

    /** 造成伤害 +125%；攻击附加随机负面效果（35% 概率，等级 1~20 随机） */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && hasHardman(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_DEALT_MULTIPLIER);

            LivingEntity target = event.getEntity();
            if (target != player && player.getRandom().nextFloat() * 100f < DEBUFF_PROC_PERCENT) {
                Holder<MobEffect> debuff = DEBUFFS[player.getRandom().nextInt(DEBUFFS.length)];
                int level = 1 + player.getRandom().nextInt(ATTACK_DEBUFF_MAX_LEVEL);
                target.addEffect(new MobEffectInstance(debuff, ATTACK_DEBUFF_DURATION, level - 1));
            }
        }
    }

    /** 20 tick 节流：护甲维持 / 清除活性 / 自惩计时；摘下时复位数据 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        if (hasHardman(player)) {
            applyArmorBonus(player);
            clearFusions(player);
            advanceCurse(player);
        } else {
            removeArmorBonus(player);
            resetCurseTimer(player);
        }
    }

    /** 基础护甲值 +10（幂等，摘下后由 removeArmorBonus 移除） */
    private static void applyArmorBonus(ServerPlayer player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        if (armor.getModifier(ARMOR_MODIFIER_ID) == null) {
            armor.addTransientModifier(
                    new AttributeModifier(ARMOR_MODIFIER_ID, ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** 摘下羽毛时移除护甲加成 */
    private static void removeArmorBonus(ServerPlayer player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(ARMOR_MODIFIER_ID);
        }
    }

    /** 药水活性无效：清除全部水火土末影活性效果（含玩家自己喝的有限时长） */
    private static void clearFusions(ServerPlayer player) {
        for (Holder<MobEffect> infusion : INFUSIONS) {
            player.removeEffect(infusion);
        }
    }

    /**
     * 自惩计时：持久化 gameTime 时间戳（PlayerPersisted 子键，掉线/死亡不丢）。
     * 初次佩戴 60 秒后开始第一次惩罚；间隔 60 秒，随机负面效果持续 10 秒。
     */
    private static void advanceCurse(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        long now = player.level().getGameTime();
        long next = tag.getLong(KEY_NEXT_CURSE);
        if (next <= 0L) {
            tag.putLong(KEY_NEXT_CURSE, now + CURSE_INTERVAL_TICKS);
            writeBack(player, tag);
            return;
        }
        if (now >= next) {
            Holder<MobEffect> debuff = DEBUFFS[player.getRandom().nextInt(DEBUFFS.length)];
            player.addEffect(new MobEffectInstance(debuff, CURSE_DURATION_TICKS, 0));
            tag.putLong(KEY_NEXT_CURSE, now + CURSE_INTERVAL_TICKS);
            writeBack(player, tag);
        }
    }

    /** 摘下羽毛：清除自惩计时器，重新戴上从新周期开始 */
    private static void resetCurseTimer(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        if (tag.contains(KEY_NEXT_CURSE)) {
            tag.remove(KEY_NEXT_CURSE);
            writeBack(player, tag);
        }
    }
}
