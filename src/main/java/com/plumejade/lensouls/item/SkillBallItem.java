package com.plumejade.lensouls.item;

import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 能力球物品——右键解锁对应能力。
 * <p>
 * 有两种变体：
 * <ul>
 *   <li><b>随机能力球</b>（{@code specificAbility == null}）：右键从全部能力中随机抽取一个并解锁</li>
 *   <li><b>指定能力球</b>（{@code specificAbility != null}）：右键解锁对应能力</li>
 * </ul>
 * 已解锁时提示"你已经解锁这个能力了！"。
 */
public class SkillBallItem extends Item {

    @Nullable
    private final AbilityType specificAbility;

    /** 随机能力球构造器 */
    public SkillBallItem(Properties properties) {
        super(properties);
        this.specificAbility = null;
    }

    /** 指定能力球构造器 */
    public SkillBallItem(AbilityType specificAbility, Properties properties) {
        super(properties);
        this.specificAbility = specificAbility;
    }

    @Nullable
    public AbilityType getSpecificAbility() {
        return specificAbility;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        // 确定要解锁的能力
        AbilityType ability = specificAbility;
        if (ability == null) {
            // 随机能力球：从全部能力中抽取
            AbilityType[] all = AbilityType.values();
            ability = all[player.getRandom().nextInt(all.length)];
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        Component abilityName = Component.translatable("ability.lensouls." + ability.getId() + ".name");

        if (AbilityManager.getInstance().isUnlocked(player, ability)) {
            // 已解锁 → 黄色提示
            player.sendSystemMessage(
                    Component.translatable("message.lensouls.skill_ball.already_unlocked", abilityName)
                            .copy().withStyle(ChatFormatting.YELLOW));
        } else {
            // 未解锁 → 解锁（setUnlocked 内部会发送首次描述）
            AbilityManager.getInstance().setUnlocked(serverPlayer, ability, true);
        }

        // 消耗物品
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (specificAbility != null) {
            Component abilityName = Component.translatable("ability.lensouls." + specificAbility.getId() + ".name");
            tooltip.add(Component.translatable("message.lensouls.skill_ball.tooltip", abilityName)
                    .copy().withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("message.lensouls.skill_ball.tooltip_random")
                    .copy().withStyle(ChatFormatting.GREEN));
        }
    }
}
