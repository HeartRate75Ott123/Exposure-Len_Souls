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
 * 强化配方「整堆吞掉原物品」：在结果真正被取走的那一刻，把被强化物品所在槽位整堆清空。
 * <p>
 * <b>为什么必须在这里做</b>：{@link ReinforceRecipe#assemble} 的输出数量 = 输入整堆数量（8 个铁锭 → 8 个强化铁锭），
 * 而原版 {@code ResultSlot.onTake} 对每个输入槽只 {@code removeItem(1)}。所以任何「原槽没被整堆清掉」的路径
 * 都会变成「花 1 个铁锭换来 8 个强化铁锭」的复制。
 * <p>
 * <b>为什么不能用 {@code PlayerEvent.ItemCraftedEvent}</b>（原先的做法，已废弃）：该事件由
 * {@code ResultSlot.checkTakeAchievements} 派发，而它外面套着 {@code if (this.removeCount > 0)}；
 * {@code removeCount} 只在 {@link ResultSlot#remove(int)} 里累加，也就是<b>普通左键取出</b>那一条路径。
 * shift 快速移动走的是 {@code CraftingMenu.quickMoveStack → moveItemStackTo}（直接搬运 ItemStack，
 * 不经过 {@code ResultSlot.remove}），{@code removeCount} 保持 0 → <b>事件不派发</b> → 槽位清不掉，
 * 于是「8 铁锭强化后得到 7 未强化 + 8 强化过的」。
 * <p>
 * {@code onTake} 在两条路径上都会被调用（快速移动那条传进来的结果堆是空的，但方法照常执行），
 * 且 HEAD 处输入容器内容完整、还没有发生任何消耗，是最可靠的注入点。
 * 该注入点在格子里预览配方时不会触发，天然只在真实合成时执行一次。
 * <p>
 * <b>材料槽翻倍（实测「钻石一组 → 126」）的兜底与取证</b>：{@code onTake} 的消耗循环里
 * 唯一能让一个槽位超过原有数量的只有 {@code itemstack.grow(remaining.get(i).getCount())}，
 * 而 126 = 63 + 63（63 = 扣掉 1 之后的余量）说明当时被取用的 `remaining` 那一格与槽内**是同一个
 * ItemStack 实例**（某个配方的 {@code getRemainingItems} 把输入堆原样退了回来，没有 copy）。
 * 本模组的 {@link ReinforceRecipe#getRemainingItems} 返回全空，所以问题出在「配方查询在两次之间
 * 换了对象」——HEAD 清空原物品槽后网格只剩材料，`getRecipeFor` 可能选中另一个也能匹配该输入的配方。
 * 因此这里做两件事：
 * <ol>
 *   <li>HEAD 时把「会退回剩余物」的配方查出来并打日志（每个会话一次），直接点名凶手；</li>
 *   <li>HEAD 快照网格各槽数量、TAIL 校验：任何槽位数量变大即判定为复制，按「消耗 1 个」的预期修正回去，
 *       保证玩家永远不会真的拿到翻倍材料。</li>
 * </ol>
 */
@Mixin(ResultSlot.class)
public class ReinforceCraftConsumeMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    /** 取走前的格子数量快照（null = 本次不是强化配方，不做校验） */
    @Unique
    private int[] lensoulsSnapshot;

    /** 每个会话只点名一次凶手，避免刷屏 */
    @Unique
    private static boolean lensoulsAudited;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void lensouls$consumeWholeReinforceStack(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide) return;

        CraftingInput input = this.craftSlots.asCraftInput();
        ReinforceRecipe.Match match = ReinforceRecipe.findMatch(input);
        if (match == null) return;

        if (!lensoulsAudited) {
            lensoulsAudited = true;
            lensouls$auditRemaining(player, input, match.baseSlot(), match.materialSlot());
        }

        // 取走前快照（TAIL 用来判定有没有被别的逻辑灌回材料）
        int size = this.craftSlots.getContainerSize();
        int[] snapshot = new int[size];
        for (int i = 0; i < size; i++) snapshot[i] = this.craftSlots.getItem(i).getCount();
        this.lensoulsSnapshot = snapshot;

        // 整堆清空被强化物品槽；材料槽留给原版循环 removeItem(1) 消耗恰好 1 个
        if (!this.craftSlots.getItem(match.baseSlot()).isEmpty()) {
            this.craftSlots.setItem(match.baseSlot(), ItemStack.EMPTY);
        }
    }

    /**
     * 兜底：本次取出结束后，网格里任何槽位的数量都不应该比取走前更多
     * （强化配方的预期是「原物品槽清零 + 材料槽 -1」）。一旦变多就说明有配方退回了剩余物，
     * 把它按预期修正回去，并打一条 WARN 便于定位。
     */
    @Inject(method = "onTake", at = @At("TAIL"))
    private void lensouls$guardAgainstMaterialDuplication(Player player, ItemStack stack, CallbackInfo ci) {
        int[] snapshot = this.lensoulsSnapshot;
        this.lensoulsSnapshot = null;
        if (snapshot == null) return;

        for (int i = 0; i < snapshot.length && i < this.craftSlots.getContainerSize(); i++) {
            ItemStack now = this.craftSlots.getItem(i);
            int before = snapshot[i];
            if (now.getCount() <= before) continue;   // 正常：清空(base) 或 -1(材料)
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
     * 取证：找出「会退回非空剩余物」的配方并检查退回的是不是槽内同一实例（同一实例正是
     * 「扣 1 后 63，再 grow(63) 变 126」的成因）。
     * <p>
     * 关键是要查<b>清空原物品槽之后</b>那一次查询：原版 {@code onTake} 的顺序是
     * 「HEAD(我们清空 base) → getRemainingItemsFor(网格只剩材料) → 消耗循环」，
     * 所以真正决定 returning items 的是「只剩材料」这个输入下 {@code getRecipeFor} 选中的配方，
     * 而不是清空之前。两个输入都查一遍，日志里就能直接点名。
     */
    private void lensouls$auditRemaining(Player player, CraftingInput input, int baseSlot, int materialSlot) {
        try {
            lensouls$report(player, input, "清空前");

            // 复制一份「原物品槽已清空」的输入，模拟真实那一刻
            NonNullList<ItemStack> items = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            for (int i = 0; i < input.size(); i++) items.set(i, input.getItem(i).copy());
            items.set(baseSlot, ItemStack.EMPTY);
            items.set(materialSlot, input.getItem(materialSlot).copy());
            CraftingInput emptied = CraftingInput.of(input.width(), input.height(), items);
            lensouls$report(player, emptied, "清空原物品后（这才是真实生效的那次查询）");
        } catch (Throwable t) {
            LenSouls.LOGGER.warn("[Reinforce] 剩余物取证失败：{}", t.toString());
        }
    }

    private void lensouls$report(Player player, CraftingInput input, String phase) {
        for (RecipeHolder<CraftingRecipe> holder : player.level().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!holder.value().matches(input, player.level())) continue;
            NonNullList<ItemStack> remaining = holder.value().getRemainingItems(input);
            for (int i = 0; i < remaining.size() && i < input.size(); i++) {
                ItemStack rem = remaining.get(i);
                if (rem.isEmpty()) continue;
                ItemStack slotStack = input.getItem(i);
                LenSouls.LOGGER.warn("[Reinforce] {}：配方 {} 对槽 {} 退回 {} x{}{}",
                        phase, holder.id(), i, rem.getItem(), rem.getCount(),
                        rem == slotStack ? "（与槽内堆是同一实例 → 会让数量翻倍！）" : "（副本）");
            }
        }
    }
}
