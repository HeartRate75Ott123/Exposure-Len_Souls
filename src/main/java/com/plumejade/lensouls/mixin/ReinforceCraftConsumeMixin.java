package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.recipe.ReinforceRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 强化配方在合成台上的<b>取走兜底与取证</b>（不再做整堆消耗）。
 * <p>
 * <b>定价模型（定案）</b>：所有合成台一律「1 个材料 = 1 次强化、1 个原物品」。配方契约就是
 * 「输入按<b>每槽 1 个</b>消耗、{@code assemble} 给出这一份的产出」，原版工作台、便携工作台（复用原版
 * {@code CraftingMenu}）、自动合成器以及各模组自研合成逻辑都遵循它；只有输出恒为 1
 * （见 {@link ReinforceRecipe#assemble}）才在<b>任何</b>站台上都不会复制。
 * <b>整堆强化不在合成台上做</b>：次元锤界面（{@code ReinforceMenu}）直接改槽位、由模组自己扣料，
 * 保持「1 材料 = 整堆」不变 —— 这也是同类模组的惯例（升级类功能放自研站台，原版配方保持 1:1）。
 * <p>
 * <b>历史教训（别再来一遍）</b>：
 * <ul>
 *   <li>早期 {@code assemble} 返回「原堆数量」→ 自研合成台只扣 1 个原料却整份发货 → 恶性大量物品复制；</li>
 *   <li>随后改成「原版 {@code ResultSlot} 上整堆、其它站台按件」→ 站台之间价格不一致（玩家会问为什么
 *       模组工作台更贵），且整堆依赖我们替原版循环把原槽清空，一旦此时有别的配方被 {@code getRecipeFor}
 *       选中就会串味；</li>
 *   <li>故最终统一为按件，价格一致、逻辑最简。</li>
 * </ul>
 * <p>
 * <b>仍然保留的两件事</b>：
 * <ol>
 *   <li>TAIL 校验：取走结束后网格里任何槽位数量都不该比取走前更多（预期：原物品 -1、材料 -1）；
 *       一旦变多即判定复制，按预期修正并打 WARN。</li>
 *   <li>每会话一次的取证：遍历当前输入下能匹配的配方，打印任何会退回非空剩余物的配方 id，
 *       并标出它退回的是不是<b>槽内同一实例</b>。</li>
 * </ol>
 * 这两条针对的是「某个配方的 {@code getRemainingItems} 把输入堆原样退回（未 copy）」——实测出现过
 * 「钻石一组 → 126」：{@code onTake} 的消耗循环里 {@code removeItem(1)} 让那个活引用变 63，随后
 * {@code grow(63)} 翻倍。本模组的 {@link ReinforceRecipe#getRemainingItems} 返回全空，所以凶手是别的配方。
 */
@Mixin(ResultSlot.class)
public class ReinforceCraftConsumeMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    /** 取走前的格子数量快照（null = 本次不是强化配方，不做校验） */
    @Unique
    private int[] lensoulsSnapshot;

    /** 每个会话只取证一次，避免刷屏 */
    @Unique
    private static boolean lensoulsAudited;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void lensouls$snapshotAndAudit(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide) return;

        CraftingInput input = this.craftSlots.asCraftInput();
        ReinforceRecipe.Match match = ReinforceRecipe.findMatch(input);
        if (match == null) return;

        if (!lensoulsAudited) {
            lensoulsAudited = true;
            lensouls$auditRemaining(player, input);
        }

        int size = this.craftSlots.getContainerSize();
        int[] snapshot = new int[size];
        for (int i = 0; i < size; i++) snapshot[i] = this.craftSlots.getItem(i).getCount();
        this.lensoulsSnapshot = snapshot;
    }

    /**
     * 兜底：本次取出结束后，网格里任何槽位的数量都不应该比取走前更多
     * （预期是「原物品 -1、材料 -1」）。一旦变多就说明有配方退回了剩余物，按预期修正回去并打 WARN。
     */
    @Inject(method = "onTake", at = @At("TAIL"))
    private void lensouls$guardAgainstMaterialDuplication(Player player, ItemStack stack, CallbackInfo ci) {
        int[] snapshot = this.lensoulsSnapshot;
        this.lensoulsSnapshot = null;
        if (snapshot == null) return;

        for (int i = 0; i < snapshot.length && i < this.craftSlots.getContainerSize(); i++) {
            ItemStack now = this.craftSlots.getItem(i);
            int before = snapshot[i];
            if (now.getCount() <= before) continue;   // 正常：每个原料槽 -1
            int expected = Math.max(0, before - 1);
            LenSouls.LOGGER.warn("[Reinforce] 强化取出后槽 {} 数量异常增多（{} → {}，预期 {}），已修正；"
                            + "说明有配方的 getRemainingItems 把输入堆原样退回（未 copy）",
                    i, before, now.getCount(), expected);
            if (expected <= 0) {
                this.craftSlots.setItem(i, ItemStack.EMPTY);
            } else {
                now.setCount(expected);
                this.craftSlots.setItem(i, now);
            }
        }
    }

    /**
     * 取证：列出当前输入下所有「能匹配且会退回非空剩余物」的配方，并检查退回的是不是槽内同一实例
     * （同一实例正是「扣 1 后 63，再 grow(63) 变 126」的成因）。
     */
    private void lensouls$auditRemaining(Player player, CraftingInput input) {
        try {
            for (RecipeHolder<CraftingRecipe> holder : player.level().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                if (!holder.value().matches(input, player.level())) continue;
                NonNullList<ItemStack> remaining = holder.value().getRemainingItems(input);
                for (int i = 0; i < remaining.size() && i < input.size(); i++) {
                    ItemStack rem = remaining.get(i);
                    if (rem.isEmpty()) continue;
                    ItemStack slotStack = input.getItem(i);
                    LenSouls.LOGGER.warn("[Reinforce] 配方 {} 对槽 {} 退回 {} x{}{}",
                            holder.id(), i, rem.getItem(), rem.getCount(),
                            rem == slotStack ? "（与槽内堆是同一实例 → 会让数量翻倍！）" : "（副本）");
                }
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.warn("[Reinforce] 剩余物取证失败：{}", t.toString());
        }
    }
}
