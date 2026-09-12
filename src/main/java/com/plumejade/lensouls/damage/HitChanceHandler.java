package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.attribute.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 命中率（{@link ModAttributes#HIT_CHANCE}）结算。
 * <p>
 * 照片饰品以百分比加减命中率（弹幕类 -24%、小白/唤魔者 -6%、其余 +7%~+14%），
 * 这里在<b>玩家造成伤害时按最终命中率掷骰</b>：未命中则整次伤害彻底不成立，
 * 命中则原样走正常伤害流程（不做任何增减）。默认 100% 且上限 100%。
 * <p>
 * <b>钩子选择</b>：用 {@link LivingIncomingDamageEvent} 而不是 {@code LivingDamageEvent.Pre}。
 * 后者既不可取消，且触发时受伤方已经进入受击流程——把伤害置 0 仍会白送 20 tick 无敌帧
 * 与受击动画（等于"打空了却帮对手挡住下一刀"）。{@code LivingIncomingDamageEvent}
 * 位于受伤流程最前端且可取消，取消后等同于这次攻击从未发生。
 * <p>
 * <b>玩家归属判定</b>：覆盖全部源自玩家的伤害源，不留漏网——
 * 近战（造成者即玩家）、玩家发射的投射物/投掷物、玩家召唤或拥有的实体
 * （{@link OwnableEntity#getOwner()}）。非玩家来源（怪物、环境、其他玩家）一律不掷骰。
 * <p>
 * 命中率为 100% 时直接返回，不掷骰、零开销（绝大多数玩家不戴惩罚照片）。
 */
public class HitChanceHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        ServerPlayer attacker = resolvePlayerAttacker(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null) return;
        // 自伤不算"攻击"，不参与命中判定
        if (event.getEntity() == attacker) return;

        AttributeInstance instance = attacker.getAttribute(ModAttributes.HIT_CHANCE);
        if (instance == null) return;

        double chance = instance.getValue();
        if (chance >= 1.0) return;

        if (chance <= 0.0 || attacker.getRandom().nextDouble() >= chance) {
            event.setCanceled(true);
        }
    }

    /**
     * 解析这次伤害是否「源自某个玩家」。
     *
     * @param causing 伤害的造成者（投射物会解析为其发射者）
     * @param direct  伤害的直接实体（箭、雪球、弹幕等）
     * @return 负责的玩家；不是玩家来源则 null
     */
    private static ServerPlayer resolvePlayerAttacker(Entity causing, Entity direct) {
        if (causing instanceof ServerPlayer player) return player;
        if (direct instanceof ServerPlayer player) return player;
        // 玩家拥有的实体：投掷物/投射物/区域效果云/被驯服生物等
        if (direct instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        if (causing instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
