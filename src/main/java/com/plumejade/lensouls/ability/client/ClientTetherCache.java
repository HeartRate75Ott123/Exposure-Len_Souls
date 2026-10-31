package com.plumejade.lensouls.ability.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端保存的「牵引关系」镜像：{@code targetId -> ownerId}（由 {@code GravityTetherSyncPacket} 全量覆盖）。
 * <p>
 * 渲染层只认这份关系，不再依赖子弹实体是否还在客户端、是否被跟踪。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTetherCache {

    private static final Map<Integer, Integer> LINKS = new LinkedHashMap<>();

    private ClientTetherCache() {
    }

    /** 全量覆盖（空数组 = 全部解除）。 */
    public static void apply(int[] owners, int[] targets) {
        LINKS.clear();
        int n = Math.min(owners.length, targets.length);
        for (int i = 0; i < n; i++) {
            LINKS.put(targets[i], owners[i]);
        }
    }

    public static Map<Integer, Integer> links() {
        return LINKS;
    }

    public static boolean isEmpty() {
        return LINKS.isEmpty();
    }

    /** 断开 / 换维度 / 退出世界时清空。 */
    public static void clear() {
        LINKS.clear();
    }
}
