package com.plumejade.lensouls.client.outline;

public record ScreenRect(int minX, int minY, int maxX, int maxY) {
    public int width() { return maxX - minX; }
    public int height() { return maxY - minY; }
    public boolean isEmpty() { return width() <= 0 || height() <= 0; }
    
    public ScreenRect expand(int pad) {
        return new ScreenRect(minX - pad, minY - pad, maxX + pad, maxY + pad);
    }
    
    public boolean intersects(ScreenRect other) {
        return this.maxX > other.minX && this.minX < other.maxX 
            && this.maxY > other.minY && this.minY < other.maxY;
    }
    
    public static ScreenRect empty() {
        return new ScreenRect(0, 0, 0, 0);
    }
}