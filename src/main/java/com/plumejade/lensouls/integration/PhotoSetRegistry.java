package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.PhotoSetDefs;
import com.plumejade.lensouls.config.PhotoSetLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.item.PhotoAlbumItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 套装查询与 tooltip 辅助。结合成员归属（{@link PhotoSetLoader}）与定义（{@link PhotoSetDefs}）。
 */
public class PhotoSetRegistry {

    /** 某实体所属套装 id 列表 */
    public static List<String> getSets(String entityId) {
        try {
            return PhotoSetLoader.getSets(ResourceLocation.parse(entityId));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 计算玩家当前「满足张数要求」的所有档位（含 when 变体的全部档位；无 when 的套装只取最高档）。
     * 档位内的条件（when / cond:）由 {@link PhotoSetEffects#applyPlan} 每 tick 求值，故此处不做时间相关过滤。
     */
    /**
     * 计算玩家当前「满足张数要求」的所有档位（含 when 变体的全部档位；无 when 的套装只取最高档）。
     * 档位内的条件（when / cond:）由 {@link PhotoSetEffects#applyPlan} 每 tick 求值，故此处不做时间相关过滤。
     */
    public record ActiveSet(String setId, PhotoSetDefs.Tier tier) {}

    public static List<ActiveSet> getActiveSets(Player player, List<String> gear, int bossCount) {
        Map<String, Integer> counts = new HashMap<>();
        for (String id : gear) {
            for (String setId : getSets(id)) {
                if ("boss_barrage".equals(setId)) continue; // 首领套由照片的 Boss 标记驱动，不依赖手工清单
                counts.merge(setId, 1, Integer::sum);
            }
        }
        if (bossCount > 0) counts.merge("boss_barrage", bossCount, Integer::sum);
        List<ActiveSet> result = new ArrayList<>();
        Map<String, List<PhotoSetDefs.Tier>> bySet = new HashMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            PhotoSetDefs.SetDef def = PhotoSetDefs.get(e.getKey());
            if (def == null) continue;
            for (PhotoSetDefs.Tier t : def.tiers()) {
                if (e.getValue() >= PhotoSetDefs.effectiveCount(e.getKey(), t)) {
                    bySet.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(t);
                }
            }
        }
        for (Map.Entry<String, List<PhotoSetDefs.Tier>> e : bySet.entrySet()) {
            List<PhotoSetDefs.Tier> qs = e.getValue();
            boolean hasWhen = false;
            for (PhotoSetDefs.Tier t : qs) {
                if (t.when() != null) { hasWhen = true; break; }
            }
            if (hasWhen) {
                for (PhotoSetDefs.Tier t : qs) result.add(new ActiveSet(e.getKey(), t));
            } else {
                PhotoSetDefs.Tier best = null;
                for (PhotoSetDefs.Tier t : qs) {
                    if (best == null || t.count() > best.count()) best = t;
                }
                if (best != null) result.add(new ActiveSet(e.getKey(), best));
            }
        }
        return result;
    }

    public static List<PhotoSetDefs.Tier> getActiveTiers(Player player, List<String> gear, int bossCount) {
        return getActiveSets(player, gear, bossCount).stream().map(ActiveSet::tier).toList();
    }

    /** 单个档位 → 逐行带配色 Component（供背包照片效果页渲染），处理 when / cond: 前缀 */
    public static List<Component> formatTier(PhotoSetDefs.Tier t) {
        List<Component> out = new ArrayList<>();
        String whenPrefix = t.when() != null ? "§e" + condCn(t.when()) + "：" : "";
        for (String eff : t.effects()) {
            Component line = effectLine(eff);
            if (!whenPrefix.isEmpty()) line = Component.literal(whenPrefix).append(line);
            out.add(line);
        }
        return out;
    }

    /** 目标实体是否属于某套装（用于 dmg_mod 判定） */
    public static boolean isInSet(Entity entity, String setId) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null) return false;
        return PhotoSetLoader.getSets(id).contains(setId);
    }

    /** 反查某套装包含的实体 id 列表（tooltip 表头用） */
    public static List<String> getMembers(String setId) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<String>> e : PhotoSetLoader.getAll().entrySet()) {
            if (e.getValue().contains(setId)) out.add(e.getKey().toString());
        }
        return out;
    }

    /**
     * 收集玩家「已安装」的照片主体实体 id（Curios 照片栏 + 相册内容物，按实体去重）。
     * <p>
     * 与「照片效果」面板同口径（相册里收着的照片也算已装）。原实现是
     * {@code client.tabs.PhotoSetClient#collectGearEntities} 的方法体，为了让 common 侧的
     * tooltip（{@link #appendTooltip}）复用同一份逻辑而搬到这里，客户端那边改为转发——
     * <b>不要再写第二份</b>，否则 tooltip 与面板的「已装」口径会漂移。
     */
    public static List<String> collectInstalledEntities(Player player) {
        List<String> ids = new ArrayList<>();
        if (player == null) return ids;
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                for (var stacksHandler : handler.getCurios().values()) {
                    IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                    for (int i = 0; i < stackHandler.getSlots(); i++) {
                        ItemStack stack = stackHandler.getStackInSlot(i);
                        if (stack.isEmpty()) continue;
                        if (stack.getItem() instanceof PhotoAlbumItem) {
                            ItemContainerContents contents =
                                    stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                            for (ItemStack photo : contents.nonEmptyItems()) addPhotoEntity(ids, photo);
                            continue;
                        }
                        addPhotoEntity(ids, stack);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
        return ids;
    }

    private static void addPhotoEntity(List<String> ids, ItemStack photo) {
        String id = PhotographEffectRegistry.getStolenEntity(photo);
        if (id == null) id = PhotographEffectRegistry.getElementEntity(photo);
        if (id == null) return;
        String n = norm(id);
        if (!ids.contains(n)) ids.add(n);
    }

    /** 玩家已安装的 Boss 照片<b>种类</b>数（同 Boss 多张只算一次，与首领套张数统计同口径） */
    public static int countInstalledBossPhotos(Player player) {
        if (player == null) return 0;
        Set<String> seen = new HashSet<>();
        int n = 0;
        try {
            var handlerOpt = CuriosApi.getCuriosInventory(player);
            if (handlerOpt.isEmpty()) return 0;
            for (var sh : handlerOpt.get().getCurios().values()) {
                IDynamicStackHandler stacks = sh.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    if (stack.getItem() instanceof PhotoAlbumItem) {
                        ItemContainerContents contents =
                                stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                        for (ItemStack photo : contents.nonEmptyItems()) {
                            if (bossPhotoSeen(photo, seen)) n++;
                        }
                    } else if (bossPhotoSeen(stack, seen)) {
                        n++;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    private static boolean bossPhotoSeen(ItemStack stack, Set<String> seen) {
        if (!PhotographEffectRegistry.isBossPhoto(stack)) return false;
        String ent = PhotographEffectRegistry.getPhotoEntity(stack);
        return ent != null ? seen.add(norm(ent)) : true;
    }

    /** 实体 id 归一化（补默认命名空间 / 统一写法），供「成员清单 ↔ 已安装照片」比对 */
    private static String norm(String id) {
        if (id == null) return "";
        try {
            return ResourceLocation.parse(id).toString();
        } catch (Exception e) {
            return id;
        }
    }

    /** 在照片 tooltip 中追加套装归属（含四元素抑制顶部块、译名表头、逐行配色） */
    public static void appendTooltip(ItemTooltipEvent event, String entityId) {
        Set<String> setIds = new LinkedHashSet<>(getSets(entityId));
        ItemStack stack = event.getItemStack();
        if (stack != null && PhotographEffectRegistry.isBossPhoto(stack)) {
            setIds.add("boss_barrage");
        }

        boolean shift = false;
        try {
            shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        } catch (Exception ignored) {}

        // 四元素抑制顶部块（仅当本照片含活性时提示该元素规则；独立于套装归属，先显示）
        try {
            var id = ResourceLocation.parse(entityId);
            ElementDamage elem = AttackerElementLoader.getElement(id);
            if (elem != null) {
                int lvl = AttackerElementLoader.getLevel(id, elem);
                if (lvl > 0) {
                    String name = elementCn(elem);
                    event.getToolTip().add(Component.literal("§5集齐任意两张" + name + "活性照片，你造成伤害后有概率触发"
                            + name + "元素抑制；每增两张该元素照片，抑制效果等级+1").withStyle(ChatFormatting.DARK_PURPLE));
                }
            }
        } catch (Exception ignored) {}

        if (setIds.isEmpty()) return;

        event.getToolTip().add(Component.literal("§6套装：").withStyle(ChatFormatting.GOLD));
        for (String setId : setIds) {
            PhotoSetDefs.SetDef def = PhotoSetDefs.get(setId);
            if (def == null) continue;
            event.getToolTip().add(Component.literal("§a[ " + def.name() + " ]").withStyle(ChatFormatting.GREEN));
            if (!shift) continue;
            // 成员译名表头（首领套按实际张数显示）+ 动态识别：已安装的成员名绿色、未安装灰色，行尾给进度
            List<String> members = getMembers(setId);
            Player viewer = event.getEntity();
            Set<String> owned = Set.of();
            boolean dynamic = false;
            try {
                if (viewer != null) {
                    owned = new HashSet<>(collectInstalledEntities(viewer));
                    dynamic = true;
                }
            } catch (Throwable ignored) {
            }
            // 严格口径（1.5.20 用户纠偏）：染色与「已装 X/N」都只用**真实已装**
            // （Curios 照片栏 + 相册内容物）。**不要**退回「正在悬停的这张也算已装」的预览写法
            // （1.5.7 曾这么写）——那会让 JEI / 背包里任何一张还没装的照片，自己那一格永远点绿，
            // 等于谎报已装；JEI 那条路径 `getEntity()` 常常是 null，连进度行都不显示，绿得更没道理。
            // 张数走 effectiveCount：没写 count 的套装 = 动态取成员数（首领套另有口径）
            int need = def.tiers().stream().mapToInt(t -> PhotoSetDefs.effectiveCount(setId, t)).min().orElse(1);
            MutableComponent head = Component.literal("  ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("集齐 ").withStyle(ChatFormatting.GRAY));
            if (setId.equals("boss_barrage")) {
                need = def.tiers().stream().mapToInt(t -> PhotoSetDefs.effectiveCount(setId, t)).max().orElse(1);
                head.append(Component.literal(need + " 张首领").withStyle(ChatFormatting.GRAY));
            } else {
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) head.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY));
                    String memberId = members.get(i);
                    head.append(Component.literal(entityName(memberId))
                            .withStyle(owned.contains(norm(memberId)) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                }
            }
            head.append(Component.literal(" 照片，触发效果").withStyle(ChatFormatting.GRAY));
            if (dynamic) {
                int have;
                if (setId.equals("boss_barrage")) {
                    have = countInstalledBossPhotos(viewer);
                } else {
                    have = 0;
                    for (String memberId : members) {
                        if (owned.contains(norm(memberId))) have++;
                    }
                }
                have = Math.min(have, need);
                head.append(Component.literal(" 已装 " + have + "/" + need)
                        .withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            }
            event.getToolTip().add(head);
            for (PhotoSetDefs.Tier t : def.tiers()) {
                String whenPrefix = t.when() != null ? "§e" + condCn(t.when()) + "：" : "";
                for (String eff : t.effects()) {
                    Component line = effectLine(eff);
                    if (!whenPrefix.isEmpty()) line = Component.literal(whenPrefix).append(line);
                    event.getToolTip().add(Component.literal("  ").append(line));
                }
            }
        }
        if (!shift) {
            event.getToolTip().add(Component.literal("§7（Shift 查看套装效果）").withStyle(ChatFormatting.GRAY));
        }
    }

    /** 实体 id → 译名（取官方 entity 描述） */
    private static String entityName(String id) {
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (et != null) return et.getDescription().getString();
            return Component.translatable("entity." + id.replace(':', '.')).getString();
        } catch (Exception e) {
            return id;
        }
    }

    /** 单个效果 token → 带配色的 Component（处理 cond: 前缀） */
    private static Component effectLine(String token) {
        String cond = null;
        String inner = token;
        if (token.startsWith("cond:")) {
            int bar = token.indexOf('|');
            if (bar >= 0) {
                cond = token.substring(5, bar);
                inner = token.substring(bar + 1);
            }
        }
        ChatFormatting color = effectColor(inner);
        Component base = Component.literal(effectText(inner)).withStyle(color);
        if (cond != null) {
            return Component.literal("§e" + condCn(cond) + "：").append(base);
        }
        return base;
    }

    /** 效果中文文本 */
    private static String effectText(String inner) {
        String[] p = inner.split(":");
        if (p.length == 0) return inner;
        // 兼容 defs 里 immune_fire / immune_explosion（无冒号）写法
        if (p[0].startsWith("immune_")) {
            String flag = p[0].substring("immune_".length());
            return "你免疫" + switch (flag) {
                case "fire" -> "火焰";
                case "explosion" -> "爆炸";
                case "poison" -> "中毒";
                case "wither" -> "凋零";
                case "projectile" -> "抛射物";
                case "fall" -> "摔落";
                default -> flag;
            } + "伤害";
        }
        try {
            return switch (p[0]) {
                case "maxhp" -> "最大生命值 +" + pct(Double.parseDouble(p[1]));
                case "speed" -> "移动速度 +" + pct(Double.parseDouble(p[1]));
                case "armor" -> "护甲值 +" + pct(Double.parseDouble(p[1]));
                case "kb_resist" -> "击退抗性 +" + pct(Double.parseDouble(p[1]));
                case "elem_activity" -> elementCn(ElementDamage.byName(p[1])) + "元素活性 +" + p[2];
                case "immune" -> "你免疫" + switch (p[1]) {
                    case "fire" -> "火焰";
                    case "explosion" -> "爆炸";
                    case "poison" -> "中毒";
                    case "wither" -> "凋零";
                    case "projectile" -> "抛射物";
                    case "fall" -> "摔落";
                    default -> p[1];
                } + "伤害";
                case "env" -> switch (p[1]) {
                    case "water_breath" -> "赋予水下呼吸效果";
                    case "swim" -> "赋予海豚恩典效果";
                    case "dark_invis" -> "赋予隐身效果";
                    case "flight" -> "获得创造飞行（飞行时受到伤害增加）";
                    default -> "环境效果:" + p[1];
                };
                case "death_revive" -> p[1] + " 次不死图腾机会";
                case "dodge" -> "受击 " + pct(Double.parseDouble(p[1])) + " 概率闪避这次伤害";
                case "on_hit_effect" -> "造成伤害时 " + pct(Double.parseDouble(p[2])) + " 概率附加"
                        + effectNameCn(p[1]) + ampCn(Integer.parseInt(p[4])) + "效果";
                case "on_hit_suppress" -> "造成伤害时 " + pct(Double.parseDouble(p[2])) + " 概率施加"
                        + elementCn(ElementDamage.byName(p[1])) + "元素抑制";
                case "dmg_mod" -> "boss_barrage".equals(p[1])
                        ? "对 200 血以上的生物造成伤害 +" + pct(Double.parseDouble(p[2]) - 1.0)
                        : "对套装所需的这几种生物造成伤害 +" + pct(Double.parseDouble(p[2]) - 1.0);
                case "dmg_taken" -> "boss_barrage".equals(p[1])
                        ? "受到 200 血以上的生物伤害 -" + pct(1.0 - Double.parseDouble(p[2]))
                        : "受到套装所需的这几种生物伤害 -" + pct(1.0 - Double.parseDouble(p[2]));
                case "infusion_boost" -> "元素活性等级 +" + p[1];
                case "speed_mult" -> "移动速度 ×" + p[1] + "（独立乘区）";
                case "dmg_element" -> "你造成的" + elementKindCn(p[1]) + "伤害 ×" + p[2];
                case "dot_mult" -> elementCn(ElementDamage.byName(p[1])) + "元素 DoT 伤害 ×" + p[2];
                case "convert_eff" -> "获得" + effectNameCn(p[1]) + "时转为" + effectNameCn(p[2]) + "效果";
                case "convert_heal" -> "获得" + effectNameCn(p[1]) + "时回复 " + p[2] + " 点生命";
                case "convert_buff" -> "获得" + effectNameCn(p[1]) + "时获得 "
                        + pct(Double.parseDouble(p[2])) + " 增伤（" + p[3] + " 秒）";
                case "barrage_trigger" -> "弹幕额外触发 " + p[1] + " 次";
                case "barrage_dmg" -> "弹幕伤害 ×" + p[1];
                default -> inner;
            };
        } catch (Exception e) {
            return inner;
        }
    }

    /**
     * 效果行配色按「对玩家自己有利 / 有害」判定：§a 有利、§7 中性。
     * 「进攻类」词条（造成的元素伤害、元素 DoT、弹幕强化、给敌人挂负面/元素抑制、
     * 对特定生物增伤、受到它们伤害减少）全部是<b>玩家打出去或吃到的增益</b>，因此统一绿色；
     * §c 只留给真正对玩家有害的条目。§9 机动/增益与 §5 转化仍是分类色，不参与利弊判定。
     */
    private static ChatFormatting effectColor(String inner) {
        String[] p = inner.split(":");
        if (p[0].startsWith("immune_")) return ChatFormatting.GREEN;
        return switch (p[0]) {
            case "dmg_mod", "dmg_taken", "barrage_trigger", "barrage_dmg", "on_hit_effect", "on_hit_suppress",
                 "dmg_element", "dot_mult" -> ChatFormatting.GREEN;
            case "maxhp", "armor", "kb_resist", "immune", "dodge", "death_revive" -> ChatFormatting.GREEN;
            case "speed", "speed_mult", "env", "infusion_boost", "elem_activity" -> ChatFormatting.BLUE;
            case "convert_eff", "convert_heal", "convert_buff" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.GRAY;
        };
    }

    private static String pct(double x) {
        return ((int) Math.round(x * 100)) + "%";
    }

    /** dmg_element 的四类伤害中文名 */
    private static String elementKindCn(String kind) {
        return switch (kind) {
            case "fire" -> "火焰";
            case "freeze" -> "冰冻";
            case "wither" -> "凋零";
            case "poison" -> "中毒";
            default -> kind;
        };
    }

    private static String ampCn(int amp) {
        return amp <= 0 ? "" : (amp + 1) + "";
    }

    /** 条件中文 */
    private static String condCn(String c) {
        String[] kv = c.split(":");
        return switch (kv[0]) {
            case "night" -> "夜间";
            case "day" -> "白昼";
            case "water" -> "水中";
            case "fire" -> "燃烧时";
            case "flying" -> "飞行时";
            case "armor_gt" -> "护甲>" + kv[1];
            case "hp_gt" -> "生命>" + kv[1];
            case "has_eff" -> "持有" + effectNameCn(kv[1]);
            default -> c;
        };
    }

    private static String elementCn(ElementDamage e) {
        return switch (e) {
            case FIRE -> "火";
            case WATER -> "水";
            case EARTH -> "土";
            case ENDER -> "末影";
            default -> e.getSerializedName();
        };
    }

    private static String effectNameCn(String name) {
        return switch (name) {
            case "weak" -> "虚弱";
            case "slow" -> "缓慢";
            case "poison" -> "中毒";
            case "wither" -> "凋零";
            case "blind" -> "失明";
            case "healing" -> "再生";
            case "speed" -> "迅捷";
            case "strength" -> "力量";
            case "fire_resist" -> "抗火";
            case "invis" -> "隐身";
            case "jump" -> "跳跃";
            case "night_vision" -> "夜视";
            case "resistance" -> "抗性提升";
            case "water_breath" -> "水下呼吸";
            case "haste" -> "急迫";
            case "fire" -> "火";
            default -> name;
        };
    }
}
