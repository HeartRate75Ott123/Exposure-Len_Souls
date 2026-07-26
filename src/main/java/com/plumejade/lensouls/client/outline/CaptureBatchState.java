package com.plumejade.lensouls.client.outline;

public final class CaptureBatchState {
    public boolean initialized;
    public boolean needsDepth;
    public boolean firstPersonHandFastPath;
    public boolean maskDirty;
    public int dirtyMinX = Integer.MAX_VALUE;
    public int dirtyMinY = Integer.MAX_VALUE;
    public int dirtyMaxX = Integer.MIN_VALUE;
    public int dirtyMaxY = Integer.MIN_VALUE;
    public int dirtyMaxRadius = 1;

    public boolean hasDirtyRect() {
        return dirtyMinX < dirtyMaxX && dirtyMinY < dirtyMaxY;
    }

    public ScreenRect dirtyRect() {
        return hasDirtyRect() ? new ScreenRect(dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY) : ScreenRect.empty();
    }

    public void mergeDirtyRect(ScreenRect rect, int radiusPixels) {
        if (!hasDirtyRect()) {
            dirtyMinX = rect.minX();
            dirtyMinY = rect.minY();
            dirtyMaxX = rect.maxX();
            dirtyMaxY = rect.maxY();
            dirtyMaxRadius = radiusPixels;
            return;
        }
        dirtyMinX = Math.min(dirtyMinX, rect.minX());
        dirtyMinY = Math.min(dirtyMinY, rect.minY());
        dirtyMaxX = Math.max(dirtyMaxX, rect.maxX());
        dirtyMaxY = Math.max(dirtyMaxY, rect.maxY());
        dirtyMaxRadius = Math.max(dirtyMaxRadius, radiusPixels);
    }

    public void resetDirtyRect() {
        dirtyMinX = Integer.MAX_VALUE;
        dirtyMinY = Integer.MAX_VALUE;
        dirtyMaxX = Integer.MIN_VALUE;
        dirtyMaxY = Integer.MIN_VALUE;
    }

    public void reset() {
        initialized = false;
        needsDepth = false;
        firstPersonHandFastPath = false;
        maskDirty = false;
        dirtyMaxRadius = 1;
        resetDirtyRect();
    }

    public void copyFrom(CaptureBatchState other) {
        this.initialized = other.initialized;
        this.needsDepth = other.needsDepth;
        this.firstPersonHandFastPath = other.firstPersonHandFastPath;
        this.maskDirty = other.maskDirty;
        this.dirtyMinX = other.dirtyMinX;
        this.dirtyMinY = other.dirtyMinY;
        this.dirtyMaxX = other.dirtyMaxX;
        this.dirtyMaxY = other.dirtyMaxY;
        this.dirtyMaxRadius = other.dirtyMaxRadius;
    }
}