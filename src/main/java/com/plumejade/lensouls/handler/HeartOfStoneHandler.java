package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isWorn(player)) return;

        float damage = event.getNewDamage() * DAMAGE_TAKEN_MULTIPLIER;
        float cap = (float) (player.getMaxHealth() * MAX_HIT_FRACTION);
        event.setNewDamage(Math.min(damage, cap));
    }
}
