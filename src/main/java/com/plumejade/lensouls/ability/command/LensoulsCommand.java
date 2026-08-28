package com.plumejade.lensouls.ability.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.boss.BossToughnessAttributes;
import com.plumejade.lensouls.boss.BossToughnessData;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.item.DimensionalGunItem;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
                                                        () -> Component.literal("§a韧性数据已删除"), true);
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("get")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity target = EntityArgument.getEntity(ctx, "target");
                                            if (!(target instanceof LivingEntity le)) {
                                                ctx.getSource().sendFailure(Component.literal("目标不是生物实体"));
                                                return 0;
                                            }
                                            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).toString();
                                            int hits = BossToughnessAttributes.getRequiredHits(le);
                                            int stun = BossToughnessAttributes.getStunDurationTicks(le);
                                            int inv = BossToughnessAttributes.getInvincibleTicks(le);
                                            BossToughnessData data = BossToughnessManager.getInstance().get(le);
                                            if (data != null) {
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "§6" + id + " §7削韧次数: §e" + hits + "§7 定身: §e" + stun + "§7tick 间隔: §e" + inv + "§7tick 当前: §e" + data.getCurrentHits() + "/" + data.getRequiredHits()), false);
                                            } else {
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "§6" + id + " §7削韧次数: §e" + hits + "§7 定身: §e" + stun + "§7tick 间隔: §e" + inv + "§7tick (未注册)"), false);
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("set")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("hits", IntegerArgumentType.integer(1, 100))
                                                .then(Commands.argument("stun", IntegerArgumentType.integer(0, 6000))
                                                        .then(Commands.argument("interval", IntegerArgumentType.integer(1, 600))
                                                                .executes(ctx -> {
                                                                    Entity target = EntityArgument.getEntity(ctx, "target");
                                                                    if (!(target instanceof LivingEntity le)) {
                                                                        ctx.getSource().sendFailure(Component.literal("目标不是生物实体"));
                                                                        return 0;
                                                                    }
                                                                    int h = IntegerArgumentType.getInteger(ctx, "hits");
                                                                    int s = IntegerArgumentType.getInteger(ctx, "stun");
                                                                    int i = IntegerArgumentType.getInteger(ctx, "interval");
                                                                    String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).toString();
                                                                    BossToughnessAttributes.put(id, new BossToughnessAttributes.ToughnessConfig(h, s, i));
                                                                    // 如果有活跃韧性数据，更新 requiredHits
                                                                    BossToughnessData data = BossToughnessManager.getInstance().get(le);
                                                                    if (data != null) {
                                                                        // 通过反射更新私有字段
                                                                        try {
                                                                            var reqField = BossToughnessData.class.getDeclaredField("requiredHits");
                                                                            reqField.setAccessible(true);
                                                                            reqField.setInt(data, h);
                                                                            var stunField = BossToughnessData.class.getDeclaredField("stunRemainingTicks");
                                                                            stunField.setAccessible(true);
                                                                            int currentStun = stunField.getInt(data);
                                                                            if (s > currentStun) stunField.setInt(data, s);
                                                                            var invField = BossToughnessData.class.getDeclaredField("invincibleTicks");
                                                                            invField.setAccessible(true);
                                                                            invField.setInt(data, 0);
                                                                        } catch (Exception ignored) {}
                                                                        com.plumejade.lensouls.boss.BossToughnessManager.getInstance().broadcastToughness(le);
                                                                    }
                                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                                            "§a设置成功 §7削韧:§e" + h + "§7 定身:§e" + s + "§7tick 间隔:§e" + i + "§7tick"), true);
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
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
                .then(Commands.literal("abyss_calamity_test")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.plumejade.lensouls.handler.FeatherAbyssHandler.triggerTestCalamity(player);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§a已触发祸之可能性（测试，不污染计时器）"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("dump")
                        .then(Commands.literal("mobs")
                                .executes(LensoulsCommand::dumpMobs)
                        )
                )
        );

    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * /lensouls dump mobs — 扫描实体注册表，把所有继承 {@link Mob} 的实体按 namespace
     * 输出到游戏目录 dump/lensoulsmobdump/ 下的 <namespace>.json（如 minecraft:zombie 格式）。
     */
    private static int dumpMobs(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var server = source.getServer();
        Path outDir = server.getServerDirectory().resolve("dump").resolve("lensoulsmobdump");

        Map<String, List<String>> byNamespace = new TreeMap<>();
        Registry<EntityType<?>> registry = BuiltInRegistries.ENTITY_TYPE;
        for (EntityType<?> type : registry) {
            ResourceLocation key = registry.getKey(type);
            if (key == null) continue;
            try {
                if (type.create(server.overworld()) instanceof Mob) {
                    byNamespace.computeIfAbsent(key.getNamespace(), k -> new ArrayList<>()).add(key.toString());
                }
            } catch (Exception ignored) {
                // 个别实体无法在服务端构造，跳过
            }
        }

        int total = 0;
        try {
            Files.createDirectories(outDir);
            for (Map.Entry<String, List<String>> entry : byNamespace.entrySet()) {
                List<String> ids = entry.getValue();
                ids.sort(String::compareTo);
                Files.writeString(outDir.resolve(entry.getKey() + ".json"), GSON.toJson(ids));
                total += ids.size();
            }
        } catch (IOException e) {
            source.sendFailure(Component.literal("§c写入失败: " + e.getMessage()));
            return 0;
        }

        final int totalMobs = total;
        source.sendSuccess(() -> Component.literal(
                "§a已输出 §e" + totalMobs + " §a个 mob 实体到 §e" + outDir.toAbsolutePath()), false);
        return totalMobs;
    }
}
