package io.github.cmdgz.holdthefort.game;

/**
 * 对局结果。
 */
public enum MatchOutcome {

    /** 还没结束。 */
    ONGOING,

    /** 红方把战线推到蓝方基地（{@code u = U}）。 */
    RED_WIN,

    /** 蓝方把战线推到红方基地（{@code u = L}）。 */
    BLUE_WIN,

    /** 到点结算时双方走的路程一样多。 */
    DRAW
}
