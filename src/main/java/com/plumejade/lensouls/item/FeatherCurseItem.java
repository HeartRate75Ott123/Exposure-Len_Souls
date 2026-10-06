package com.plumejade.lensouls.item;

import com.plumejade.lensouls.client.CurseClientCache;
import com.plumejade.lensouls.feather.CurseDef;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 诅咒道具（⑤ 羽·断时炉心 / ⑥ 羽·终焉秘仪 / ⑦ 羽·零号誓炉）。
 * <p>
 * 三款共用这一个类：差异全在 {@link CurseDef}（条目数 / 条件是否整款共用 / 是否有大成 / 是否分段）
 * 与 lang 键里，Java 侧没有一行硬编码文案。
 * <p>
 * <b>7 槽全开、7 款可同时佩戴</b>，所以这里**不做互斥判定**（旧四根羽毛之间的互斥是它们自己的历史行为）。
 * <p>
 * tooltip 严格按 {@code docs/诅咒系统-重设计方案.md} §4.2：
 * <ul>
 *   <li><b>常显</b>：逐条显示「该条当前实际生效的效果」——未反转用 {@code .curse}（红），
 *       已反转用 {@code .after}（绿）；标〔保留〕的条目用 {@code .merged}（红绿合并）</li>
 *   <li><b>按住 Shift</b>：未反转条目两行（条件+进度 / 反转后效果），已反转条目只剩一行「原条件」；
 *       ①②③ 的整款共用条件在顶部显示一次；全铺开、不折叠</li>
 * </ul>
 */
public class FeatherCurseItem extends Item {

    private final CurseDef def;

    public FeatherCurseItem(CurseDef def) {
        super(new Item.Properties().stacksTo(1).fireResistant());
        this.def = def;
    }

    public CurseDef def() {
        return def;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = def.key();
        boolean shift = shiftDown();
        // ⑦ 的 sectionAt 之后是**祝福**：它们不是「反转」，而是「解锁」
        int curseCount = def.sectionAt() > 0 ? def.sectionAt() : def.entries();

        // ── 头部 ──
        tooltip.add(Component.translatable(key + ".desc"));
        tooltip.add(Component.translatable(key + ".wear"));
        if (def.sectionAt() > 0) {
            tooltip.add(Component.translatable(key + ".progress_split",
                    countReversed(def, 0, curseCount), countReversed(def, curseCount, def.entries())));
        } else {
            tooltip.add(Component.translatable(key + ".progress", CurseClientCache.reversedCount(def)));
        }
        tooltip.add(Component.translatable(key + ".shift_hint"));

        // ①②③ 的整款共用条件：按住 Shift 时在条目之前显示一次
        if (shift && def.sharedCond()) {
            tooltip.add(Component.literal("§7")
                    .append(Component.translatable("gui.lensouls.curse.shared_cond"))
                    .append(Component.translatable(key + ".cond_shared")));
        }

        // ── 逐条：常显「当前实际生效的效果」；按住 Shift 时**紧跟其后**补条件与结果 ──
        for (int i = 0; i < def.entries(); i++) {
            section(tooltip, i);
            boolean blessing = i >= curseCount;
            boolean on = CurseClientCache.isReversed(def, i);
            String name = Component.translatable(def.entryKey(i) + ".name").getString();
            String bodyKey = on ? (def.retained(i) ? ".merged" : ".after") : ".curse";

            if (blessing) {
                // 祝福：未解锁 ⇒ 基础效果压暗 + 标注；已解锁 ⇒ 直接显示升级后效果
                if (on) {
                    tooltip.add(Component.literal("§a✔ §a" + name + " ")
                            .append(Component.translatable(def.entryKey(i) + ".after")));
                } else {
                    tooltip.add(Component.literal("§7✖ §7" + name + " ")
                            .append(Component.translatable(def.entryKey(i) + ".curse"))
                            .append(Component.literal(" §8")
                                    .append(Component.translatable("gui.lensouls.curse.locked_mark"))));
                }
            } else {
                tooltip.add(Component.literal((on ? "§a✔ §a" : "§c✖ §c") + name + " ")
                        .append(Component.translatable(def.entryKey(i) + bodyKey)));
            }

            if (!shift || def.noReverse(i)) continue;

            Component cond = def.sharedCond()
                    ? Component.translatable(key + ".cond_shared")
                    : Component.translatable(def.entryKey(i) + ".cond");
            String stateLabel = Component.translatable(on
                    ? (blessing ? "gui.lensouls.curse.on_blessing" : "gui.lensouls.curse.on_curse")
                    : (blessing ? "gui.lensouls.curse.off_blessing" : "gui.lensouls.curse.off_curse")).getString();
            String condLabel = Component.translatable(blessing
                    ? "gui.lensouls.curse.cond_blessing" : "gui.lensouls.curse.cond_curse").getString();
            String afterLabel = Component.translatable(blessing
                    ? "gui.lensouls.curse.after_blessing" : "gui.lensouls.curse.after_curse").getString();

            if (on) {
                tooltip.add(Component.literal("§8    └ §a" + stateLabel + " §8· " + condLabel + "：").append(cond));
            } else {
                tooltip.add(Component.literal("§8    ├ §7" + stateLabel + " §8· §7" + condLabel + "：").append(cond)
                        .append(Component.literal(" §8[进度 " + CurseClientCache.progress(def.maskOffset() + i) + "]")));
                tooltip.add(Component.literal("§8    └ §7" + afterLabel + "：")
                        .append(Component.translatable(def.entryKey(i) + ".after")));
            }
        }

        if (def.grand()) {
            tooltip.add(Component.translatable(key + ".grand"));
        }
    }

    /** 统计 [from, to) 区间里已反转/已解锁的条数（⑦ 的诅咒段与祝福段分开算） */
    private int countReversed(CurseDef def, int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) {
            if (CurseClientCache.isReversed(def, i)) n++;
        }
        return n;
    }

    /** ⑦ 第 8 条起是祝福，插一行小标题 */
    private void section(List<Component> tooltip, int index) {
        if (def.sectionAt() > 0 && index == def.sectionAt()) {
            tooltip.add(Component.literal("§7【祝福】"));
        }
    }

    /** Shift 检测：服务端永远返回 false（避免加载 client-only 类） */
    private static boolean shiftDown() {
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) return false;
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }
}
