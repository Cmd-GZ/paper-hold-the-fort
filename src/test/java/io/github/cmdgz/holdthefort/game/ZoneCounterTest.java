package io.github.cmdgz.holdthefort.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.Keyframe;
import io.github.cmdgz.holdthefort.frontline.Vec2;

@DisplayName("ZoneCounter：谁在谁的地盘里")
class ZoneCounterTest {

    /** 起点在 (0,0)、平面朝 +z、半长 50、中性带 1 格。 */
    private static FrontlineChain chain() {
        return FrontlineChain.build(List.of(
                new Keyframe(-1, new Vec2(-50, -32), new Vec2(50, -32)),
                new Keyframe(0, new Vec2(-50, 0), new Vec2(50, 0)),
                new Keyframe(1, new Vec2(-50, 32), new Vec2(50, 32))),
                new Vec2(0, -100), 1.0);
    }

    private static final Vec2 DEEP_BLUE = new Vec2(0, 40);
    private static final Vec2 DEEP_RED = new Vec2(0, -40);
    private static final Vec2 ON_LINE = new Vec2(0, 0);
    private static final Vec2 OUTSIDE = new Vec2(500, 40);

    @Test
    @DisplayName("红方越线记 r，蓝方越线记 b")
    void countsInvaders() {
        final Counts counts = ZoneCounter.count(chain(), 0,
                List.of(DEEP_BLUE, DEEP_BLUE, DEEP_RED),
                List.of(DEEP_RED, DEEP_BLUE));
        assertEquals(2, counts.redInvaders(), "两个红方在蓝区");
        assertEquals(1, counts.blueInvaders(), "一个蓝方在红区");
        assertEquals(1, counts.net());
    }

    @Test
    @DisplayName("守在自己半场的人不计入")
    void ownSideDoesNotCount() {
        final Counts counts = ZoneCounter.count(chain(), 0,
                List.of(DEEP_RED), List.of(DEEP_BLUE));
        assertEquals(Counts.NONE, counts);
    }

    @Test
    @DisplayName("中性带与判定范围外都不计入")
    void deadBandAndOutsideDoNotCount() {
        final Counts counts = ZoneCounter.count(chain(), 0,
                List.of(ON_LINE, OUTSIDE), List.of(ON_LINE, OUTSIDE));
        assertEquals(Counts.NONE, counts);
    }

    @Test
    @DisplayName("态势因子只取符号：1 人和 5 人推得一样快")
    void factorIsSignOnly() {
        assertEquals(1.0, ZoneCounter.factor(new Counts(1, 0)));
        assertEquals(1.0, ZoneCounter.factor(new Counts(5, 0)));
        assertEquals(-1.0, ZoneCounter.factor(new Counts(0, 3)));
        assertEquals(0.0, ZoneCounter.factor(new Counts(3, 3)));
        assertEquals(0.0, ZoneCounter.factor(Counts.NONE));
    }

    @Test
    @DisplayName("战线碾过一个固定点后，他会从入侵者变成守方")
    void countsFollowProgress() {
        // (0,20) 在 u=0 时位于战线前方（蓝区）
        final Vec2 point = new Vec2(0, 20);
        assertEquals(1, ZoneCounter.count(chain(), 0, List.of(point), List.of()).redInvaders());
        // 战线推到 z=32 之后，这个点已被甩到后方，不再算入侵者
        assertEquals(0, ZoneCounter.count(chain(), 1, List.of(point), List.of()).redInvaders());
    }
}
