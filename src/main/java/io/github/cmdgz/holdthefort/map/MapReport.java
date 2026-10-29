package io.github.cmdgz.holdthefort.map;

import java.util.ArrayList;
import java.util.List;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.frontline.Zone;

/**
 * 生成地图加载时的读数（§5.6、§6.2）。
 *
 * <p>纯字符串拼接，不依赖 Bukkit —— 这样格式本身也能被单测覆盖。
 */
public final class MapReport {

    private MapReport() {
    }

    /**
     * 两行读数：路程与耗时、开局站位。
     *
     * @param map   地图定义
     * @param chain 已经归一化的战线链
     */
    public static List<String> describe(MapDefinition map, FrontlineChain chain) {
        final List<String> lines = new ArrayList<>(2);

        final double toBlue = chain.pathLength(0, chain.to());
        final double toRed = Math.abs(chain.pathLength(0, chain.from()));
        final double slower = Math.max(toBlue, toRed);

        lines.add(String.format(
                "起点→蓝方基地 %.1f 格 | 起点→红方基地 %.1f 格 | V₀ = %.2f 格/秒 | 常规期约 %s | 共 %d 个关键帧",
                toBlue, toRed, map.speed(), duration(slower / map.speed()), chain.frameCount()));

        final Vec2 red = map.redSpawn().horizontal();
        final Vec2 blue = map.blueSpawn().horizontal();
        lines.add(String.format(
                "开局站位：红方 %s = %s | 蓝方 %s = %s",
                point(red), side(chain.zoneAt(red, 0)),
                point(blue), side(chain.zoneAt(blue, 0))));

        return List.copyOf(lines);
    }

    /** 把秒数写成「x 分 y 秒」，不足一分钟只写秒。 */
    public static String duration(double seconds) {
        final long total = Math.round(seconds);
        if (total < 60) {
            return total + " 秒";
        }
        return (total / 60) + " 分 " + (total % 60) + " 秒";
    }

    /** 坐标写成 {@code (x, z)}，保留一位小数且去掉多余的 .0。 */
    public static String point(Vec2 p) {
        return "(" + trim(p.x()) + ", " + trim(p.z()) + ")";
    }

    /** 判定结果的中文说法。 */
    public static String side(Zone zone) {
        return switch (zone) {
            case BLUE -> "前方";
            case RED -> "后方";
            case DEAD_BAND -> "线上";
            case OUT_OF_RANGE -> "范围外";
        };
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        final String fixed = String.format("%.2f", value);
        return fixed.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
