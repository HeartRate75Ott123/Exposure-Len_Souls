package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.entity.PhantomDamageHandler;
import com.plumejade.lensouls.util.PhotoProjMarker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 照片弹幕自伤 / 误伤保护。
 * <p>
 * 弹幕以玩家为 owner/caster，被 BOSS 的反弹、护盾弹回、高延迟下的乱弹之后可能打回玩家自己
 * （实测：骷髅照片的三连箭在网络延迟高时被反弹，命中玩家自身造成伤害）。原版「自己的箭打自己」
 * 是合法行为（盾反），但照片弹幕是拍照附带的派生攻击，回打自己只会变成纯粹的挫败感。
 * <p>
 * 判定严格收窄到「本模组标记过的照片弹幕（{@code lensouls:photo_proj}）」：
 * <ul>
 *   <li>命中发射者本人 → 免伤（本次修复的问题）；</li>
 *   <li>命中任意玩家 → 免伤（与弹幕框架注释里既有的「不伤自身/队友」口径一致，
 *       队友挡在弹道上也属于误伤而非设计意图）；</li>
 *   <li>命中任意<b>驯服生物</b>（狼/猫/鹦鹉/马…）→ 免伤。弹幕是「打面前那个敌人」的选敌逻辑
 *       （{@code findNearestNonPlayer} 只排除玩家），玩家身边的狗会挤进这条弹道上挨打，
 *       那是纯粹的误伤。口径取「只要是驯服的就不打」，不区分是谁的宠物——
 *       队友的宠物挡在弹道上与队友本人一样属于误伤。</li>
 * </ul>
 * 普通弓箭、别的玩家射来的箭、BOSS 自己的弹幕一律不受影响；玩家用剑/弓打自己的狗也照旧掉血。
 * <p>
 * 用 {@link LivingIncomingDamageEvent}（无敌帧与护甲结算之前、可取消）而不是
 * {@code LivingDamageEvent.Pre}——后者只能把伤害值置 0，击退与受击动画仍会触发。
 */
public class PhotoProjSafetyHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) return;
        Entity victim = event.getEntity();
        if (PhotoProjMarker.isSelfHit(event.getSource(), victim)) {
            event.setCanceled(true);
            return;
        }
        if (victim instanceof Player || PhantomDamageHandler.isTamedPet(victim)) {
            if (PhotoProjMarker.isBarrageDamage(event.getSource())) {
                event.setCanceled(true);
            }
        }
    }

    private PhotoProjSafetyHandler() {
    }
}
