package com.plumejade.lensouls.config;

import com.google.gson.*;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * 复制之魂数据驱动过滤（两套黑白名单，支持 {@code "all"} 通配与 {@code "#tag"} 标签）。
 * <p>
 * 扫描 {@code data/lensouls/copysoul_filter/} 下四个文件，支持 {@code /reload} 热重载：
 * <ul>
 *   <li>{@code drop_whitelist.json} / {@code drop_blacklist.json} —— 控制哪些实体死亡掉落复制之魂</li>
 *   <li>{@code copy_whitelist.json} / {@code copy_blacklist.json} —— 控制哪些物品可被复制之魂复制</li>
 * </ul>
 * 每个文件为实体/物品 ID 的 JSON 数组（或对象，键为 ID）。三类条目：
 * <ul>
 *   <li>{@code "minecraft:oak_sapling"} —— 点名一个具体 ID</li>
 *   <li>{@code "#minecraft:saplings"} —— 引用标签，代表该标签（含子标签继承）下的全部成员</li>
 *   <li>{@code "all"} —— 通配，代表该领域全部内容</li>
 * </ul>
 *
 * <h2>合并规则：谁更「具体」谁赢</h2>
 * 对目标 ID 取白名单与黑名单各自命中的<b>最具体条目</b>，按「具体度」比大小：
 * 白名单更具体 → 允许；黑名单更具体 → 禁止；<b>同具体度 → 黑名单胜</b>（收紧）。
 * 具体度由高到低：
 * <ol>
 *   <li>点名具体 ID（{@code "modid:foo"}，具体度 {@value #RANK_CONCRETE}）</li>
 *   <li>具名标签：路径层级 +1（{@code mod:group/child} = 1 胜过 {@code mod:group} = 0；
 *       原版单层标签路径无斜杠，故模组分段标签天然压过原版大类标签）</li>
 *   <li>{@code "all"}（{@value #RANK_ALL}，<b>严格低于任何具名标签</b>；
 *       不可用 0，否则零深度的原版大类标签会与 all 撞成同级）</li>
 * </ol>
 * 于是「白名单点名具体物品 + 黑名单含其所在标签」→ 允许；「白名单引用标签 + 黑名单点名标签下的具体物品」→ 禁止；
 * 「黑名单 {@code "all"} + 白名单引用标签」→ 允许（标签比 all 具体）。
 * 另两条默认语义由同一规则自然导出：黑名单 {@code "all"} + 白名单点名 → 仅白名单允许；
 * 白名单 {@code "all"} + 黑名单点名 → 仅黑名单禁止。空白名单 = 无白名单约束（默认放行）。
 *
 * <h2>标签如何解析</h2>
 * 用静态注册表把 {@code #tag} <b>展开成 ID 集合</b>（{@code Registry#getTags()}，含子标签继承），
 * 而不是查物品栈自身的标签 —— 后者漏掉 {@code minecraft:logs} 这类「只挂子标签、无直接成员」的空壳标签。
 * 展开结果按注册表建索引缓存，并在 {@link TagsUpdatedEvent}（服务端数据包重载 / 客户端收包）时整体失效，
 * 因此其它数据包往 {@code #minecraft:saplings} 里塞树苗后本黑名单会自动跟上。
 * <p>
 * 不存在的标签只记 WARN 并忽略该条（与无效 ID 同口径），不会让整份名单失效。
 * <p>
 * 默认（四文件为空或白名单为 {@code ["all"]}、黑名单为空）：掉落仅限 200 血以上实体，
 * 复制之魂本身不可复制，其余全部允许。
 */
public class CopySoulFilter extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LenSouls.LOGGER;
    private static final String FOLDER = "copysoul_filter";
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /** 通配令牌 */
    private static final String ALL = "all";
    /** 标签前缀 */
    private static final String TAG_PREFIX = "#";

    /** 点名具体 ID 的具体度（永远高于任何标签与 all） */
    private static final int RANK_CONCRETE = 1_000_000;
    /**
     * {@code "all"} 的具体度。必须低于「任何具名标签」（标签 = 路径深度 + 1 ≥ 1），
     * 否则零深度的原版大类标签（如 {@code #minecraft:saplings}）会与 all 撞成同级，
     * 「白名单 #tag + 黑名单 all」会被误判为禁止。
     */
    private static final int RANK_ALL = -1;
    /** 未命中 */
    private static final int RANK_NONE = Integer.MIN_VALUE;

    private static final CopySoulFilter INSTANCE = new CopySoulFilter();

    /** 最近一次编译结果（按注册表索引）；标签重绑或数据包重载时整体失效 */
    private static volatile Compiled compiled;
    /** 已解析缓存：注册表身份 → 「标签 → 成员 ID」索引（弱键，避免吊住注册表类） */
    private static final List<CacheEntry> CACHE = new ArrayList<>();

    // ===== 原始配置（apply 时写入，按注册表延迟编译） =====
    private static volatile List<String> dropWlTokens = List.of();
    private static volatile List<String> dropBlTokens = List.of();
    private static volatile List<String> copyWlTokens = List.of();
    private static volatile List<String> copyBlTokens = List.of();

    public CopySoulFilter() {
        super(GSON, FOLDER);
    }

    /** 供 {@code AddReloadListenerEvent} 使用：与判定逻辑共用同一份静态配置 */
    public static CopySoulFilter instance() {
        return INSTANCE;
    }

    // ===================== 对外判定 =====================

    /** 该物品是否可被复制之魂复制（已综合复制黑白名单，含 {@code #tag} 展开） */
    public static boolean isCopyAllowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        return isCopyAllowed(BuiltInRegistries.ITEM, stack.getItem());
    }

    /** 该物品是否可被复制（{@code item} 为 null / 未注册时放行，避免注册表异常时误封） */
    public static boolean isCopyAllowed(Registry<Item> registry, @Nullable Item item) {
        if (item == null) return true;
        ResourceLocation id = registry.getKey(item);
        if (id == null) return true;
        return evaluate(compiled(registry), true, id);
    }

    /** 该实体类型是否被允许掉落复制之魂（调用方仍需满足基础血量/首领清单判定） */
    public static boolean isDropAllowed(EntityType<?> type) {
        if (type == null) return true;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return true;
        return evaluate(compiled(BuiltInRegistries.ENTITY_TYPE), false, id);
    }

    // ===================== 查询指令支持 =====================

    /** 目标在给定名单里的「最佳命中」描述（点名 ID / 标签 / all），无命中返回 null */
    @Nullable
    public static String describeBestCopyMatch(Registry<Item> registry, ResourceLocation id, boolean whitelist) {
        return compiled(registry).describeBest(whitelist, id);
    }

    /** 目标命中的全部条目描述，按具体度从高到低（供查询指令逐条列出） */
    public static List<String> describeCopyMatches(Registry<Item> registry, ResourceLocation id, boolean whitelist) {
        return compiled(registry).describeAll(whitelist, id);
    }

    /** 该 ID 在复制名单里的具体度（点名=1000000、标签=深度+1、all=0、未命中=Integer.MIN_VALUE） */
    public static int copyRank(Registry<Item> registry, ResourceLocation id, boolean whitelist) {
        return compiled(registry).bestRank(whitelist, id);
    }

    /**
     * 一次查询拿到完整判定：是否允许、双方命中的最佳条目、以及**决定胜负的那一条规则**。
     * 供 {@code /lensouls copysoul} 核验合并规则用。
     */
    public static Decision describeCopyDecision(Registry<Item> registry, ResourceLocation id) {
        Compiled c = compiled(registry);
        int wr = c.bestRank(true, id);
        int br = c.bestRank(false, id);
        boolean allowed;
        String matchedRule;
        if (br == RANK_NONE) {
            allowed = true;
            matchedRule = wr == RANK_NONE ? "两侧都没命中 → 默认允许" : "黑名单没命中 → 默认允许";
        } else if (wr == RANK_NONE) {
            allowed = false;
            matchedRule = "白名单没命中，黑名单命中 → 禁止";
        } else if (wr != br) {
            allowed = wr > br;
            matchedRule = allowed ? "白名单更具体 → 允许" : "黑名单更具体 → 禁止";
        } else {
            allowed = false;
            matchedRule = "两侧具体度相同（" + wr + "）→ 黑名单胜";
        }
        return new Decision(allowed, wr, br,
                c.describeBest(true, id), c.describeBest(false, id),
                c.describeAll(true, id), c.describeAll(false, id), matchedRule);
    }

    /** 查询结果：判定 + 双方最佳命中 + 决定胜负的规则 */
    public record Decision(boolean allowed, int whitelistRank, int blacklistRank,
                           @Nullable String whitelistBest, @Nullable String blacklistBest,
                           List<String> whitelistHits, List<String> blacklistHits, String matchedRule) {
    }

    // ===================== 判定核心 =====================

    private static boolean evaluate(Compiled c, boolean copyList, ResourceLocation id) {
        // 黑名单为空（默认形态）→ 直接放行，不做任何标签查表
        if ((copyList ? copyBlTokens : dropBlTokens).isEmpty()) return true;
        int wr = c.bestRank(true, id);
        int br = c.bestRank(false, id);
        if (br == RANK_NONE) return true;   // 黑名单没命中
        if (wr == RANK_NONE) return false;  // 白名单没命中
        if (wr != br) return wr > br;       // 谁更具体谁说了算
        return false;                       // 同具体度 → 黑名单胜
    }

    // ===================== 编译（按注册表展开标签） =====================

    private static <T> Compiled compiled(Registry<T> registry) {
        Compiled c = compiled;
        if (c != null && c.owner == registry) return c;
        synchronized (CopySoulFilter.class) {
            c = compiled;
            if (c != null && c.owner == registry) return c;
            Compiled built = new Compiled(registry);
            built.partition(dropWlTokens, true, registry, "掉落");
            built.partition(dropBlTokens, false, registry, "掉落");
            if ((Object) registry == BuiltInRegistries.ITEM) {
                built.partition(copyWlTokens, true, registry, "复制");
                built.partition(copyBlTokens, false, registry, "复制");
            }
            compiled = built;
            return built;
        }
    }

    /** 标签重绑（数据包重载 / 客户端收包）→ 丢弃展开缓存与编译结果，下次判定按新标签重建 */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) return;
        synchronized (CopySoulFilter.class) {
            synchronized (CACHE) {
                CACHE.clear();
            }
            compiled = null;
        }
    }

    // ===================== 数据包加载 =====================

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> entries,
                         @NotNull ResourceManager manager,
                         @NotNull ProfilerFiller profiler) {

        List<String> dWl = new ArrayList<>(), dBl = new ArrayList<>();
        List<String> cWl = new ArrayList<>(), cBl = new ArrayList<>();
        boolean[] dWlAll = {false}, dBlAll = {false}, cWlAll = {false}, cBlAll = {false};

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation loc = entry.getKey();
            String path = loc.getPath(); // NeoForge 已剥离文件夹前缀与 .json 后缀，实际为裸名如 "copy_blacklist"
            List<String> set;
            boolean[] allFlag;
            if (path.endsWith("drop_whitelist")) { set = dWl; allFlag = dWlAll; }
            else if (path.endsWith("drop_blacklist")) { set = dBl; allFlag = dBlAll; }
            else if (path.endsWith("copy_whitelist")) { set = cWl; allFlag = cWlAll; }
            else if (path.endsWith("copy_blacklist")) { set = cBl; allFlag = cBlAll; }
            else {
                LOGGER.warn("复制之魂过滤忽略未知文件（仅支持 drop_/copy_whitelist.json 与 drop_/copy_blacklist.json）: {}", loc);
                continue;
            }
            // 与其他数据驱动加载器一致：直接解析 entry 内容（已是最高优先级 pack 的 JSON，
            // 数据包覆盖模组时即为数据包内容），按命名空间不同的同名文件各自累加。
            parseList(entry.getValue(), set, allFlag, loc);
        }

        // 空白名单 = 无白名单约束（默认放行）= 等价于白名单含 "all"
        if (dWl.isEmpty() && !dWlAll[0]) dWlAll[0] = true;
        if (cWl.isEmpty() && !cWlAll[0]) cWlAll[0] = true;

        synchronized (CopySoulFilter.class) {
            dropWlTokens = freeze(dWl, dWlAll[0]);
            dropBlTokens = freeze(dBl, dBlAll[0]);
            copyWlTokens = freeze(cWl, cWlAll[0]);
            copyBlTokens = freeze(cBl, cBlAll[0]);
            compiled = null;
        }
        LOGGER.info("复制之魂过滤加载完成：掉落 白[{}]黑[{}]，复制 白[{}]黑[{}]",
                describe(dWl, dWlAll[0]), describe(dBl, dBlAll[0]),
                describe(cWl, cWlAll[0]), describe(cBl, cBlAll[0]));
    }

    private static String describe(List<String> tokens, boolean all) {
        long tags = tokens.stream().filter(t -> t.startsWith(TAG_PREFIX)).count();
        StringBuilder sb = new StringBuilder();
        if (all) sb.append("all");
        if (!tokens.isEmpty()) {
            if (sb.length() > 0) sb.append('+');
            sb.append(tokens.size() - tags).append("项");
        }
        if (tags > 0) {
            if (sb.length() > 0) sb.append('+');
            sb.append(tags).append("标签");
        }
        return sb.length() == 0 ? "空" : sb.toString();
    }

    /** 冻结为不可变列表；列表内的 "all" 丢弃（all 由 {@code apply} 里的布尔标志承载，避免重复计入） */
    private static List<String> freeze(List<String> tokens, boolean all) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : tokens) {
            if (t.isEmpty() || ALL.equalsIgnoreCase(t)) continue;
            out.add(t);
        }
        return List.copyOf(out);
    }

    private static void parseList(JsonElement json, List<String> out, boolean[] allFlag, ResourceLocation source) {
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) parseToken(e.getAsString(), out, allFlag, source);
        } else if (json.isJsonObject()) {
            for (String key : json.getAsJsonObject().keySet()) parseToken(key, out, allFlag, source);
        } else {
            LOGGER.warn("复制之魂过滤文件格式无效（需为数组或对象）: {}", source);
        }
    }

    private static void parseToken(String token, List<String> out, boolean[] allFlag, ResourceLocation source) {
        if (ALL.equalsIgnoreCase(token)) {
            allFlag[0] = true;
            return;
        }
        if (token.startsWith(TAG_PREFIX)) {
            String raw = token.substring(TAG_PREFIX.length());
            // 标签是否真实存在延迟到编译期判断（编译时才按注册表展开），这里只校验语法
            if (ResourceLocation.tryParse(raw) == null) {
                LOGGER.warn("复制之魂过滤无效标签 '{}' (文件: {}): 标签名语法非法", token, source);
                return;
            }
            out.add(TAG_PREFIX + ResourceLocation.parse(raw));
            return;
        }
        if (ResourceLocation.tryParse(token) == null) {
            LOGGER.warn("复制之魂过滤无效 ID '{}' (文件: {})", token, source);
            return;
        }
        out.add(ResourceLocation.parse(token).toString());
    }

    // ===================== 不可变编译结果 =====================

    /**
     * 某个注册表下的一份编译结果：点名 ID、标签展开成员、是否含 all，白黑各一套。
     * 不可变，读线程安全；标签绑定变化时整体重建而不是原地改。
     */
    static final class Compiled {
        /** 注册表身份（引用比较，用于判断编译结果是否对应当前注册表） */
        final Object owner;
        final Set<ResourceLocation> whitelistIds = new HashSet<>();
        final Set<ResourceLocation> blacklistIds = new HashSet<>();
        final Map<String, Set<ResourceLocation>> whitelistTags = new HashMap<>();
        final Map<String, Set<ResourceLocation>> blacklistTags = new HashMap<>();
        boolean whitelistAll, blacklistAll;

        Compiled(Object owner) {
            this.owner = owner;
        }

        <T> void partition(List<String> tokens, boolean whitelist, Registry<T> registry, String label) {
            for (String token : tokens) {
                if (ALL.equalsIgnoreCase(token)) {
                    if (whitelist) whitelistAll = true; else blacklistAll = true;
                    continue;
                }
                if (token.startsWith(TAG_PREFIX)) {
                    addTag(token.substring(TAG_PREFIX.length()), whitelist, registry, label);
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(token);
                if (id == null) continue;
                if (registry.getOptional(id).isEmpty()) {
                    LOGGER.warn("[{}] {}名单里的 '{}' 在当前注册表中不存在，已忽略该条",
                            label, whitelist ? "白" : "黑", token);
                    continue;
                }
                (whitelist ? whitelistIds : blacklistIds).add(id);
            }
        }

        private void addTag(String raw, boolean whitelist, Registry<?> registry, String label) {
            ResourceLocation tagId = ResourceLocation.tryParse(raw);
            if (tagId == null) return;
            Set<ResourceLocation> members = resolveTag(tagId, registry);
            if (members == null) {
                LOGGER.warn("[{}] {}名单里的标签 '#{}' 不存在（模组未装或拼写错误），已忽略该条",
                        label, whitelist ? "白" : "黑", raw);
                return;
            }
            (whitelist ? whitelistTags : blacklistTags).put(TAG_PREFIX + raw, members);
        }

        /** 命中的最高具体度（未命中 {@link #RANK_NONE}） */
        int bestRank(boolean whitelist, ResourceLocation id) {
            boolean all = whitelist ? whitelistAll : blacklistAll;
            Set<ResourceLocation> ids = whitelist ? whitelistIds : blacklistIds;
            Map<String, Set<ResourceLocation>> tags = whitelist ? whitelistTags : blacklistTags;
            if (!all) {
                // 无 all 的常见路径：点名命中即可提前返回，其余情况才扫标签
                if (ids.contains(id)) return RANK_CONCRETE;
                return tagBestRank(tags, id);
            }
            int best = RANK_ALL; // all 本身的具体度（低于任何具名标签）
            for (Map.Entry<String, Set<ResourceLocation>> e : tags.entrySet()) {
                if (e.getValue().contains(id)) best = Math.max(best, pathLength(e.getKey()));
            }
            return ids.contains(id) ? RANK_CONCRETE : best;
        }

        /** 最佳命中的描述（点名 ID / 标签 / all），无命中返回 null */
        @Nullable
        String describeBest(boolean whitelist, ResourceLocation id) {
            Set<ResourceLocation> ids = whitelist ? whitelistIds : blacklistIds;
            if (ids.contains(id)) return id + "（点名" + (whitelist ? "白" : "黑") + "名单，具体度 " + RANK_CONCRETE + "）";
            String best = null;
            int bestRank = RANK_NONE;
            for (Map.Entry<String, Set<ResourceLocation>> e : (whitelist ? whitelistTags : blacklistTags).entrySet()) {
                if (!e.getValue().contains(id)) continue;
                int r = pathLength(e.getKey());
                if (r > bestRank) {
                    bestRank = r;
                    best = e.getKey() + "（标签，含 " + e.getValue().size() + " 个 ID，具体度 " + r + "）";
                }
            }
            if (best != null) return best;
            return (whitelist ? whitelistAll : blacklistAll) ? "\"all\"（通配，具体度 " + RANK_ALL + "）" : null;
        }

        /** 命中的全部条目，按具体度从高到低（点名恒在最前） */
        List<String> describeAll(boolean whitelist, ResourceLocation id) {
            List<String> hits = new ArrayList<>();
            if ((whitelist ? whitelistIds : blacklistIds).contains(id)) hits.add("点名 " + id);
            List<Map.Entry<String, Integer>> tags = new ArrayList<>();
            for (Map.Entry<String, Set<ResourceLocation>> e : (whitelist ? whitelistTags : blacklistTags).entrySet()) {
                if (e.getValue().contains(id)) tags.add(Map.entry(e.getKey(), pathLength(e.getKey())));
            }
            tags.sort(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());
            for (Map.Entry<String, Integer> e : tags) hits.add(e.getKey() + "（具体度 " + e.getValue() + "）");
            if (whitelist ? whitelistAll : blacklistAll) hits.add("\"all\"（具体度 " + RANK_ALL + "）");
            return hits;
        }

        private static int tagBestRank(Map<String, Set<ResourceLocation>> tags, ResourceLocation id) {
            int best = RANK_NONE;
            for (Map.Entry<String, Set<ResourceLocation>> e : tags.entrySet()) {
                if (e.getValue().contains(id)) best = Math.max(best, pathLength(e.getKey()));
            }
            return best;
        }
    }

    /** 令牌的路径层级：{@code "#mod:group/child"} → 1（斜杠越多越具体，无斜杠为 0） */
    private static int pathLength(String token) {
        String id = token.startsWith(TAG_PREFIX) ? token.substring(TAG_PREFIX.length()) : token;
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') depth++;
        }
        return depth;
    }

    /**
     * 把 {@code #tag} 展开成 ID 集合，含子标签继承。标签不存在返回 null。
     * 结果按注册表建索引缓存，{@link TagsUpdatedEvent} 时整体失效。
     */
    @Nullable
    private static Set<ResourceLocation> resolveTag(ResourceLocation tagId, Registry<?> registry) {
        synchronized (CACHE) {
            for (CacheEntry entry : CACHE) {
                if (entry.owner.get() == registry) return entry.resolve(tagId, registry);
            }
            CacheEntry entry = new CacheEntry(registry);
            CACHE.add(entry);
            return entry.resolve(tagId, registry);
        }
    }

    /** 单个注册表的「标签 → 成员 ID」索引，惰性建立 */
    private static final class CacheEntry {
        final WeakReference<Registry<?>> owner;
        private Map<ResourceLocation, Set<ResourceLocation>> byTag; // null = 尚未建索引

        CacheEntry(Registry<?> registry) {
            this.owner = new WeakReference<>(registry);
        }

        @Nullable
        Set<ResourceLocation> resolve(ResourceLocation tagId, Registry<?> registry) {
            Map<ResourceLocation, Set<ResourceLocation>> map = byTag;
            if (map == null) {
                map = build(registry);
                byTag = map;
            }
            return map.get(tagId);
        }

        /** 遍历注册表全部标签建立索引（{@code getTags} 返回的成员已含子标签继承） */
        private Map<ResourceLocation, Set<ResourceLocation>> build(Registry<?> registry) {
            Map<ResourceLocation, Set<ResourceLocation>> map = new HashMap<>();
            try {
                registry.getTags().forEach(pair -> {
                    Set<ResourceLocation> ids = new HashSet<>();
                    for (Holder<?> holder : pair.getSecond()) {
                        ResourceKey<?> id = holder.unwrapKey().orElse(null);
                        if (id != null) ids.add(id.location());
                    }
                    map.put(pair.getFirst().location(), Collections.unmodifiableSet(ids));
                });
            } catch (Exception ex) {
                LOGGER.warn("复制之魂过滤展开标签失败（注册表 {}）: {}", registry.key().location(), ex.toString());
            }
            return map;
        }
    }
}
