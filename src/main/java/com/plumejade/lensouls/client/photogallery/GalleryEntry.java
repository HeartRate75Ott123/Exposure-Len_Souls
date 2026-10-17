package com.plumejade.lensouls.client.photogallery;

import net.minecraft.network.chat.Component;

/**
 * 图鉴网格的一个条目（照片页 = 一个生物；套装页 = 一套照片套装）。
 *
 * @param isSet          true = 套装条目
 * @param id             entityId 或 setId
 * @param name           显示名（照片页 = 实体的当前语言译名；套装页 = 套装名）
 * @param unlocked       照片页 = 已窃取过；套装页 = 该套装全部成员都已窃取
 * @param favorite       收藏夹命中
 * @param photoHave      套装：已装备（去重）成员数；照片页无意义
 * @param photoNeed      套装：需求张数（{@code PhotoSetDefs.effectiveCount}）
 * @param memberTotal    套装：登记的成员总数
 * @param collected      套装：已窃取（图鉴已解锁）成员数
 * @param collectedTotal 套装：收集口径的总数（首领套 = 需求张数）
 * @param complete       套装：已装备进度达标（画完成标记）
 * @param firstTime      照片页：首次窃取时间；套装页：**集齐时刻**（排序用；迁移补记 = 0，
 *                       套装未集齐 = {@link Long#MIN_VALUE}）
 */
public record GalleryEntry(
        boolean isSet,
        String id,
        Component name,
        boolean unlocked,
        boolean favorite,
        int photoHave,
        int photoNeed,
        int memberTotal,
        int collected,
        int collectedTotal,
        boolean complete,
        long firstTime) {

    public static GalleryEntry photo(String entityId, Component name, boolean unlocked, boolean favorite, long firstTime) {
        return new GalleryEntry(false, entityId, name, unlocked, favorite, 0, 0, 0, 0, 0, false, firstTime);
    }

    public static GalleryEntry set(String setId, Component name, boolean unlocked, boolean favorite,
                                   int photoHave, int photoNeed, int memberTotal,
                                   int collected, int collectedTotal, boolean complete, long firstTime) {
        return new GalleryEntry(true, setId, name, unlocked, favorite,
                photoHave, photoNeed, memberTotal, collected, collectedTotal, complete, firstTime);
    }

    /** 收藏键（服务端附件里用的就是这两个前缀） */
    public String favoriteKey() {
        return (isSet ? "set:" : "entity:") + id;
    }
}
