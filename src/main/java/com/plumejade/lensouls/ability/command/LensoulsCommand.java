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
import com.plumejade.lensouls.config.CopySoulFilter;
import com.plumejade.lensouls.item.DimensionalGunItem;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                .then(Commands.literal("gui")
                        .then(Commands.literal("photo_set")
                                .then(Commands.literal("testopen")
                                        .executes(ctx -> setPhotoSetDebug(ctx, true)))
                                .then(Commands.literal("testfalse")
                                        .executes(ctx -> setPhotoSetDebug(ctx, false)))
                        )
                )
                .then(Commands.literal("dump")
                        .then(Commands.literal("mobs")
                                .executes(LensoulsCommand::dumpMobs)
                        )
                )
                .then(Commands.literal("copysoul")
                        .then(Commands.literal("tags")
                                .executes(ctx -> copySoulTags(ctx))
                        )
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .executes(ctx -> copySoulCheck(ctx, StringArgumentType.getString(ctx, "target")))
                        )
                        .executes(ctx -> copySoulCheck(ctx, ""))
                )
        );

    }

    // ===================== /lensouls copysoul =====================

    /**
     * {@code /lensouls copysoul [<物品ID>|#<标签>|all|held]}（无参 = 主手物品）
     * <p>
     * 打印复制之魂复制名单对目标的最终判定、双方各自命中的条目与**决定胜负的那一条规则**，
     * 用来核验「具体&gt;标签&gt;all，同级黑名单胜」的合并语义。
     * {@code /lensouls copysoul tags [命名空间]} 列出可用的物品标签（带成员数与所属命名空间）。
     */
    private static int copySoulCheck(CommandContext<CommandSourceStack> ctx, String raw) {
        var source = ctx.getSource();
        Registry<Item> registry = BuiltInRegistries.ITEM;
        String arg = raw.trim();
        StringBuilder head = new StringBuilder();
        ResourceLocation id;

        if (arg.isEmpty() || arg.equalsIgnoreCase("held")) {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("§c控制台执行请显式给出物品 ID，例如 §e/lensouls copysoul minecraft:oak_sapling"));
                return 0;
            }
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                source.sendFailure(Component.literal("§c主手是空的：请手持物品，或显式给出物品 ID"));
                return 0;
            }
            Item item = held.getItem();
            id = registry.getKey(item);
            if (id == null) {
                source.sendFailure(Component.literal("§c主手物品未注册，无法判定"));
                return 0;
            }
            head.append("§7主手：§f").append(id).append(" §7×").append(held.getCount()).append('\n');
        } else if (arg.startsWith("#")) {
            // 标签自省：列出索引里该标签的成员，并对照注册表绑定表，暴露「运行时补标签」的差异
            ResourceLocation tagId = ResourceLocation.tryParse(arg.substring(1));
            if (tagId == null) {
                source.sendFailure(Component.literal("§c标签名不合法：" + arg));
                return 0;
            }
            CopySoulFilter.TagDiagnostic diag = CopySoulFilter.diagnoseItemTag(tagId);
            List<String> members = new ArrayList<>();
            for (ResourceLocation m : diag.indexedMembers()) members.add(m.toString());
            final String head2 = "§e" + arg + "§7：索引内成员 §f" + members.size()
                    + " §7个，注册表绑定表 §f" + diag.registryMembers() + " §7个｜整包物品标签 §f"
                    + diag.totalIndexedTags() + " §7个";
            source.sendSuccess(() -> Component.literal(head2), false);
            if (diag.registryMembers() != members.size()) {
                source.sendSuccess(() -> Component.literal(
                        "§7两列不一致：可能是模组在运行时直接写进物品 Holder（如 gytrinket），"
                                + "也可能只是「只挂子标签、没有直接成员」。§a本名单一律认索引那一列"
                                + "§7（与 §f物品.is(标签)§7 同源）"), false);
            }
            if (members.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§7该标签当前没有任何成员。"), false);
                return 0;
            }
            source.sendSuccess(() -> Component.literal(String.join("§7, §f", members)), false);
            return members.size();
        } else {
            id = ResourceLocation.tryParse(arg);
            if (id == null || !registry.containsKey(id)) {
                source.sendFailure(Component.literal("§c物品 ID 不存在：§e" + arg));
                return 0;
            }
            head.append("§7目标：§f").append(id).append('\n');
        }

        CopySoulFilter.Decision d = CopySoulFilter.describeCopyDecision(registry, id);
        final String report = head
                + "§7白名单命中：" + (d.whitelistBest() == null ? "§8无" : "§a" + d.whitelistBest())
                + "  §8[具体度 " + rankText(d.whitelistRank()) + "]\n"
                + "§7黑名单命中：" + (d.blacklistBest() == null ? "§8无" : "§c" + d.blacklistBest())
                + "  §8[具体度 " + rankText(d.blacklistRank()) + "]\n"
                + "§7裁决：" + d.matchedRule() + "\n"
                + (d.allowed() ? "§a✔ 允许复制" : "§c✘ 禁止复制");
        source.sendSuccess(() -> Component.literal(report), false);
        return d.allowed() ? 1 : 0;
    }

    private static String rankText(int rank) {
        if (rank == Integer.MIN_VALUE) return "未命中";
        if (rank == 1_000_000) return "点名";
        return String.valueOf(rank);
    }

    /** {@code /lensouls copysoul tags}：列出索引里的物品标签与成员数 */
    private static int copySoulTags(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        Map<String, Integer> tags;
        try {
            tags = CopySoulFilter.indexedItemTags();
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c枚举标签失败：" + e));
            return 0;
        }
        if (tags.isEmpty()) {
            source.sendFailure(Component.literal("§7索引里没有物品标签"));
            return 0;
        }
        Map<String, List<String>> byNamespace = new TreeMap<>();
        for (Map.Entry<String, Integer> e : tags.entrySet()) {
            int colon = e.getKey().indexOf(':');
            String ns = colon < 0 ? "?" : e.getKey().substring(0, colon);
            byNamespace.computeIfAbsent(ns, k -> new ArrayList<>())
                    .add("#" + e.getKey() + " §8(" + e.getValue() + ")");
        }
        final int count = tags.size();
        source.sendSuccess(() -> Component.literal("§7索引内物品标签共 §f" + count
                + " §7个（含模组运行时给物品补的标签），写进名单时前缀 §e#§7；括号内为成员数："), false);
        for (Map.Entry<String, List<String>> e : byNamespace.entrySet()) {
            List<String> list = e.getValue();
            list.sort(String::compareTo);
            final String line = "§e" + e.getKey() + "§7：§f" + String.join("§7, §f", list);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return count;
    }

    /**
     * {@code /lensouls gui photo_set testopen|testfalse}：照片套装面板排版调试开关。
     * <p>
     * 只对执行指令的玩家生效（S2C 单发）：开启后「照片效果」面板会额外塞入人造长文本，
     * 用来检验分页是否按面板实际高度切页、超长套装块能否被拆页、超长单行是否被裁切。
     */
    private static int setPhotoSetDebug(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                        boolean enable) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c只有玩家可以执行该调试指令"));
            return 0;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new com.plumejade.lensouls.network.PhotoSetDebugPacket(enable));
        ctx.getSource().sendSuccess(() -> Component.literal(enable
                ? "§a照片套装面板排版调试已开启（打开背包「照片效果」页查看长文本分页）"
                : "§7照片套装面板排版调试已关闭"), true);
        return 1;
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
