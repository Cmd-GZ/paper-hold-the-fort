package io.github.cmdgz.holdthefort.frontline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FrontlineChain：归一化、插值与判定")
class FrontlineChainTest {

    private static final double EPS = 1e-9;
    private static final double DEFAULT_EPSILON = 1.0;

    /** 文档 §6.2 的例子：三帧中点 (0,0)、(0,1)、(0,-1)，红方出生点 (0,-100)。 */
    private static List<Keyframe> zigzag() {
        return List.of(
                new Keyframe(-1, new Vec2(-50, 0), new Vec2(50, 0)),
                new Keyframe(0, new Vec2(-50, 1), new Vec2(50, 1)),
                new Keyframe(1, new Vec2(-50, -1), new Vec2(50, -1)));
    }

    /**
     * 盲评给的反例（原编号 1..4、起点在 3，这里整体平移成 -2..1、起点在 0）。
     * 相邻夹角依次 0°/90°/90°/0°，累积却转了 270°。
     */
    private static List<Keyframe> cumulativeRotation() {
        return List.of(
                new Keyframe(-2, new Vec2(-10, -50), new Vec2(10, -50)),
                new Keyframe(-1, new Vec2(50, -60), new Vec2(50, -40)),
                new Keyframe(0, new Vec2(40, -70), new Vec2(60, -70)),
                new Keyframe(1, new Vec2(40, -90), new Vec2(60, -90)));
    }

    private static double angleDegrees(Vec2 a, Vec2 b) {
        return Math.toDegrees(Math.abs(Vec2.signedAngle(a, b)));
    }

    @Test
    @DisplayName("锚定：起点帧处红方必定落在后方（不可能是蓝区）")
    void anchorPutsRedBehind() {
        final Vec2 redSpawn = new Vec2(0, -100);

        // 反复横跳的例子：出生点在线段跨度内，应当明确落在红区
        final FrontlineChain zig = FrontlineChain.build(zigzag(), redSpawn, DEFAULT_EPSILON);
        assertEquals(Zone.RED, zig.zoneAt(redSpawn, 0));

        // 累积旋转的反例：出生点离线段很远，属于「判定范围外」，但绝不能是蓝区
        final FrontlineChain cum = FrontlineChain.build(cumulativeRotation(), redSpawn, DEFAULT_EPSILON);
        assertNotEquals(Zone.BLUE, cum.zoneAt(redSpawn, 0));
        final FrontlineSample s = cum.sample(0);
        assertTrue(redSpawn.sub(s.center()).dot(s.normal()) < 0, "红方必须在朝向的反侧");
    }

    @Test
    @DisplayName("向两侧传播后，相邻夹角处处不超过 90°")
    void adjacentAnglesWithinNinety() {
        for (List<Keyframe> frames : List.of(zigzag(), cumulativeRotation())) {
            final FrontlineChain chain = FrontlineChain.build(frames, new Vec2(0, -100), DEFAULT_EPSILON);
            for (int i = chain.from(); i < chain.to(); i++) {
                final double angle = angleDegrees(chain.normalAt(i), chain.normalAt(i + 1));
                assertTrue(angle <= 90 + 1e-6, "第 " + i + " 与 " + (i + 1) + " 帧夹角 " + angle + " 超过 90°");
            }
        }
    }

    @Test
    @DisplayName("反复横跳的例子：三帧朝向一致，且第 0→1 段是后退")
    void zigzagKeepsOrientation() {
        final FrontlineChain chain = FrontlineChain.build(zigzag(), new Vec2(0, -100), DEFAULT_EPSILON);
        final Vec2 expected = new Vec2(0, 1);
        for (int i = chain.from(); i <= chain.to(); i++) {
            assertEquals(0.0, chain.normalAt(i).sub(expected).length(), EPS, "第 " + i + " 帧朝向应保持一致");
        }
        // 第 0 帧中点 (0,1) → 第 1 帧中点 (0,-1)：位移与朝向反向 = 后退
        final Vec2 move = new Vec2(0, -2);
        assertTrue(move.dot(chain.normalAt(0)) < 0, "第 0→1 段应当是后退");
    }

    @Test
    @DisplayName("插值在两端正好落在首末帧上")
    void interpolationHitsEndpoints() {
        final FrontlineChain chain = FrontlineChain.build(zigzag(), new Vec2(0, -100), DEFAULT_EPSILON);

        final FrontlineSample atFrom = chain.sample(chain.from());
        assertEquals(chain.keyframe(chain.from()).midpoint(), atFrom.center());
        assertEquals(chain.keyframe(chain.from()).halfLength(), atFrom.halfLength(), EPS);

        final FrontlineSample atTo = chain.sample(chain.to());
        assertEquals(chain.keyframe(chain.to()).midpoint(), atTo.center());
        assertEquals(chain.keyframe(chain.to()).halfLength(), atTo.halfLength(), EPS);
    }

    @Test
    @DisplayName("超出范围的进度会被夹住，不会去取不存在的下一帧")
    void interpolationClamps() {
        final FrontlineChain chain = FrontlineChain.build(zigzag(), new Vec2(0, -100), DEFAULT_EPSILON);
        assertEquals(chain.sample(chain.to()).center(), chain.sample(chain.to() + 10).center());
        assertEquals(chain.sample(chain.from()).center(), chain.sample(chain.from() - 10).center());
    }

    @Test
    @DisplayName("三态判定：蓝区 / 红区 / 中性带 / 判定范围外")
    void zoneClassification() {
        final FrontlineChain chain = FrontlineChain.build(zigzag(), new Vec2(0, -100), DEFAULT_EPSILON);

        assertEquals(Zone.BLUE, chain.zoneAt(new Vec2(0, 60), 0));
        assertEquals(Zone.RED, chain.zoneAt(new Vec2(0, -60), 0));
        assertEquals(Zone.DEAD_BAND, chain.zoneAt(new Vec2(0, 1), 0));
        // 沿线段方向跑到两端之外
        assertEquals(Zone.OUT_OF_RANGE, chain.zoneAt(new Vec2(200, 60), 0));
        assertEquals(Zone.OUT_OF_RANGE, chain.zoneAt(new Vec2(-200, 60), 0));
        // 范围外的点即使落在「前方」也不算蓝区
        assertTrue(chain.zoneAt(new Vec2(200, 60), 0) != Zone.BLUE);
    }

    @Test
    @DisplayName("路程权重 = 平移 + 平均半长 × 转角")
    void weights() {
        final FrontlineChain chain = FrontlineChain.build(zigzag(), new Vec2(0, -100), DEFAULT_EPSILON);
        // 第 -1→0 段：中点 (0,0)→(0,1)，平移 1 格，无旋转
        assertEquals(1.0, chain.weight(-1), EPS);
        // 第 0→1 段：中点 (0,1)→(0,-1)，平移 2 格，无旋转
        assertEquals(2.0, chain.weight(0), EPS);
        assertEquals(3.0, chain.totalLength(), EPS);
    }

    @Test
    @DisplayName("纯旋转段（中点不动、只转向）路程权重为正，不会除零")
    void pureRotationHasPositiveWeight() {
        final List<Keyframe> frames = List.of(
                new Keyframe(-1, new Vec2(-50, -30), new Vec2(50, -30)),
                new Keyframe(0, new Vec2(-50, 0), new Vec2(50, 0)),
                new Keyframe(1, new Vec2(0, -50), new Vec2(0, 50)));
        final FrontlineChain chain = FrontlineChain.build(frames, new Vec2(0, -100), DEFAULT_EPSILON);
        // 第 0→1 段：中点都是 (0,0)，只转了 90° → 权重 = 平均半长 × π/2
        final double expected = 50.0 * (Math.PI / 2);
        assertEquals(expected, chain.weight(0), 1e-6);
    }

    @Test
    @DisplayName("相邻两帧完全相同会被拒绝")
    void identicalFramesRejected() {
        final List<Keyframe> frames = List.of(
                new Keyframe(-1, new Vec2(-50, 0), new Vec2(50, 0)),
                new Keyframe(0, new Vec2(-50, 0), new Vec2(50, 0)),
                new Keyframe(1, new Vec2(-50, 10), new Vec2(50, 10)));
        assertThrows(FrontlineException.class,
                () -> FrontlineChain.build(frames, new Vec2(0, -100), DEFAULT_EPSILON));
    }

    @Test
    @DisplayName("路径长度按积分计算，端点可以是小数")
    void pathLengthSupportsFractions() {
        final FrontlineChain chain = FrontlineChain.build(zigzag(), new Vec2(0, -100), DEFAULT_EPSILON);
        assertEquals(2.0, chain.pathLength(0, 1), 1e-9, "第 0→1 段共 2 格");
        assertEquals(1.0, chain.pathLength(0, 0.5), 1e-9, "半段按比例计入");
        assertEquals(-2.0, chain.pathLength(1, 0), 1e-9, "反向为负");
        assertEquals(-0.5, chain.pathLength(0, -0.5), 1e-9, "向左半段为负，w_{-1}=1");
    }

    @Test
    @DisplayName("非法数据被拒绝")
    void rejectsInvalid() {
        final Vec2 red = new Vec2(0, -100);
        // 少于三帧
        assertThrows(FrontlineException.class, () -> FrontlineChain.build(
                List.of(new Keyframe(-1, new Vec2(-1, 0), new Vec2(1, 0)),
                        new Keyframe(0, new Vec2(-1, 1), new Vec2(1, 1))),
                red, DEFAULT_EPSILON));
        // 起点帧在端点
        assertThrows(FrontlineException.class, () -> FrontlineChain.build(
                List.of(new Keyframe(0, new Vec2(-1, 0), new Vec2(1, 0)),
                        new Keyframe(1, new Vec2(-1, 1), new Vec2(1, 1)),
                        new Keyframe(2, new Vec2(-1, 2), new Vec2(1, 2))),
                red, DEFAULT_EPSILON));
        // 编号不连续
        assertThrows(FrontlineException.class, () -> FrontlineChain.build(
                List.of(new Keyframe(-2, new Vec2(-1, 0), new Vec2(1, 0)),
                        new Keyframe(0, new Vec2(-1, 1), new Vec2(1, 1)),
                        new Keyframe(1, new Vec2(-1, 2), new Vec2(1, 2))),
                red, DEFAULT_EPSILON));
        // 红方出生点压在第 0 帧的线上
        assertThrows(FrontlineException.class, () -> FrontlineChain.build(
                zigzag(), new Vec2(0, 1), DEFAULT_EPSILON));
    }
}
