package com.plumejade.lensouls.client.photogallery;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网格用的**生物模型实例缓存**（每个实体一次性 {@code create()}，后台线程建）。
 * <p>
 * 照 Remorphed 的 {@code SummonEntitiesData.loadAll}：跳过 {@code EntityType.PLAYER}、
 * 非 {@code Mob}、以及 {@code create} 抛异常者；实例只用于 GUI 渲染（不 tick、不入世）。
 *
 * <h2>为什么必须自愈（实测踩过）</h2>
 * 首次调用可能发生在**世界还没加载完**的时刻（客户端刚登录就预热），此时
 * {@code Minecraft.getInstance().level == null}，{@code type.create(level)} 会返回 null ——
 * 若把这种 null 记成「该实体永久失败」，模型就再也不会被建出来（症状：图鉴里一个模型都没有）。
 * 因此这里区分两种失败：
 * <ul>
 *   <li><b>判定性失败</b>（类型不存在 / 不是 Mob / 构造抛异常）→ 记进 {@code FAILED}，不再重试；</li>
 *   <li><b>可重试失败</b>（level 为空 / create 返回 null）→ 不记，下次调用再试一轮。</li>
 * </ul>
 * 只有一轮里全部实体都判定完成，才把 {@code loadedVersion} 落下去。
 */
public final class PhotoGalleryEntityCache {

    private static final Map<String, Mob> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean LOADING = new AtomicBoolean();
    private static volatile int loadedVersion = -1;
    private static volatile boolean loading;

    private PhotoGalleryEntityCache() {
    }

    public static boolean isLoading() {
        return loading;
    }

    public static int readyCount() {
        return CACHE.size();
    }

    public static void ensureLoadedAsync(Collection<String> ids, int version) {
        if (loadedVersion == version) return;

        Level level = Minecraft.getInstance() == null ? null : Minecraft.getInstance().level;
        // 世界还没就绪：**不**标记失败、也不标记完成，等下一次调用（登录/开屏/换维度）再试
        if (level == null) return;

        if (!LOADING.compareAndSet(false, true)) return;

        List<String> pending = new ArrayList<>();
        for (String id : ids) {
            if (FAILED.contains(id) || CACHE.containsKey(id)) continue;
            pending.add(id);
        }
        if (pending.isEmpty()) {
            loadedVersion = version;
            LOADING.set(false);
            return;
        }

        loading = true;
        CompletableFuture.runAsync(() -> {
            boolean allResolved = true;
            try {
                for (String id : pending) {
                    try {
                        ResourceLocation rl = ResourceLocation.tryParse(id);
                        if (rl == null) {
                            FAILED.add(id);
                            continue;
                        }
                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                        if (type == null || type == EntityType.PLAYER) {
                            FAILED.add(id);
                            continue;
                        }
                        var entity = type.create(level);
                        if (entity instanceof Mob mob) {
                            CACHE.put(id, mob);
                        } else if (entity != null) {
                            entity.discard();
                            FAILED.add(id);
                        } else {
                            allResolved = false;   // 可重试：留着下轮再建
                        }
                    } catch (Throwable t) {
                        FAILED.add(id);
                    }
                }
                if (allResolved) loadedVersion = version;
                // 留一条可排查的日志（图鉴「没有模型」时的第一现场）：
                // 已建 / 本轮总数 / 判定失败 / 待重试
                com.plumejade.lensouls.LenSouls.LOGGER.info(
                        "[PhotoGallery] 生物模型缓存：成功 {} · 本轮 {} · 判定失败 {} · 待重试 {}",
                        CACHE.size(), pending.size(), FAILED.size(), allResolved ? 0 : 1);
            } finally {
                loading = false;
                LOADING.set(false);
            }
        });
    }

    public static Mob get(String entityId) {
        return CACHE.get(entityId);
    }

    public static boolean isReady(String entityId) {
        return CACHE.containsKey(entityId);
    }

    public static void clear() {
        CACHE.clear();
        FAILED.clear();
        loadedVersion = -1;
    }
}
