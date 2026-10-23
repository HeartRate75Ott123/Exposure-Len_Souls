package com.plumejade.lensouls.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 {@code LivingEntity.dead}（protected 字段，没有公开 getter）。
 * <p>
 * 唯一用途：区分「正常死亡中（{@code dead == true}，死亡界面）」与
 * 「血量为 0 但根本没走死亡流程（{@code dead == false}）」——后者就是玩家报的「假死」：
 * 血量 ≤ 0 时原版 {@code LivingEntity.hurt} 第一行就 {@code if (isDeadOrDying()) return false;}，
 * 于是任何伤害（包括 {@code /kill}、999 真伤）都打不进去，人却活着。
 * <p>
 * 这是 {@code LivingEntity} 上唯一能区分两者的字段，{@code isDeadOrDying()} 把两种情况混在一起了。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDeadAccessor {

    @Accessor("dead")
    boolean lensouls$isDeadFlag();
}
