package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把其它模组物品的最大堆叠上限改成 64（这些物品本来都是 1）：
 * <pre>
 * legendary_monsters:anchor_handle   ← 剑类，见下
 * fdbosses:chesed_trophy
 * fdbosses:malkuth_trophy
 * fdbosses:geburah_trophy
 * </pre>
 * <p>
 * 全部走声明式 {@link ModifyDefaultComponentsEvent}（{@code MAX_STACK_SIZE=64}）。
 * <p>
 * <b>anchor_handle 额外摘掉耐久（最终口径，用户拍板「无耐久、永不磨损」）：</b>
 * 它是 {@code AnchorHandleItem extends SwordItem}，原版 {@code TieredItem} 构造器写入
 * MAX_DAMAGE=250 / <b>DAMAGE=0</b> / MAX_STACK_SIZE=1，而 NeoForge 事件落地时会跑原版校验
 * {@code Item$Properties.validateComponents}：
 * {@code has(DAMAGE) && MAX_STACK_SIZE > 1 → 抛 IllegalStateException("Item cannot have
 * both durability and be stackable")}——所以 declaration 路径必须**连 DAMAGE 一起移除**：
 * <ul>
 *   <li>校验不再成立，可启动即生效（避免启动即炸）；</li>
 *   <li>{@code ItemStack.isDamageableItem()} = {@code has(MAX_DAMAGE) && !has(UNBREAKABLE) && has(DAMAGE)}
 *       ⇒ {@code has(DAMAGE)=false} → 不再把这类堆当耐久物品，{@code hurtAndBreak} 进门即 return
 *       ⇒ <b>永不磨损</b>，也不再有耐久条；</li>
 *   <li>没有耐久值 ⇒ 「大堆拆小堆 / 小堆并大堆」不存在耐久翻倍/缩水的歧义（耐久是每堆一个整数，
 *       堆拆分时复制的是 damage 分量——堆叠的耐久语义本身就不支撑拆并，这也是原版强制
 *       {@code durability ⇒ stackSize=1} 的原因；现在没有耐久，就彻底绕开了这层语义）。</li>
 * </ul>
 * <p>
 * 仍保留 {@code MAX_DAMAGE}（=250）这一默认组件：只摘 {@code DAMAGE}，不过其它模组按
 * {@code MAX_DAMAGE} 判「是武器」的逻辑照旧成立，行为口径最小。
 */
public final class ItemStackSizeHandler {

    private ItemStackSizeHandler() {
    }

    /**
     * 物品 id → 期望的最大堆叠上限（LinkedHashMap 保证日志输出顺序稳定）。
     */
    public static final Map<ResourceLocation, Integer> OVERRIDES;

    /**
     * 默认组件里要一并移除 {@code DAMAGE} 的物品 id（= 让它永不磨损，从而能声明式堆叠 64）。
     */
    private static final List<ResourceLocation> STRIP_DURABILITY =
            List.of(ResourceLocation.parse("legendary_monsters:anchor_handle"));

    static {
        Map<ResourceLocation, Integer> m = new LinkedHashMap<>();
        m.put(ResourceLocation.parse("legendary_monsters:anchor_handle"), 64);
        m.put(ResourceLocation.parse("fdbosses:chesed_trophy"), 64);
        m.put(ResourceLocation.parse("fdbosses:malkuth_trophy"), 64);
        m.put(ResourceLocation.parse("fdbosses:geburah_trophy"), 64);
        OVERRIDES = Collections.unmodifiableMap(m);
    }

    /**
     * {@link ModifyDefaultComponentsEvent}（mod 总线事件）：
     * 所有 RegisterEvent 跑完之后、注册表冻结之前触发，此时各模组物品都已注册；
     * 这里打的补丁最终走 {@code Item.modifyDefaultComponentsFrom}，会再次过原版
     * {@code validateComponents}——所以带 {@code DAMAGE} 的物品必须先摘 DAMAGE 再改堆叠。
     */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (Map.Entry<ResourceLocation, Integer> e : OVERRIDES.entrySet()) {
            ResourceLocation id = e.getKey();
            Item item = find(id);
            if (item == null) {
                LenSouls.LOGGER.debug("[ItemStack] 目标物品不存在，跳过（模组未装/已改名）: {}", id);
                continue;
            }
            int size = e.getValue();
            boolean strip = STRIP_DURABILITY.contains(id);
            if (strip != item.components().has(DataComponents.DAMAGE)) {
                LenSouls.LOGGER.debug("[ItemStack] {} 实际有/无 DAMAGE 与预期不符（有={}，预期摘除={}），同样处理",
                        id, item.components().has(DataComponents.DAMAGE), strip);
            }
            event.modify(item, builder -> {
                builder.set(DataComponents.MAX_STACK_SIZE, size);
                if (strip) {
                    // 摘掉 DAMAGE：绕过原版「耐久+可堆叠」校验，并让物品吃不到耐久损耗（永不磨损）
                    builder.remove(DataComponents.DAMAGE);
                }
            });
        }
        LenSouls.LOGGER.info("[ItemStack] 已把 {} 个物品的最大堆叠上限改为 64（其中 {} 个同时摘除耐久）",
                OVERRIDES.size(), Math.min(STRIP_DURABILITY.size(), OVERRIDES.size()));
    }

    /**
     * 按 id 查物品；{@code BuiltInRegistries.ITEM} 是 DefaultedRegistry，
     * 缺条目时 {@code get()} 返回 minecraft:air 而不是 null，必须用 {@code containsKey} 排除。
     */
    @Nullable
    private static Item find(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? null : item;
    }
}
