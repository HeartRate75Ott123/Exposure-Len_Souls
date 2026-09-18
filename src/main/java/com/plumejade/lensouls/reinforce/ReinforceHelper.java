package com.plumejade.lensouls.reinforce;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * 强化系统的公共实现：已强化列表组件读写、属性修饰符写入、可强化判定。
 * <p>
 * 两条强化路径共用本类：
 * <ul>
 *     <li>GUI 快捷强化（{@code ReinforceMenu} → C2S → 服务端 {@link #apply}）</li>
 *     <li>工作台合成（{@code ReinforceRecipe.assemble}）</li>
 * </ul>
 */
public final class ReinforceHelper {

    private ReinforceHelper() {
    }

    /**
     * 材料 → 属性修饰符 ID（{@code lensouls:reinforced/<ns>_<path>}）。
     * 同一材料对同一物品只会写入一条，重复施加由已强化列表拦截。
     */
    public static ResourceLocation modifierId(ResourceLocation materialId) {
        return ResourceLocation.fromNamespaceAndPath(LenSouls.MODID,
                "reinforced/" + materialId.getNamespace() + "_" + materialId.getPath());
    }

    /** 已强化材料 ID 列表（组件缺失 → 空列表）。 */
    public static List<String> getUsedMaterialIds(ItemStack stack) {
        List<String> used = stack.get(ModDataComponents.REINFORCED.get());
        return used == null ? List.of() : used;
    }

    /** 该物品是否已被该材料强化过。 */
    public static boolean isUsed(ItemStack stack, ResourceLocation materialId) {
        return getUsedMaterialIds(stack).contains(materialId.toString());
    }

    /** 该物品是否已被强化过任意材料。 */
    public static boolean isReinforced(ItemStack stack) {
        return !getUsedMaterialIds(stack).isEmpty();
    }

    /** 已强化的材料数量。 */
    public static int usedCount(ItemStack stack) {
        return getUsedMaterialIds(stack).size();
    }

    /** 是否可以作为被强化目标（非空、不在黑名单）。 */
    public static boolean canBeTarget(ItemStack stack) {
        return !stack.isEmpty() && !ReinforceDataLoader.isBlacklisted(stack);
    }

    /** 材料是否可用于该物品（目标合法 + 材料已配置 + 同一材料未用过）。 */
    public static boolean canApply(ItemStack stack, ResourceLocation materialId) {
        return canBeTarget(stack)
                && ReinforceDataLoader.isMaterial(materialId)
                && !isUsed(stack, materialId);
    }

    /**
     * 施加一次强化：写入属性修饰符并记录材料 ID。
     *
     * @return 成功返回 true；目标非法 / 材料未配置 / 已强化过返回 false（不做任何修改）
     */
    public static boolean apply(ItemStack stack, ResourceLocation materialId) {
        if (!canApply(stack, materialId)) return false;
        ReinforceMaterial material = ReinforceDataLoader.getMaterial(materialId);
        if (material == null) return false;
        addModifiers(stack, material);
        List<String> used = new ArrayList<>(getUsedMaterialIds(stack));
        used.add(materialId.toString());
        stack.set(ModDataComponents.REINFORCED.get(), List.copyOf(used));
        return true;
    }

    /**
     * 把材料的修饰符写入 {@code ATTRIBUTE_MODIFIERS} 组件。
     * <p>
     * 以「物品自带的默认修饰符」为基准（组件缺失时用 {@code Item.getDefaultAttributeModifiers()}），
     * 逐条复制后追加，避免丢失原版/模组物品自身的属性条目。
     */
    public static void addModifiers(ItemStack stack, ReinforceMaterial material) {
        if (material.modifiers().isEmpty()) return;
        ItemAttributeModifiers existing = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers base = existing != null
                ? existing
                : stack.getItem().getDefaultAttributeModifiers();
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : base.modifiers()) {
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        for (ReinforceModifier modifier : material.modifiers()) {
            modifier.addTo(builder, material.id());
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    /** 生成一份「已强化」副本（供工作台配方输出；数量与全部组件完整保留）。 */
    public static ItemStack createReinforced(ItemStack source, ResourceLocation materialId) {
        if (!canApply(source, materialId)) return ItemStack.EMPTY;
        ItemStack result = source.copy();
        return apply(result, materialId) ? result : ItemStack.EMPTY;
    }

    /** 物品 ID（未注册物品回退 {@code minecraft:air}）。 */
    public static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
