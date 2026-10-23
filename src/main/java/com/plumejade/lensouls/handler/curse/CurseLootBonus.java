package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * ⑤ 断时炉心的「时运 / 抢夺 ±2」按**真正的附魔等级**实现（而不是自己伪造掉落物）。
 * <p>
 * <b>为什么重做</b>：旧实现是在 {@code BlockDropsEvent} / {@code LivingDropsEvent} 里
 * 「复制一件已有掉落物」（{@code src.getItem().copy()}）。掉落物已经是原版按战利品表算完的结果，
 * 再复制一份就等于**对所有方块、所有生物无条件加产量** —— 实测一块石头会掉 2 个，
 * 而原版时运只对「战利品表里写了 {@code apply_bonus}」的对象生效（煤矿/钻石矿/沙砾出燧石、
 * 小麦/甜菜/胡萝卜的额外产物…），抢夺只对「写了 {@code enchanted_count_increase}」的生物掉落生效。
 * <p>
 * <b>现在的口径</b>：把「角色身上多带 2 级附魔」这件事交给原版自己去算 ——
 * <ul>
 *   <li>时运（方块）：{@code Block#getDrops} 的战利品上下文里把 {@code TOOL} 换成**带时运 II 的副本**。
 *       原版 {@code ApplyBonusCount} 读的就是 {@code LootContextParams.TOOL} + 工具上的时运等级，
 *       所以之后完全走原版公式（含 {@code ore_drops} 与 {@code uniform_bonus_count} 两种算法），
 *       没有写时运的方块依旧掉 1 个。入口见 {@code mixin/BlockFortuneDropMixin}。</li>
 *   <li>抢夺（生物）：在原版问「这个生物身上有几级抢夺」时回答「+2」。
 *       1.21.1 的生物战利品表用 {@code EnchantedCountIncreaseFunction}，它读
 *       {@code EnchantmentHelper.getEnchantmentLevel(LOOTING, ATTACKING_ENTITY)}，
 *       所以在这里加 2 等价于「手上那把武器的抢夺等级高 2」。
 *       入口见 {@code mixin/EnchantmentHelperLootingMixin}。</li>
 * </ul>
 * <b>诅咒态（−2）不在这里</b>：原版没有「负等级附魔」，那头仍然是
 * {@code TimecoreCurse} 里「按概率删掉 0~2 件掉落」的近似（对玩家是纯惩罚，近似无害）。
 */
public final class CurseLootBonus {

    /** 反转态追加的附魔等级（= 文案里的「时运/抢夺 +2」） */
    public static final int ENCHANT_BONUS = 2;

    /** e4 徒劳（0 基序 3）：反转 = 时运 +2 */
    private static final int E4_VAIN = 3;
    /** e15 厄运（0 基序 14）：反转 = 抢夺 +2 */
    private static final int E15_MISFORTUNE = 14;

    private CurseLootBonus() {
    }

    /** 该生物作为「攻击者 / 破坏者」时应额外获得的时运等级（0 = 不额外） */
    public static int extraFortune(Entity breaker) {
        if (!(breaker instanceof ServerPlayer player)) return 0;
        return CurseManager.rev(player, CurseDefs.TIMECORE, E4_VAIN) ? ENCHANT_BONUS : 0;
    }

    /** 该生物作为「攻击者」时应额外获得的抢夺等级（0 = 不额外） */
    public static int extraLooting(LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer player)) return 0;
        return CurseManager.rev(player, CurseDefs.TIMECORE, E15_MISFORTUNE) ? ENCHANT_BONUS : 0;
    }

    /**
     * 把「破坏方块用的工具」换成**带额外时运的副本**（原工具一律不动）。
     * <p>
     * 空手不处理：空物品堆复制出来还是空物品堆，挂不上附魔；语义上也对 ——
     * 手里没有工具就没有「工具上的时运」。
     */
    public static ItemStack fortuneTool(ItemStack tool, Entity breaker, ServerLevel level) {
        int bonus = extraFortune(breaker);
        if (bonus <= 0 || tool == null || tool.isEmpty() || level == null) return tool;
        Holder<Enchantment> fortune = level.registryAccess().holderOrThrow(Enchantments.FORTUNE);
        ItemStack copy = tool.copy();
        // 注意：**不能**用 EnchantmentHelper.updateEnchantments —— 它在本堆没有任何附魔组件时
        // 直接 return EMPTY、什么都不写（反编译确认），于是「给没附魔的工具临时加时运」会静默失效。
        // 这里显式读写组件：工具用 ENCHANTMENTS，附魔书用 STORED_ENCHANTMENTS（getComponentType 已分好）。
        DataComponentType<ItemEnchantments> type = EnchantmentHelper.getComponentType(copy);
        ItemEnchantments.Mutable mutable =
                new ItemEnchantments.Mutable(copy.getOrDefault(type, ItemEnchantments.EMPTY));
        mutable.set(fortune, mutable.getLevel(fortune) + bonus);
        copy.set(type, mutable.toImmutable());
        return copy;
    }
}
