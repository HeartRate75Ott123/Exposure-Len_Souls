package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.PhotoSetDefs;
import com.plumejade.lensouls.config.PhotoSetLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.HashMap;
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
                if (e.getValue() >= t.count()) {
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

    /** 在照片 tooltip 中追加套装归属（含四元素抑制顶部块、译名表头、逐行配色） */
    public static void appendTooltip(ItemTooltipEvent event, String entityId) {
        Set<String> setIds = new LinkedHashSet<>(getSets(entityId));
        ItemStack stack = event.getItemStack();
        if (stack != null && PhotographEffectRegistry.isBossPhoto(stack)) {
            setIds.add("boss_barrage");
        }
        if (setIds.isEmpty()) return;

        boolean shift = false;
        try {
            shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        } catch (Exception ignored) {}

        // 四元素抑制顶部块（仅当本照片含活性时提示该元素规则）
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

        event.getToolTip().add(Component.literal("§6套装：").withStyle(ChatFormatting.GOLD));
        for (String setId : setIds) {
            PhotoSetDefs.SetDef def = PhotoSetDefs.get(setId);
            if (def == null) continue;
            event.getToolTip().add(Component.literal("§a[ " + def.name() + " ]").withStyle(ChatFormatting.GREEN));
            if (!shift) continue;
            // 成员译名表头
            List<String> members = getMembers(setId);
            String names = setId.equals("boss_barrage") ? "各类首领"
                    : members.stream().map(PhotoSetRegistry::entityName).toList().stream().collect(java.util.stream.Collectors.joining("/"));
            event.getToolTip().add(Component.literal("  §7集齐 " + names + " 照片，触发效果").withStyle(ChatFormatting.GRAY));
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
                case "dmg_mod" -> "对套装所需的这几种生物造成伤害 +" + pct(Double.parseDouble(p[2]));
                case "dmg_taken" -> "受到套装所需的这几种生物伤害 -" + pct(Double.parseDouble(p[2]));
                case "infusion_boost" -> "元素活性等级 +" + p[1];
                case "convert_eff" -> "获得" + effectNameCn(p[1]) + "时转为" + effectNameCn(p[2]) + "效果";
                case "barrage_trigger" -> "弹幕额外触发 " + p[1] + " 次";
                case "barrage_dmg" -> "弹幕伤害 ×" + p[1];
                default -> inner;
            };
        } catch (Exception e) {
            return inner;
        }
    }

    /** 效果行配色：§c 进攻/伤害、§a 防御/生存、§9 增益/机动、§5 转化 */
    private static ChatFormatting effectColor(String inner) {
        String[] p = inner.split(":");
        return switch (p[0]) {
            case "dmg_mod", "dmg_taken", "barrage_trigger", "barrage_dmg", "on_hit_effect", "on_hit_suppress" -> ChatFormatting.RED;
            case "maxhp", "armor", "kb_resist", "immune", "dodge", "death_revive" -> ChatFormatting.GREEN;
            case "speed", "env", "infusion_boost", "elem_activity" -> ChatFormatting.BLUE;
            case "convert_eff" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.GRAY;
        };
    }

    private static String pct(double x) {
        return ((int) Math.round(x * 100)) + "%";
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
