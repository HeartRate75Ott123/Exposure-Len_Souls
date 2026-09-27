package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 减速铁板：玩家物品栏里每有 1 块，移动速度 −10%。
 * <p>
 * <b>性能</b>（需求点名「注意性能开销」）：
 * <ul>
 *   <li>每 10 tick（0.5 秒）扫一次玩家物品栏 41 格，只做 {@code ItemStack.is} 与计数，
 *       不读 NBT/组件、不建立集合——单玩家约 41 次数组判空，常数级；</li>
 *   <li>只有「目标减速值」与当前修饰符不一致时才写属性修饰符：属性修饰符一旦写入会走
 *       {@code ClientboundUpdateAttributesPacket} 同步，每 tick 重写会造成无意义的网络风暴；</li>
 *   <li>修饰符状态直接从属性实例读回（{@code getModifier}），不额外维护「上次数量」缓存——
 *       死亡重生/换维度后玩家属性实例会被重建，缓存会残留脏值导致减速丢失。</li>
 * </ul>
 * <b>扫描范围</b>：只扫玩家自己的 41 格物品栏（主背包 + 快捷栏 + 护甲 + 副手）。
 * 饰品栏、精妙背包内容物、超越维度终端存储<b>一律不计</b>——铁板收进容器就不再拖慢速度。
 * <p>
 * 减速上限 −90%：10 块就到顶，避免 −100% 让玩家彻底无法移动（连走回去丢铁板都做不到）。
 */
public class SlowIronPlateHandler {

    /** 扫描间隔（tick）：0.5 秒一次，足够跟手且开销可忽略 */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** 每块铁板的减速比例 */
    private static final double SLOW_PER_PLATE = 0.10D;
    /** 减速上限（比例，正数表示减速多少） */
    private static final double MAX_SLOW = 0.90D;

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "slow_iron_plate");

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % SCAN_INTERVAL_TICKS != 0) return;
        update(player);
    }

    /** 按当前物品栏铁板数量刷新移动速度修饰符（无变化时不动属性）。 */
    private static void update(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        int plates = countPlates(player);
        double desired = plates <= 0 ? 0.0D : -Math.min(MAX_SLOW, SLOW_PER_PLATE * plates);

        AttributeModifier existing = speed.getModifier(MODIFIER_ID);
        if (existing != null && existing.amount() == desired) return; // 已是最新，不碰属性（免同步）

        if (desired >= 0.0D) {
            if (existing != null) speed.removeModifier(MODIFIER_ID);
            return;
        }
        speed.addOrUpdateTransientModifier(
                new AttributeModifier(MODIFIER_ID, desired, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 统计玩家物品栏（41 格）里的减速铁板数量。 */
    private static int countPlates(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(ModItems.SLOW_IRON_PLATE.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private SlowIronPlateHandler() {
    }
}
