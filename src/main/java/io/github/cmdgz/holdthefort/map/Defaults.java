package io.github.cmdgz.holdthefort.map;

/**
 * 地图里没写、需要从全局配置继承的默认值。
 *
 * @param speed   物理速度 $V_0$（格/秒）
 * @param epsilon 中性带半宽（格）
 */
public record Defaults(double speed, double epsilon) {

    public static Defaults standard() {
        return new Defaults(0.6, 1.0);
    }
}
