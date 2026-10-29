package io.github.cmdgz.holdthefort.config;

import io.github.cmdgz.holdthefort.game.Acceleration;
import io.github.cmdgz.holdthefort.map.Defaults;
import io.github.cmdgz.holdthefort.map.Spawn;

/**
 * 全局配置（§8.1），对应 {@code plugins/HoldTheFort/config.yml}。
 *
 * @param defaults       地图没写时继承的速度与中性带
 * @param acceleration   时间加速曲线
 * @param minPlayers     开局所需总人数
 * @param teamLimit      每队人数上限
 * @param respawnSeconds 重生等待
 * @param lobbyWorld     大厅所在世界
 * @param lobby          大厅传送点
 */
public record PluginConfig(
        Defaults defaults,
        Acceleration acceleration,
        int minPlayers,
        int teamLimit,
        int respawnSeconds,
        String lobbyWorld,
        Spawn lobby) {

    public static PluginConfig standard() {
        return new PluginConfig(
                Defaults.standard(),
                Acceleration.defaults(),
                2,
                8,
                5,
                "world",
                new Spawn(0.5, 65, 0.5));
    }
}
