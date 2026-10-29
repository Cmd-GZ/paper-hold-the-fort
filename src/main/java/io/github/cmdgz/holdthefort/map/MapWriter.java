package io.github.cmdgz.holdthefort.map;

import java.util.List;

/**
 * 把地图定义写成 YAML 文本，供游戏内建场直接落盘。
 *
 * <p>输出与手写的地图文件格式完全一致，管理员之后仍可手动编辑。
 */
public final class MapWriter {

    private MapWriter() {
    }

    public static String toYaml(MapDefinition map) {
        final StringBuilder sb = new StringBuilder();
        sb.append("# 由 /htf setup 在游戏内生成，可直接手动修改\n");
        sb.append("name: ").append(map.name()).append('\n');
        sb.append("world: ").append(map.world()).append('\n');
        sb.append('\n');
        sb.append("spawns:\n");
        sb.append("  red:  ").append(spawn(map.redSpawn())).append('\n');
        sb.append("  blue: ").append(spawn(map.blueSpawn())).append('\n');
        sb.append('\n');
        sb.append("frontline:\n");
        sb.append("  start_index: ").append(map.startIndex()).append('\n');
        sb.append("  speed: ").append(number(map.speed())).append('\n');
        sb.append("  epsilon: ").append(number(map.epsilon())).append('\n');
        sb.append("  turn_limit: ").append(map.turnLimit().yamlValue()).append('\n');
        sb.append("  checkpoints:\n");
        final List<CheckpointPair> pairs = map.checkpoints();
        for (CheckpointPair pair : pairs) {
            sb.append("    - { p: { x: ").append(number(pair.p().x()))
                    .append(", z: ").append(number(pair.p().z()))
                    .append(" }, q: { x: ").append(number(pair.q().x()))
                    .append(", z: ").append(number(pair.q().z()))
                    .append(" } }\n");
        }
        return sb.toString();
    }

    private static String spawn(Spawn spawn) {
        return "{ x: " + number(spawn.x())
                + ", y: " + number(spawn.y())
                + ", z: " + number(spawn.z()) + " }";
    }

    /** 最多两位小数，去掉多余的 0，避免写出 {@code 64.000000000001} 这种值。 */
    static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        String text = String.format("%.2f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
