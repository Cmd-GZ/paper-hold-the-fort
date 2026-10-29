package io.github.cmdgz.holdthefort.game;

/**
 * 时间加速曲线（§5.3.1）。
 *
 * @param t1Seconds       加速开始时刻，之前速度恒定
 * @param tmaxSeconds     兜底时刻，到点强制结算
 * @param multiplier      到 {@code tmax} 时的速度倍率
 */
public record Acceleration(double t1Seconds, double tmaxSeconds, double multiplier) {

    public Acceleration {
        if (t1Seconds <= 0 || tmaxSeconds <= t1Seconds || multiplier < 1) {
            throw new IllegalArgumentException(
                    "加速参数不合法：t1=" + t1Seconds + " tmax=" + tmaxSeconds + " M=" + multiplier);
        }
    }

    /** 默认 $T_1=8$ 分钟、$T_{\max}=15$ 分钟、$M=4$。 */
    public static Acceleration defaults() {
        return new Acceleration(8 * 60, 15 * 60, 4);
    }

    /** 给定基准速度与已用时长，返回当前物理速度（格/秒）。 */
    public double speedAt(double baseSpeed, double elapsedSeconds) {
        if (elapsedSeconds < t1Seconds) {
            return baseSpeed;
        }
        if (elapsedSeconds >= tmaxSeconds) {
            return baseSpeed * multiplier;
        }
        final double ratio = (elapsedSeconds - t1Seconds) / (tmaxSeconds - t1Seconds);
        return baseSpeed * (1 + (multiplier - 1) * ratio);
    }
}
