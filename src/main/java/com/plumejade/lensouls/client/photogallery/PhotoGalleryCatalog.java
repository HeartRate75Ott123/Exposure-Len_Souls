package com.plumejade.lensouls.client.photogallery;

import com.plumejade.lensouls.config.BossEntityLoader;
import com.plumejade.lensouls.config.PhotoSetDefs;
import com.plumejade.lensouls.integration.PhotoSetRegistry;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 图鉴的**候选池 / 过滤 / 排序 / tooltip 文本**统一来源。
 *
 * <h2>候选池</h2>
 * 照片页 = {@code PhotographEffectRegistry.getAllEntityIds()}（注册了照片效果的实体，现存 262 条 / 9 个命名空间）；
 * 套装页 = {@code PhotoSetDefs.all()}（现存 54 套）。两者都是**数据驱动**，界面不写死任何一条。
 *
 * <h2>排序（已确认口径）</h2>
 * 照片页：收藏 → 已窃取（首次窃取时间倒序，最新首拍在最上）→ 未窃取（原版优先 → 模组显示名 → 语言名，
 * 无翻译键者垫底）；套装页：收藏 → 进度降序 → setId 字典序。所有层级都用 id 字典序收口，保证顺序稳定。
 *
 * <h2>进度口径</h2>
 * 「已装备」走 {@code PhotoSetRegistry.collectInstalledEntities}（照片槽 + 相册内容物，按实体去重）——
 * 与照片 tooltip 的套装块**同一个方法**；「已窃取」走 {@code PhotoGalleryClientCache}（图鉴解锁集合）。
 * 两者语义不同，别互相替代。
 */
public final class PhotoGalleryCatalog {

    /** 照片页索引版本（候选池变化时 +1，用于重建拼音索引与模型缓存） */
    private static volatile int version = -1;
    /** 套装 tooltip 纯文本缓存（搜索「按 tooltip」用） */
    private static final Map<String, String> SET_TOOLTIP_TEXT = new LinkedHashMap<>();
    /** 套装名（补全与显示用） */
    private static volatile List<PhotoGalleryNameIndex.SetEntry> setEntriesCache = List.of();

    private PhotoGalleryCatalog() {
    }

    // ========== 候选池 ==========

    public static Set<String> photoIds() {
        return PhotographEffectRegistry.getAllEntityIds();
    }

    public static int version() {
        if (version < 0) version = 1;
        return version;
    }

    /** 预热：拼音/双语名索引 + 生物模型缓存（都在后台线程，界面先用子串兜底） */
    public static void warmUp() {
        // 节流 250ms：渲染线程每帧都会催（模型是异步建的），但没必要每帧重扫一遍候选池
        long now = System.nanoTime();
        if (now - lastWarmUpNanos < 250_000_000L) return;
        lastWarmUpNanos = now;

        Set<String> ids = photoIds();
        List<PhotoGalleryNameIndex.SetEntry> setEntries = setEntries();
        PhotoGalleryNameIndex.ensureLoadedAsync(ids, setIds(setEntries), version());
        PhotoGalleryNameIndex.indexSets(setEntries);
        PhotoGalleryEntityCache.ensureLoadedAsync(ids, version());
    }

    private static volatile long lastWarmUpNanos;

    private static List<String> setIds(List<PhotoGalleryNameIndex.SetEntry> entries) {
        List<String> out = new ArrayList<>(entries.size());
        for (var e : entries) out.add(e.setId());
        return out;
    }

    public static List<PhotoGalleryNameIndex.SetEntry> setEntries() {
        List<PhotoGalleryNameIndex.SetEntry> cached = setEntriesCache;
        if (!cached.isEmpty()) return cached;
        List<PhotoGalleryNameIndex.SetEntry> out = new ArrayList<>();
        for (PhotoSetDefs.SetDef def : PhotoSetDefs.all()) {
            out.add(new PhotoGalleryNameIndex.SetEntry(def.id(), def.name()));
        }
        out.sort(Comparator.comparing(PhotoGalleryNameIndex.SetEntry::setId));
        setEntriesCache = List.copyOf(out);
        return setEntriesCache;
    }

    // ========== 照片页 ==========

    /**
     * 照片页候选（**与套装页同一套搜索规则**）：`名称:` / `tooltip:` 命名模式，可叠加取交集。
     * <ul>
     *   <li>{@code 名称:} 走 {@link PhotoGalleryNameIndex}（中文拼音 / 中英名 / 实体 id）；</li>
     *   <li>{@code tooltip:} 走照片的**效果描述文本**（{@code PhotographEffectRegistry.descriptionText} + 所属套装名，剥掉 § 颜色码）；</li>
     *   <li>不写前缀时按名称匹配，**名称没命中再兜底试一次 tooltip**（避免照着效果描述搜出空白页）。</li>
     * </ul>
     */
    public static List<GalleryEntry> photoEntries(String rawQuery, boolean showLocked, boolean favoritesOnly) {
        TextQuery q = parseTextQuery(rawQuery);
        String nameQuery = q.name();
        String tipQuery = q.tip().toLowerCase(Locale.ROOT);

        List<GalleryEntry> out = new ArrayList<>();
        for (String id : photoIds()) {
            boolean unlocked = PhotoGalleryClientCache.isUnlocked(id);
            if (!unlocked && !showLocked) continue;
            boolean favorite = PhotoGalleryClientCache.isFavoriteEntity(id);
            if (favoritesOnly && !favorite) continue;
            if (!matchesPhoto(id, q, nameQuery, tipQuery)) continue;
            out.add(GalleryEntry.photo(id, Component.literal(PhotoGalleryNameIndex.displayName(id)),
                    unlocked, favorite, PhotoGalleryClientCache.firstTime(id)));
        }
        out.sort(PHOTO_ORDER);
        return out;
    }

    private static boolean matchesPhoto(String id, TextQuery q, String nameQuery, String tipQuery) {
        boolean nameHit = nameQuery.isEmpty() || PhotoGalleryNameIndex.matchesEntity(id, nameQuery);
        boolean tipHit = tipQuery.isEmpty()
                || photoTooltipText(id).toLowerCase(Locale.ROOT).contains(tipQuery);
        if (q.explicitTip()) {
            // 显式 tooltip: ⇒ tooltip 必须命中；名称条件只在也显式写了前缀时才要求
            return tipHit && (q.explicitName() ? nameHit : true);
        }
        if (q.explicitName()) return nameHit && tipHit;
        return nameHit || tipHit;   // 无前缀：容错，两者任一命中
    }

    /** 照片的 tooltip 纯文本（效果描述 + 所属套装名），懒构建 + 缓存 */
    private static final Map<String, String> PHOTO_TOOLTIP_TEXT = new LinkedHashMap<>();

    private static String photoTooltipText(String entityId) {
        String cached = PHOTO_TOOLTIP_TEXT.get(entityId);
        if (cached != null) return cached;
        StringBuilder sb = new StringBuilder(PhotographEffectRegistry.descriptionText(entityId));
        for (String setId : PhotoSetRegistry.getSets(entityId)) {
            PhotoSetDefs.SetDef def = PhotoSetDefs.get(setId);
            if (def != null) sb.append(' ').append(def.name());
        }
        String text = sb.toString();
        PHOTO_TOOLTIP_TEXT.put(entityId, text);
        return text;
    }

    private static final Comparator<GalleryEntry> PHOTO_ORDER = (a, b) -> {
        if (a.favorite() != b.favorite()) return a.favorite() ? -1 : 1;
        if (a.unlocked() != b.unlocked()) return a.unlocked() ? -1 : 1;
        if (a.unlocked()) {
            int c = Long.compare(b.firstTime(), a.firstTime());   // 新拍到的在前
            if (c != 0) return c;
        } else {
            int c = Integer.compare(untranslatedRank(a.id()), untranslatedRank(b.id()));
            if (c != 0) return c;
            c = compareByNamespace(a.id(), b.id());
            if (c != 0) return c;
        }
        return a.id().compareTo(b.id());
    };

    /** 翻译键缺失者垫底（0 = 有翻译，1 = 无） */
    private static int untranslatedRank(String entityId) {
        try {
            String key = "entity." + entityId.replace(':', '.');
            return net.minecraft.locale.Language.getInstance().has(key) ? 0 : 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int compareByNamespace(String idA, String idB) {
        String na = namespaceOf(idA);
        String nb = namespaceOf(idB);
        boolean vanillaA = "minecraft".equals(na);
        boolean vanillaB = "minecraft".equals(nb);
        if (vanillaA != vanillaB) return vanillaA ? -1 : 1;
        if (!na.equals(nb)) {
            int c = modDisplayName(na).compareTo(modDisplayName(nb));
            if (c != 0) return c;
        }
        return PhotoGalleryNameIndex.displayName(idA).compareTo(PhotoGalleryNameIndex.displayName(idB));
    }

    private static String namespaceOf(String id) {
        int i = id.indexOf(':');
        return i < 0 ? "minecraft" : id.substring(0, i);
    }

    private static String modDisplayName(String namespace) {
        if ("minecraft".equals(namespace)) return "Minecraft";
        try {
            return net.neoforged.fml.ModList.get().getModContainerById(namespace)
                    .map(container -> container.getModInfo().getDisplayName())
                    .orElse(namespace);
        } catch (Throwable ignored) {
            return namespace;
        }
    }

    // ========== 套装页 ==========

    /**
     * 文本查询：命名模式 {@code 名称:} / {@code 套装:} / {@code 生物:}（都算「名称」）与 {@code tooltip:}，
     * 空格分隔多条件。**照片页与套装页共用这一套解析**（用户口径：两页搜索规则对齐）。
     */
    public record TextQuery(String name, String tip, boolean explicitName, boolean explicitTip) {
    }

    public static TextQuery parseTextQuery(String raw) {
        String name = "";
        String tip = "";
        boolean explicitName = false;
        boolean explicitTip = false;
        if (raw != null) {
            for (String token : raw.trim().split("\\s+")) {
                if (token.isEmpty()) continue;
                String lower = token.toLowerCase(Locale.ROOT);
                if (lower.startsWith("name:") || lower.startsWith("set:") || lower.startsWith("mob:")
                        || token.startsWith("名称:") || token.startsWith("套装:") || token.startsWith("生物:")) {
                    name += token.substring(token.indexOf(':') + 1);
                    explicitName = true;
                } else if (lower.startsWith("tip:") || lower.startsWith("tooltip:")) {
                    tip += token.substring(token.indexOf(':') + 1);
                    explicitTip = true;
                } else {
                    name += token;
                }
            }
        }
        return new TextQuery(name, tip, explicitName, explicitTip);
    }

    public static List<GalleryEntry> setEntries(Player player, String rawQuery,
                                               boolean showLocked, boolean favoritesOnly) {
        List<String> installed = installedEntities(player);
        TextQuery q = parseTextQuery(rawQuery);
        String tip = q.tip().toLowerCase(Locale.ROOT);
        String nameQuery = q.name();

        List<GalleryEntry> out = new ArrayList<>();
        for (PhotoSetDefs.SetDef def : PhotoSetDefs.all()) {
            String setId = def.id();
            List<String> members = PhotoSetRegistry.getMembers(setId);
            int memberTotal = members.size();
            int need = requiredPhotos(def);
            int collected = countCollected(setId, members);
            int collectedTotal = Math.max(1, "boss_barrage".equals(setId) ? need : memberTotal);
            int photoHave = countInstalled(setId, members, installed, player);
            boolean unlocked = collected >= collectedTotal;
            boolean complete = photoHave >= need;

            if (!unlocked && !showLocked) continue;
            boolean favorite = PhotoGalleryClientCache.isFavoriteSet(setId);
            if (favoritesOnly && !favorite) continue;

            // 与照片页同一套语义：显式前缀各自独立要求；无前缀时名称没命中再兜底试 tooltip
            boolean nameHit = nameQuery.isEmpty()
                    || PhotoGalleryNameIndex.matchesSetName(setId, def.name(), nameQuery);
            boolean tipHit = tip.isEmpty()
                    || tooltipText(setId, def).toLowerCase(Locale.ROOT).contains(tip);
            if (q.explicitTip()) {
                if (!tipHit) continue;
                if (q.explicitName() && !nameHit) continue;
            } else if (q.explicitName()) {
                if (!nameHit || !tipHit) continue;
            } else if (!nameHit && !tipHit) {
                continue;
            }

            out.add(GalleryEntry.set(setId, Component.literal(def.name()), unlocked, favorite,
                    photoHave, need, memberTotal, collected, collectedTotal, complete));
        }

        out.sort(SET_ORDER);
        return out;
    }

    private static final Comparator<GalleryEntry> SET_ORDER = (a, b) -> {
        if (a.favorite() != b.favorite()) return a.favorite() ? -1 : 1;
        float ra = a.photoNeed() <= 0 ? 1f : (float) a.photoHave() / a.photoNeed();
        float rb = b.photoNeed() <= 0 ? 1f : (float) b.photoHave() / b.photoNeed();
        int c = Float.compare(Math.min(rb, 1f), Math.min(ra, 1f));
        if (c != 0) return c;
        return a.id().compareTo(b.id());
    };

    private static int requiredPhotos(PhotoSetDefs.SetDef def) {
        if ("boss_barrage".equals(def.id())) {
            return def.tiers().stream().mapToInt(t -> PhotoSetDefs.effectiveCount(def.id(), t)).max().orElse(1);
        }
        return def.tiers().stream().mapToInt(t -> PhotoSetDefs.effectiveCount(def.id(), t)).min().orElse(1);
    }

    private static int countCollected(String setId, List<String> members) {
        if ("boss_barrage".equals(setId)) {
            int n = 0;
            for (String id : PhotoGalleryClientCache.stolen().keySet()) {
                if (BossEntityLoader.isBoss(id)) n++;
            }
            return n;
        }
        int n = 0;
        for (String member : members) {
            if (PhotoGalleryClientCache.isUnlocked(member)) n++;
        }
        return n;
    }

    private static int countInstalled(String setId, List<String> members, List<String> installed, Player player) {
        if ("boss_barrage".equals(setId)) {
            try {
                return PhotoSetRegistry.countInstalledBossPhotos(player);
            } catch (Throwable ignored) {
                return 0;
            }
        }
        int n = 0;
        for (String member : members) {
            if (installed.contains(member)) n++;
        }
        return n;
    }

    private static List<String> installedEntities(Player player) {
        try {
            return PhotoSetRegistry.collectInstalledEntities(player);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    // ========== 文本 / tooltip ==========

    /** 套装的 tooltip：套装名 → 逐行效果 → 集齐 x/x/x 照片 → 已收集 x/x */
    public static List<Component> setTooltip(GalleryEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(entry.name());
        PhotoSetDefs.SetDef def = PhotoSetDefs.get(entry.id());
        if (def != null) {
            for (PhotoSetDefs.Tier tier : def.tiers()) {
                lines.addAll(PhotoSetRegistry.formatTier(tier));
            }
        }
        // 「集齐 <成员生物名> 照片」——把原来的三个数字换成**该套装成员的具体生物名**
        // （2026-10 反馈：数字看不出这套到底要哪些生物）
        lines.add(Component.translatable("gui.lensouls.photos.set.prog.need",
                memberNames(entry.id())));
        lines.add(Component.translatable("gui.lensouls.photos.set.prog.collected",
                entry.collected(), entry.collectedTotal()));
        if (!entry.unlocked()) {
            lines.add(Component.translatable("gui.lensouls.photos.locked.set"));
        }
        return lines;
    }

    /** 成员名列表最多列出几个（超出的折成「等 N 只」，免得 boss_barrage 那类套装把提示撑爆） */
    private static final int MEMBER_NAME_LIMIT = 6;

    /**
     * 套装成员生物名：**本地化显示名**、`/` 连接、按实体 id 定序（与数据文件顺序一致、不随语言跳序）。
     * 成员过多时只列前 {@link #MEMBER_NAME_LIMIT} 个，其余折成「等 N 只」。
     */
    private static String memberNames(String setId) {
        List<String> members = new ArrayList<>(PhotoSetRegistry.getMembers(setId));
        if (members.isEmpty()) return "-";
        members.sort(Comparator.naturalOrder());
        int shown = Math.min(members.size(), MEMBER_NAME_LIMIT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append('/');
            sb.append(PhotoGalleryNameIndex.displayName(members.get(i)));
        }
        if (members.size() > shown) {
            sb.append(' ').append(Component.translatable(
                    "gui.lensouls.photos.set.prog.more", members.size() - shown).getString());
        }
        return sb.toString();
    }

    /** 套装的 tooltip 纯文本（按 tooltip 搜索用；懒构建 + 缓存） */
    private static String tooltipText(String setId, PhotoSetDefs.SetDef def) {
        String cached = SET_TOOLTIP_TEXT.get(setId);
        if (cached != null) return cached;
        StringBuilder sb = new StringBuilder();
        if (def.desc() != null) sb.append(def.desc());
        for (PhotoSetDefs.Tier tier : def.tiers()) {
            for (Component line : PhotoSetRegistry.formatTier(tier)) {
                sb.append('\n').append(line.getString());
            }
        }
        String text = sb.toString();
        SET_TOOLTIP_TEXT.put(setId, text);
        return text;
    }
}
