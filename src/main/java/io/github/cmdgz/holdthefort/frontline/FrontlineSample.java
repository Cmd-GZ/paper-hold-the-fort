package io.github.cmdgz.holdthefort.frontline;

/**
 * 进度为 {@code u} 时战线的形态。
 *
 * @param u          已经夹到 {@code [L, U]} 的进度
 * @param center     线段中心（也就是战线实体的位置）
 * @param direction  沿线段的单位方向（指向哪一边不重要，判定只用绝对值）
 * @param normal     单位朝向，指向**蓝方区域**一侧
 * @param halfLength 半长：线段从中心向两侧各延伸这么多
 * @param yawRadians 对应的 Minecraft yaw（弧度）
 */
public record FrontlineSample(
        double u,
        Vec2 center,
        Vec2 direction,
        Vec2 normal,
        double halfLength,
        double yawRadians) {
}
