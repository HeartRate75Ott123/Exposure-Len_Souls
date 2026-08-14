package com.plumejade.lensouls.boss;

/**
 * 客户端韧性条目（由 S2C 包更新）。
 *
 * @param entityId    目标实体 ID
 * @param progress    韧性进度 [0..1]
 * @param broken      是否破防（定身中）
 * @param invincible  是否处于削韧无敌窗口
 * @param requiredHits 破防所需削韧次数（Jade 显示 "x/x" 用）
 */
public record ToughnessEntry(int entityId, float progress, boolean broken, boolean invincible, int requiredHits) {}
