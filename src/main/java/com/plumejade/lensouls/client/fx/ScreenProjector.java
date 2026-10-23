package com.plumejade.lensouls.client.fx;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 世界坐标 → 屏幕 UV（0..1）。
 * <p>
 * 矩阵取 {@code RenderLevelStageEvent} 的投影矩阵与"只含相机旋转"的 modelView，
 * 相机平移单独存，因此纯数学即可把任意世界点投到屏幕上——不需要 mixin，
 * 供"从目标身上扩散的涟漪""索命方向的拉力线"这类效果使用。
 */
public final class ScreenProjector {

    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f modelView = new Matrix4f();
    private static final Vector3f cameraPos = new Vector3f();
    private static boolean valid;

    private ScreenProjector() {
    }

    public static void update(Matrix4f projectionMatrix, Matrix4f modelViewMatrix, Vec3 camera) {
        projection.set(projectionMatrix);
        modelView.set(modelViewMatrix);
        cameraPos.set((float) camera.x, (float) camera.y, (float) camera.z);
        valid = true;
    }

    /**
     * @return {@code [u, v, visible]}；visible = 1 表示在相机前方
     */
    public static float[] project(Vec3 world) {
        if (!valid) return new float[]{0.5F, 0.5F, 0.0F};
        Vector4f p = new Vector4f(
                (float) (world.x - cameraPos.x),
                (float) (world.y - cameraPos.y),
                (float) (world.z - cameraPos.z),
                1.0F);
        modelView.transform(p);
        projection.transform(p);
        if (p.w <= 1.0E-4F) return new float[]{0.5F, 0.5F, 0.0F};
        float ndcX = p.x / p.w;
        float ndcY = p.y / p.w;
        return new float[]{ndcX * 0.5F + 0.5F, ndcY * 0.5F + 0.5F, 1.0F};
    }
}
