package io.github.cmdgz.holdthefort.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.cmdgz.holdthefort.frontline.Vec2;

@DisplayName("MapValidator：分级校验")
class MapValidatorTest {

    private static CheckpointPair pair(double px, double pz, double qx, double qz) {
        return new CheckpointPair(new Vec2(px, pz), new Vec2(qx, qz));
    }

    /** 文档 §8.2 的示例地图：5 帧、start_index=2，编号 -2..+2。 */
    private static MapDefinition sampleMap() {
        return new MapDefinition(
                "river_crossing",
                "world",
                new Spawn(0, 64, -100),
                new Spawn(0, 64, 100),
                TurnLimit.NINETY,
                0.6,
                1.0,
                2,
                List.of(
                        pair(-160, -64, 160, -64),
                        pair(-160, -32, 160, -32),
                        pair(-160, 0, 160, 0),
                        pair(-160, 32, 160, 32),
                        pair(-160, 64, 160, 64)));
    }

    private static MapDefinition withSpawns(MapDefinition base, Spawn red, Spawn blue) {
        return new MapDefinition(base.name(), base.world(), red, blue, base.turnLimit(),
                base.speed(), base.epsilon(), base.startIndex(), base.checkpoints());
    }

    private static MapDefinition withStartIndex(MapDefinition base, int index) {
        return new MapDefinition(base.name(), base.world(), base.redSpawn(), base.blueSpawn(),
                base.turnLimit(), base.speed(), base.epsilon(), index, base.checkpoints());
    }

    private static MapDefinition withCheckpoints(MapDefinition base, List<CheckpointPair> pairs) {
        return new MapDefinition(base.name(), base.world(), base.redSpawn(), base.blueSpawn(),
                base.turnLimit(), base.speed(), base.epsilon(), base.startIndex(), pairs);
    }

    @Test
    @DisplayName("示例地图完全合规，一条提示都没有")
    void sampleMapIsClean() {
        final MapValidator.Result result = MapValidator.validate(sampleMap());
        assertFalse(result.hasErrors(), "不应有 L 级问题：" + result.problems());
        assertNotNull(result.chain());
        assertEquals(List.of(), result.problems(), "示例地图不该有任何提示");
    }

    @Test
    @DisplayName("编号范围由列表长度与 start_index 推出：5 项、起点在第 3 项 → -2..+2")
    void indexRangeDerived() {
        final MapValidator.Result result = MapValidator.validate(sampleMap());
        assertEquals(-2, result.chain().from());
        assertEquals(2, result.chain().to());
        assertEquals(0, result.chain().keyframe(0).index());
    }

    @Test
    @DisplayName("L4：少于三帧被拒绝")
    void rejectsTooFewFrames() {
        final MapDefinition base = sampleMap();
        final MapDefinition bad = withCheckpoints(base, List.of(
                pair(-160, 0, 160, 0), pair(-160, 32, 160, 32)));
        final MapValidator.Result result = MapValidator.validate(bad);
        assertTrue(result.hasErrors());
        assertNull(result.chain());
        assertTrue(result.problems().getFirst().message().contains("3"));
    }

    @Test
    @DisplayName("L5：start_index 落在端点被拒绝")
    void rejectsStartIndexAtEnds() {
        assertTrue(MapValidator.validate(withStartIndex(sampleMap(), 0)).hasErrors());
        assertTrue(MapValidator.validate(withStartIndex(sampleMap(), 4)).hasErrors());
    }

    @Test
    @DisplayName("L2：两个检查点重合被拒绝")
    void rejectsDegeneratePair() {
        final List<CheckpointPair> pairs = new ArrayList<>(sampleMap().checkpoints());
        pairs.set(1, pair(10, 10, 10, 10));
        final MapValidator.Result result = MapValidator.validate(withCheckpoints(sampleMap(), pairs));
        assertTrue(result.hasErrors());
        assertNull(result.chain());
    }

    @Test
    @DisplayName("L7：红方出生点压在第 0 帧的线上被拒绝")
    void rejectsRedSpawnOnAnchorLine() {
        final MapDefinition bad = withSpawns(sampleMap(), new Spawn(0, 64, 0), new Spawn(0, 64, 100));
        final MapValidator.Result result = MapValidator.validate(bad);
        assertTrue(result.hasErrors());
        assertTrue(result.problems().getFirst().message().contains("红方出生点"));
    }

    @Test
    @DisplayName("W2：战线太短只提示，不阻止加载")
    void warnsOnShortFrontline() {
        final List<CheckpointPair> pairs = List.of(
                pair(-5, -32, 5, -32),
                pair(-5, 0, 5, 0),
                pair(-5, 32, 5, 32));
        final MapDefinition map = new MapDefinition("short", "world",
                new Spawn(0, 64, -100), new Spawn(0, 64, 100),
                TurnLimit.NINETY, 0.6, 1.0, 1, pairs);
        final MapValidator.Result result = MapValidator.validate(map);
        assertFalse(result.hasErrors(), "短战线不该阻止加载");
        assertTrue(result.of(MapValidator.Severity.WARNING).stream()
                .anyMatch(p -> p.message().contains("判定范围会很小")));
    }

    @Test
    @DisplayName("W4：开局两队同侧只提示，不阻止加载")
    void warnsOnSameSideStart() {
        final MapDefinition map = withSpawns(sampleMap(), new Spawn(0, 64, 100), new Spawn(0, 64, 200));
        final MapValidator.Result result = MapValidator.validate(map);
        assertFalse(result.hasErrors(), "开局不平衡不该阻止加载");
        assertTrue(result.of(MapValidator.Severity.WARNING).stream()
                .anyMatch(p -> p.message().contains("入侵者")));
    }

    @Test
    @DisplayName("W3：开局两队都在范围外只提示")
    void warnsOnApproachPhase() {
        // 把两点拉到线段跨度之外很远处（沿 x 方向），并放在同一侧
        final MapDefinition map = withSpawns(sampleMap(), new Spawn(900, 64, -100), new Spawn(-900, 64, 100));
        final MapValidator.Result result = MapValidator.validate(map);
        assertFalse(result.hasErrors());
        assertTrue(result.of(MapValidator.Severity.WARNING).stream()
                .anyMatch(p -> p.message().contains("接近阶段")));
    }

    @Test
    @DisplayName("TurnLimit 只接受 90 与 free")
    void turnLimitParsing() {
        assertEquals(TurnLimit.NINETY, TurnLimit.parse("90"));
        assertEquals(TurnLimit.FREE, TurnLimit.parse("free"));
        assertEquals(TurnLimit.FREE, TurnLimit.parse(" FREE "));
        try {
            TurnLimit.parse("45");
            throw new AssertionError("应当抛出异常");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("45"));
        }
    }
}
