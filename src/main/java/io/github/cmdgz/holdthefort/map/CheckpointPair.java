package io.github.cmdgz.holdthefort.map;

import io.github.cmdgz.holdthefort.frontline.Vec2;

/**
 * 地图文件里一组检查点的原始坐标，**没有编号**。
 *
 * <p>编号由它在列表里的位置和 {@code start_index} 换算得出（§8.2），所以设计者写反 p、q
 * 不影响任何结果。
 */
public record CheckpointPair(Vec2 p, Vec2 q) {
}
