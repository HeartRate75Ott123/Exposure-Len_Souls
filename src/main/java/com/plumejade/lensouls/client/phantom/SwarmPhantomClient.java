package com.plumejade.lensouls.client.phantom;

import com.plumejade.lensouls.entity.SwarmPhantomFade;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Phantom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧的「自家幻翼」缓存：实体 id → 到期刻（客户端 {@code level.getGameTime()} 口径）。
 *
 * <p>取代旧的「往原版 {@code Phantom} 加同步数据槽」方案（那套会因为两端 {@code defineId} 分配时机不同
 * 而模型/数据错位，直接把客户端踢下线，详见 {@link SwarmPhantomFade} 的说明）。
 * 时效由服务端在玩家开始追踪该幻翼时用 S2C 包下发一次，之后渐隐全在本地按时间算 ——
 * 不发逐 tick 同步，也不会因为区块重载重建实体而「淡到一半又复活成不透明」。
 */
public final class SwarmPhantomClient {

    /** 实体 id → 到期刻（客户端 gameTime） */
    private static final Map<Integer, Long> EXPIRE_AT = new ConcurrentHashMap<>();

    /** 清理扫描间隔（刻）：不必每帧扫表 */
    private static final long PRUNE_INTERVAL = 200L;
    /** 到期后多久可以判定为「永远不会再用到」并删条目 */
    private static final long STALE_AFTER = 200L;

    private static long lastPrune = Long.MIN_VALUE;

    private SwarmPhantomClient() {}

    /**
     * 收到 S2C 时效包。
     *
     * @param remainingTicks 服务端发包那一刻的余命（>0）
     * @param nowGameTime    客户端当前 gameTime
     */
    public static void put(int entityId, int remainingTicks, long nowGameTime) {
        EXPIRE_AT.put(entityId, nowGameTime + remainingTicks);
    }

    /**
     * 供渲染层查询：<b>不是</b>自家幻翼时返回 {@code null}。
     * <p>
     * 必须用 {@code null} 而不是某个数值来表达「不是我们的」——否则原版幻翼也会被套上半透明渲染管线
     * （把 cutout 换成 translucent 会改变混合与排序行为）。
     */
    public static Float alphaOrNull(Entity entity) {
        if (!(entity instanceof Phantom)) return null;
        int id = entity.getId();
        Long expireAt = EXPIRE_AT.get(id);
        if (expireAt == null) return null;

        long now = entity.level().getGameTime();
        prune(now);

        long remaining = expireAt - now;
        if (remaining <= -SwarmPhantomFade.FADE_TICKS) {
            // 早已过淡出末尾：实体本该被服务端收回，条目留着也没用
            EXPIRE_AT.remove(id);
            return null;
        }
        return SwarmPhantomFade.alphaFor(remaining);
    }

    /** 过期条目清理（不依赖断线钩子，最多残留 200 刻） */
    private static void prune(long now) {
        if (now - lastPrune < PRUNE_INTERVAL) return;
        lastPrune = now;
        EXPIRE_AT.values().removeIf(expireAt -> now - expireAt > STALE_AFTER);
    }

    /** 断线 / 切档清理 */
    public static void clear() {
        EXPIRE_AT.clear();
    }
}
