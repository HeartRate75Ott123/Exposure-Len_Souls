package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * BOSS 韧性伤害处理器。
 * <p>
 * 在受到伤害时自动注册 BOSS 韧性系统，并应用减伤。
 * BOSS 判定基于两类检测：
 * <ol>
 *   <li>配置白名单</li>
 *   <li>通用高血量阈值（maxHealth ≥ 200）</li>
 * </ol>
 */
public class ToughnessDamageHandler {

    /** BOSS 通用判定阈值：超过此血量的实体自动注册韧性 */
    private static final double GENERIC_BOSS_HP_THRESHOLD = 200.0;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (event.getOriginalDamage() <= 0f) return;

        BossToughnessManager manager = BossToughnessManager.getInstance();

        // ── 1. BOSS 自动注册 ──
        if (!manager.has(target) && isBoss(target)) {
            manager.register(target);
        }

        // ── 2. 韧性减伤 ──
        if (manager.has(target)) {
            float currentDamage = event.getNewDamage();
            float reduced = manager.applyDamageReduction(target, currentDamage);
            if (reduced != currentDamage) {
                event.setNewDamage(reduced);
            }
        }
    }

    /**
     * 判断实体是否触发韧性。
     * <p>
     * 白名单 / 黑名单均支持通配 {@code "all"}，语义对称：
     * <ul>
     *   <li>两表都不含 {@code all} → 默认按通用血量阈值（maxHealth ≥ {@link #GENERIC_BOSS_HP_THRESHOLD}）判定；</li>
     *   <li>白名单含 {@code all} → 默认全包含；黑名单含 {@code all} → 默认全排除；
     *       两者皆含 {@code all} 时黑名单优先（全排除）；</li>
     *   <li>具体条目（非 {@code all}）作为例外覆盖默认方向，且黑名单优先于白名单。</li>
     * </ul>
     * 例：白名单=all + 黑名单=[x] → 除 x 外全部触发；黑名单=all + 白名单=[x] → 仅 x 触发。
     */
    public static boolean isBoss(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String idStr = id.toString();
        var wl = Config.TOUGHNESS_WHITELIST.get();
        var bl = Config.TOUGHNESS_BLACKLIST.get();
        boolean hasAllWL = wl.contains("all");
        boolean hasAllBL = bl.contains("all");

        // 默认基准：白名单 all → 全包含；黑名单 all → 全排除；否则按 200 血阈值
        boolean result;
        if (hasAllWL && hasAllBL) result = false;       // 冲突：黑名单 all 优先（全排除）
        else if (hasAllWL) result = true;               // 默认全包含
        else if (hasAllBL) result = false;              // 默认全排除
        else result = entity.getMaxHealth() >= GENERIC_BOSS_HP_THRESHOLD;

        // 具体条目覆盖（黑名单优先于白名单）
        if (wl.contains(idStr)) result = true;
        if (bl.contains(idStr)) result = false;
        return result;
    }
}
