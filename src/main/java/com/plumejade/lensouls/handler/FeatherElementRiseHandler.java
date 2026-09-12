package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 羽·元素觉醒者效果处理器。
 * <p>
 * 佩戴检测：Curios 任意槽位（findFirstCurio 遍历所有槽）。
 * 效果：
 * <ul>
 *   <li>受到伤害 +50%（LivingDamageEvent.Pre 受害者为佩戴者）</li>
 *   <li>造成伤害 +40%（LivingDamageEvent.Pre 伤害来源为佩戴者）</li>
 *   <li>身上的药水活性等级 <b>+3</b>（在现有活性基础上叠加，带防滚雪球记录）</li>
 *   <li>造成的元素 DoT 伤害 <b>×3</b>（见 {@code SoulDotHandler}）</li>
 * </ul>
 * 佩戴者「无法使用复制之魂」由 CopySoulItem 封印（配方层拦截）处理；
 * 「无法掉落复制之魂」已按需求<b>移除</b>——佩戴时 BOSS 照常掉落复制之魂。
 */
public class FeatherElementRiseHandler {

    /** 受击伤害倍率（+50%） */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 1.5f;
    /** 造成伤害倍率（+40%） */
    public static final float DAMAGE_DEALT_MULTIPLIER = 1.4f;
    /** 元素 DoT 增伤倍率（×3） */
    public static final float DOT_MULTIPLIER = 3.0f;
    /** 药水活性等级加成（+3 级） */
    public static final int INFUSION_LEVEL_BONUS = 3;
    /** 活性效果时长：-1 = 无限（信标式常驻） */
    public static final int INFUSION_DURATION = -1;

    /** 防滚雪球：记录我们写入的等级与推算出的玩家基础等级 */
    private static final String TAG_APPLIED = "lensouls:feather_rise_applied";
    private static final String TAG_BASE = "lensouls:feather_rise_base";

    /** 佩戴检测：Curios 任意槽位持有羽毛 */
    public static boolean hasFeather(Player player) {
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.FEATHER_ELEMENTRISE.get())).isPresent())
                .orElse(false);
    }

    /** 受到伤害 +50% */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && hasFeather(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_TAKEN_MULTIPLIER);
        }
    }

    /** 造成伤害 +40% */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && hasFeather(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_DEALT_MULTIPLIER);
        }
    }

    /** 常驻四种活性效果（信标式：无限时长 + ambient，无粒子无到期提醒） */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        if (hasFeather(player)) {
            boostPotions(player);
        } else {
            removeBoostPotions(player);
        }
    }

    private static final net.minecraft.core.Holder<MobEffect>[] INFUSIONS = new net.minecraft.core.Holder[]{
            ModEffects.FIRE_INFUSION,
            ModEffects.WATER_INFUSION,
            ModEffects.EARTH_INFUSION,
            ModEffects.ENDER_INFUSION
    };

    /**
     * 在玩家现有药水活性基础上 +3 级（无限时长），周期性刷新兜底维持。
     * <p>
     * 防滚雪球：每 20 tick 都会重写效果等级，若直接「读当前等级再 +3」会无限叠加。
     * 因此记录我们上次写入的等级；当读到的等级恰好等于它时，说明那是我们的产物，
     * 基础等级沿用上次推算值，不参与 +3。
     */
    private static void boostPotions(ServerPlayer player) {
        var pd = player.getPersistentData();
        int recorded = pd.getInt(TAG_APPLIED);
        var probe = player.getEffect(INFUSIONS[0]);
        int current = probe == null ? -1 : probe.getAmplifier();
        int base = (current >= 0 && current == recorded) ? pd.getInt(TAG_BASE) : Math.max(current, 0);
        int target = base + INFUSION_LEVEL_BONUS;

        pd.putInt(TAG_BASE, base);
        pd.putInt(TAG_APPLIED, target);

        for (net.minecraft.core.Holder<MobEffect> infusion : INFUSIONS) {
            player.addEffect(new MobEffectInstance(infusion, INFUSION_DURATION, target, true, true, true));
        }
    }

    /** 摘下羽毛时移除常驻灌注（仅限无限时长者，保留玩家自己喝的有限时长活性药水） */
    private static void removeBoostPotions(ServerPlayer player) {
        for (net.minecraft.core.Holder<MobEffect> infusion : INFUSIONS) {
            MobEffectInstance inst = player.getEffect(infusion);
            if (inst != null && inst.getDuration() == MobEffectInstance.INFINITE_DURATION) {
                player.removeEffect(infusion);
            }
        }
        var pd = player.getPersistentData();
        pd.remove(TAG_APPLIED);
        pd.remove(TAG_BASE);
    }
}
