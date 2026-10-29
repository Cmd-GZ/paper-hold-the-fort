package io.github.cmdgz.holdthefort.frontline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Keyframe：由两个坐标算出的几何量")
class KeyframeTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("中点、半长、方向")
    void basicGeometry() {
        final Keyframe k = new Keyframe(0, new Vec2(-10, 4), new Vec2(10, 4));
        assertEquals(new Vec2(0, 4), k.midpoint());
        assertEquals(10.0, k.halfLength(), EPS);
        assertEquals(new Vec2(1, 0), k.direction());
    }

    @Test
    @DisplayName("两个候选互为反向：点积 -1、和为 0")
    void candidatesAreOpposite() {
        final Keyframe k = new Keyframe(0, new Vec2(-3, -1), new Vec2(5, 7));
        assertEquals(-1.0, k.candidateA().dot(k.candidateB()), EPS);
        assertEquals(0.0, k.candidateA().add(k.candidateB()).length(), EPS);
    }

    @Test
    @DisplayName("交换 p、q 只让方向反号，中点和半长不变")
    void orderDoesNotMatter() {
        final Keyframe a = new Keyframe(0, new Vec2(-3, -1), new Vec2(5, 7));
        final Keyframe b = new Keyframe(0, new Vec2(5, 7), new Vec2(-3, -1));
        assertEquals(a.midpoint(), b.midpoint());
        assertEquals(a.halfLength(), b.halfLength(), EPS);
        assertEquals(a.direction(), b.direction().negate());
    }

    @Test
    @DisplayName("两个检查点重合会被拒绝")
    void degenerateRejected() {
        try {
            new Keyframe(0, new Vec2(1, 1), new Vec2(1, 1));
            throw new AssertionError("应当抛出异常");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
