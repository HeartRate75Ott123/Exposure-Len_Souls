package com.plumejade.lensouls.ability.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;

/**
 * 冻结实体描边捕获状态管理。
 * <p>
 * 由 {@link com.plumejade.lensouls.mixin.client.FrozenEntityRenderMixin} 在
 * {@code LivingEntityRenderer.render()} HEAD/RETURN 中管理生命周期。
 * <p>
 * {@link com.plumejade.lensouls.mixin.client.BufferSourceGetBufferMixin} 在
 * {@code BufferSource.getBuffer()} RETURN 中读取。
 */
public class CaptureState {

    private static final ThreadLocal<Integer> captureEntityId = ThreadLocal.withInitial(() -> -1);

    private static final IntOpenHashSet capturedThisFrame = new IntOpenHashSet();

    private static MultiBufferSource.BufferSource maskBufferSource;

    private static final ThreadLocal<Boolean> inMaskWrite = ThreadLocal.withInitial(() -> false);

    public static boolean isInMaskWrite() {
        return inMaskWrite.get();
    }

    public static void setInMaskWrite(boolean value) {
        inMaskWrite.set(value);
    }

    /**
     * 尝试捕获实体。如果该实体在本帧已经被捕获过（光影阴影 pass 重复渲染），返回 false。
     */
    public static boolean tryStartCapture(int entityId) {
        if (!capturedThisFrame.add(entityId)) return false;
        captureEntityId.set(entityId);
        return true;
    }

    public static int getCaptureEntityId() {
        return captureEntityId.get();
    }

    public static void setCaptureEntityId(int id) {
        captureEntityId.set(id);
    }

    public static void endCapture() {
        captureEntityId.remove();
    }

    /** 在 AFTER_SKY 清空帧捕获记录，新一帧的所有实体都可重新捕获。 */
    public static void clearFrameCaptures() {
        capturedThisFrame.clear();
    }

    public static void flushMask() {
        if (maskBufferSource != null) {
            maskBufferSource.endBatch();
        }
    }

    public static MultiBufferSource.BufferSource getMaskBufferSource() {
        if (maskBufferSource == null) {
            maskBufferSource = new RenderBuffers(256).bufferSource();
        }
        return maskBufferSource;
    }
}
