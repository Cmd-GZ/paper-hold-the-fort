package io.github.cmdgz.holdthefort.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.Keyframe;
import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.frontline.Zone;

@DisplayName("MapReport：加载时的读数")
class MapReportTest {

    private static final FrontlineChain CHAIN = FrontlineChain.build(List.of(
            new Keyframe(-2, new Vec2(-160, -64), new Vec2(160, -64)),
            new Keyframe(-1, new Vec2(-160, -32), new Vec2(160, -32)),
            new Keyframe(0, new Vec2(-160, 0), new Vec2(160, 0)),
            new Keyframe(1, new Vec2(-160, 32), new Vec2(160, 32)),
            new Keyframe(2, new Vec2(-160, 64), new Vec2(160, 64))),
            new Vec2(0, -100), 1.0);

    private static MapDefinition map() {
        return new MapDefinition("demo", "world",
                new Spawn(0, 64, -100), new Spawn(0, 64, 100),
                TurnLimit.NINETY, 0.6, 1.0, 2,
                List.of(new CheckpointPair(new Vec2(-160, -64), new Vec2(160, -64))));
    }

    @Test
    @DisplayName("第一行报出两端路程与耗时")
    void firstLineShowsDistances() {
        final String line = MapReport.describe(map(), CHAIN).getFirst();
        assertTrue(line.contains("起点→蓝方基地 64.0 格"), line);
        assertTrue(line.contains("起点→红方基地 64.0 格"), line);
        assertTrue(line.contains("V₀ = 0.60"), line);
        assertTrue(line.contains("1 分 47 秒"), line);
        assertTrue(line.contains("共 5 个关键帧"), line);
    }

    @Test
    @DisplayName("第二行报出开局站位")
    void secondLineShowsSides() {
        final String line = MapReport.describe(map(), CHAIN).get(1);
        assertTrue(line.contains("红方 (0, -100) = 后方"), line);
        assertTrue(line.contains("蓝方 (0, 100) = 前方"), line);
    }

    @Test
    @DisplayName("耗时的写法")
    void durationFormat() {
        assertEquals("47 秒", MapReport.duration(47));
        assertEquals("1 分 47 秒", MapReport.duration(107));
        assertEquals("15 分 0 秒", MapReport.duration(900));
    }

    @Test
    @DisplayName("坐标去掉多余的 .0")
    void coordinateFormat() {
        assertEquals("(0, -100)", MapReport.point(new Vec2(0, -100)));
        assertEquals("(1.5, 2.25)", MapReport.point(new Vec2(1.5, 2.25)));
    }

    @Test
    @DisplayName("四种判定都有中文说法")
    void sideNames() {
        assertEquals("前方", MapReport.side(Zone.BLUE));
        assertEquals("后方", MapReport.side(Zone.RED));
        assertEquals("线上", MapReport.side(Zone.DEAD_BAND));
        assertEquals("范围外", MapReport.side(Zone.OUT_OF_RANGE));
    }
}
