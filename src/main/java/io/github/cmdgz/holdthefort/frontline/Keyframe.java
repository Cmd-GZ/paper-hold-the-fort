package io.github.cmdgz.holdthefort.frontline;

/**
 * 一段关键帧的纯几何量：由设计者提供的两个坐标算出方向、中点与半长。
 *
 * <p>这是 §2.1 的公式的直接实现。注意它**不包含朝向的正负号** —— 那是归一化（§6.2）的事。
 *
 * @param index 编号，起点帧为 {@code 0}
 * @param p     第一个检查点
 * @param q     第二个检查点
 */
public record Keyframe(int index, Vec2 p, Vec2 q) {

    public Keyframe {
        if (p.equals(q)) {
            throw new IllegalArgumentException("关键帧 " + index + " 的两个检查点重合");
        }
    }

    /** 两点连线的单位方向 {@code d̂}。交换 p、q 只会让它反号。 */
    public Vec2 direction() {
        return q.sub(p).normalized();
    }

    /** 中点 {@code m} —— 战线实体的位置。 */
    public Vec2 midpoint() {
        return p.add(q).scale(0.5);
    }

    /** 战线半长：线段从中点向两侧各延伸这么多。 */
    public double halfLength() {
        return q.sub(p).length() / 2.0;
    }

    /**
     * 两个候选朝向之一（把 {@code d̂} 旋转 90°）。另一个是它的反向。
     *
     * <p>用哪一个由 §6.2 的归一化决定；这里是固定的「第一个候选」，
     * 90° 边界上也取它，保证结果确定。
     */
    public Vec2 candidateA() {
        return direction().rot90();
    }

    /** 另一个候选朝向，与 {@link #candidateA()} 互为反向。 */
    public Vec2 candidateB() {
        return candidateA().negate();
    }
}
