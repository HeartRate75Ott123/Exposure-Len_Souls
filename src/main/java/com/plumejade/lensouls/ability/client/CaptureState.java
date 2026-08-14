package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 冻结实体描边捕获状态管理。
 * <p>
 * 由 {@link com.plumejade.lensouls.mixin.client.EntityRenderDispatcherMixin} 在
 * {@code EntityRenderDispatcher.render()} HEAD/RETURN 中管理生命周期；
 * mask 顶点由 {@link StatusGlintBufferSource}（顶点双写）直接写入
 * {@link #getMaskBufferSource()}。
 * <p>
 * Iris 光影下另由
 * {@code IrisBufferSourceGetBufferMixin}（lensouls.compat.mixins.json）在
 * {@code FullyBufferedMultiBufferSource.getBuffer()} RETURN 中读取。
 */
public class CaptureState {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ThreadLocal<Integer> captureEntityId = ThreadLocal.withInitial(() -> -1);

    private static final IntOpenHashSet capturedThisFrame = new IntOpenHashSet();

    private static MultiBufferSource.BufferSource maskBufferSource;

    private static final ThreadLocal<Boolean> inMaskWrite = ThreadLocal.withInitial(() -> false);

    /** 主渲染 pass 标志：AFTER_SKY 后置 true、AFTER_LEVEL 置 false——Iris 阴影 pass 在 AFTER_SKY 之前，据此隔离。 */
    private static boolean mainPassActive = false;

    public static boolean isMainPassActive() {
        return mainPassActive;
    }

    public static void setMainPassActive(boolean value) {
        mainPassActive = value;
    }

    // ---- 调试：mask 顶点统计（定位描边错位/方框） ----
    private static int maskVertexCount = 0;
    private static float maskMinX = Float.MAX_VALUE, maskMaxX = -Float.MAX_VALUE;
    private static float maskMinY = Float.MAX_VALUE, maskMaxY = -Float.MAX_VALUE;
    private static float maskMinZ = Float.MAX_VALUE, maskMaxZ = -Float.MAX_VALUE;

    public static boolean isInMaskWrite() {
        return inMaskWrite.get();
    }

    public static void setInMaskWrite(boolean value) {
        inMaskWrite.set(value);
    }

    /** 记录一个 mask 顶点（相机空间坐标），用于 flush 时输出 AABB 调试日志。 */
    public static void recordMaskVertex(float x, float y, float z) {
        maskVertexCount++;
        if (x < maskMinX) maskMinX = x;
        if (x > maskMaxX) maskMaxX = x;
        if (y < maskMinY) maskMinY = y;
        if (y > maskMaxY) maskMaxY = y;
        if (z < maskMinZ) maskMinZ = z;
        if (z > maskMaxZ) maskMaxZ = z;
    }

    private static void resetMaskStats() {
        maskVertexCount = 0;
        maskMinX = maskMaxX = maskMinY = maskMaxY = maskMinZ = maskMaxZ = 0;
        maskMinX = maskMinY = maskMinZ = Float.MAX_VALUE;
        maskMaxX = maskMaxY = maskMaxZ = -Float.MAX_VALUE;
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
        if (maskVertexCount > 0) {
            LOGGER.info("[FrozenMask] entity={} vertices={} AABB=({},{},{})-({},{},{})",
                    captureEntityId.get(), maskVertexCount,
                    maskMinX, maskMinY, maskMinZ, maskMaxX, maskMaxY, maskMaxZ);
        }
        resetMaskStats();
    }

    public static MultiBufferSource.BufferSource getMaskBufferSource() {
        if (maskBufferSource == null) {
            maskBufferSource = new RenderBuffers(256).bufferSource();
        }
        return maskBufferSource;
    }

    // ---- 实体纹理提取（mask/glint alpha 测试剔除透明面；对齐原版 rendertype_outline 语义） ----
    // 反射路径：CompositeRenderType.state()（protected）→ CompositeState.textureState
    // → TextureStateShard.cutoutTexture()（protected）→ Optional<ResourceLocation>。
    // 失败兜底：1×1 白色不透明纹理（alpha=1，不剔除任何面，退化为现状）。
    public static ShaderInstance glintEntityShader;

    private static final Map<RenderType, Integer> TEXTURE_ID_CACHE = new IdentityHashMap<>();
    private static DynamicTexture whitePixelTexture;

    /**
     * 提取渲染类型对应的实体纹理 ID（反射 + IdentityHashMap 缓存；失败兜底白像素）。
     * per-tex 方案：每层类型即时提取，层切换即 flush——每层 mask/glint 用自己纹理
     * 做 alpha 测试，修复多纹理实体（焰魔身体+盾牌、斯库拉身体+剑）的误滤。
     */
    public static int entityTextureId(RenderType type) {
        return TEXTURE_ID_CACHE.computeIfAbsent(type, CaptureState::extractTextureId);
    }

    private static int extractTextureId(RenderType type) {
        try {
            Method stateMethod = findDeclaredMethod(type.getClass(), "state");
            Object state = stateMethod != null ? stateMethod.invoke(type) : null;
            if (state == null) return whitePixelTextureId();
            Field textureStateField = findDeclaredField(state.getClass(), "textureState");
            Object textureState = textureStateField != null ? textureStateField.get(state) : null;
            if (textureState == null) return whitePixelTextureId();
            Method cutoutMethod = findDeclaredMethod(textureState.getClass(), "cutoutTexture");
            Object loc = cutoutMethod != null ? cutoutMethod.invoke(textureState) : null;
            if (loc instanceof Optional<?> opt && opt.isPresent()
                    && opt.get() instanceof ResourceLocation rl) {
                return Minecraft.getInstance().getTextureManager().getTexture(rl).getId();
            }
            return whitePixelTextureId();
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[FrozenMask] 反射提取实体纹理失败（fallback 白像素）: {}", e.toString());
            return whitePixelTextureId();
        }
    }

    private static Method findDeclaredMethod(Class<?> start, String name) {
        Class<?> c = start;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Field findDeclaredField(Class<?> start, String name) {
        Class<?> c = start;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** 白像素兜底纹理 ID（alpha=1，不剔除任何面）。调试层/反射失败时使用。 */
    public static int whitePixelTextureId() {
        if (whitePixelTexture == null) {
            NativeImage image = new NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, 0xFFFFFFFF);
            whitePixelTexture = new DynamicTexture(image);
            whitePixelTexture.upload();
        }
        return whitePixelTexture.getId();
    }
}
