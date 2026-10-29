package io.github.cmdgz.holdthefort.map;

import io.github.cmdgz.holdthefort.frontline.Vec2;

/**
 * 一个出生点。战线判定只用水平坐标，但传送需要 {@code y}，所以这里保留三维。
 */
public record Spawn(double x, double y, double z) {

    /** 供战线计算使用的水平投影。 */
    public Vec2 horizontal() {
        return new Vec2(x, z);
    }
}
