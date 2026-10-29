package io.github.cmdgz.holdthefort.game;

/**
 * 对局阶段（§7）。
 */
public enum MatchPhase {

    /** 无人，什么都不做。 */
    IDLE,

    /** 玩家在大厅，等待人数达标。 */
    WAITING,

    /** 人数达标，倒计时中；人数掉回阈值以下则退回 WAITING。 */
    STARTING,

    /** 对局进行中。 */
    RUNNING,

    /** 结算，倒计时回大厅。 */
    ENDING
}
