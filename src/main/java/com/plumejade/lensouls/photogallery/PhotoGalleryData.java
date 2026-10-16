package com.plumejade.lensouls.photogallery;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 照片图鉴的玩家数据（玩家附件，序列化 + {@code copyOnDeath}）。
 *
 * <h2>为什么是玩家附件而不是客户端文件</h2>
 * 「不同存档不污染」的硬要求：附件随玩家 NBT 存进存档的 {@code playerdata}，天然按存档 + 按玩家隔离；
 * 客户端 {@code .dat}（如 {@code ReinforceClientPrefs}）只适合纯客户端偏好，不能承载服务端权威数据。
 * {@code copyOnDeath} 是必须的——NeoForge 复活只重建玩家实体，不复制的话「死一次图鉴全灰」。
 *
 * <h2>字段</h2>
 * <ul>
 *   <li>{@code stolen} entityId → 首次窃取的 {@code level.getGameTime()}：解锁集合 + 排序源；</li>
 *   <li>{@code counts} entityId → 累计窃取次数（仅展示用，不参与排序）；</li>
 *   <li>{@code favorites} 收藏键（{@code entity:<id>} / {@code set:<setId>}）；</li>
 *   <li>{@code clickRule} 点击网格的结算规则；</li>
 *   <li>{@code revision} 每次变更 +1，客户端镜像据此判断是否过期（也用于 toast 去重）。</li>
 * </ul>
 * 所有写入都集中在 {@link PhotoGalleryRecorder} / {@link PhotoGallerySettlement}，别在界面里直接改。
 */
public class PhotoGalleryData implements INBTSerializable<CompoundTag> {

    /** 点击网格槽位的结算规则 */
    public enum ClickRule {
        /** 发放到物品栏（满则丢在脚下） */
        GRANT,
        /** 直接装备到照片饰品栏（已装备则不重复、无空槽则退化为发放） */
        EQUIP;

        public ClickRule next() {
            return this == GRANT ? EQUIP : GRANT;
        }

        public static ClickRule byName(String name) {
            return "EQUIP".equalsIgnoreCase(name) ? EQUIP : GRANT;
        }
    }

    /** 收藏键前缀：生物条目 */
    public static final String FAV_ENTITY = "entity:";
    /** 收藏键前缀：套装条目 */
    public static final String FAV_SET = "set:";

    private static final String TAG_STOLEN = "stolen_first";
    private static final String TAG_COUNTS = "stolen_counts";
    private static final String TAG_FAVORITES = "favorites";
    private static final String TAG_RULE = "click_rule";
    private static final String TAG_REVISION = "revision";

    private final Map<String, Long> stolen = new HashMap<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private ClickRule clickRule = ClickRule.GRANT;
    private int revision;

    // ========== 解锁 ==========

    /**
     * 记录一次能力窃取出片。
     *
     * @return true = 这是**首次**记录该生物（新解锁条目）
     */
    public boolean record(String entityId, long gameTime) {
        if (entityId == null || entityId.isEmpty()) return false;
        counts.merge(entityId, 1, Integer::sum);
        if (stolen.containsKey(entityId)) return false;
        stolen.put(entityId, gameTime);
        return true;
    }

    /** 迁移补记（旧存档里已经在背包/饰品栏里的能力窃取照片）：时间记 0 ⇒ 排序时自然落到最末 */
    public boolean recordMigration(String entityId) {
        if (entityId == null || entityId.isEmpty() || stolen.containsKey(entityId)) return false;
        stolen.put(entityId, 0L);
        return true;
    }

    public boolean isUnlocked(String entityId) {
        return entityId != null && stolen.containsKey(entityId);
    }

    public long firstTime(String entityId) {
        Long t = stolen.get(entityId);
        return t == null ? 0L : t;
    }

    public int countOf(String entityId) {
        Integer c = counts.get(entityId);
        return c == null ? 0 : c;
    }

    public int unlockedCount() {
        return stolen.size();
    }

    public Set<String> stolenIds() {
        return new LinkedHashSet<>(stolen.keySet());
    }

    public Map<String, Long> stolenSnapshot() {
        return new HashMap<>(stolen);
    }

    // ========== 收藏 ==========

    public boolean isFavorite(String key) {
        return key != null && favorites.contains(key);
    }

    /** 切换收藏，返回切换后是否收藏 */
    public boolean toggleFavorite(String key) {
        if (key == null || key.isEmpty()) return false;
        if (favorites.remove(key)) return false;
        favorites.add(key);
        return true;
    }

    public Set<String> favoritesSnapshot() {
        return new LinkedHashSet<>(favorites);
    }

    // ========== 规则 / 版本 ==========

    public ClickRule clickRule() {
        return clickRule;
    }

    public void setClickRule(ClickRule rule) {
        if (rule != null) clickRule = rule;
    }

    public int revision() {
        return revision;
    }

    /** 任何会改变客户端镜像的写入之后都要调 */
    public void bumpRevision() {
        revision++;
    }

    // ========== NBT ==========

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        CompoundTag stolenTag = new CompoundTag();
        for (Map.Entry<String, Long> e : stolen.entrySet()) {
            stolenTag.putLong(e.getKey(), e.getValue());
        }
        tag.put(TAG_STOLEN, stolenTag);

        CompoundTag countsTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            countsTag.putInt(e.getKey(), e.getValue());
        }
        tag.put(TAG_COUNTS, countsTag);

        ListTag list = new ListTag();
        for (String key : favorites) list.add(StringTag.valueOf(key));
        tag.put(TAG_FAVORITES, list);

        tag.putString(TAG_RULE, clickRule.name());
        tag.putInt(TAG_REVISION, revision);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        stolen.clear();
        counts.clear();
        favorites.clear();

        CompoundTag stolenTag = tag.getCompound(TAG_STOLEN);
        for (String key : stolenTag.getAllKeys()) {
            stolen.put(key, stolenTag.getLong(key));
        }
        CompoundTag countsTag = tag.getCompound(TAG_COUNTS);
        for (String key : countsTag.getAllKeys()) {
            counts.put(key, countsTag.getInt(key));
        }
        ListTag list = tag.getList(TAG_FAVORITES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) favorites.add(list.getString(i));

        clickRule = ClickRule.byName(tag.getString(TAG_RULE));
        revision = tag.getInt(TAG_REVISION);
    }
}
