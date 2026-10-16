package com.plumejade.lensouls.client.photogallery;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plumejade.lensouls.reinforce.pinyin.DictLoader;
import com.plumejade.lensouls.reinforce.pinyin.Keyboard;
import com.plumejade.lensouls.reinforce.pinyin.PinIn;
import com.plumejade.lensouls.reinforce.pinyin.searchers.Searcher;
import com.plumejade.lensouls.reinforce.pinyin.searchers.TreeSearcher;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 搜索索引：**中文拼音 / 中文名 / 英文名 / 实体 id** 四种输入都能命中同一个生物。
 *
 * <h2>为什么必须自己读 lang 文件</h2>
 * {@code EntityType.getDescription()} 只给**当前语言**的名字：英文语言下拿不到中文名，
 * 于是「打拼音搜中文名」会失效（用户明确要求中英双语都能搜）。
 * 所以这里直接扫客户端 ResourceManager 里所有资源包的 {@code lang/zh_cn.json} 与
 * {@code lang/en_us.json}，抽出 {@code entity.<ns>.<path>} 键还原成 {@code ns:path} 再与注册表对齐。
 *
 * <h2>性能</h2>
 * PinIn 字典 302KB，构建耗时数百毫秒 ⇒ 一律后台线程建；就绪前主线程走**大小写不敏感子串**兜底，
 * 就绪后自动切到拼音索引（与 {@code ReinforceSearchContext} 同一套范式，别再内嵌第二份拼音库）。
 * <p>
 * 拉丁字母查询前统一 {@code toLowerCase}：PinIn 索引对大小写敏感（实测 {@code DIAMOND} 搜不到 {@code diamond}）。
 */
public final class PhotoGalleryNameIndex {

    private static final AtomicBoolean LOADING = new AtomicBoolean();

    private static volatile PinIn pinin;
    private static volatile TreeSearcher<String> entityTree;
    private static volatile TreeSearcher<String> setTree;
    private static volatile int indexedVersion = -1;

    /** id → 中文名 / 英文名（仅用于**搜索**，显示一律用当前语言） */
    private static volatile Map<String, String> zhNames = Map.of();
    private static volatile Map<String, String> enNames = Map.of();

    private PhotoGalleryNameIndex() {
    }

    /** 后台预热（可重复调用）；{@code ids} 是当前候选池（照片页实体 id），{@code setIds} 是套装 id */
    public static void ensureLoadedAsync(Collection<String> ids, Collection<String> setIds, int version) {
        if (entityTree != null && indexedVersion == version) return;
        if (!LOADING.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                build(ids, setIds, version);
            } catch (Throwable ignored) {
                // 拼音引擎不可用时保持子串兜底
            } finally {
                LOADING.set(false);
            }
        });
    }

    private static synchronized void build(Collection<String> ids, Collection<String> setIds, int version) {
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

        if (zhNames.isEmpty() && enNames.isEmpty()) {
            harvestLanguageFiles();
        }

        TreeSearcher<String> newEntityTree = new TreeSearcher<>(Searcher.Logic.CONTAIN, context);
        for (String id : ids) {
            newEntityTree.put(id, id);
            String zh = zhNames.get(id);
            if (zh != null && !zh.isEmpty()) newEntityTree.put(zh, id);
            String en = enNames.get(id);
            if (en != null && !en.isEmpty()) newEntityTree.put(en, id);
            String display = displayName(id);
            if (!display.isEmpty() && !display.equals(id)) newEntityTree.put(display, id);
        }
        entityTree = newEntityTree;
        indexedVersion = version;
    }

    /** 套装名也进拼音索引（套装名是中文意境名，拼音能直接搜） */
    public static void indexSets(Collection<SetEntry> sets) {
        // 签名未变就不重建：warmUp 会被渲染线程定时催，这里必须幂等
        int signature = sets.size();
        for (SetEntry e : sets) signature = signature * 31 + e.setId().hashCode();
        if (setTree != null && signature == setIndexSignature) return;

        PinIn context = pinin;
        if (context == null) return;
        TreeSearcher<String> newSetTree = new TreeSearcher<>(Searcher.Logic.CONTAIN, context);
        for (SetEntry e : sets) {
            if (e.name() == null || e.name().isEmpty()) continue;
            newSetTree.put(e.name(), e.setId());
            newSetTree.put(e.setId(), e.setId());
        }
        setTree = newSetTree;
        setIndexSignature = signature;
    }

    private static volatile int setIndexSignature = Integer.MIN_VALUE;

    /** 套装索引条目（名 + id） */
    public record SetEntry(String setId, String name) {
    }

    // ========== 查询 ==========

    /** 生物是否命中查询（空查询 = 全通过） */
    public static boolean matchesEntity(String entityId, String query) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) return true;
        String lower = text.toLowerCase(Locale.ROOT);

        TreeSearcher<String> tree = entityTree;
        if (tree != null) {
            try {
                if (tree.search(lower).contains(entityId)) return true;
            } catch (Throwable ignored) {
            }
        }

        // 兜底（引擎未就绪时是唯一路径）：id / 当前语言名 / 中英名 子串
        if (entityId.toLowerCase(Locale.ROOT).contains(lower)) return true;
        if (displayName(entityId).toLowerCase(Locale.ROOT).contains(lower)) return true;
        String zh = zhNames.get(entityId);
        if (zh != null && zh.toLowerCase(Locale.ROOT).contains(lower)) return true;
        String en = enNames.get(entityId);
        return en != null && en.toLowerCase(Locale.ROOT).contains(lower);
    }

    /** 套装名是否命中查询（拼音 + 子串） */
    public static boolean matchesSetName(String setId, String name, String query) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) return true;
        String lower = text.toLowerCase(Locale.ROOT);

        TreeSearcher<String> tree = setTree;
        if (tree != null) {
            try {
                if (tree.search(lower).contains(setId)) return true;
            } catch (Throwable ignored) {
            }
        }
        if (setId.toLowerCase(Locale.ROOT).contains(lower)) return true;
        return name != null && name.toLowerCase(Locale.ROOT).contains(lower);
    }

    /** 当前语言显示名（locale 缺失时回退到 id） */
    public static String displayName(String entityId) {
        try {
            ResourceLocation rl = ResourceLocation.parse(entityId);
            var type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (type != null) {
                Component description = type.getDescription();
                if (description != null) {
                    String s = description.getString();
                    if (!s.isEmpty() && !s.startsWith("entity.")) return s;
                }
            }
        } catch (Throwable ignored) {
        }
        int colon = entityId.indexOf(':');
        if (colon < 0) return entityId;
        return Component.translatable("entity." + entityId.substring(0, colon) + "." + entityId.substring(colon + 1)).getString();
    }

    // ========== lang 文件采集 ==========

    private static void harvestLanguageFiles() {
        Map<String, String> zh = new HashMap<>();
        Map<String, String> en = new HashMap<>();
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;
            Map<ResourceLocation, Resource> resources = minecraft.getResourceManager().listResources(
                    "lang", path -> {
                        String p = path.getPath();
                        return p.endsWith("zh_cn.json") || p.endsWith("en_us.json");
                    });
            Set<String> visited = new HashSet<>();
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                ResourceLocation key = entry.getKey();
                if (!visited.add(key.toString())) continue;   // 同一文件被多个包提供时只取第一个
                boolean isZh = key.getPath().endsWith("zh_cn.json");
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (root == null || !root.isJsonObject()) continue;
                    JsonObject obj = root.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> kv : obj.entrySet()) {
                        String k = kv.getKey();
                        if (!k.startsWith("entity.")) continue;
                        if (!kv.getValue().isJsonPrimitive()) continue;
                        String id = keyToEntityId(k);
                        if (id == null) continue;
                        String value = kv.getValue().getAsString();
                        if (value.isEmpty()) continue;
                        if (isZh) zh.putIfAbsent(id, value);
                        else en.putIfAbsent(id, value);
                    }
                } catch (Throwable ignored) {
                    // 单个包坏掉不影响其它包
                }
            }
        } catch (Throwable ignored) {
        }
        zhNames = Map.copyOf(zh);
        enNames = Map.copyOf(en);
    }

    /** {@code entity.minecraft.zombie} → {@code minecraft:zombie}（只替换第一个点） */
    private static String keyToEntityId(String key) {
        String rest = key.substring("entity.".length());
        int dot = rest.indexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) return null;
        return rest.substring(0, dot) + ":" + rest.substring(dot + 1);
    }
}
