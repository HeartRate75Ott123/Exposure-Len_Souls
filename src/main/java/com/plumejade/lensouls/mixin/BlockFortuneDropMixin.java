package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.curse.CurseLootBonus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ⑤ e4 徒劳 反转态「时运 +2」：把**战利品上下文里的工具**换成带时运 II 的副本。
 * <p>
 * 原版方块掉落是「{@code Block#getDrops(state, level, pos, be, entity, tool)} 里把 tool 塞进
 * {@code LootContextParams.TOOL} → 战利品表里的 {@code ApplyBonusCount} 读工具上的时运等级」，
 * 所以只要在这一步换掉 tool，之后完全走原版公式：<b>写了时运的方块才加产量，没写的（石头、泥土…）
 * 依旧掉 1 个</b>。这修掉的正是「把已有掉落物复制一份」造成的一刀切加产量。
 * <p>
 * 注入点选 {@code dropResources(...)} 内部的 {@code getDrops} 调用（而不是 {@code getDrops} 自己），
 * 因为只有这里能同时拿到<b>破坏者实体</b>（判断戴没戴诅咒）与<b>工具</b>；
 * 而且它是所有「玩家挖方块」路径的收口（原版 {@code playerDestroy} 以及自己挖的模组方块都走它）。
 * 空手 / 没戴诅咒 / 没反转时原样返回，不产生任何副本。
 */
@Mixin(Block.class)
public class BlockFortuneDropMixin {

    @Redirect(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops("
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private static List<ItemStack> lensouls$fortuneToolForDrops(BlockState state, ServerLevel level,
                                                               BlockPos pos, @Nullable BlockEntity blockEntity,
                                                               @Nullable Entity entity, ItemStack tool) {
        return Block.getDrops(state, level, pos, blockEntity, entity,
                CurseLootBonus.fortuneTool(tool, entity, level));
    }
}
