package com.plumejade.lensouls.feather;

/**
 * 「某条诅咒此刻是否生效」的**端无关**查询接口。
 * <p>
 * 服务端可以直接读权威附件（{@link CurseManager#on} / {@link CurseManager#rev}），
 * 但客户端拿不到羽毛槽附件（刻意不同步），只有 {@code CurseSyncPacket} 推下来的
 * {@code CurseClientCache} 镜像。
 * <p>
 * <b>为什么需要它</b>：{@code PlayerEvent.BreakSpeed} 在 {@code Player.getDigSpeed} 里触发，
 * **客户端与服务端各算一次**，而 1.21.1 的 {@code ServerPlayerGameMode} 会用**服务端**的
 * {@code getDestroyProgress} 自己累积挖掘进度（{@code incrementDestroyProgress} /
 * {@code hasDelayedDestroy}）。如果只在服务端改速度，两端进度就对不上 —— 实机表现正是
 * 「挖完方块不消失、延迟 0.x 秒才消失并掉出掉落物」。
 * <p>
 * 由 {@code LenSoulsClient} 在客户端启动时注入实现（专用服保持 {@code null}）。
 */
public interface CurseSideQuery {

    /** 该条的**诅咒态**是否生效（选中 ∧ 未反转） */
    boolean on(CurseDef def, int entry);

    /** 该条的**反转态**是否生效（选中 ∧ 已反转） */
    boolean rev(CurseDef def, int entry);
}
