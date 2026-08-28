package com.plumejade.lensouls.client.itemoutline;

/** 第一人称手持物描边数据：单色（0xRRGGBB）+ 描边宽度（像素） */
public record ItemOutlineData(int rgb, int radiusPixels) {
}
