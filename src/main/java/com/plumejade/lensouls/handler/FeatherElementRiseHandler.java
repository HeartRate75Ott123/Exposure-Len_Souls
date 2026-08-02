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
 *   <li>受到伤害 +60%（LivingDamageEvent.Pre 受害者为佩戴者）</li>
 *   <li>造成伤害 +75%（LivingDamageEvent.Pre 伤害来源为佩戴者）</li>
 *   <li>常驻水火土末影活性 2 级（每 20 tick 直接赋予 10 秒的四种活性效果）</li>
 * </ul>
 * 佩戴者击杀 BOSS 不掉落复制之魂、无法使用复制之魂由 CopySoulDropHandler / CraftingMenuMixin 处理。
 */
public class FeatherElementRiseHandler {

    /** 受击伤害倍率（+60%） */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 1.6f;
    /** 造成伤害倍率（+75%） */
    public static final float DAMAGE_DEALT_MULTIPLIER = 1.75f;
    /** 活性效果等级（amp 1 = 2 级） */
    public static final int INFUSION_LEVEL = 1;
    /** 活性效果时长：-1 = 无限（信标式常驻） */
    public static final int INFUSION_DURATION = -1;

    /** 佩戴检测：Curios 任意槽位持有羽毛 */
    public static boolean hasFeather(Player player) {
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.FEATHER_ELEMENTRISE.get())).isPresent())
                .orElse(false);
    }

    /** 受到伤害 +60% */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && hasFeather(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_TAKEN_MULTIPLIER);
        }
    }

    /** 造成伤害 +75% */
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
        if (!hasFeather(player)) return;
        boostPotions(player);
    }

    /** 直接赋予水火土末影活性 2 级（无限时长），周期性刷新兜底维持 */
    private static void boostPotions(ServerPlayer player) {
        @SuppressWarnings("unchecked")
        net.minecraft.core.Holder<MobEffect>[] infusions = new net.minecraft.core.Holder[]{
                ModEffects.FIRE_INFUSION,
                ModEffects.WATER_INFUSION,
                ModEffects.EARTH_INFUSION,
                ModEffects.ENDER_INFUSION
        };
        for (net.minecraft.core.Holder<MobEffect> infusion : infusions) {
            player.addEffect(new MobEffectInstance(infusion, INFUSION_DURATION, INFUSION_LEVEL, true, true, true));
        }
    }
}
