package com.plumejade.lensouls.reinforce;

import com.plumejade.lensouls.reinforce.pinyin.DictLoader;
import com.plumejade.lensouls.reinforce.pinyin.Keyboard;
import com.plumejade.lensouls.reinforce.pinyin.PinIn;
import com.plumejade.lensouls.reinforce.pinyin.searchers.Searcher;
import com.plumejade.lensouls.reinforce.pinyin.searchers.TreeSearcher;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 强化材料搜索上下文：拼音 / 中文 / 英文 / 物品 ID 子串匹配。
 * <p>
 * 复用内嵌的 PinIn 拼音库（见 {@code reinforce.pinyin} 包，MIT，upstream PinIn by Towdium）：
 * 字典约 2 万条，构建耗时数百毫秒，因此在后台线程首次构建，主线程先用
 * 大小写不敏感子串兜底匹配；构建完成后自动切到拼音索引。
 */
public final class ReinforceSearchContext {

    private static volatile PinIn pinin;
    private static volatile TreeSearcher<String> tree;
    private static volatile int indexedVersion = -1;
    private static final AtomicBoolean LOADING = new AtomicBoolean();

    private ReinforceSearchContext() {
    }

    /** 后台预热（界面打开时调用；可重复调用）。 */
    public static void ensureLoadedAsync() {
        int version = ReinforceDataLoader.version();
        if (tree != null && indexedVersion == version) return;
        if (!LOADING.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                build(version);
            } catch (Throwable ignored) {
                // 拼音引擎不可用时保持子串兜底
            } finally {
                LOADING.set(false);
            }
        });
    }

    private static synchronized void build(int version) {
        PinIn context = pinin;
        if (context == null) {
            context = new PinIn(new DictLoader.Default()).config()
                    .keyboard(Keyboard.QUANPIN)
                    .fZh2Z(true)
                    .fSh2S(true)
                    .fCh2C(true)
                    .fAng2An(true)
                    .fIng2In(true)
                    .fEng2En(true)
                    .fU2V(true)
                    .accelerate(true)
                    .commit();
            pinin = context;
        }
        TreeSearcher<String> rebuilt = new TreeSearcher<>(Searcher.Logic.CONTAIN, context);
        for (ReinforceMaterial material : ReinforceDataLoader.getMaterials()) {
            String id = material.id().toString();
            rebuilt.put(id, id);
            Item item = ReinforceClientData.itemOf(material.id());
            if (item != null) {
                String name = item.getDefaultInstance().getHoverName().getString();
                if (!name.isEmpty()) rebuilt.put(name, id);
            }
        }
        tree = rebuilt;
        indexedVersion = version;
    }

    /**
     * 查询匹配的材料 ID 集合。
     *
     * @return 空查询 → null（表示不过滤）；无匹配 → 空集合
     */
    public static Set<String> matches(String query) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) return null;
        // PinIn 的索引对拉丁文大小写敏感（实测 "DIAMOND" 搜不到 "diamond"），统一小写后再查；
        // 中文不受影响，模糊音/首字母/全拼均照常。
        String lower = text.toLowerCase(Locale.ROOT);

        TreeSearcher<String> current = tree;
        int version = ReinforceDataLoader.version();
        if (current != null && indexedVersion != version) {
            current = null;
            ensureLoadedAsync();
        }
        if (current == null) {
            ensureLoadedAsync();
        }

        Set<String> result = new HashSet<>();
        if (current != null) {
            result.addAll(current.search(lower));
        }
        // 兜底（引擎未就绪时是唯一路径，就绪后仍保留以覆盖大小写/英文子串等边界）：
        // 物品 ID 与显示名的大小写不敏感子串
        for (ReinforceMaterial material : ReinforceDataLoader.getMaterials()) {
            String id = material.id().toString();
            if (id.toLowerCase(Locale.ROOT).contains(lower)) {
                result.add(id);
                continue;
            }
            Item item = ReinforceClientData.itemOf(material.id());
            String name = item == null ? "" : item.getDefaultInstance().getHoverName().getString();
            if (name.toLowerCase(Locale.ROOT).contains(lower)) {
                result.add(id);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
