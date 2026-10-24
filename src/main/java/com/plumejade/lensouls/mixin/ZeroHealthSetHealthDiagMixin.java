package com.plumejade.lensouls.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 假死取证：抓「把玩家血量写成 0」的**调用栈**。
 * <p>
 * 背景：0 血未死（{@code dead == false}）之后，原版 {@code LivingEntity.hurt} 第一行
 * {@code if (isDeadOrDying()) return false;} 会把一切后续伤害吞掉 —— 玩家从此杀不死，
 * 而 {@code FalseDeathGuard} 只能每隔 2 秒把血量补回 1 点（表现为「反复救活 / 掉血抖动」）。
 * <p>
 * 产生 0 血只有两条路：① 死亡事件被取消却没回血；② 有人**直接 {@code setHealth(0)}**
 * （不经过伤害流程 ⇒ 根本不会有 {@code die()}）。本 mixin 只做第二条的取证：
 * 当血量被写成 ≤0 且调用栈里既没有 {@code actuallyHurt} 也没有 {@code die} 时，
 * 打一条 WARN 带完整调用栈（每 5 秒最多一条），直接指出是谁写的。
 * <p>
 * 纯取证：不改变任何行为。
 */
@Mixin(LivingEntity.class)
public abstract class ZeroHealthSetHealthDiagMixin {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("lensouls.zerodiag");
    private static long lensouls$lastLog;

    @Inject(method = "setHealth", at = @At("TAIL"))
    private void lensouls$diagnoseZeroHealth(float health, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        if (player.getHealth() > 0.0f) return;

        long now = System.currentTimeMillis();
        if (now - lensouls$lastLog < 5000L) return;

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean normalDeathFlow = false;
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement el : stack) {
            String cn = el.getClassName();
            if (cn.contains("actuallyHurt") || cn.contains("hurt") || cn.endsWith(".die")
                    || cn.contains("ServerPlayer.die") || cn.contains("LivingEntity.die")) {
                normalDeathFlow = true;
            }
            if (sb.length() < 1400 && (cn.contains("lensouls") || cn.contains("minecraft.world"))) {
                sb.append(System.lineSeparator()).append("    at ")
                        .append(cn).append('.').append(el.getMethodName()).append(':')
                        .append(el.getLineNumber());
            }
        }
        if (normalDeathFlow) return;   // 正常致死流程（hurt → actuallyHurt → setHealth(0) → die）

        lensouls$lastLog = now;
        LOGGER.warn("[ZeroHealth] 玩家血量被直接写成 {}（未经 hurt/die 流程）上限={} 死亡标记={}：{}",
                health, player.getMaxHealth(), player.isDeadOrDying(), sb);
    }
}
