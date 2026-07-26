package com.plumejade.lensouls.client.outline;

import java.util.List;

public record ModelProjectionData(List<ModelVertex> vertices, List<ModelEdge> edges) {
    public boolean isValid() {
        return !vertices.isEmpty() && !edges.isEmpty();
    }
}