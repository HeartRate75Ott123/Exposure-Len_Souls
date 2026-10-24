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
import com.plumejade.lensouls.handler.ElementActivityMath;
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

    /**
     * OP 门槛。
     * <p>
     * <b>必须挂在每个 OP 子命令上，不能挂在根字面量 {@code lensouls} 上</b>：
     * Brigadier 的 {@code requires} 会被所有子命令继承，挂在根上普通玩家就执行不了
     * {@code /lensouls photos}（图鉴是玩家功能）。
     */
    private static final java.util.function.Predicate<CommandSourceStack> OP =
            source -> source.hasPermission(2);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("lensouls")
                // 玩家功能：无门槛
                .then(Commands.literal("photos").executes(ctx -> openPhotoGallery(ctx)))

        // 测试用：开启/关闭诅咒反转（/lensouls curse reverse <款|all> [true|false]）
        // 省略布尔参数 = true（照旧）；false = 清掉反转位并把该款各条进度归零，方便反复测同一条件。
        .then(Commands.literal("curse").requires(OP)
                .then(Commands.literal("reverse")
                        .then(Commands.argument("feather", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("all", "hardman", "twitcher", "elementrise",
                                                "abyss", "timecore", "finalrite", "zeroforge"), b))
                                .executes(ctx -> setCurseReversed(ctx, true))
                                .then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx -> setCurseReversed(ctx,
                                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on")))))))
                // 攻击链路诊断（命中率 / ⑦ e3 锈契掷骰 / 造成伤害收口，各打一行 [Atk]）
                .then(Commands.literal("debug")
                        .then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
                                    com.plumejade.lensouls.handler.curse.AttackDiag.setEnabled(on);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            on ? "§a[Atk] 攻击诊断已开启（每次攻击打一行日志）"
                                                    : "§7[Atk] 攻击诊断已关闭"), false);
                                    return 1;
                                })))
                // 天数自检：地图口径的绝对天 / 该玩家自己的天（折翼 e3 的窗口按后者算）
                .then(Commands.literal("days").requires(OP)
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            long mapDay = com.plumejade.lensouls.handler.WorldDays.absoluteDay(player.getServer());
                            int elapsed = com.plumejade.lensouls.handler.WorldDays.elapsedDays(player);
                            int window = com.plumejade.lensouls.handler.WorldDays.ABYSS_REVERSE_WINDOW_DAYS;
                            String state = elapsed >= window
                                    ? "§c已过期（无法反转）"
                                    : "§a窗口内（剩余 " + (window - elapsed) + " 天）";
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§e地图第 " + mapDay + " 天 §7| §a你自己第 " + (elapsed + 1)
                                            + " 天§7（已过 " + elapsed + " 天）§7| §b安魂曲反转窗口：" + state), false);
                            return 1;
                        }))
                .then(Commands.literal("skill").requires(OP)
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
                .then(Commands.literal("toughness").requires(OP)
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
                .then(Commands.literal("dimension_gun").requires(OP)
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
                .then(Commands.literal("abyss_calamity_test").requires(OP)
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.plumejade.lensouls.handler.FeatherAbyssHandler.triggerTestCalamity(player);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§a已触发祸之可能性（测试，不污染计时器）"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("gui").requires(OP)
                        .then(Commands.literal("photo_set")
                                .then(Commands.literal("testopen")
                                        .executes(ctx -> setPhotoSetDebug(ctx, true)))
                                .then(Commands.literal("testfalse")
                                        .executes(ctx -> setPhotoSetDebug(ctx, false)))
                        )
                )
                .then(Commands.literal("activity").requires(OP)
                        .then(Commands.literal("state").executes(LensoulsCommand::activityState))
                        .then(Commands.literal("selftest").executes(LensoulsCommand::activitySelftest))
                        .then(Commands.argument("verbose", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = com.mojang.brigadier.arguments.BoolArgumentType
                                            .getBool(ctx, "verbose");
                                    com.plumejade.lensouls.handler.ElementActivityMath.VERBOSE = on;
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "§a元素活性诊断日志：" + (on ? "开" : "关")), true);
                                    return 1;
                                }))
                )
                .then(Commands.literal("dump").requires(OP)
                        .then(Commands.literal("mobs")
                                .executes(LensoulsCommand::dumpMobs)
                        )
                )
                .then(Commands.literal("copysoul").requires(OP)
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

    // ===================== /lensouls photos =====================

    /**
     * {@code /lensouls photos} —— 打开照片图鉴界面（能力窃取照片 / 照片套装）。
     * <p>
     * 命令在服务端执行 ⇒ 直接 {@code openMenu}：客户端收到 {@code ClientboundOpenScreenPacket}
     * 后经 {@code RegisterMenuScreensEvent} 建屏（不需要额外开屏包，与 l2tabs 分页那种纯客户端触发不同）。
     * <p>
     * 开屏前先推一份全量同步，界面 init 里还会再主动拉一次（兜底登录时序 / 断线重连）。
     */
    private static int openPhotoGallery(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c只有玩家可以打开照片图鉴"));
            return 0;
        }
        try {
            com.plumejade.lensouls.photogallery.PhotoGallerySync.send(player);
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.plumejade.lensouls.gui.PhotoGalleryMenu(id, inv),
                    Component.translatable("gui.lensouls.photos.title")));
            return 1;
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[PhotoGallery] 打开照片图鉴失败", t);
            ctx.getSource().sendFailure(Component.literal("§c打开照片图鉴失败，详见日志"));
            return 0;
        }
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

    /**
     * `/lensouls curse reverse <款|all> [true|false]` 的实现。
     * <p>
     * true  = 直接把该款所有条目标记为已反转（测试用）；
     * false = 清掉反转位并**把各条进度归零**（否则目标已达成，下一次任何相关事件都会立刻再反转，
     *         反复测同一条件时很烦）。
     */
    private static int setCurseReversed(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer sp;
        try {
            sp = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c只有玩家可以执行该调试指令"));
            return 0;
        }
        String which = StringArgumentType.getString(ctx, "feather");
        var defs = java.util.List.of(
                com.plumejade.lensouls.feather.CurseDefs.HARDMAN,
                com.plumejade.lensouls.feather.CurseDefs.TWITCHER,
                com.plumejade.lensouls.feather.CurseDefs.ELEMENTRISE,
                com.plumejade.lensouls.feather.CurseDefs.ABYSS,
                com.plumejade.lensouls.feather.CurseDefs.TIMECORE,
                com.plumejade.lensouls.feather.CurseDefs.FINALRITE,
                com.plumejade.lensouls.feather.CurseDefs.ZEROFORGE);
        int done = 0;
        for (var def : defs) {
            if (!which.equals("all") && !def.id().endsWith(which)) continue;
            for (int i = 0; i < def.entries(); i++) {
                if (on) {
                    com.plumejade.lensouls.feather.CurseManager.reverse(sp, def, i);
                } else {
                    com.plumejade.lensouls.feather.CurseManager.unreverse(sp, def, i);
                    com.plumejade.lensouls.feather.CurseManager.setProgress(sp, def, i, 0L);
                }
                done++;
            }
        }
        final int n = done;
        ctx.getSource().sendSuccess(() -> Component.literal(on
                ? "§a已反转 " + n + " 条诅咒"
                : "§7已取消反转并归零进度：" + n + " 条"), true);
        return n;
    }

    /**
     * /lensouls activity state — 逐元素打印：可见等级 / 源等级 / 源剩余 / 应为等级 / 实例时长
     */
    private static int activityState(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c只有玩家可以执行该调试指令"));
            return 0;
        }
        for (String line : com.plumejade.lensouls.handler.ElementActivityMath.describe(player)) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * /lensouls activity selftest — 自己跑一遍施加路径，把结果直接打在聊天框里。
     * <p>
     * 目的：不再靠人去核对图标。这里用**注册表 Holder**（`/effect` 命令走的正是这条）去施加，
     * 覆盖三件事：① 命令路径能不能被钩子看见并记账；② 修整后的等级对不对；
     * ③ 外部清除后会不会被错误地“复活”。
     * <p>
     * ⚠ 会清空执行者的全部药水效果（调试指令，仅 OP）。
     */
    private static int activitySelftest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c只有玩家可以执行该调试指令"));
            return 0;
        }
        var src = ctx.getSource();
        int boost = ElementActivityMath.boost(player);
        int pen = ElementActivityMath.penalty(player);
        boolean block = ElementActivityMath.blocked(player);
        src.sendSuccess(() -> Component.literal(String.format(
                "§b[自检] 开始：boost(③)=%d penalty(①)=%d 禁=%s", boost, pen, block)), false);

        // ── 基线：清空后连续两次 push，应无任何活性、源记录应为 0 ──
        player.removeAllEffects();
        ElementActivityMath.push(player);
        ElementActivityMath.push(player);
        int baseSrc = ElementActivityMath.sourceLevel(player, 0);
        int baseLvl = effectLevel(player);
        report(ctx, "清零基线", baseLvl == 0 && baseSrc == 0,
                String.format("可见=%d 源=%d（应 0/0）", baseLvl, baseSrc));

        // ── A：命令路径（Holder.Reference，正是 /effect give 走的这条） ──
        var holder = BuiltInRegistries.MOB_EFFECT
                .getHolder(ElementActivityMath.INFUSIONS[0].unwrapKey().orElseThrow()).orElse(null);
        if (holder == null) {
            report(ctx, "A 命令路径", false, "取不到注册表 Holder");
        } else {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, 300, 3, false, true, true));
            int afterHook = effectLevel(player);
            int recSrc = ElementActivityMath.sourceLevel(player, 0);
            int expect = block ? 0 : Math.max(0, 4 + boost - pen);
            ElementActivityMath.push(player);
            int afterPush = effectLevel(player);
            boolean pass = recSrc == 4 && afterPush == expect
                    && (expect == 0 || afterHook == expect);
            report(ctx, "A 命令路径（4 级）", pass,
                    String.format("记账源=%d（应 4）钩子后=%d push后=%d（应 %d）", recSrc, afterHook, afterPush, expect));
        }

        // ── B：外部清除（/effect clear、牛奶、死亡、别的模组 remove） ──
        // 先确保存在一份源，再清掉，连续两次 push 都不该复活
        if (holder != null) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, 300, 3, false, true, true));
            ElementActivityMath.push(player);
        }
        player.removeAllEffects();
        ElementActivityMath.push(player);          // 这一下应判定“外部清除→放弃源”
        ElementActivityMath.push(player);          // 这一下不该重建源
        int clearSrc = ElementActivityMath.sourceLevel(player, 0);
        int clearLvl = effectLevel(player);
        int expectClear = block ? 0 : Math.max(0, boost - pen);
        report(ctx, "B 外部清除", clearSrc == 0 && clearLvl == expectClear,
                String.format("源=%d 可见=%d（应 0 / %d）", clearSrc, clearLvl, expectClear));

        // ── C：第一种模式的样子（ambient 蓝框 + 30 秒） ──
        if (expectClear > 0) {
            var inst = player.getEffect(ElementActivityMath.INFUSIONS[0]);
            boolean pass = inst != null && inst.getAmplifier() + 1 == expectClear
                    && inst.isAmbient() && inst.getDuration() >= 580 && inst.getDuration() <= 600;
            report(ctx, "C 自建份外观", pass, inst == null ? "没有实例"
                    : String.format("等级=%d 蓝框=%s 时长=%d（应 %d/true/600）",
                    inst.getAmplifier() + 1, inst.isAmbient(), inst.getDuration(), expectClear));
        } else {
            report(ctx, "C 自建份外观", true, "跳过（③ 未佩戴，应得等级为 0）");
        }

        src.sendSuccess(() -> Component.literal("§b[自检] 结束（效果已被清空，重新戴羽毛会自动恢复）"), false);
        return 1;
    }

    private static int effectLevel(ServerPlayer player) {
        var inst = player.getEffect(ElementActivityMath.INFUSIONS[0]);
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }

    private static void report(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                               String name, boolean pass, String detail) {
        String tag = pass ? "§aPASS" : "§cFAIL";
        LenSouls.LOGGER.info("[ActTest] {} {} — {}", pass ? "PASS" : "FAIL", name, detail);
        ctx.getSource().sendSuccess(() -> Component.literal("§b[自检] " + tag + " §f" + name + " §7" + detail), false);
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
