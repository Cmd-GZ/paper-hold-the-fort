package io.github.cmdgz.holdthefort.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.Keyframe;
import io.github.cmdgz.holdthefort.frontline.Vec2;

@DisplayName("ProgressState：推进、加速与结算")
class ProgressStateTest {

    private static final double EPS = 1e-6;

    /** 5 帧、起点在中间：每段 32 格，从起点推到任一端都是 64 格。 */
    private static FrontlineChain chain() {
        return FrontlineChain.build(List.of(
                new Keyframe(-2, new Vec2(-160, -64), new Vec2(160, -64)),
                new Keyframe(-1, new Vec2(-160, -32), new Vec2(160, -32)),
                new Keyframe(0, new Vec2(-160, 0), new Vec2(160, 0)),
                new Keyframe(1, new Vec2(-160, 32), new Vec2(160, 32)),
                new Keyframe(2, new Vec2(-160, 64), new Vec2(160, 64))),
                new Vec2(0, -100), 1.0);
    }

    /** 关掉时间加速，方便算静力学的账。 */
    private static Acceleration noAcceleration() {
        return new Acceleration(1e9, 2e9, 4);
    }

    @Test
    @DisplayName("全程 $S(0→U)$ 等于两段之和")
    void pathToWin() {
        assertEquals(64.0, chain().pathLength(0, 2), EPS);
        assertEquals(-64.0, chain().pathLength(0, -2), EPS);
    }

    @Test
    @DisplayName("恒定优势下，推到 U 的耗时 = 路程 / V₀")
    void constantAdvantageTakesPathOverSpeed() {
        final ProgressState state = new ProgressState(chain(), 0.6, noAcceleration());
        final double dt = 0.05;
        int guard = 0;
        while (state.isRunning() && guard++ < 100_000) {
            state.tick(dt, 1.0);
        }
        assertEquals(MatchOutcome.RED_WIN, state.outcome());
        assertEquals(64.0 / 0.6, state.elapsedSeconds(), 0.2, "64 格 ÷ 0.6 格每秒");
        assertEquals(2.0, state.u(), EPS);
    }

    @Test
    @DisplayName("双方僵持时 u 不动，到点判平")
    void stalemateEndsInDraw() {
        final ProgressState state = new ProgressState(chain(), 0.6, Acceleration.defaults());
        int guard = 0;
        // 逐 tick 推进到兜底时刻；多给一点余量，避免浮点累积误差卡在 900 秒之前
        while (state.isRunning() && guard++ < 20 * 16 * 60) {
            state.tick(0.05, 0.0);
        }
        assertEquals(MatchOutcome.DRAW, state.outcome());
        assertEquals(0.0, state.u(), EPS, "僵持时 u 不应变化");
    }

    @Test
    @DisplayName("时间加速：常规期恒定，中点 2.5 倍，终点 4 倍")
    void accelerationCurve() {
        final Acceleration a = Acceleration.defaults();
        assertEquals(0.6, a.speedAt(0.6, 0), EPS);
        assertEquals(0.6, a.speedAt(0.6, 8 * 60 - 1), EPS);
        assertEquals(0.6 * 2.5, a.speedAt(0.6, 11.5 * 60), 1e-9);
        assertEquals(0.6 * 4, a.speedAt(0.6, 15 * 60), EPS);
    }

    @Test
    @DisplayName("蓝方推进时 u 向负方向走，能推到头")
    void blueCanAdvance() {
        final ProgressState state = new ProgressState(chain(), 0.6, noAcceleration());
        int guard = 0;
        while (state.isRunning() && guard++ < 100_000) {
            state.tick(0.05, -1.0);
        }
        assertEquals(MatchOutcome.BLUE_WIN, state.outcome());
        assertEquals(-2.0, state.u(), EPS);
        assertTrue(state.signedDistance() < 0);
    }

    @Test
    @DisplayName("胜负已分之后不再推进")
    void stopsAfterOutcome() {
        final ProgressState state = new ProgressState(chain(), 0.6, noAcceleration());
        int guard = 0;
        while (state.isRunning() && guard++ < 100_000) {
            state.tick(0.05, 1.0);
        }
        final double elapsed = state.elapsedSeconds();
        state.tick(10, -1.0);
        assertEquals(elapsed, state.elapsedSeconds(), EPS, "结束后不应再累积时间");
        assertEquals(MatchOutcome.RED_WIN, state.outcome());
    }

    @Test
    @DisplayName("tick 的粒度不影响结果：1 秒拆成 20 个 tick 与 1 个大 tick 一致")
    void tickGranularityDoesNotMatter() {
        final ProgressState fine = new ProgressState(chain(), 0.6, noAcceleration());
        for (int i = 0; i < 20; i++) {
            fine.tick(0.05, 1.0);
        }
        final ProgressState coarse = new ProgressState(chain(), 0.6, noAcceleration());
        coarse.tick(1.0, 1.0);
        assertEquals(coarse.u(), fine.u(), 1e-9);
    }
}
