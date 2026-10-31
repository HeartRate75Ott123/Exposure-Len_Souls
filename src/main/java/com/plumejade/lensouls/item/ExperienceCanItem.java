package com.plumejade.lensouls.item;

import com.plumejade.lensouls.entity.Level2StaffBossEntity;
import com.plumejade.lensouls.entity.ModEntities;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 经验罐头：手持右键播放开罐音、消耗 1 个，并召唤一只 N公司2级员工（{@code level2_staff_boss}）。
 * <p>
 * 召唤位点与下面这条命令**逐字对齐**（用户指定）：
 * <pre>
 * /execute as @p at @s anchored feet positioned ~ ~ ~ rotated ~ 0 run summon lensouls:level2_staff_boss ^ ^ ^2
 * </pre>
 * 即：以玩家<b>脚部</b>坐标为原点、用「仅偏航（俯仰压成 0）」的水平朝向向前 **2 格**，
 * 实体偏航与玩家一致（{@code rotated ~ 0} 的 summon 就是取源头朝向，不额外转身）。
 * 刻意<b>不做</b>「卡墙就换近一点的位置」的兜底搜索——那会和命令结果不一致；墙里/一格缝隙里
 * 也照样按命令生成（和命令一样，可能卡在方块里）。
 * <p>
 * 开罐音走客户端本地播放：自定义音效在服务端 {@code playSound} 不可靠是既有踩坑，
 * 项目惯例（次元瓶等）都是在 {@code use} 的客户端分支里播。
 */
public class ExperienceCanItem extends Item {

    /** 召唤距离（格，水平）：命令里的 {@code ^ ^ ^2} */
    public static final double SUMMON_DISTANCE = 2.0D;

    public ExperienceCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // 开罐音（客户端本地，避免服务端播自定义音效丢音）
            player.playSound(ModSounds.EXPERIENCE_CAN_OPEN.get(), 1.0F, 1.0F);
            return InteractionResultHolder.success(stack);
        }

        if (!summon((ServerLevel) level, player)) {
            return InteractionResultHolder.fail(stack);
        }
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    /** 按 {@code ^ ^ ^2} 的口径召唤一只 N公司2级员工；实体创建失败才返回 false */
    private static boolean summon(ServerLevel level, Player player) {
        Level2StaffBossEntity boss = ModEntities.LEVEL2_STAFF_BOSS.get().create(level);
        if (boss == null) return false;

        // anchored feet（玩家 position() 就是脚部）+ rotated ~ 0（只取偏航，俯仰压平）→ 水平前进 2 格
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 pos = player.position().add(forward.scale(SUMMON_DISTANCE));
        boss.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
        boss.setYHeadRot(player.getYRot());
        level.addFreshEntity(boss);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.experience_can.desc1"));
        tooltip.add(Component.translatable("item.lensouls.experience_can.desc2"));
        tooltip.add(Component.translatable("item.lensouls.experience_can.desc3"));
        tooltip.add(Component.translatable("item.lensouls.experience_can.desc4"));
    }
}
