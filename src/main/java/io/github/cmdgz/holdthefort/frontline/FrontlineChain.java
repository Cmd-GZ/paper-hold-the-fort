package io.github.cmdgz.holdthefort.frontline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 归一化之后的整条战线链（§6.2），负责三件事：
 *
 * <ul>
 *   <li>把每帧的朝向定下来（锚定起点帧 + 向两侧传播）</li>
 *   <li>在任意进度 {@code u} 处给出战线形态（§6 的插值）</li>
 *   <li>判定某个点属于哪一侧、是否在判定范围外（§5.1）</li>
 * </ul>
 *
 * <p>本类**不依赖 Bukkit**，全部逻辑可用纯 JUnit 覆盖。
 */
public final class FrontlineChain {

    private final int from;
    private final int to;
    private final Keyframe[] frames;
    private final Vec2[] normals;
    private final double[] yaws;
    private final double epsilon;

    private FrontlineChain(int from, Keyframe[] frames, Vec2[] normals, double epsilon) {
        this.from = from;
        this.to = from + frames.length - 1;
        this.frames = frames;
        this.normals = normals;
        this.epsilon = epsilon;
        this.yaws = new double[normals.length];
        for (int i = 0; i < normals.length; i++) {
            this.yaws[i] = normals[i].toYawRadians();
        }
    }

    /**
     * 按 §6.2 的两步构建整条链。
     *
     * @param input    关键帧，编号必须连续且包含 {@code 0}，且 {@code 0} 不在两端
     * @param redSpawn 红方出生点，用来锚定起点帧的朝向
     * @param epsilon  中性带半宽
     * @throws FrontlineException 数据自相矛盾（L 级）
     */
    public static FrontlineChain build(List<Keyframe> input, Vec2 redSpawn, double epsilon) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(redSpawn, "redSpawn");
        if (epsilon < 0) {
            throw new FrontlineException("中性带宽度不能为负：" + epsilon);
        }
        if (input.size() < 3) {
            throw new FrontlineException("至少需要 3 个关键帧，实际 " + input.size());
        }

        final List<Keyframe> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingInt(Keyframe::index));

        final int from = sorted.getFirst().index();
        final int to = sorted.getLast().index();
        for (int i = 0; i < sorted.size(); i++) {
            final int expected = from + i;
            if (sorted.get(i).index() != expected) {
                throw new FrontlineException("关键帧编号必须连续，缺少编号 " + expected);
            }
        }
        if (from > -1 || to < 1) {
            throw new FrontlineException(
                    "起点帧（编号 0）必须既不是首帧也不是末帧，当前范围 " + from + ".." + to);
        }

        final Keyframe[] frames = sorted.toArray(Keyframe[]::new);
        final Vec2[] normals = new Vec2[frames.length];
        final int zero = -from;

        // 第一步：锚定起点帧 —— 取使红方落在后方的那个候选
        final Keyframe anchor = frames[zero];
        final Vec2 toRed = redSpawn.sub(anchor.midpoint());
        final double sRed = anchor.candidateA().dot(toRed);
        if (Math.abs(sRed) <= epsilon) {
            throw new FrontlineException("红方出生点落在第 0 帧的线上，无法确定朝向");
        }
        normals[zero] = sRed < 0 ? anchor.candidateA() : anchor.candidateB();

        // 第二步：向两侧传播
        for (int i = zero + 1; i < frames.length; i++) {
            normals[i] = pickCloser(normals[i - 1], frames[i]);
        }
        for (int i = zero - 1; i >= 0; i--) {
            normals[i] = pickCloser(normals[i + 1], frames[i]);
        }

        final FrontlineChain chain = new FrontlineChain(from, frames, normals, epsilon);

        // L3：相邻两帧不得完全相同，否则该段路程权重为 0，后续会除零
        for (int k = from; k < to; k++) {
            if (chain.weight(k) <= 1e-9) {
                throw new FrontlineException(
                        "第 " + k + " 帧与第 " + (k + 1) + " 帧完全相同，该段路程权重为 0");
            }
        }
        return chain;
    }

    /** 两个候选互为反向，取与邻居点积非负的那个；恰好 90° 时取候选 A。 */
    private static Vec2 pickCloser(Vec2 neighbour, Keyframe frame) {
        final Vec2 a = frame.candidateA();
        return a.dot(neighbour) >= 0 ? a : frame.candidateB();
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public double epsilon() {
        return epsilon;
    }

    public int frameCount() {
        return frames.length;
    }

    public Keyframe keyframe(int index) {
        return frames[index - from];
    }

    /** 归一化之后第 {@code index} 帧的朝向。 */
    public Vec2 normalAt(int index) {
        return normals[index - from];
    }

    /** {@code u} 所在段的段号，夹到 {@code [from, to-1]}。 */
    public int segmentIndex(double u) {
        final int k = (int) Math.floor(u);
        return Math.max(from, Math.min(to - 1, k));
    }

    /** 进度 {@code u} 处的战线形态；{@code u} 会自动夹到 {@code [from, to]}。 */
    public FrontlineSample sample(double u) {
        final double clamped = Math.max(from, Math.min(to, u));
        final int k = segmentIndex(clamped);
        final double tau = clamped - k;

        final Keyframe a = frames[k - from];
        final Keyframe b = frames[k + 1 - from];

        final Vec2 center = a.midpoint().add(b.midpoint().sub(a.midpoint()).scale(tau));
        final double halfLength = a.halfLength() + tau * (b.halfLength() - a.halfLength());

        final Vec2 na = normals[k - from];
        final Vec2 nb = normals[k + 1 - from];
        final double yaw = yaws[k - from] + tau * Vec2.signedAngle(na, nb);
        final Vec2 normal = Vec2.fromYawRadians(yaw);
        // 线段方向与朝向垂直：d = (n_z, −n_x)，与 Keyframe.direction() 至多差一个符号
        final Vec2 direction = new Vec2(normal.z(), -normal.x());

        return new FrontlineSample(clamped, center, direction, normal, halfLength, yaw);
    }

    /** 判定某个点相对战线的位置（§5.1 的三态 + 中性带）。 */
    public Zone zoneAt(Vec2 point, double u) {
        final FrontlineSample s = sample(u);
        final Vec2 v = point.sub(s.center());
        if (Math.abs(v.dot(s.direction())) > s.halfLength()) {
            return Zone.OUT_OF_RANGE;
        }
        final double side = v.dot(s.normal());
        if (side > epsilon) {
            return Zone.BLUE;
        }
        if (side < -epsilon) {
            return Zone.RED;
        }
        return Zone.DEAD_BAND;
    }

    /**
     * 第 {@code segment} 段（连接 {@code segment} 与 {@code segment+1} 两帧）的路程权重（§5.6）。
     *
     * <p>平移距离 + 平均半长 × 最短转角（弧度）。转角用最短弧，所以不会把「差 2°」算成「差 358°」。
     */
    public double weight(int segment) {
        if (segment < from || segment >= to) {
            throw new IllegalArgumentException("段号越界：" + segment + "，应为 " + from + ".." + (to - 1));
        }
        final Keyframe a = frames[segment - from];
        final Keyframe b = frames[segment + 1 - from];
        final double move = b.midpoint().sub(a.midpoint()).length();
        final double avgHalfLength = (a.halfLength() + b.halfLength()) / 2.0;
        final double turn = Math.abs(
                Vec2.signedAngle(normals[segment - from], normals[segment + 1 - from]));
        return move + avgHalfLength * turn;
    }

    /** 全程路程 {@code S}（§5.6）。 */
    public double totalLength() {
        double sum = 0;
        for (int k = from; k < to; k++) {
            sum += weight(k);
        }
        return sum;
    }

    /**
     * 带符号路程 {@code S(a→b)}（§5.6）：积分而不是求和，所以端点可以是小数。
     *
     * <p>{@code a<b} 时为正；末段按比例计入。
     */
    public double pathLength(double a, double b) {
        final double from2 = Math.max(from, Math.min(to, a));
        final double to2 = Math.max(from, Math.min(to, b));
        if (from2 == to2) {
            return 0;
        }
        final double sign = to2 > from2 ? 1 : -1;
        final double lo = Math.min(from2, to2);
        final double hi = Math.max(from2, to2);

        double sum = 0;
        double x = lo;
        int guard = 0;
        while (x < hi - 1e-9 && guard++ < 10_000) {
            final int k = segmentIndex(x);
            final double next = Math.min(k + 1, hi);
            sum += weight(k) * (next - x);
            x = next;
        }
        return sign * sum;
    }
}
