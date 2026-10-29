package io.github.cmdgz.holdthefort.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import io.github.cmdgz.holdthefort.frontline.Vec2;

/**
 * 把地图 YAML 读成 {@link MapDefinition}（§8.2）。
 *
 * <p>只负责「读」：字段缺失、类型不对会抛 {@link MapParseException}；数据是否合理交给
 * {@link MapValidator}。
 */
public final class MapParser {

    private MapParser() {
    }

    /**
     * @param yaml     地图文件全文
     * @param defaults 缺省值（{@code speed} / {@code epsilon} 没写时用）
     */
    public static MapDefinition parse(String yaml, Defaults defaults) {
        final Object root;
        try {
            root = new Yaml().load(yaml);
        } catch (RuntimeException e) {
            throw new MapParseException("YAML 语法错误：" + e.getMessage());
        }
        if (root == null) {
            throw new MapParseException("地图文件是空的");
        }
        final Map<String, Object> map = asMap(root, "文件根");

        final String name = asString(map.get("name"), "name");
        final String world = asString(map.get("world"), "world");

        final Map<String, Object> spawns = asMap(map.get("spawns"), "spawns");
        final Spawn red = asSpawn(spawns.get("red"), "spawns.red");
        final Spawn blue = asSpawn(spawns.get("blue"), "spawns.blue");

        final Map<String, Object> front = asMap(map.get("frontline"), "frontline");
        final int startIndex = asInt(front.get("start_index"), "frontline.start_index");
        final double speed = front.containsKey("speed")
                ? asDouble(front.get("speed"), "frontline.speed")
                : defaults.speed();
        final double epsilon = front.containsKey("epsilon")
                ? asDouble(front.get("epsilon"), "frontline.epsilon")
                : defaults.epsilon();
        final TurnLimit turnLimit = front.containsKey("turn_limit")
                ? parseTurnLimit(front.get("turn_limit"))
                : TurnLimit.NINETY;

        final List<Object> rawCheckpoints = asList(front.get("checkpoints"), "frontline.checkpoints");
        final List<CheckpointPair> checkpoints = new ArrayList<>(rawCheckpoints.size());
        for (int i = 0; i < rawCheckpoints.size(); i++) {
            final String where = "frontline.checkpoints[" + i + "]";
            final Map<String, Object> entry = asMap(rawCheckpoints.get(i), where);
            checkpoints.add(new CheckpointPair(
                    asPoint(entry.get("p"), where + ".p"),
                    asPoint(entry.get("q"), where + ".q")));
        }

        return new MapDefinition(name, world, red, blue, turnLimit, speed, epsilon,
                startIndex, List.copyOf(checkpoints));
    }

    private static TurnLimit parseTurnLimit(Object raw) {
        try {
            return TurnLimit.parse(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            throw new MapParseException("frontline." + e.getMessage());
        }
    }

    private static Spawn asSpawn(Object raw, String where) {
        final Map<String, Object> m = asMap(raw, where);
        return new Spawn(
                asDouble(m.get("x"), where + ".x"),
                asDouble(m.get("y"), where + ".y"),
                asDouble(m.get("z"), where + ".z"));
    }

    private static Vec2 asPoint(Object raw, String where) {
        final Map<String, Object> m = asMap(raw, where);
        return new Vec2(
                asDouble(m.get("x"), where + ".x"),
                asDouble(m.get("z"), where + ".z"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw, String where) {
        if (!(raw instanceof Map<?, ?> m)) {
            throw new MapParseException(where + " 应当是一个映射，实际是 " + describe(raw));
        }
        return (Map<String, Object>) m;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object raw, String where) {
        if (!(raw instanceof List<?> list)) {
            throw new MapParseException(where + " 应当是一个列表，实际是 " + describe(raw));
        }
        return (List<Object>) list;
    }

    private static String asString(Object raw, String where) {
        if (raw == null) {
            throw new MapParseException(where + " 缺失");
        }
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof Number n) {
            return String.valueOf(n);
        }
        throw new MapParseException(where + " 应当是字符串，实际是 " + describe(raw));
    }

    private static int asInt(Object raw, String where) {
        if (raw == null) {
            throw new MapParseException(where + " 缺失");
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        throw new MapParseException(where + " 应当是整数，实际是 " + describe(raw));
    }

    private static double asDouble(Object raw, String where) {
        if (raw == null) {
            throw new MapParseException(where + " 缺失");
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        throw new MapParseException(where + " 应当是数字，实际是 " + describe(raw));
    }

    private static String describe(Object raw) {
        return raw == null ? "null" : raw.getClass().getSimpleName() + "(" + raw + ")";
    }
}
