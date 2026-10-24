package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 石之心效果处理器。
 * <p>
 * <ul>
 *   <li><b>单次受伤上限</b>：任何一次伤害最终不超过佩戴者最大生命值的 <b>25%</b>；</li>
 *   <li><b>代价</b>：受到的所有伤害 <b>+17%</b>。</li>
 * </ul>
 * 顺序与描述一致：先把这次伤害乘以 1.17，再用最大生命的 25% 封顶
 * （所以小额伤害只吃 +17%，大额伤害被砍到 25% 上限）。
 * <p>
 * 佩戴检测：Curios 任意槽位（{@code findFirstCurio} 遍历所有槽）。
 *
 * <h2>为什么必须挂在 {@link EventPriority#LOWEST}（1.7.94 修复）</h2>
 * 旧实现是默认优先级 {@code NORMAL}，而「受到伤害倍率」这一类处理器全部注册在它之后：
 * <ul>
 *   <li>{@code FeatherTwitcherHandler.onDamaged}（NORMAL，① e1 外伤 +100% / e2 苦楚 每点扭曲 +1%）；</li>
 *   <li>{@code FeatherAbyssHandler.damageTaken}（NORMAL，④ e3 安魂曲 +13%/天 / e11 疯狂 每点扭曲 +0.2%）；</li>
 *   <li>{@code FeatherHardmanHandler.onDamaged}（NORMAL，③ e3 保留项）；</li>
 *   <li>{@code FinalriteCurse.onArmorPierce}（NORMAL，⑥ e7 穿甲：把 50% 的护甲削减量**加回**）。</li>
 * </ul>
 * 同优先级按注册顺序派发，这些处理器都注册在石之心之后 ⇒ 它们乘/加完的伤害**完全绕过 25% 封顶**。
 * 实测最狠的一档：② e1 外伤 ×2 叠 e2 苦楚（扭曲 100 再 ×2）= ×4，
 * 25% ×4 = 100% ⇒ 满血被一击秒杀，正是「理论至少承伤 4 次却还是被秒杀」的来源。
 * 挂到 {@code LOWEST} 后，本处理器在这些乘区之后收口，封顶才是真的「最终伤害」。
 * <p>
 * 派发顺序：{@code HIGHEST → HIGH → NORMAL → LOW → LOWEST}，同优先级再按注册顺序。
 * 因此 {@code LOWEST} 里排在我们之后的只有 {@code FeatherAbyssHandler.onDamagedLast}
 * （⑥ e12 恶意反转态，只做**减法**）——封顶不会被它突破。
 *
 * <h2>为什么还要 Post 兜底</h2>
 * {@code LOWEST} 只能保证「我们之后注册的同优先级监听器」不越界；别的模组（灾变/传奇怪物等）
 * 若也挂 {@code LOWEST} 且在我们之后加伤，或者直接走 {@code setHealth} 之外的自定义管线，
 * {@code Pre} 就管不到了。{@code LivingDamageEvent.Post} 拿到的是**实际结算值**，
 * 多出来的部分按血量补回（只补差额，不打断任何死亡流程：必死级伤害已在上面的 bypass 分支豁免）。
 */
public class HeartOfStoneHandler {

    /** 受到伤害倍率（+17%） */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 1.17f;
    /** 单次受伤上限占最大生命的比例（25%） */
    public static final double MAX_HIT_FRACTION = 0.25;

    /** 佩戴检测：Curios 任意槽位持有石之心 */
    public static boolean isWorn(Player player) {
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.HEART_OF_STONE.get())).isPresent())
                .orElse(false);
    }

    /** 单次受伤上限（当前最大生命的 25%） */
    public static float maxHitDamage(Player player) {
        return (float) (player.getMaxHealth() * MAX_HIT_FRACTION);
    }

    /** 「必死级」伤害（带 BYPASSES_INVULNERABILITY，例如 ② 阻死结束的 999 真伤）不封顶、不兜底。 */
    private static boolean isUncappable(LivingDamageEvent.Pre event) {
        return event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isWorn(player)) return;
        // 「必死级」伤害不封顶：
        // 否则高上限玩家会被石之心把 999 砍成 25% 上限，阻死结束却杀不死，留下一个本该死了的人。
        if (isUncappable(event)) return;

        float damage = event.getNewDamage() * DAMAGE_TAKEN_MULTIPLIER;
        float cap = maxHitDamage(player);
        event.setNewDamage(Math.min(damage, cap));
    }

    /**
     * 兜底：实际结算值仍超过 25% 时（有别的监听器在我们之后抬价，或走了非 Pre 管线），把差额补回。
     * <p>
     * 差额口径：{@code 实际值 − 上限}。{@code getHealth()} 此时已被扣成
     * {@code 受击前 − 实际值}（可为负），所以 {@code 当前血量 + 差额} 正好等于
     * 「受击前 − 上限」——不多给也不少给。夹到 {@code [0, 最大生命]}，不会把人治成超血。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagedPost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isWorn(player)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        float applied = event.getNewDamage();
        if (applied <= 0.0f) return;
        float cap = maxHitDamage(player);
        if (applied <= cap) return;

        float healed = player.getHealth() + (applied - cap);
        player.setHealth(net.minecraft.util.Mth.clamp(healed, 0.0f, player.getMaxHealth()));
    }
}
