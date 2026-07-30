package com.plumejade.lensouls.ability.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.item.DimensionalGunItem;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * /lensouls skill <id> <true/false> — 修改能力的解锁状态。
 * <p>
 * 需要 OP 权限。false→true 时触发首次描述播报。
 * true→false 且该能力当前启用时，自动回退到下一个已解锁能力。
 */
public class LensoulsCommand {

    private static final DynamicCommandExceptionType ERROR_INVALID_SKILL =
            new DynamicCommandExceptionType(id ->
                    Component.translatable("command.lensouls.skill.invalid", id));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("lensouls")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("skill")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(AbilityType.values()).map(AbilityType::getId),
                                        builder))
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            AbilityType type = AbilityType.byId(id);
                                            if (type == null) throw ERROR_INVALID_SKILL.create(id);

                                            boolean value = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value");
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();

                                            AbilityManager.getInstance().setUnlocked(player, type, value);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.translatable("command.lensouls.skill.set",
                                                            type.getId(), value), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("toughness")
                        .then(Commands.literal("register")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                            if (target instanceof LivingEntity le) {
                                                BossToughnessManager mgr = BossToughnessManager.getInstance();
                                                if (mgr.has(le)) {
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("该实体已有韧性数据"), false);
                                                } else {
                                                    mgr.register(le);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("BOSS 韧性已注册"), true);
                                                }
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("目标不是生物实体"));
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("hit")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                            if (target instanceof LivingEntity le) {
                                                BossToughnessManager mgr = BossToughnessManager.getInstance();
                                                if (!mgr.has(le)) {
                                                    mgr.register(le);
                                                }
                                                mgr.hit(le);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("韧性 -1"), true);
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("reset")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                            if (target instanceof LivingEntity le) {
                                                BossToughnessManager.getInstance().remove(le);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("韧性数据已删除"), true);
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("dimension_gun")
                        .then(Commands.argument("kills", IntegerArgumentType.integer(0, 10000))
                                .executes(ctx -> {
                                    int kills = IntegerArgumentType.getInteger(ctx, "kills");
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ItemStack held = player.getMainHandItem();
                                    if (!held.is(ModItems.DIMENSIONAL_GUN.get())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("主手未持有次元枪"));
                                        return 0;
                                    }
                                    DimensionalGunItem gun = (DimensionalGunItem) held.getItem();
                                    gun.setKills(held, kills);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("次元枪击杀数已设为 " + kills), true);
                                    return 1;
                                })
                        )
                )
        );

    }
}
