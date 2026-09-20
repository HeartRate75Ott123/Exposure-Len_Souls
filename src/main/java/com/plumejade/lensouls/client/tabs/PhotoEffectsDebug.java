package com.plumejade.lensouls.client.tabs;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「照片效果」面板的排版调试开关（纯客户端）。
 * <p>
 * 由指令 {@code /lensouls gui photo_set testopen|testfalse} 经 {@code PhotoSetDebugPacket} 打开 / 关闭。
 * 打开时面板会额外塞入三段人造内容（短块 / 跨页块 / 超长块），用来验证：
 * <ul>
 *   <li>分页是否按面板实际可用高度切页（而不是固定行数硬编码）；</li>
 *   <li>单个套装块比一整页还高时能否被拆到下一页（而不是溢出到面板外面）；</li>
 *   <li>超长单行（含中文与英文混排）在面板内是否被裁切。</li>
 * </ul>
 * 关闭即完全恢复正常显示，不残留任何状态。
 */
public final class PhotoEffectsDebug {

    /** 调试块总行数：故意超过两页（面板约 15 行/页），保证一定会触发翻页与拆块 */
    private static final int SHORT_BLOCK_LINES = 5;
    private static final int CROSS_PAGE_LINES = 18;
    private static final int HUGE_BLOCK_LINES = 26;

    private static volatile boolean enabled;

    private PhotoEffectsDebug() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** 开关状态文本（指令反馈用） */
    public static String stateText() {
        return enabled ? "§a已开启" : "§7已关闭";
    }

    /**
     * 调试用内容块（按「套装块」的形式返回，交给与真实套装相同的分页逻辑处理）。
     * 不开启时返回空列表。
     */
    public static List<List<Component>> debugBlocks() {
        if (!enabled) return List.of();

        List<List<Component>> blocks = new ArrayList<>();
        blocks.add(block("§e[ 调试·短块 ]", SHORT_BLOCK_LINES, 0));
        blocks.add(block("§e[ 调试·跨页块 ]", CROSS_PAGE_LINES, 100));
        blocks.add(block("§c[ 调试·超长块 ]", HUGE_BLOCK_LINES, 200));
        return blocks;
    }

    private static List<Component> block(String title, int lines, int indexBase) {
        List<Component> block = new ArrayList<>(lines + 2);
        block.add(Component.literal(title));
        for (int i = 0; i < lines; i++) {
            int n = indexBase + i + 1;
            // 每行都写成「序号 + 中文 + 英文 + 数值」，既能看行数也能看裁切
            block.add(Component.literal(String.format("§7测试行 %02d 命中率 hit_chance +%.1f%% L2 panel line", n, n * 0.5)));
        }
        // 末尾追加一条超长行：专门验证「到达右侧边距时自动折行、而不是被裁掉」
        block.add(Component.literal("§b超长行测试：这一行刻意写得非常长，用来确认面板会在抵达右侧边距时自动折到下一行，"
                + "而不是把右端一小截裁掉 —— hit_chance / dodge_chance / fire_weakness / water_weakness / "
                + "earth_weakness / ender_weakness 六个属性名连起来也应该正常断行。"));
        return block;
    }
}
