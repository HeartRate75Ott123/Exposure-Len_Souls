package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.config.ItemElementActivityLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 元素伤害绕过辅助类。
 * <p>
 * 用于 BOSS 实体 {@code hurt()} Mixin 中检测当前攻击是否需要绕过伤害限制：
 * <ul>
 *   <li><b>伤害桶绕过</b>：有元素活性 + 目标有显式弱点 → 不限活性等级</li>
 *   <li><b>单次伤害上限绕过</b>：活性等级 5 + 目标有显式弱点 → 跳过 Math.min(cap, amount)</li>
 * </ul>
 * <p>
 * 绕过仅作用于 BOSS 原生限伤（Cataclysm/LegendaryMonsters 的伤害桶和单次上限），
 * 不影响护甲、抗性提升、冠军模组强化等机制。
 */
public class ElementBypassHelper {

    /** Mixin @Inject HEAD 写入 → @ModifyArg 读取，传递 cap 绕过状态 */
    private static final ThreadLocal<Boolean> BYPASS_CAP = ThreadLocal.withInitial(() -> false);

    /**
     * 在 BOSS hurt() 方法开头调用：判断武器活性 + 目标弱点是否匹配。
     * <p>
     * 破定期间 + 元素弱点武器 → 额外绕过单次伤害上限（不限活性等级）。
     * @return true = 命中元素弱点
     */
    public static boolean evaluateAndShouldBypassBucket(DamageSource source, LivingEntity target) {
        BYPASS_CAP.set(false);

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;

        // ── 我方定身（破韧定身 / 时间定格）期间：原模组的一切被动限伤都应当**无条件**失效 ──
        // 用户口径：定身时只走原版伤害事件，不再受灾变「伤害桶」/ 传奇怪物「单次上限」约束。
        // 早期实现把「破定」当成额外放行条件，仍要求「玩家 + 元素活性武器 + 目标有该元素弱点」，
        // 于是平A / 无活性武器 / 未登记弱点的 BOSS 全部照旧被压 —— 实测 99999999 也打不穿。
        // 定身是"已付出代价换来"的状态，判定应当提前短路，不参与元素匹配。
        // StunPauseHelper 是既有双态口径：破刹（韧性清空）或 时间定格，两者都算。
        if (com.plumejade.lensouls.boss.StunPauseHelper.isStunPaused(target)) {
            BYPASS_CAP.set(true);
            return true;
        }

        if (!(source.getEntity() instanceof Player player)) return false;

        ItemStack weapon = player.getMainHandItem();
        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(weapon.getItem());
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());

        // 弱点透镜照片提供的 2 级活性同样算武器活性（与 DamageHandler 同一口径）
        ElementDamage lensElement = com.plumejade.lensouls.util.WeaknessLensPhoto
                .inspectActive(weapon, target.level().registryAccess()).element();

        // 破定状态：韧性条打爆（定身中）时元素武器可突破单次上限
        boolean broken = false;
        var mgr = com.plumejade.lensouls.boss.BossToughnessManager.getInstance();
        if (mgr.has(target)) {
            var data = mgr.get(target);
            broken = data != null && data.isBroken();
        }

        for (ElementDamage element : ElementDamage.values()) {
            if (element == ElementDamage.PROJECTILE) continue;
            if (!DataPackLoader.getAllWeaknesses(targetId).containsKey(element)) continue;

            int level = ItemElementActivityLoader.getLevel(weaponId, element);
            if (lensElement == element) {
                level = Math.max(level, com.plumejade.lensouls.util.WeaknessLensPhoto.ACTIVITY_LEVEL);
            }
            if (level <= 0) continue;

            // 破定期间 + 有弱点 → 绕过单次伤害上限（不限活性等级）
            if (broken) {
                BYPASS_CAP.set(true);
            }
            return true;
        }
        return false;
    }

    /** @ModifyArg 中调用：检查是否需要绕过 Math.min(cap, amount) */
    public static boolean shouldBypassCap() {
        return BYPASS_CAP.get();
    }

    /** hurt() 结束后清标志：避免 DamageCap() 在战斗之外的调用（UI/描述）读到残留的 MAX */
    public static void clearBypass() {
        BYPASS_CAP.set(false);
    }
}
