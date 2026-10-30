package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.handler.curse.TimecoreCurse;
import com.plumejade.lensouls.handler.curse.ZeroforgeCurse;
import com.plumejade.lensouls.integration.PhotoSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * Curios 槽位里「<b>不是由饰品物品本身给出</b>」的那一类加成 —— 照片栏位（按已装备照片推导）、
 * ⑦ e12 的首饰（head）栏位（按诅咒状态推导）等。
 * <p>
 * <b>为什么需要单独管这一层（实测根因）：</b>
 * <ul>
 *   <li>照片栏位加成（{@code lensouls:photo_slot_bonus}，+8）是
 *       {@link PhotoSpecialEffects#refreshPhotoSlots} 每刻按「<b>当前已装备的照片</b>」推导的
 *       —— 容器内容和槽位数<b>互为因果</b>。</li>
 *   <li>死亡瞬间虚影核心在 {@code LivingDeathEvent} 里把容器全部搬空 ⇒ 下一刻推导值变 0、
 *       {@code photograph} 容器缩回基础 3 格。</li>
 *   <li>几秒后拾取归还时，还原流程按「记忆槽位」放物品，却只看到 3 格 ⇒ 越界的照片被挤回背包
 *       （实测：9 件只装回 3 件、其余 6 件进背包，且随后推导值又回到 +8，看着像「槽位够却不肯装」）。</li>
 * </ul>
 * 所以归还流程要两步走：
 * <ol>
 *   <li>{@link #refresh}：让<b>与内容无关</b>的那部分推导加成先回来（如 e12 首饰栏、e14 照片栏）；</li>
 *   <li>{@link #growTo}：用临时槽位修饰符把容器<b>顶到记忆尺寸</b>，让物品先进得去；
 *       内容就位后再 {@link #refresh}（这次推导值由「已装备内容」算出来），
 *       最后 {@link #releaseTemporary} 收掉临时修饰符 —— 此时推导值已经和内容对上，
 *       尺寸自然停在正确值上。</li>
 * </ol>
 * 注意顺序不能颠倒：先撤临时修饰符再 refresh 会让容器瞬间缩回基础尺寸，
 * 越界物品被 Curios 的 {@code loseStacks} 甩进背包（正是我们要修的症状）。
 */
public final class CurioSlotBonuses {

    /** 归还期间临时顶开容器尺寸用的修饰符 id（只在归还方法体内存在，结束即撤） */
    public static final ResourceLocation TEMP_CAPACITY_MOD =
            ResourceLocation.parse("lensouls:remnant_capacity");

    /** 容器尺寸上限（与虚影容器的容量上限同量级，防脏数据把槽位顶到离谱的值） */
    private static final int CAPACITY_CAP = 256;

    private CurioSlotBonuses() {
    }

    /**
     * 重算全部「推导型」槽位加成。可重复调用（各加成方内部都会比对现值，值没变就不动）。
     * <p>
     * 每个来源单独 try/catch：某个来源炸掉不能连累其余，更不能把归还流程带崩。
     */
    public static void refresh(ServerPlayer player) {
        if (player == null) return;
        try {
            PhotoSpecialEffects.refreshPhotoSlots(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[CurioSlot] 照片栏位加成重算失败", t);
        }
        try {
            ZeroforgeCurse.refreshHeadSlots(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[CurioSlot] ⑦ e12 首饰栏位重算失败", t);
        }
        try {
            TimecoreCurse.refreshPhotoSlots(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[CurioSlot] ⑤ e14 照片栏位重算失败", t);
        }
        // 上面各来源只改修饰符，尺寸是 Curios 的惰性 resize —— 这里统一落实一次
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                try {
                    stacksHandler.update();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /**
     * 把某个容器临时顶到至少 {@code targetSlots} 格。
     * <p>
     * 走 Curios 官方的 {@code addTransientSlotModifier} + {@code update()}，而不是直接
     * {@code IDynamicStackHandler#grow}：前者会连带 resize 掉 {@code cosmetics / renders /
     * activeStates} 这几份与 stacks 等长的列表，后者只长 stacks，会让它们尺寸失配。
     *
     * @return 是否真的顶开过（供归还日志计数）
     */
    public static boolean growTo(ServerPlayer player, String identifier, int targetSlots) {
        if (player == null || identifier == null || targetSlots <= 0) return false;
        int target = Math.min(targetSlots, CAPACITY_CAP);
        boolean[] grown = {false};
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var stacksHandler = handler.getCurios().get(identifier);
            if (stacksHandler == null) return;
            int current = stacksHandler.getSlots();     // 顺带把惰性 resize 落实
            if (current >= target) return;
            handler.addTransientSlotModifier(identifier, TEMP_CAPACITY_MOD,
                    target - current, AttributeModifier.Operation.ADD_VALUE);
            stacksHandler.update();
            grown[0] = true;
        });
        return grown[0];
    }

    /**
     * 撤掉所有临时顶开的修饰符（必须先 {@link #refresh} 让推导值就位，否则容器会缩回基础尺寸）。
     *
     * @return 撤掉的容器数量
     */
    public static int releaseTemporary(ServerPlayer player) {
        if (player == null) return 0;
        int[] released = {0};
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var entry : handler.getCurios().entrySet()) {
                // 只处理「确实还挂着临时修饰符」的容器：removeSlotModifier 无修饰符时是空操作，
                // 但 update() 会白跑一趟，没必要。
                if (!entry.getValue().getModifiers().containsKey(TEMP_CAPACITY_MOD)) continue;
                handler.removeSlotModifier(entry.getKey(), TEMP_CAPACITY_MOD);
                entry.getValue().update();
                released[0]++;
            }
        });
        return released[0];
    }

    /**
     * 读某个容器上某个槽位修饰符的<b>现值</b>（不存在算 0）。
     * <p>
     * 供给「推导型加成」的拥有者做<b>自愈判定</b>：只看自己存在玩家身上的标记位
     * （如 {@code persistentData} 里的 "上次写入多少"）是不够的 ——
     * 死亡/重生会重建属性与容器，标记位还在、修饰符却已经没了，于是永远不再重写。
     */
    public static double slotModifierAmount(ServerPlayer player, String identifier, ResourceLocation id) {
        if (player == null || identifier == null || id == null) return 0.0D;
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return 0.0D;
        var stacksHandler = handler.getCurios().get(identifier);
        if (stacksHandler == null) return 0.0D;
        AttributeModifier modifier = stacksHandler.getModifiers().get(id);
        return modifier == null ? 0.0D : modifier.amount();
    }
}
