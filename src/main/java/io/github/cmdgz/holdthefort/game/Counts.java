package io.github.cmdgz.holdthefort.game;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.Zone;

/**
 * 某次计数的结果（§5.2）。
 *
 * @param redInvaders  红方玩家中落在**蓝方区域**的人数 $r$
 * @param blueInvaders 蓝方玩家中落在**红方区域**的人数 $b$
 */
public record Counts(int redInvaders, int blueInvaders) {

    public static final Counts NONE = new Counts(0, 0);

    public int net() {
        return redInvaders - blueInvaders;
    }
}
