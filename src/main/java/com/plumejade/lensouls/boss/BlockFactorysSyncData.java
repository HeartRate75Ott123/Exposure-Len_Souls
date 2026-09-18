package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.syncher.EntityDataAccessor;

/**
 * block_factorys_bosses 同步数据访问器（纯反射 + 惰性解析）。
 * <p>
 * <b>为什么放在普通包而不是 mixin 包</b>：Mixin 的类加载器禁止 mixin 引用
 * {@code com.plumejade.lensouls.mixin.*} 里<b>未被注册为 mixin</b> 的类 ——
 * 曾经把解析逻辑写成 {@code YetiStunPassiveMixin$Accessors} 嵌套类，直接在受击瞬间抛
 * {@code IllegalClassLoadError: ... is in a defined mixin package ... cannot be referenced directly}，
 * 服务端 tick 循环异常、游戏卡死。辅助类一律放在普通包里，mixin 只引用普通包类。
 * <p>
 * <b>为什么全部用反射</b>：block_factorys_bosses 是可选依赖（无编译期依赖），
 * 且这些字段<b>声明在父类</b>（{@code AbstractBossEntity}）—— 用 {@code @Shadow} 定位继承字段会
 * 让整个 mixin 被跳过（{@code InvalidMixinException}，只在日志留一行 WARN，游戏内毫无提示）。
 * <p>
 * 解析失败会打一条 WARN 并返回 {@code null}，调用方逐项判空：少一道闸门也不至于整个 mixin 报废，
 * 同时失败是<b>可见</b>的，不会再出现「静默失效」。
 */
public final class BlockFactorysSyncData {

    /** 字段声明在父类 {@code AbstractBossEntity}（Yeti 继承而来） */
    private static final String BOSS_BASE =
            "net.unusual.block_factorys_bosses.entity.boss.AbstractBossEntity";
    /** 字段声明在 Yeti 自身 */
    private static final String YETI =
            "net.unusual.block_factorys_bosses.entity.boss.yeti.YetiEntity";

    private static volatile boolean resolved;
    private static EntityDataAccessor<Integer> hitAnimtime;
    private static EntityDataAccessor<Integer> spawnAnimtime;
    private static EntityDataAccessor<Integer> groundSmashAnimtime;
    private static EntityDataAccessor<Integer> enraged;

    private BlockFactorysSyncData() {
    }

    /** 受击动画计时器 {@code DATA_HIT_ANIMTIME}：Yeti 的伤害闸门要求 {@code <= 13}。 */
    public static EntityDataAccessor<Integer> hitAnimtime() {
        ensureResolved();
        return hitAnimtime;
    }

    /** 出场动画计时器 {@code DATA_SPAWN_ANIMTIME}：闸门要求 {@code <= 0}。 */
    public static EntityDataAccessor<Integer> spawnAnimtime() {
        ensureResolved();
        return spawnAnimtime;
    }

    /** 砸地动画计时器 {@code DATA_GROUNDSMASH_ANIMTIME}：闸门要求 {@code <= 0 || >= 119}。 */
    public static EntityDataAccessor<Integer> groundSmashAnimtime() {
        ensureResolved();
        return groundSmashAnimtime;
    }

    /** 狂暴相位 {@code DATA_IS_ENRAGED}：值 3/4 为过渡态，此时 {@code hurt} 直接拒绝伤害。 */
    public static EntityDataAccessor<Integer> enraged() {
        ensureResolved();
        return enraged;
    }

    private static void ensureResolved() {
        if (resolved) return;
        synchronized (BlockFactorysSyncData.class) {
            if (resolved) return;
            hitAnimtime = resolve(BOSS_BASE, "DATA_HIT_ANIMTIME");
            spawnAnimtime = resolve(BOSS_BASE, "DATA_SPAWN_ANIMTIME");
            groundSmashAnimtime = resolve(YETI, "DATA_GROUNDSMASH_ANIMTIME");
            enraged = resolve(YETI, "DATA_IS_ENRAGED");
            resolved = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Integer> resolve(String owner, String name) {
        try {
            Object value = Class.forName(owner).getField(name).get(null);
            if (value instanceof EntityDataAccessor<?> accessor) {
                return (EntityDataAccessor<Integer>) accessor;
            }
            LenSouls.LOGGER.warn("[Stun] {}.{} 不是 EntityDataAccessor，定身闸门将跳过该项", owner, name);
        } catch (Throwable t) {
            LenSouls.LOGGER.warn("[Stun] 无法解析 {}.{}（{}），定身闸门将跳过该项", owner, name, t.toString());
        }
        return null;
    }
}
