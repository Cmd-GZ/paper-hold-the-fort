package io.github.cmdgz.holdthefort.game;

import java.util.List;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.frontline.Zone;

/**
 * 区域计数（§5.2）：
 *
 * <pre>
 *   r = 红方玩家中落在蓝方区域的人数
 *   b = 蓝方玩家中落在红方区域的人数
 * </pre>
 *
 * <p>只接收已经通过「可计数」筛选的位置 —— 存活、非观战、同世界，这些条件的判断在调用方。
 * 判定范围外与中性带的玩家天然不计入，由 {@link Zone} 保证。
 */
public final class ZoneCounter {

    private ZoneCounter() {
    }

    public static Counts count(FrontlineChain chain, double u,
            List<Vec2> redPositions, List<Vec2> bluePositions) {
        int red = 0;
        for (Vec2 p : redPositions) {
            if (chain.zoneAt(p, u) == Zone.BLUE) {
                red++;
            }
        }
        int blue = 0;
        for (Vec2 p : bluePositions) {
            if (chain.zoneAt(p, u) == Zone.RED) {
                blue++;
            }
        }
        return new Counts(red, blue);
    }

    /**
     * v1 的态势因子 $f=\operatorname{sgn}(r-b)$（§5.5）。
     *
     * <p>只取符号，所以「1 个人越线」和「5 个人越线」推得一样快。
     */
    public static double factor(Counts counts) {
        return Integer.signum(counts.net());
    }
}
