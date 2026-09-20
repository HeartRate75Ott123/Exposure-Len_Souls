package com.plumejade.lensouls.client.tabs;

import com.plumejade.lensouls.config.PhotoSetDefs;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.integration.PhotoSetRegistry;
import dev.xkmc.l2tabs.tabs.contents.BaseTextScreen;
import dev.xkmc.l2tabs.tabs.core.TabManager;
import dev.xkmc.l2tabs.tabs.inventory.InvTabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 背包「照片效果」选项卡对应的界面：按当前装备照片动态列出已触发的套装效果。
 * <p>
 * <b>分页按面板实际可用高度计算</b>：l2tabs 的 {@code BaseTextScreen} 面板固定
 * {@code imageWidth × imageHeight = 176 × 166}（构造器里写死，{@code leftPos/topPos} 由 {@code init()} 居中），
 * 正文从 {@code topPos + TOP_PAD} 开始逐行 10px 排下去。写死行数的后果是：内容一多就冲出面板下边缘。
 * <p>
 * <b>超长行自动折行</b>：一行比面板内宽还长时（例如「测试行 01 命中率 hit_chance …」这类混排），
 * 以前直接画出去、右侧被裁掉一截；现在先按面板内宽折行（优先在空格处断，单词超宽才硬断），
 * <b>折出来的行同样计入分页行数</b>，颜色码 {@code §x} 会带到下一行，不会掉色。
 * <p>
 * 其余：套装整块优先不跨页（单块超一页时拆页续排）、正文区 {@code enableScissor} 兜底裁切、
 * 分页结果在 {@code init()} 里算一次缓存（折行要量字宽，不能每帧重算）。
 */
public class PhotoEffectsScreen extends BaseTextScreen {

    /** 正文起始位置（相对面板左上角） */
    private static final int TOP_PAD = 6;
    private static final int LEFT_PAD = 2;
    /** 正文底部留白（避免最后一行贴着面板下边框） */
    private static final int BOTTOM_PAD = 6;
    /** 行高：原版字体 9px + 1px 行距 */
    private static final int LINE_HEIGHT = 10;

    private final int page;

    /** 分页结果缓存（含折行），在 init() 里算一次 */
    private List<List<Component>> pages = List.of();
    /** 首页正文前的「通用效果」行（同样计入首页预算） */
    private List<Component> headerLines = List.of();
    /** 是否处于「没有任何套装效果」的空状态 */
    private boolean emptyState;

    public PhotoEffectsScreen(Component title) {
        this(title, 0);
    }

    public PhotoEffectsScreen(Component title, int page) {
        super(title, ResourceLocation.fromNamespaceAndPath("l2tabs", "textures/gui/empty.png"));
        this.page = page;
    }

    /** 正文区域可用行数（由面板高度推导，不写死） */
    private int linesPerPage() {
        int inner = this.imageHeight - TOP_PAD - BOTTOM_PAD;
        return Math.max(1, inner / LINE_HEIGHT);
    }

    /** 正文区域可用宽度（左右各留 LEFT_PAD） */
    private int contentWidth() {
        return Math.max(16, this.imageWidth - LEFT_PAD * 2);
    }

    @Override
    public void init() {
        super.init();
        try {
            new TabManager<>(this, new InvTabData()).init(this::addRenderableWidget, PhotoTabRegistry.TAB_PHOTO.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            this.pages = List.of(List.of());
            this.headerLines = List.of();
            this.emptyState = true;
            return;
        }

        this.headerLines = wrapAll(generalEffectLines(player));
        var sets = PhotoSetRegistry.getActiveSets(player,
                PhotoSetClient.collectGearEntities(player), PhotoSetClient.countBossPhotos(player));
        this.emptyState = sets.isEmpty() && !PhotoEffectsDebug.isEnabled();
        this.pages = buildPages(sets);

        int totalPage = Math.max(1, this.pages.size());
        int x = (this.width + this.imageWidth) / 2 - 16;
        int y = (this.height - this.imageHeight) / 2 + 4;
        int w = 10, h = 11;
        if (this.page > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), (e) -> click(-1))
                    .pos(x - w - 1, y).size(w, h).build());
        }
        if (this.page < totalPage - 1) {
            this.addRenderableWidget(Button.builder(Component.literal(">"), (e) -> click(1))
                    .pos(x, y).size(w, h).build());
        }
    }

    private void click(int btn) {
        Minecraft.getInstance().setScreen(new PhotoEffectsScreen(this.getTitle(), this.page + btn));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);

        int x = leftPos + LEFT_PAD;
        int y = topPos + TOP_PAD;

        // 兜底裁切：折行后正常不会越界，这里只是保证任何意外都不画到面板外
        g.enableScissor(leftPos + 1, topPos + TOP_PAD - 1,
                leftPos + imageWidth - LEFT_PAD + 1, topPos + imageHeight - BOTTOM_PAD + 1);
        try {
            if (this.emptyState) {
                for (Component line : headerLines) {
                    g.drawString(font, line, x, y, 0, false);
                    y += LINE_HEIGHT;
                }
                g.drawString(font, Component.translatable("lensouls.tabs.photo_effects.empty"), x, y, 0x888888, false);
                return;
            }
            if (page < 0 || page >= pages.size()) {
                for (Component line : headerLines) {
                    g.drawString(font, line, x, y, 0, false);
                    y += LINE_HEIGHT;
                }
                return;
            }

            // 首页先占掉「通用效果」的行数，剩余额度给套装内容
            int budget = linesPerPage();
            for (Component line : headerLines) {
                if (budget <= 0) break;
                g.drawString(font, line, x, y, 0, false);
                y += LINE_HEIGHT;
                budget--;
            }
            int used = 0;
            for (Component comp : pages.get(page)) {
                if (used >= budget) break;   // 双保险：分页逻辑已经控住行数
                g.drawString(font, comp, x, y, 0, false);
                y += LINE_HEIGHT;
                used++;
            }
        } finally {
            g.disableScissor();
        }
    }

    /** 通用效果行（各元素抑制等级）：标题 + 每个元素一行 + 空行，空则返回空列表 */
    private List<Component> generalEffectLines(Player player) {
        Map<ElementDamage, Integer> levels = PhotoSetClient.collectElementLevels(player);
        if (levels.isEmpty()) return List.of();
        List<Component> lines = new ArrayList<>(levels.size() + 2);
        lines.add(Component.literal("§d通用效果"));
        for (Map.Entry<ElementDamage, Integer> e : levels.entrySet()) {
            lines.add(Component.literal("§5概率触发" + elementCn(e.getKey()) + "元素抑制" + e.getValue() + "级"));
        }
        lines.add(Component.empty());
        return lines;
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

    /**
     * 按 setId 分组（大标题只出现一次），再把每个套装作为整块打包进分页。
     * 块优先不跨页；块本身高于一页时拆开续到下一页。折行在打包前完成，折出的行照常占额度。
     */
    private List<List<Component>> buildPages(List<PhotoSetRegistry.ActiveSet> sets) {
        LinkedHashMap<String, List<PhotoSetDefs.Tier>> grouped = new LinkedHashMap<>();
        for (var as : sets) {
            grouped.computeIfAbsent(as.setId(), k -> new ArrayList<>()).add(as.tier());
        }
        List<List<Component>> blocks = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<Component> block = new ArrayList<>();
            PhotoSetDefs.SetDef def = PhotoSetDefs.get(entry.getKey());
            String name = def != null ? def.name() : entry.getKey();
            block.add(Component.literal("§a[ " + name + " ]"));
            for (PhotoSetDefs.Tier tier : entry.getValue()) {
                block.addAll(PhotoSetRegistry.formatTier(tier));
            }
            blocks.add(wrapAll(block));
        }
        // 调试开关：把人造长块放在最前面，专门撑爆分页 / 触发折行
        blocks.addAll(0, wrapAll2(PhotoEffectsDebug.debugBlocks()));

        int limit = linesPerPage();
        List<List<Component>> result = new ArrayList<>();
        List<Component> cur = new ArrayList<>();
        for (var block : blocks) {
            if (!cur.isEmpty() && cur.size() + block.size() > limit) {
                result.add(cur);
                cur = new ArrayList<>();
            }
            if (block.size() <= limit) {
                cur.addAll(block);
            } else {
                for (Component line : block) {
                    if (cur.size() >= limit) {
                        result.add(cur);
                        cur = new ArrayList<>();
                    }
                    cur.add(line);
                }
            }
        }
        if (!cur.isEmpty()) result.add(cur);
        if (result.isEmpty()) result.add(new ArrayList<>());
        return result;
    }

    private List<Component> wrapAll(List<Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) out.addAll(wrap(line));
        return out;
    }

    private List<List<Component>> wrapAll2(List<List<Component>> blocks) {
        List<List<Component>> out = new ArrayList<>(blocks.size());
        for (List<Component> block : blocks) out.add(wrapAll(block));
        return out;
    }

    /**
     * 单行折行：量字宽（{@code §x} 颜色码零宽），超出面板内宽就断行。
     * 优先在空格后断；整段没有空格（中文/超长单词）则硬断，保证一定不越界。
     * 断点处已生效的颜色/样式码会补到下一行开头，避免续行掉色。
     */
    private List<Component> wrap(Component line) {
        String text = line.getString();
        if (text.isEmpty()) return List.of(line);
        int max = contentWidth();
        if (this.font.width(Component.literal(text)) <= max) return List.of(line);

        List<Component> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        StringBuilder codes = new StringBuilder();   // 当前生效的颜色/样式码
        int lastBreak = -1;                          // cur 内最后一个空格之后的位置

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (code == 'r') codes.setLength(0);
                else codes.append('§').append(code);
                cur.append('§').append(code);
                i++;
                continue;
            }
            cur.append(c);
            if (c == ' ') lastBreak = cur.length();

            if (this.font.width(Component.literal(cur.toString())) > max) {
                int cut = lastBreak > 0 ? lastBreak : cur.length() - 1;
                if (cut <= 0) cut = cur.length() - 1;
                String head = cur.substring(0, cut);
                String tail = cur.substring(cut);
                if (!head.isEmpty()) out.add(Component.literal(head));
                cur = new StringBuilder(codes).append(tail);
                lastBreak = -1;
            }
        }
        if (cur.length() > 0) out.add(Component.literal(cur.toString()));
        return out.isEmpty() ? List.of(line) : out;
    }
}
