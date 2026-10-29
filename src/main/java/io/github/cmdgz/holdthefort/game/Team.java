package io.github.cmdgz.holdthefort.game;

/**
 * 两支队伍。
 */
public enum Team {

    /** 红方：基地在编号 $L$ 端，推进方向为编号增大。 */
    RED,

    /** 蓝方：基地在编号 $U$ 端，推进方向为编号减小。 */
    BLUE;

    public Team opposite() {
        return this == RED ? BLUE : RED;
    }
}
