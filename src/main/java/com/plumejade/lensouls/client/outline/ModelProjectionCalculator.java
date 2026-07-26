package com.plumejade.lensouls.client.outline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.*;

public final class ModelProjectionCalculator {
    private static final float MODEL_MIN = -0.30F;
    private static final float MODEL_MAX = 1.30F;
    private static final float CLIP_EPSILON = 1.0e-4F;
    private static final float TIGHT_W_EPSILON = 1.0e-4F;
    private static final float BOX_EPSILON = 1.0e-3F;
    
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int[][] BOX_EDGES = {
        {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3}, {2, 6}, {3, 7},
        {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };
    
    private static final Map<BakedModel, ModelBounds> MODEL_BOUNDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<BakedModel, ModelProjectionData> MODEL_PROJECTION_DATA = Collections.synchronizedMap(new WeakHashMap<>());
    private static final RandomSource BOUNDS_RAND = RandomSource.create(0L);
    
    private static final Matrix4f SCRATCH_COMBINED_MATRIX = new Matrix4f();
    private static final Vector4f SCRATCH_CLIP_VECTOR = new Vector4f();
    private static final float[] SCRATCH_CLIP_X = new float[8];
    private static final float[] SCRATCH_CLIP_Y = new float[8];
    private static final float[] SCRATCH_CLIP_Z = new float[8];
    private static final float[] SCRATCH_CLIP_W = new float[8];
    
    private ModelProjectionCalculator() {}
    
    public static ScreenRect projectItemBoundsToScreen(PoseStack poseStack, BakedModel model, int targetWidth, int targetHeight) {
        updateCombinedMatrix(poseStack);
        ModelProjectionData projectionData = getModelProjectionData(model);
        
        if (!projectionData.isValid()) {
            return projectItemBoundsToScreenLegacy(poseStack, targetWidth, targetHeight);
        }
        
        FloatBounds ndcBounds = new FloatBounds();
        int count = accumulateProjectedModelBounds(projectionData, ndcBounds);
        
        if (count == 0 || ndcBounds.hasNonFinite()) {
            return ScreenRect.empty();
        }
        
        ndcBounds.clampToNdc();
        float minScreenX = (ndcBounds.minX * 0.5F + 0.5F) * targetWidth;
        float maxScreenX = (ndcBounds.maxX * 0.5F + 0.5F) * targetWidth;
        float minScreenY = (ndcBounds.minY * 0.5F + 0.5F) * targetHeight;
        float maxScreenY = (ndcBounds.maxY * 0.5F + 0.5F) * targetHeight;
        
        return toScreenRect(minScreenX, minScreenY, maxScreenX, maxScreenY, targetWidth, targetHeight);
    }
    
    private static ScreenRect projectItemBoundsToScreenLegacy(PoseStack poseStack, int targetWidth, int targetHeight) {
        updateCombinedMatrix(poseStack);
        
        int index = 0;
        for (float x : new float[]{MODEL_MIN, MODEL_MAX}) {
            for (float y : new float[]{MODEL_MIN, MODEL_MAX}) {
                for (float z : new float[]{MODEL_MIN, MODEL_MAX}) {
                    storeTransformedCorner(index, x, y, z);
                    index++;
                }
            }
        }
        
        FloatBounds bounds = new FloatBounds();
        int projectedCount = accumulateLegacyProjectedBounds(bounds, targetWidth, targetHeight);
        
        if (projectedCount == 0 || bounds.hasNonFinite()) {
            return ScreenRect.empty();
        }
        
        return toScreenRect(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, targetWidth, targetHeight);
    }
    
    private static void updateCombinedMatrix(PoseStack poseStack) {
        SCRATCH_COMBINED_MATRIX.set(RenderSystem.getProjectionMatrix())
            .mul(RenderSystem.getModelViewMatrix())
            .mul(poseStack.last().pose());
    }
    
    private static void storeTransformedCorner(int index, float x, float y, float z) {
        SCRATCH_CLIP_VECTOR.set(x, y, z, 1.0F);
        SCRATCH_COMBINED_MATRIX.transform(SCRATCH_CLIP_VECTOR);
        SCRATCH_CLIP_X[index] = SCRATCH_CLIP_VECTOR.x;
        SCRATCH_CLIP_Y[index] = SCRATCH_CLIP_VECTOR.y;
        SCRATCH_CLIP_Z[index] = SCRATCH_CLIP_VECTOR.z;
        SCRATCH_CLIP_W[index] = SCRATCH_CLIP_VECTOR.w;
    }
    
    private static int accumulateProjectedModelBounds(ModelProjectionData projectionData, FloatBounds bounds) {
        List<ModelVertex> vertices = projectionData.vertices();
        int vertexCount = vertices.size();
        float[] clipX = new float[vertexCount];
        float[] clipY = new float[vertexCount];
        float[] clipZ = new float[vertexCount];
        float[] clipW = new float[vertexCount];
        int count = 0;
        
        for (int i = 0; i < vertexCount; i++) {
            ModelVertex vertex = vertices.get(i);
            SCRATCH_CLIP_VECTOR.set(vertex.x(), vertex.y(), vertex.z(), 1.0F);
            SCRATCH_COMBINED_MATRIX.transform(SCRATCH_CLIP_VECTOR);
            clipX[i] = SCRATCH_CLIP_VECTOR.x;
            clipY[i] = SCRATCH_CLIP_VECTOR.y;
            clipZ[i] = SCRATCH_CLIP_VECTOR.z;
            clipW[i] = SCRATCH_CLIP_VECTOR.w;
            
            if (hasNonFiniteClipVertex(clipX[i], clipY[i], clipZ[i], clipW[i]) 
                    || isOutsideClipVolumeXYZ(clipX[i], clipY[i], clipZ[i], clipW[i])) {
                continue;
            }
            
            bounds.include(clipX[i] / clipW[i], clipY[i] / clipW[i]);
            count++;
        }
        
        for (ModelEdge edge : projectionData.edges()) {
            int a = edge.a();
            int b = edge.b();
            count += accumulateClippedEdge(bounds, clipX[a], clipY[a], clipZ[a], clipW[a], 
                    clipX[b], clipY[b], clipZ[b], clipW[b], TIGHT_W_EPSILON);
        }
        
        return count;
    }
    
    private static int accumulateLegacyProjectedBounds(FloatBounds bounds, int width, int height) {
        int projectedCount = 0;
        
        for (int i = 0; i < 8; i++) {
            float clipX = SCRATCH_CLIP_X[i];
            float clipY = SCRATCH_CLIP_Y[i];
            float clipW = SCRATCH_CLIP_W[i];
            
            if (!Float.isFinite(clipX) || !Float.isFinite(clipY) || !Float.isFinite(clipW) || clipW <= CLIP_EPSILON) {
                continue;
            }
            
            float ndcX = clipX / clipW;
            float ndcY = clipY / clipW;
            
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
                continue;
            }
            
            bounds.include(ndcX, ndcY);
            projectedCount++;
        }
        
        for (int[] edge : BOX_EDGES) {
            int aIndex = edge[0];
            int bIndex = edge[1];
            projectedCount += accumulateClippedEdge(bounds, 
                    SCRATCH_CLIP_X[aIndex], SCRATCH_CLIP_Y[aIndex], SCRATCH_CLIP_Z[aIndex], SCRATCH_CLIP_W[aIndex],
                    SCRATCH_CLIP_X[bIndex], SCRATCH_CLIP_Y[bIndex], SCRATCH_CLIP_Z[bIndex], SCRATCH_CLIP_W[bIndex],
                    CLIP_EPSILON);
        }
        
        bounds.clampToNdc();
        bounds.scaleToScreen(width, height);
        return projectedCount;
    }
    
    private static int accumulateClippedEdge(FloatBounds bounds, float ax, float ay, float az, float aw,
                                              float bx, float by, float bz, float bw, float minW) {
        if (hasNonFiniteClipVertex(ax, ay, az, aw) || hasNonFiniteClipVertex(bx, by, bz, bw)) {
            return 0;
        }
        
        float t0 = 0.0F;
        float t1 = 1.0F;
        
        if (rejectByClipVolume(ax, ay, az, aw, bx, by, bz, bw, minW, t0, t1) || t1 <= t0) {
            return 0;
        }
        
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float dw = bw - aw;
        
        return includeClippedEndpoint(bounds, ax, ay, az, aw, dx, dy, dz, dw, t0) 
                + includeClippedEndpoint(bounds, ax, ay, az, aw, dx, dy, dz, dw, t1);
    }
    
    private static int includeClippedEndpoint(FloatBounds bounds, float ax, float ay, float az, float aw,
                                               float dx, float dy, float dz, float dw, float t) {
        float x = ax + dx * t;
        float y = ay + dy * t;
        float z = az + dz * t;
        float w = aw + dw * t;
        
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)) {
            return 0;
        }
        
        if (isOutsideClipVolumeXYZ(x, y, z, w)) {
            return 0;
        }
        
        bounds.include(x / w, y / w);
        return 1;
    }
    
    private static boolean rejectByClipVolume(float ax, float ay, float az, float aw,
                                               float bx, float by, float bz, float bw,
                                               float minW, float t0, float t1) {
        return rejectByClipPlane(ax + aw, bx + bw, t0, t1) 
                || rejectByClipPlane(-ax + aw, -bx + bw, t0, t1) 
                || rejectByClipPlane(ay + aw, by + bw, t0, t1) 
                || rejectByClipPlane(-ay + aw, -by + bw, t0, t1) 
                || rejectByClipPlane(az + aw, bz + bw, t0, t1) 
                || rejectByClipPlane(-az + aw, -bz + bw, t0, t1) 
                || rejectByClipPlane(aw - minW, bw - minW, t0, t1);
    }
    
    private static boolean rejectByClipPlane(float f0, float f1, float t0, float t1) {
        if (f0 >= 0.0F && f1 >= 0.0F) return false;
        if (f0 < 0.0F && f1 < 0.0F) return true;
        
        float denominator = f0 - f1;
        if (!Float.isFinite(denominator) || Math.abs(denominator) < 1.0e-20F) return true;
        
        float t = f0 / denominator;
        if (!Float.isFinite(t)) return true;
        
        if (f0 < 0.0F) {
            t0 = Math.max(t0, t);
        } else {
            t1 = Math.min(t1, t);
        }
        
        return t0 > t1;
    }
    
    private static boolean isOutsideClipVolumeXYZ(float x, float y, float z, float w) {
        return w <= TIGHT_W_EPSILON || x < -w || x > w || y < -w || y > w || z < -w || z > w;
    }
    
    private static boolean hasNonFiniteClipVertex(float x, float y, float z, float w) {
        return !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w);
    }
    
    private static ScreenRect toScreenRect(float minScreenX, float minScreenY, 
                                           float maxScreenX, float maxScreenY, 
                                           int width, int height) {
        if (!Float.isFinite(minScreenX) || !Float.isFinite(minScreenY) 
                || !Float.isFinite(maxScreenX) || !Float.isFinite(maxScreenY)) {
            return ScreenRect.empty();
        }
        
        int x0 = Mth.clamp((int) Math.floor(minScreenX), 0, width);
        int y0 = Mth.clamp((int) Math.floor(minScreenY), 0, height);
        int x1 = Mth.clamp((int) Math.ceil(maxScreenX), 0, width);
        int y1 = Mth.clamp((int) Math.ceil(maxScreenY), 0, height);
        
        if (x1 <= x0 || y1 <= y0) {
            return ScreenRect.empty();
        }
        
        return new ScreenRect(x0, y0, x1, y1);
    }
    
    private static ModelBounds getModelBounds(BakedModel model) {
        return MODEL_BOUNDS.computeIfAbsent(model, ModelProjectionCalculator::computeModelBounds);
    }
    
    private static ModelBounds computeModelBounds(BakedModel model) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        boolean any = false;
        
        for (int pass = -1; pass < DIRECTIONS.length; pass++) {
            Direction side = pass < 0 ? null : DIRECTIONS[pass];
            BOUNDS_RAND.setSeed(0L);
            
            for (BakedQuad quad : model.getQuads(null, side, BOUNDS_RAND)) {
                int[] vertices = quad.getVertices();
                if (vertices.length < 12) continue;
                
                int stride = vertices.length / 4;
                for (int i = 0; i < 4; i++) {
                    int base = i * stride;
                    float x = Float.intBitsToFloat(vertices[base]);
                    float y = Float.intBitsToFloat(vertices[base + 1]);
                    float z = Float.intBitsToFloat(vertices[base + 2]);
                    
                    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;
                    
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                    any = true;
                }
            }
        }
        
        if (!any) {
            return new ModelBounds(MODEL_MIN, MODEL_MIN, MODEL_MIN, MODEL_MAX, MODEL_MAX, MODEL_MAX);
        }
        
        return new ModelBounds(minX - BOX_EPSILON, minY - BOX_EPSILON, minZ - BOX_EPSILON,
                               maxX + BOX_EPSILON, maxY + BOX_EPSILON, maxZ + BOX_EPSILON);
    }
    
    private static ModelProjectionData getModelProjectionData(BakedModel model) {
        return MODEL_PROJECTION_DATA.computeIfAbsent(model, ModelProjectionCalculator::computeModelProjectionData);
    }
    
    private static ModelProjectionData computeModelProjectionData(BakedModel model) {
        ArrayList<ModelVertex> vertices = new ArrayList<>();
        ArrayList<ModelEdge> edges = new ArrayList<>();
        
        for (int pass = -1; pass < DIRECTIONS.length; pass++) {
            Direction side = pass < 0 ? null : DIRECTIONS[pass];
            BOUNDS_RAND.setSeed(0L);
            
            for (BakedQuad quad : model.getQuads(null, side, BOUNDS_RAND)) {
                int[] quadVertices = quad.getVertices();
                if (quadVertices.length < 12) continue;
                
                int stride = quadVertices.length / 4;
                int firstIndex = vertices.size();
                boolean validQuad = true;
                
                for (int i = 0; i < 4; i++) {
                    int base = i * stride;
                    float x = Float.intBitsToFloat(quadVertices[base]);
                    float y = Float.intBitsToFloat(quadVertices[base + 1]);
                    float z = Float.intBitsToFloat(quadVertices[base + 2]);
                    
                    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                        validQuad = false;
                        break;
                    }
                    
                    vertices.add(new ModelVertex(x, y, z));
                }
                
                if (!validQuad) {
                    while (vertices.size() > firstIndex) {
                        vertices.remove(vertices.size() - 1);
                    }
                    continue;
                }
                
                edges.add(new ModelEdge(firstIndex, firstIndex + 1));
                edges.add(new ModelEdge(firstIndex + 1, firstIndex + 2));
                edges.add(new ModelEdge(firstIndex + 2, firstIndex + 3));
                edges.add(new ModelEdge(firstIndex + 3, firstIndex));
            }
        }
        
        if (vertices.isEmpty() || edges.isEmpty()) {
            ModelBounds bounds = getModelBounds(model);
            ArrayList<ModelVertex> fallbackVertices = new ArrayList<>(8);
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.minY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.minY(), bounds.maxZ()));
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.maxY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.maxY(), bounds.maxZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.minY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.minY(), bounds.maxZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.maxY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
            
            ArrayList<ModelEdge> fallbackEdges = new ArrayList<>(BOX_EDGES.length);
            for (int[] edge : BOX_EDGES) {
                fallbackEdges.add(new ModelEdge(edge[0], edge[1]));
            }
            
            return new ModelProjectionData(Collections.unmodifiableList(fallbackVertices), 
                    Collections.unmodifiableList(fallbackEdges));
        }
        
        return new ModelProjectionData(Collections.unmodifiableList(vertices), 
                Collections.unmodifiableList(edges));
    }
    
    private static final class FloatBounds {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        
        void include(float x, float y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        
        boolean hasNonFinite() {
            return !Float.isFinite(minX) || !Float.isFinite(minY) 
                    || !Float.isFinite(maxX) || !Float.isFinite(maxY);
        }
        
        void clampToNdc() {
            minX = Math.max(minX, -1.0F);
            minY = Math.max(minY, -1.0F);
            maxX = Math.min(maxX, 1.0F);
            maxY = Math.min(maxY, 1.0F);
        }
        
        void scaleToScreen(int width, int height) {
            minX = (minX * 0.5F + 0.5F) * width;
            minY = (minY * 0.5F + 0.5F) * height;
            maxX = (maxX * 0.5F + 0.5F) * width;
            maxY = (maxY * 0.5F + 0.5F) * height;
        }
    }
}