package com.plumejade.lensouls.ability;

import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 相机能力的「行为声明」集中处——新增能力时<b>只改这里</b>，不用再翻拍照/出片的各条路径。
 * <p>
 * 背景：出片注入原先在拍立得（{@code PolaroidPrintMixin}）与暗房（{@code LightroomInjectMixin}）
 * 里各写了一份，能力专属数据（空间扭曲坐标、回溯快照）在两处 switch 里重复维护；
 * 每次加能力都要同步改两遍，极易漏改。现统一收敛：
 * <ul>
 *   <li>行为开关（是否产能力照片 / 是否要求帧内有生物）→ 本类</li>
 *   <li>出片注入本体 → {@link PhotoInjector}</li>
 *   <li>拍照瞬间的帧缓存 → {@code PhotoInjectionHandler#onFrameAdded}</li>
 * </ul>
 * 能力列表、GUI 卡片、滚轮 HUD、解锁位图、同步包均由 {@link AbilityType#values()} 驱动，
 * 追加枚举常量即自动收纳，无需改动。
 */
public final class AbilityBehavior {

    private AbilityBehavior() {
    }

    /**
     * 该能力拍摄时是否产出「能力照片」。
     * <p>
     * 时间定格 / 要害打击 / 断魂走即时效果（右键相机直接生效，不消耗相纸），不注入照片。
     */
    public static boolean producesAbilityPhoto(AbilityType ability) {
        if (ability == null) return false;
        return switch (ability) {
            case TIME_STOP, VITAL_STRIKE, SOUL_SEVER -> false;
            default -> true;
        };
    }

    /**
     * 该能力是否要求帧内有生物，否则退化为普通照片（不标 {@code lensouls:injected}）。
     */
    public static boolean requiresEntitiesInFrame(AbilityType ability) {
        if (ability == null) return false;
        return switch (ability) {
            case WEAKNESS_LENS, WILD_GLIMPSE -> true;
            default -> false;
        };
    }

    /**
     * 能力专属照片数据（出片时写入照片的 {@code CustomData}）。
     * <p>
     * 新增能力若无专属数据，保持 {@code default} 分支即可。
     */
    public static void writePhotoData(AbilityType ability, Frame frame, ServerPlayer player, CompoundTag tag) {
        if (ability == null || frame == null || tag == null) return;
        switch (ability) {
            case SPATIAL_WARP -> {
                // 记录拍照位置，作为空间扭曲球心
                Vec3 pos = frame.extraData().get(Frame.POSITION).orElse(null);
                if (pos != null) {
                    CompoundTag posTag = new CompoundTag();
                    posTag.putDouble("x", pos.x);
                    posTag.putDouble("y", pos.y);
                    posTag.putDouble("z", pos.z);
                    tag.put("lensouls:spatial_warp_pos", posTag);
                }
            }
            case TEMPORAL_RECALL -> {
                // 拍照即固化回溯快照（仅服务端玩家可用）
                if (player != null) {
                    tag.put("lensouls:snapshot", TemporalSnapshot.capture(player).toTag());
                }
            }
            case WILD_GLIMPSE -> {
                // 见微知著：把画面中的生物写进照片，作「记录到的生物」凭据
                var entities = frame.entitiesInFrame();
                if (entities != null && !entities.isEmpty()) {
                    ListTag glimpsed = new ListTag();
                    for (var entityInFrame : entities) {
                        if (entityInFrame != null && entityInFrame.id() != null) {
                            glimpsed.add(StringTag.valueOf(entityInFrame.id().toString()));
                        }
                    }
                    if (!glimpsed.isEmpty()) tag.put("lensouls:glimpsed_mobs", glimpsed);
                }
            }
            default -> {
            }
        }
    }
}
