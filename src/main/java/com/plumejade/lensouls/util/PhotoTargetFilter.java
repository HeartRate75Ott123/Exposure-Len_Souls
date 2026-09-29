package com.plumejade.lensouls.util;

import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拍照/瞄准时的「非目标」过滤（全模组唯一真源）。
 * <p>
 * 第三方辅助作战单位（gytrinket 的<b>无人机 / 蜂群 / 僚机</b>等 construct 实体）会跟着玩家满天飞，
 * 既可能被拍进画面抢走「照片主体」，也会抢走弹幕的「最近敌人」——玩家真正想拍/想打的那只怪反而选不到。
 * <p>
 * 这些实体在对方模组里的公共父类是
 * {@code com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity extends PathfinderMob}
 * ——<b>是 LivingEntity</b>，所以不能靠「非生物」把它筛掉，必须显式按类忽略。
 * <p>
 * 判定用<b>包名前缀</b>而不是逐个类名：对方所有作战单位都在
 * {@code com.gytrinket.gytrinket.core.entity.construct.} 之下（drone / swarm / wingman 三个子包），
 * 以后它再加第四种 construct 也自动覆盖。按类缓存结果（一个类只做一次字符串比较），
 * 模组没装时直接短路，开销可忽略。
 */
public final class PhotoTargetFilter {

    /** gytrinket 作战单位的公共包名前缀（drone / swarm / wingman 都在其下） */
    private static final String GYTRINKET_CONSTRUCT_PACKAGE = "com.gytrinket.gytrinket.core.entity.construct.";

    /** 实体类 → 是否忽略（同一个类只算一次） */
    private static final Map<Class<?>, Boolean> CACHE = new ConcurrentHashMap<>();

    private static volatile Boolean gytrinketLoaded;

    private PhotoTargetFilter() {
    }

    /**
     * 该实体是否应在拍照/瞄准里被忽略。
     * <p>
     * 忽略后调用方会<b>继续看下一个候选</b>（本类只回答"是不是"，不做选取），
     * 所以表现就是「无人机挡在前面时，照片/弹幕自动落到它后面的生物上」。
     */
    public static boolean isIgnored(Entity entity) {
        if (entity == null) return false;
        if (!gytrinketPresent()) return false;
        return CACHE.computeIfAbsent(entity.getClass(),
                cls -> cls.getName().startsWith(GYTRINKET_CONSTRUCT_PACKAGE));
    }

    private static boolean gytrinketPresent() {
        Boolean cached = gytrinketLoaded;
        if (cached == null) {
            cached = ModList.get().isLoaded("gytrinket");
            gytrinketLoaded = cached;
        }
        return cached;
    }
}
