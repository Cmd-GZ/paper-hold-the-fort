package io.github.cmdgz.holdthefort.config;

import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import io.github.cmdgz.holdthefort.game.Acceleration;
import io.github.cmdgz.holdthefort.map.Defaults;
import io.github.cmdgz.holdthefort.map.Spawn;

/**
 * 读全局配置。缺省项一律回落到 {@link PluginConfig#standard()}，读到错值才报错。
 */
public final class ConfigParser {

    private ConfigParser() {
    }

    public static PluginConfig parse(String yaml) {
        final Object root;
        try {
            root = new Yaml().load(yaml);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("config.yml 语法错误：" + e.getMessage());
        }
        if (root == null) {
            return PluginConfig.standard();
        }
        final Map<?, ?> map = asMap(root, "文件根");
        final PluginConfig base = PluginConfig.standard();

        final Map<?, ?> defaults = asMap(map.get("defaults"), "defaults", Map.of());
        final double speed = asDouble(defaults.get("speed"), "defaults.speed", base.defaults().speed());
        final double epsilon = asDouble(defaults.get("epsilon"), "defaults.epsilon", base.defaults().epsilon());

        final Map<?, ?> accel = asMap(map.get("accel"), "accel", Map.of());
        final double t1 = asDouble(accel.get("t1_minutes"), "accel.t1_minutes", 8) * 60;
        final double tmax = asDouble(accel.get("tmax_minutes"), "accel.tmax_minutes", 15) * 60;
        final double multiplier = asDouble(accel.get("multiplier"), "accel.multiplier", 4);

        final Map<?, ?> match = asMap(map.get("match"), "match", Map.of());
        final int minPlayers = asInt(match.get("min_players"), "match.min_players", base.minPlayers());
        final int teamLimit = asInt(match.get("team_limit"), "match.team_limit", base.teamLimit());
        final int respawn = asInt(match.get("respawn_seconds"), "match.respawn_seconds", base.respawnSeconds());
        final boolean allowSameWorld = asBoolean(
                match.get("allow_same_world"), "match.allow_same_world", base.allowSameWorld());
        final boolean configureWorld = asBoolean(
                match.get("configure_world"), "match.configure_world", base.configureWorld());

        final Map<?, ?> lobby = asMap(map.get("lobby"), "lobby", Map.of());
        final String lobbyWorld = lobby.get("world") == null
                ? base.lobbyWorld()
                : String.valueOf(lobby.get("world"));
        final Spawn lobbyPos = new Spawn(
                asDouble(lobby.get("x"), "lobby.x", base.lobby().x()),
                asDouble(lobby.get("y"), "lobby.y", base.lobby().y()),
                asDouble(lobby.get("z"), "lobby.z", base.lobby().z()));

        return new PluginConfig(
                new Defaults(speed, epsilon),
                new Acceleration(t1, tmax, multiplier),
                minPlayers,
                teamLimit,
                respawn,
                lobbyWorld,
                lobbyPos,
                allowSameWorld,
                configureWorld);
    }

    private static Map<?, ?> asMap(Object raw, String where) {
        return asMap(raw, where, Map.of());
    }

    private static Map<?, ?> asMap(Object raw, String where, Map<?, ?> fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Map<?, ?> m) {
            return m;
        }
        throw new IllegalArgumentException(where + " 应当是一个映射");
    }

    private static double asDouble(Object raw, String where, double fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException(where + " 应当是数字，实际是 " + raw);
    }

    private static boolean asBoolean(Object raw, String where, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException(where + " 应当是 true/false，实际是 " + raw);
    }

    private static int asInt(Object raw, String where, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException(where + " 应当是整数，实际是 " + raw);
    }
}
