package io.github.cmdgz.holdthefort.game;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;

/**
 * 进度 {@code u} 的推进与结算（§5.3、§5.3.1）。
 *
 * <p>核心式：
 *
 * <pre>
 *   du/dt = V(t) / w(u) * f          f ∈ [-1, 1] 由速率模型给出
 * </pre>
 *
 * <p>本类只管时间与数值，不涉及任何实体、玩家或 Bukkit。
 */
public final class ProgressState {

    private final FrontlineChain chain;
    private final double baseSpeed;
    private final Acceleration acceleration;

    private double u;
    private double elapsed;
    private MatchOutcome outcome = MatchOutcome.ONGOING;

    /**
     * @param chain        已归一化的战线链
     * @param baseSpeed    $V_0$（格/秒）
     * @param acceleration 时间加速曲线
     */
    public ProgressState(FrontlineChain chain, double baseSpeed, Acceleration acceleration) {
        if (baseSpeed <= 0) {
            throw new IllegalArgumentException("V0 必须为正：" + baseSpeed);
        }
        this.chain = chain;
        this.baseSpeed = baseSpeed;
        this.acceleration = acceleration;
    }

    /** 推进一个时间片。{@code factor} 为态势因子，超出 [-1,1] 会被夹住。 */
    public void tick(double dt, double factor) {
        if (outcome != MatchOutcome.ONGOING) {
            return;
        }
        if (dt <= 0) {
            return;
        }
        elapsed += dt;

        if (elapsed >= acceleration.tmaxSeconds()) {
            settleByDistance();
            return;
        }

        final double clamped = Math.max(-1, Math.min(1, factor));
        final double speed = acceleration.speedAt(baseSpeed, elapsed);
        final double weight = chain.weight(chain.segmentIndex(u));
        u = clamp(u + speed / weight * clamped * dt);
        checkEndpoint();
    }

    private double clamp(double value) {
        return Math.max(chain.from(), Math.min(chain.to(), value));
    }

    private void checkEndpoint() {
        if (u >= chain.to()) {
            u = chain.to();
            outcome = MatchOutcome.RED_WIN;
        } else if (u <= chain.from()) {
            u = chain.from();
            outcome = MatchOutcome.BLUE_WIN;
        }
    }

    /** 到点结算：按带符号路程比大小，相等判平（§5.3.1）。 */
    private void settleByDistance() {
        final double distance = chain.pathLength(0, u);
        if (distance > 0) {
            outcome = MatchOutcome.RED_WIN;
        } else if (distance < 0) {
            outcome = MatchOutcome.BLUE_WIN;
        } else {
            outcome = MatchOutcome.DRAW;
        }
    }

    public double u() {
        return u;
    }

    public double elapsedSeconds() {
        return elapsed;
    }

    public MatchOutcome outcome() {
        return outcome;
    }

    public boolean isRunning() {
        return outcome == MatchOutcome.ONGOING;
    }

    /** 当前物理速度（格/秒）。 */
    public double currentSpeed() {
        return acceleration.speedAt(baseSpeed, elapsed);
    }

    /** 带符号路程，正数代表红方领先多少格（用于结算面板与 BossBar）。 */
    public double signedDistance() {
        return chain.pathLength(0, u);
    }
}
