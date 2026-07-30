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
     * 如果匹配，设置对应的绕过标志。
     * @return true = 需要绕过伤害桶（调用方应将 damageBucket 置 0）
     */
    public static boolean evaluateAndShouldBypassBucket(DamageSource source, LivingEntity target) {
        BYPASS_CAP.set(false);

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (!(source.getEntity() instanceof Player player)) return false;

        ItemStack weapon = player.getMainHandItem();
        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(weapon.getItem());
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());

        for (ElementDamage element : ElementDamage.values()) {
            if (element == ElementDamage.PROJECTILE) continue;
            if (!DataPackLoader.getAllWeaknesses(targetId).containsKey(element)) continue;

            int level = ItemElementActivityLoader.getLevel(weaponId, element);
            if (level <= 0) continue;

            // 有活性 + 有弱点 → 绕过桶（不限等级）
            // 活性等级 5 → 额外绕过单次伤害上限
            if (level >= 5) {
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
}
