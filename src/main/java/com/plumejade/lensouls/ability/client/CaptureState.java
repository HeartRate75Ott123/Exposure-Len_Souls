package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
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

    // ---- mask 顶点内存收集（GeckoLib 多部件崩溃修复） ----
    // GeckoLib 每骨骼递归 checkAndRefreshBuffer 只认识 BufferBuilder/EntityOutlineGenerator/Double，
    // 不认识我们的 MaskColorConsumer——多纹理实体的 maskType 切换触发 BufferSource 自动 endBatch 后，
    // GeckoLib 仍向已关闭的 BufferBuilder 写顶点 → "Not building!" 崩溃。
    // 改为：渲染期间只收集顶点（含 maskType/矩阵/uv/normal），帧末统一提交——写入时机完全自控。

    /** 单个待提交的 mask 顶点条目。 */
    public static final class MaskVertexEntry {
        final RenderType maskType;
        /** null = 3 参 addVertex（走 RenderSystem 全局矩阵）；非 null = 矩阵版（深拷贝，防渲染后矩阵被改）。 */
        final Matrix4f matrix;
        final float x, y, z;
        float u, v;
        int uv1U, uv1V;
        int uv2U, uv2V;
        float nx, ny, nz;

        MaskVertexEntry(RenderType maskType, Matrix4f matrix, float x, float y, float z) {
            this.maskType = maskType;
            this.matrix = matrix != null ? new Matrix4f(matrix) : null;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final List<MaskVertexEntry> PENDING_MASK_VERTICES = new ArrayList<>();
    private static MaskVertexEntry currentMaskVertex;

    /** 开始一个新 mask 顶点（隐式结束上一个）。 */
    public static void startMaskVertex(RenderType maskType, Matrix4f matrix, float x, float y, float z) {
        endMaskVertex();
        currentMaskVertex = new MaskVertexEntry(maskType, matrix, x, y, z);
        recordMaskVertex(x, y, z);
    }

    public static void setMaskVertexUv(float u, float v) {
        if (currentMaskVertex != null) {
            currentMaskVertex.u = u;
            currentMaskVertex.v = v;
        }
    }

    public static void setMaskVertexUv1(int u, int v) {
        if (currentMaskVertex != null) {
            currentMaskVertex.uv1U = u;
            currentMaskVertex.uv1V = v;
        }
    }

    public static void setMaskVertexUv2(int u, int v) {
        if (currentMaskVertex != null) {
            currentMaskVertex.uv2U = u;
            currentMaskVertex.uv2V = v;
        }
    }

    public static void setMaskVertexNormal(float nx, float ny, float nz) {
        if (currentMaskVertex != null) {
            currentMaskVertex.nx = nx;
            currentMaskVertex.ny = ny;
            currentMaskVertex.nz = nz;
        }
    }

    /** 结束当前顶点并入列（未开始则无操作）。 */
    public static void endMaskVertex() {
        if (currentMaskVertex != null) {
            PENDING_MASK_VERTICES.add(currentMaskVertex);
            currentMaskVertex = null;
        }
    }

    /** 把收集的 mask 顶点按 maskType 分组写入 mask FBO 并清空。 */
    private static void commitMaskVertices() {
        if (PENDING_MASK_VERTICES.isEmpty()) return;
        MultiBufferSource.BufferSource source = getMaskBufferSource();
        VertexConsumer consumer = null;
        for (MaskVertexEntry e : PENDING_MASK_VERTICES) {
            if (consumer == null || e.maskType != consumerType) {
                consumerType = e.maskType;
                consumer = source.getBuffer(e.maskType);
            }
            if (e.matrix != null) {
                consumer.addVertex(e.matrix, e.x, e.y, e.z);
            } else {
                consumer.addVertex(e.x, e.y, e.z);
            }
            consumer.setColor(255, 255, 255, 255);
            consumer.setUv(e.u, e.v);
            consumer.setUv1(e.uv1U, e.uv1V);
            consumer.setUv2(e.uv2U, e.uv2V);
            consumer.setNormal(e.nx, e.ny, e.nz);
        }
        source.endBatch();
        PENDING_MASK_VERTICES.clear();
    }

    private static RenderType consumerType;

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
        endMaskVertex();
        commitMaskVertices();
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

    // ---- 实体纹理 ResourceLocation 提取（虚灵半透明用） ----

    private static final Map<RenderType, ResourceLocation> TEXTURE_LOC_CACHE = new IdentityHashMap<>();

    /** 提取渲染类型对应的实体纹理 ResourceLocation（失败返回 null）。 */
    public static ResourceLocation entityTextureLocation(RenderType type) {
        return TEXTURE_LOC_CACHE.computeIfAbsent(type, CaptureState::extractTextureLocation);
    }

    private static ResourceLocation extractTextureLocation(RenderType type) {
        try {
            Method stateMethod = findDeclaredMethod(type.getClass(), "state");
            Object state = stateMethod != null ? stateMethod.invoke(type) : null;
            if (state == null) return null;
            Field textureStateField = findDeclaredField(state.getClass(), "textureState");
            Object textureState = textureStateField != null ? textureStateField.get(state) : null;
            if (textureState == null) return null;
            Method cutoutMethod = findDeclaredMethod(textureState.getClass(), "cutoutTexture");
            Object loc = cutoutMethod != null ? cutoutMethod.invoke(textureState) : null;
            if (loc instanceof Optional<?> opt && opt.isPresent()
                    && opt.get() instanceof ResourceLocation rl) {
                return rl;
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ---- 通用 mask 顶点收集 consumer（冻结 / BOSS 描边共用；GeckoLib 安全） ----
    // 顶点颜色强制白色（仿原版 EntityOutlineGenerator），Sobel/distance-field 只响应形状边缘。
    // 顶点不直接写 buffer，而是收集进 CaptureState —— 规避 GeckoLib 对未知 consumer
    // 不做「buffer 已 end 则刷新」判定导致写已关闭 buffer 崩溃（"Not building!"）。

    public static final class MaskColorConsumer implements VertexConsumer {

        private final RenderType maskType;

        public MaskColorConsumer(RenderType maskType) {
            this.maskType = maskType;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            CaptureState.startMaskVertex(maskType, null, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer addVertex(Matrix4f matrix4f, float x, float y, float z) {
            CaptureState.startMaskVertex(maskType, matrix4f, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            CaptureState.setMaskVertexUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            CaptureState.setMaskVertexUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            CaptureState.setMaskVertexUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            CaptureState.setMaskVertexNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
