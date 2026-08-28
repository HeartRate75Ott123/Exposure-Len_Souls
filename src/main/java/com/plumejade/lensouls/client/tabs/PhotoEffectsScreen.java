package com.plumejade.lensouls.client.tabs;

import com.plumejade.lensouls.client.tabs.PhotoSetClient;
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
 * 分页参考 L2Artifacts 的 SetEffectScreen（每页限制行数，套装整块打包，翻页按钮置于右上）。
 */
public class PhotoEffectsScreen extends BaseTextScreen {

    private static final int LINES_PER_PAGE = 14;

    private final int page;

    public PhotoEffectsScreen(Component title) {
        this(title, 0);
    }

    public PhotoEffectsScreen(Component title, int page) {
        super(title, ResourceLocation.fromNamespaceAndPath("l2tabs", "textures/gui/empty.png"));
        this.page = page;
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
        if (player == null) return;
        var sets = PhotoSetRegistry.getActiveSets(player,
                PhotoSetClient.collectGearEntities(player), PhotoSetClient.countBossPhotos(player));
        int totalPage = Math.max(1, buildPages(sets).size());
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
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        int x = leftPos + 2;
        int y = topPos + 6;

        // 通用效果块（各元素抑制等级，有等级才显示，仅首页展示）
        if (this.page == 0) {
            Map<ElementDamage, Integer> levels = PhotoSetClient.collectElementLevels(player);
            if (!levels.isEmpty()) {
                g.drawString(font, Component.literal("§d通用效果"), x, y, 0, false);
                y += 10;
                for (Map.Entry<ElementDamage, Integer> e : levels.entrySet()) {
                    g.drawString(font, Component.literal("§5概率触发" + elementCn(e.getKey()) + "元素抑制" + e.getValue() + "级"), x, y, 0, false);
                    y += 10;
                }
                y += 2;
            }
        }

        var sets = PhotoSetRegistry.getActiveSets(player,
                PhotoSetClient.collectGearEntities(player), PhotoSetClient.countBossPhotos(player));
        if (sets.isEmpty()) {
            g.drawString(font, Component.translatable("lensouls.tabs.photo_effects.empty"), x, y, 0x888888, false);
            return;
        }
        List<List<Component>> pages = buildPages(sets);
        if (page < 0 || page >= pages.size()) return;
        for (var comp : pages.get(page)) {
            g.drawString(font, comp, x, y, 0, false);
            y += 10;
        }
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

    /** 按 setId 分组（大标题只出现一次），再把每个套装作为整块打包进分页 */
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
            blocks.add(block);
        }
        List<List<Component>> pages = new ArrayList<>();
        List<Component> cur = new ArrayList<>();
        int count = 0;
        for (var block : blocks) {
            if (!cur.isEmpty() && count + block.size() > LINES_PER_PAGE) {
                pages.add(cur);
                cur = new ArrayList<>();
                count = 0;
            }
            cur.addAll(block);
            count += block.size();
        }
        if (cur.isEmpty()) pages.add(new ArrayList<>());
        else pages.add(cur);
        return pages;
    }
}
