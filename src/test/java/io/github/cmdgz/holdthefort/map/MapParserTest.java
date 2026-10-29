package io.github.cmdgz.holdthefort.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.cmdgz.holdthefort.frontline.Vec2;

@DisplayName("MapParser：读地图 YAML")
class MapParserTest {

    private static final double EPS = 1e-9;

    private static String readResource(String path) throws IOException {
        try (InputStream in = MapParserTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("找不到资源 " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("仓库自带示例地图能被解析并通过校验")
    void exampleFromResourcesIsClean() throws IOException {
        final MapDefinition map = MapParser.parse(readResource("/maps/river_crossing.yml"), Defaults.standard());
        assertEquals("river_crossing", map.name());
        assertEquals("htf_arena", map.world(), "示例地图必须使用独立世界，不能落在主世界");
        assertEquals(5, map.checkpoints().size());
        assertEquals(2, map.startIndex());
        assertEquals(TurnLimit.NINETY, map.turnLimit());

        final MapValidator.Result result = MapValidator.validate(map);
        assertFalse(result.hasErrors(), "示例地图不应有 L 级问题：" + result.problems());
        assertEquals(List.of(), result.problems(), "示例地图不应有任何提示");
    }

    @Test
    @DisplayName("坐标、出生点、缺省值都读对")
    void parsesFieldsAndDefaults() {
        final String yaml = """
                name: test
                world: w
                spawns:
                  red:  { x: 1, y: 2, z: 3 }
                  blue: { x: 4, y: 5, z: 6 }
                frontline:
                  start_index: 1
                  checkpoints:
                    - { p: { x: -10, z: -10 }, q: { x: 10, z: -10 } }
                    - { p: { x: -10, z:   0 }, q: { x: 10, z:   0 } }
                    - { p: { x: -10, z:  10 }, q: { x: 10, z:  10 } }
                """;
        final MapDefinition map = MapParser.parse(yaml, new Defaults(0.75, 2.0));
        assertEquals(1.0, map.redSpawn().x(), EPS);
        assertEquals(2.0, map.redSpawn().y(), EPS);
        assertEquals(6.0, map.blueSpawn().z(), EPS);
        assertEquals(0.75, map.speed(), EPS, "未写 speed 时用全局默认");
        assertEquals(2.0, map.epsilon(), EPS, "未写 epsilon 时用全局默认");
        assertEquals(TurnLimit.NINETY, map.turnLimit(), "未写 turn_limit 时默认 90");
        assertEquals(new CheckpointPair(new Vec2(-10, -10), new Vec2(10, -10)), map.checkpoints().getFirst());
    }

    @Test
    @DisplayName("turn_limit 写成整数 90 也能读")
    void turnLimitAsNumber() {
        final String yaml = """
                name: t
                world: w
                spawns:
                  red:  { x: 0, y: 64, z: -100 }
                  blue: { x: 0, y: 64, z:  100 }
                frontline:
                  start_index: 1
                  turn_limit: 90
                  checkpoints:
                    - { p: { x: -10, z: -10 }, q: { x: 10, z: -10 } }
                    - { p: { x: -10, z:   0 }, q: { x: 10, z:   0 } }
                    - { p: { x: -10, z:  10 }, q: { x: 10, z:  10 } }
                """;
        assertEquals(TurnLimit.NINETY, MapParser.parse(yaml, Defaults.standard()).turnLimit());
    }

    @Test
    @DisplayName("缺少必填字段时报错并指出位置")
    void reportsMissingField() {
        final String yaml = """
                name: t
                world: w
                frontline:
                  start_index: 1
                  checkpoints: []
                """;
        final MapParseException e = assertThrows(MapParseException.class,
                () -> MapParser.parse(yaml, Defaults.standard()));
        assertTrue(e.getMessage().contains("spawns"), "错误信息应指出字段名：" + e.getMessage());
    }

    @Test
    @DisplayName("非法的 turn_limit 被拒绝")
    void rejectsBadTurnLimit() {
        final String yaml = """
                name: t
                world: w
                spawns:
                  red:  { x: 0, y: 64, z: -100 }
                  blue: { x: 0, y: 64, z:  100 }
                frontline:
                  start_index: 1
                  turn_limit: 45
                  checkpoints: []
                """;
        final MapParseException e = assertThrows(MapParseException.class,
                () -> MapParser.parse(yaml, Defaults.standard()));
        assertTrue(e.getMessage().contains("45"));
    }

    @Test
    @DisplayName("YAML 语法错误会带上原因")
    void reportsSyntaxError() {
        final MapParseException e = assertThrows(MapParseException.class,
                () -> MapParser.parse("name: [unclosed", Defaults.standard()));
        assertTrue(e.getMessage().contains("YAML"));
    }
}
