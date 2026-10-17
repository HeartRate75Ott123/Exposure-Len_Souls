package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 元素活性的**唯一权威结算点**（两种模式，按"有没有外部源"分）。
 *
 * <h2>怎么区分「源」和「我们发出的修改版」</h2>
 * 不靠看效果实例（那是分辨不出来的），靠**调用来源**：
 * <ul>
 *   <li>{@code LivingEntity.addEffect(...)} 的**非 PUSHING** 调用 = 外部源
 *       （照片 / 药水 / 命令 / 第三方模组）。施加钩子在这里一次性记账。</li>
 *   <li>{@code PUSHING == true} 的调用 = **我们自己写下去的**，钩子直接放行，永不二次修整。</li>
 * </ul>
 * 荒厄与元素觉醒者的写入同样全部走 {@code PUSHING}，所以它们**天然不属于源**，
 * 不需要任何"这是不是某款羽毛给的"的判别。
 *
 * <h2>第二种（有外部源，按元素独立判定）</h2>
 * 钩子记下源等级与源的剩余时长（换算成绝对到期时刻）⇒ 落桌的实例**直接被换成修整版**
 * （只改等级，时长/外观继承源）。之后 {@link #push} 每 tick 只做一件事：
 * 可见值是否仍等于「改·源数据」；被 {@code /effect clear} 抹掉或被别的模组改掉就按记录重建。
 * 源自然到期（按记录的时刻）⇒ 该元素的任务结束。
 *
 * <h2>第一种（没有外部源 = 队列空了）</h2>
 * 可见等级 = boost(③) − penalty(①)。这一份完全是我们自己的：
 * **ambient = true（信标样式）+ 30 秒**，到期前续期；写入按 10 tick 节流。
 * ① 诅咒态一律 0（表现为「不施加」）；① 反转态 −2。
 */
public final class ElementActivityMath {

    /**
     * 标记"当前这批写入是我方 push 产生的"——这条标记就是「源 / 我们的修改版」的唯一判别依据。
     * <p>
     * push 写回去的等级**已经是最终值**，绝不能再被施加钩子按「源+boost−penalty」算一遍。
     */
    public static final ThreadLocal<Boolean> PUSHING = ThreadLocal.withInitial(() -> false);

    /** ③ 元素觉醒者注册的 boost 提供者（它只维护步数，不写效果） */
    public static java.util.function.ToIntFunction<ServerPlayer> boostProvider = p -> 0;

    /** 四种活性效果（顺序固定，持久化数组与之对齐） */
    public static final Holder<MobEffect>[] INFUSIONS = new Holder[]{
            ModEffects.FIRE_INFUSION,
            ModEffects.WATER_INFUSION,
            ModEffects.EARTH_INFUSION,
            ModEffects.ENDER_INFUSION
    };

    /** 第一种模式我们自己的时长：30 秒 */
    public static final int ACTIVITY_DURATION = 600;

    /** 剩余时长低于此值就续期（第一种模式），避免 30 秒到期出现空档 */
    private static final int REFRESH_THRESHOLD = 60;

    /** 第一种模式的写入节流：同一元素最多 10 tick 写一次 */
    private static final long WRITE_THROTTLE = 10L;

    /** 外部源等级（不含 boost / penalty），下标与 {@link #INFUSIONS} 对齐 */
    private static final String KEY_SRC = "lensouls:activity_src";
    /** 外部源的到期时刻（绝对 gameTime；{@link #NEVER} = 无限时长；0 = 无源） */
    private static final String KEY_SRC_END = "lensouls:activity_src_end";
    /** 无限时长的哨兵 */
    private static final long NEVER = -1L;

    /** 第一种模式的写入节流（内存即可，重登最多多写一次） */
    private static final Map<UUID, long[]> LAST_WRITE = new HashMap<>();

    private ElementActivityMath() {
    }

    /**
     * 是不是四种活性之一 → 返回下标；不是返回 −1。
     * <p>
     * <b>必须比 {@code value()}，不能用 {@code ==} 比 Holder</b>：
     * 同一效果的 Holder 有两种实例 —— 注册表给的 {@code Holder.Reference}（{@code /effect} 命令走的这条）
     * 与 {@code DeferredHolder}（我们代码里写死的那个）。两者引用不同，
     * 用 {@code ==} 会让「命令给的活性」被判成"不是活性"，于是源不记账、push 直接按 0 清掉。
     */
    public static int indexOf(Holder<MobEffect> effect) {
        if (effect == null) return -1;
        MobEffect value;
        try {
            value = effect.value();
        } catch (Throwable t) {
            return -1;
        }
        for (int i = 0; i < INFUSIONS.length; i++) {
            if (INFUSIONS[i].value() == value) return i;
        }
        return -1;
    }

    /** 诊断开关：`/lensouls activity verbose true` 打开后，每次施加/每次写入都打日志（不节流） */
    public static boolean VERBOSE = false;

    /** 逐元素状态摘要（`/lensouls activity state`），比翻日志快 */
    public static java.util.List<String> describe(ServerPlayer p) {
        int[] src = loadInts(p, KEY_SRC);
        long[] end = loadLongs(p, KEY_SRC_END);
        long now = p.level().getGameTime();
        int b = boost(p);
        int pen = penalty(p);
        boolean block = blocked(p);
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("§b[活性] §fboost(③)=" + b + " §7penalty(①)=" + pen + " §7禁=" + block
                + " §7computed=" + (block ? 0 : "见下"));
        for (int i = 0; i < INFUSIONS.length; i++) {
            MobEffectInstance cur = p.getEffect(INFUSIONS[i]);
            int observed = cur == null ? 0 : cur.getAmplifier() + 1;
            boolean task = end[i] != 0 && (end[i] == NEVER || end[i] > now);
            int want = block ? 0 : Math.max(0, src[i] + b - pen);
            String name = INFUSIONS[i].value().getDescriptionId().replace("effect.lensouls.", "");
            out.add(String.format("§7%-14s §f可见=%d §7源=%d §7源余=%s §7应为=%d §8实例=%s",
                    name, observed, src[i],
                    task ? (end[i] == NEVER ? "∞" : (end[i] - now) + "t") : "无",
                    want, cur == null ? "无" : (cur.getDuration() < 0 ? "∞" : cur.getDuration() + "t")
                            + (cur.isAmbient() ? " 蓝框" : "")));
        }
        return out;
    }

    // ==================== 三个输入 ====================

    /** 当前身上四种活性的实际等级之和（玩家看到的值；③ 用它算速率） */
    public static int currentSum(ServerPlayer p) {
        int sum = 0;
        for (Holder<MobEffect> type : INFUSIONS) {
            MobEffectInstance inst = p.getEffect(type);
            if (inst != null) sum += Math.max(0, inst.getAmplifier() + 1);
        }
        return sum;
    }

    /** ③ 攒出的修改值（不在佩戴时不贡献，但存储保留） */
    public static int boost(ServerPlayer p) {
        return Math.max(0, boostProvider.applyAsInt(p));
    }

    /** ① 荒厄 e1 反转：活性 −2 级 */
    public static int penalty(ServerPlayer p) {
        return CurseManager.rev(p, CurseDefs.HARDMAN, 0) ? FeatherHardmanHandler.ACTIVITY_PENALTY : 0;
    }

    /** ① 荒厄 e1 诅咒态：活性整条禁用 */
    public static boolean blocked(ServerPlayer p) {
        return CurseManager.on(p, CurseDefs.HARDMAN, 0);
    }

    /** 最终可见等级（≤0 表示「不施加」） */
    public static int resolve(ServerPlayer p, int sourceLevel) {
        if (sourceLevel <= 0) return 0;
        if (blocked(p)) return 0;
        return Math.max(0, sourceLevel + boost(p) - penalty(p));
    }

    // ==================== 第二种模式：源记账（由施加钩子调用） ====================

    /**
     * 记下外部这一份源：等级 + 它自己的到期时刻（此刻起算，所以重复施加会自然延长）。
     * <p>
     * 记时刻而不是时长，是为了区分「源还在但被 {@code /effect clear} 抹掉了」（要重建）
     * 与「源自己到期了」（要降回第一种模式）。
     */
    public static void recordSource(ServerPlayer p, int index, int sourceLevel, int duration) {
        if (index < 0 || index >= INFUSIONS.length) return;
        int[] src = loadInts(p, KEY_SRC);
        long[] end = loadLongs(p, KEY_SRC_END);
        int level = Math.max(0, sourceLevel);
        long until = duration < 0 ? NEVER : p.level().getGameTime() + Math.max(0, duration);
        if (src[index] == level && end[index] == until) return;
        src[index] = level;
        end[index] = until;
        saveInts(p, KEY_SRC, src);
        saveLongs(p, KEY_SRC_END, end);
    }

    /** 源最近一次被记下的等级（0 = 无）；供日志/排查用 */
    public static int sourceLevel(ServerPlayer p, int index) {
        int[] src = loadInts(p, KEY_SRC);
        return index >= 0 && index < src.length ? src[index] : 0;
    }

    /**
     * 外部来源该不该「再施加一次」。
     * <p>
     * <b>绝不能拿可见等级当判据</b>：可见等级被我们修整过（4 → 2），来源若写
     * {@code inst.getAmplifier() != 期望} 就会**每 tick 都认为没生效** ⇒ 无限重发，
     * 表现就是「时长永远卡在 300（15 秒）、一直往下发任务」。
     * <p>
     * 判据改成我们自己的账本：该元素有没有活跃源、源等级是不是同一个、剩余时长还够不够。
     *
     * @param level 来源这次打算给的等级（可见等级 ≠ 它）
     */
    public static boolean needSource(ServerPlayer p, int index, int level) {
        if (index < 0) return true;
        int[] src = loadInts(p, KEY_SRC);
        long[] end = loadLongs(p, KEY_SRC_END);
        long now = p.level().getGameTime();
        boolean task = end[index] != 0 && (end[index] == NEVER || end[index] > now);
        MobEffectInstance cur = p.getEffect(INFUSIONS[index]);
        int left = cur == null ? -1 : cur.getDuration();
        boolean holds = task && src[index] == level && (left < 0 || left >= 100);
        return !holds;
    }

    // ==================== 每 tick 对齐（幂等） ====================

    /** 把身上四种活性对齐到当前应有的状态。每 tick 调一次；状态正确时零写入。 */
    public static void push(ServerPlayer p) {
        int[] src = loadInts(p, KEY_SRC);
        long[] end = loadLongs(p, KEY_SRC_END);
        long now = p.level().getGameTime();
        int b = boost(p);
        int pen = penalty(p);
        boolean block = blocked(p);
        boolean dirty = false;
        long[] lastWrite = LAST_WRITE.computeIfAbsent(p.getUUID(), k -> new long[INFUSIONS.length]);

        for (int i = 0; i < INFUSIONS.length; i++) {
            // ── 队列：这一元素还有没有外部源的任务 ──
            boolean task = end[i] != 0 && (end[i] == NEVER || end[i] > now);
            if (end[i] != 0 && !task) {          // 源自然到期 ⇒ 任务结束，回到第一种模式
                src[i] = 0;
                end[i] = 0;
                dirty = true;
            }

            MobEffectInstance cur = p.getEffect(INFUSIONS[i]);
            int observed = cur == null ? 0 : cur.getAmplifier() + 1;

            // 先算「本该是多少」：task 已退队时 src 必为 0
            int want = block ? 0 : Math.max(0, src[i] + b - pen);

            // ── 外部清除的侦测：源还没到期、我们也没打算清零，效果却没了 ──
            // 只有"外部把它清掉"能满足这一条（/effect clear、牛奶、死亡、别的模组 remove）：
            // 因为它自己的剩余时长就等于源的剩余时长，自然到期时 end 同时也到点、走上面那条分支。
            // ⚠ 必须是 want > 0：want == 0 是**我们自己**按 ① 的规则清掉的（诅咒态 / 减到 0），
            //   不能当成外部清除，否则源会被我们记丢（实测：反转后 1 级源被清 → 源被误判丢弃，
            //   解除反转也回不来了）。
            if (task && cur == null && want > 0) {
                src[i] = 0;
                end[i] = 0;
                dirty = true;
                task = false;
                want = block ? 0 : Math.max(0, b - pen);
                logPush(p, i, "外部清除→放弃源", want);
            }
            int target = want;

            // ── 该没有：摘掉（① 诅咒态 / 减到 0） ──
            if (target == 0) {
                if (cur != null) {
                    write(p, i, 0, 0, true, true, true);
                    lastWrite[i] = now;
                    logPush(p, i, "清零", 0);
                }
                continue;
            }

            if (task) {
                // ── 第二种：修整版必须仍是「等级=target、剩余时长=源的剩余」 ──
                int remaining = end[i] == NEVER ? MobEffectInstance.INFINITE_DURATION
                        : (int) Math.max(1L, end[i] - now);
                boolean sameLevel = cur != null && observed == target;
                boolean sameEnd = cur != null && Math.abs((long) cur.getDuration() - remaining) <= 2L;
                if (sameLevel && sameEnd) continue;
                // 外观与时长继承源；只有等级是我们的修整结果
                boolean ambient = cur != null ? cur.isAmbient() : true;
                boolean visible = cur != null ? cur.isVisible() : true;
                boolean icon = cur != null ? cur.showIcon() : true;
                write(p, i, target, remaining, ambient, visible, icon);
                lastWrite[i] = now;
                logPush(p, i, "修整源", target);
                continue;
            }

            // ── 第一种：我们自己的那份 = ambient + 30 秒，续期写入节流 ──
            boolean needNow = cur == null || observed != target;
            boolean needRefresh = cur != null && observed == target
                    && cur.getDuration() >= 0 && cur.getDuration() < REFRESH_THRESHOLD;
            if (!needNow && !needRefresh) continue;
            if (!needNow && now - lastWrite[i] < WRITE_THROTTLE) continue;   // 续期才节流
            write(p, i, target, ACTIVITY_DURATION, true, true, true);
            lastWrite[i] = now;
            if (needNow) logPush(p, i, "自建", target);
        }

        if (dirty) {
            saveInts(p, KEY_SRC, src);
            saveLongs(p, KEY_SRC_END, end);
        }
    }

    /** 唯一写入入口：remove → add（降级必须如此，MC 对更低 amplifier 只做 hidden），同 tick 不渲染中间态 */
    private static void write(ServerPlayer p, int index, int level, int duration,
                              boolean ambient, boolean visible, boolean icon) {
        PUSHING.set(true);
        try {
            p.removeEffect(INFUSIONS[index]);
            if (level > 0) {
                p.addEffect(new MobEffectInstance(INFUSIONS[index], duration, level - 1, ambient, visible, icon));
            }
        } finally {
            PUSHING.set(false);
        }
    }

    /** push 的诊断日志（同一玩家/元素/动作 2 秒内不重复），只在真的写了效果时打 */
    private static final Map<String, Long> LAST_LOG = new HashMap<>();

    private static void logPush(ServerPlayer p, int i, String what, int target) {
        try {
            long now = p.level().getGameTime();
            String key = p.getUUID() + "/" + i + "/" + what;
            Long prev = LAST_LOG.get(key);
            if (!VERBOSE && prev != null && now - prev < 40L) return;
            if (LAST_LOG.size() > 1024) LAST_LOG.clear();
            LAST_LOG.put(key, now);
            com.plumejade.lensouls.LenSouls.LOGGER.info("[ActPush] {} {} {} 可见={} (源={} boost={} pen={} 禁={})",
                    p.getName().getString(), INFUSIONS[i].value().getDescriptionId(), what, target,
                    sourceLevel(p, i), boost(p), penalty(p), blocked(p));
        } catch (Throwable ignored) {
        }
    }
    // ==================== 持久化 ====================

    private static int[] loadInts(ServerPlayer p, String key) {
        CompoundTag tag = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int[] a = tag.getIntArray(key);
        return a.length == INFUSIONS.length ? a : new int[INFUSIONS.length];
    }

    private static void saveInts(ServerPlayer p, String key, int[] a) {
        CompoundTag tag = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        tag.putIntArray(key, a);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    private static long[] loadLongs(ServerPlayer p, String key) {
        CompoundTag tag = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        long[] a = tag.getLongArray(key);
        return a.length == INFUSIONS.length ? a : new long[INFUSIONS.length];
    }

    private static void saveLongs(ServerPlayer p, String key, long[] a) {
        CompoundTag tag = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        tag.putLongArray(key, a);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }
}
