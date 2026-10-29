package io.github.cmdgz.holdthefort.frontline;

/**
 * 水平平面上的二维向量，对应 Minecraft 的 {@code (x, z)}。
 *
 * <p>战线相关的所有计算都忽略 {@code y}，因此用这个类型而不是三维向量。
 */
public record Vec2(double x, double z) {

    /**
     * 把 {@code -0.0} 归一成 {@code 0.0}。
     *
     * <p>record 生成的 {@code equals} 用 {@link Double#compare}，会把 {@code -0.0} 和 {@code 0.0}
     * 判为不等；旋转运算很容易产生 {@code -0.0}，所以在构造时统一。
     */
    public Vec2 {
        x = x == 0.0 ? 0.0 : x;
        z = z == 0.0 ? 0.0 : z;
    }

    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 add(Vec2 o) {
        return new Vec2(x + o.x, z + o.z);
    }

    public Vec2 sub(Vec2 o) {
        return new Vec2(x - o.x, z - o.z);
    }

    public Vec2 scale(double s) {
        return new Vec2(x * s, z * s);
    }

    public Vec2 negate() {
        return new Vec2(-x, -z);
    }

    public double dot(Vec2 o) {
        return x * o.x + z * o.z;
    }

    public double length() {
        return Math.hypot(x, z);
    }

    /**
     * 返回单位向量。
     *
     * @throws IllegalArgumentException 零向量无法归一化
     */
    public Vec2 normalized() {
        final double len = length();
        if (len < 1e-9) {
            throw new IllegalArgumentException("零向量无法归一化");
        }
        return new Vec2(x / len, z / len);
    }

    /** 在 xz 平面内按 {@code (x, z) → (−z, x)} 旋转 90°。 */
    public Vec2 rot90() {
        return new Vec2(-z, x);
    }

    /**
     * 两个单位向量之间的**有符号最短夹角**（弧度），范围 {@code (−π, π]}。
     *
     * <p>等价于 {@code wrap(θ₂ − θ₁)}：先取最短弧再量角度，所以不会出现「差 358°」这种值。
     */
    public static double signedAngle(Vec2 from, Vec2 to) {
        final double cross = from.x * to.z - from.z * to.x;
        final double dot = from.dot(to);
        return Math.atan2(cross, dot);
    }

    /** 按 Minecraft 约定由朝向求 yaw（弧度）：{@code atan2(−n_x, n_z)}。 */
    public double toYawRadians() {
        return Math.atan2(-x, z);
    }

    /** 由 yaw（弧度）还原朝向：{@code (−sin θ, cos θ)}。 */
    public static Vec2 fromYawRadians(double theta) {
        return new Vec2(-Math.sin(theta), Math.cos(theta));
    }
}
