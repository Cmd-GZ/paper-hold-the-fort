package io.github.cmdgz.holdthefort.frontline;

/**
 * 玩家相对战线的位置，共四种状态（§5.1）。
 *
 * <p>注意 {@link #OUT_OF_RANGE} 和 {@link #DEAD_BAND} 都**不参与计数**，区别在于：
 * 前者是「站在线段两端之外」，后者是「压在战线上」。
 */
public enum Zone {

    /** 战线前方 = 蓝方区域。 */
    BLUE,

    /** 战线后方 = 红方区域。 */
    RED,

    /** 压在战线上（{@code |s| ≤ ε}），不计数，用于防抖。 */
    DEAD_BAND,

    /** 在线段两端的延长线之外，既不算入侵也不算守卫。 */
    OUT_OF_RANGE
}
