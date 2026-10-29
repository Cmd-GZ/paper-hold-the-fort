package io.github.cmdgz.holdthefort.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.map.CheckpointPair;
import io.github.cmdgz.holdthefort.map.Defaults;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.map.MapParser;
import io.github.cmdgz.holdthefort.map.MapValidator;
import io.github.cmdgz.holdthefort.map.MapWriter;
import io.github.cmdgz.holdthefort.map.TurnLimit;

@DisplayName("SetupSession：游戏内建场的完整流程")
class SetupSessionTest {

    private static SetupSession threeFrames() {
        final SetupSession session = new SetupSession("world_htf");
        session.recordPoint(new Vec2(-160, -32));
        session.recordPoint(new Vec2(160, -32));
        session.recordPoint(new Vec2(-160, 0));
        session.recordPoint(new Vec2(160, 0));
        session.recordPoint(new Vec2(-160, 32));
        session.recordPoint(new Vec2(160, 32));
        return session;
    }

    @Test
    @DisplayName("点两下组成一帧")
    void recordsFrames() {
        final SetupSession session = new SetupSession("world_htf");
        assertNull(session.recordPoint(new Vec2(-160, -32)), "第一个点还不成帧");
        final CheckpointPair first = session.recordPoint(new Vec2(160, -32));
        assertNotNull(first);
        assertEquals(1, session.frames().size());
    }

    @Test
    @DisplayName("缺帧或缺出生点时会说明还差什么")
    void reportsMissingPieces() {
        final SetupSession session = new SetupSession("world_htf");
        assertTrue(session.missing().contains("至少 3 帧"));

        final SetupSession filled = threeFrames();
        assertTrue(filled.missing().contains("红方出生点"));
        filled.setSpawn(true, 0, 64, -100);
        assertTrue(filled.missing().contains("蓝方出生点"));
        filled.setSpawn(false, 0, 64, 100);
        assertNull(filled.missing());
    }

    @Test
    @DisplayName("起始帧默认取中间那一帧")
    void defaultStartIndexIsMiddle() {
        assertEquals(1, threeFrames().resolvedStartIndex());

        final SetupSession five = threeFrames();
        five.recordPoint(new Vec2(-160, 64));
        five.recordPoint(new Vec2(160, 64));
        five.recordPoint(new Vec2(-160, 96));
        five.recordPoint(new Vec2(160, 96));
        assertEquals(2, five.resolvedStartIndex(), "5 帧时起点应取第 3 帧（编号 2）");
    }

    @Test
    @DisplayName("撤销优先撤掉没配对的点")
    void undoRemovesPendingFirst() {
        final SetupSession session = threeFrames();
        session.recordPoint(new Vec2(0, 99));
        assertEquals(3, session.frames().size());
        assertTrue(session.undo().contains("待配对"));
        assertEquals(3, session.frames().size());
        session.undo();
        assertEquals(2, session.frames().size());
    }

    @Test
    @DisplayName("建场结果落盘后能被解析并通过校验")
    void resultRoundTrips() {
        final SetupSession session = threeFrames();
        session.setSpawn(true, 0, 64, -100);
        session.setSpawn(false, 0, 64, 100);

        final MapDefinition map = new MapDefinition(
                "world_htf", session.worldName(), session.redSpawn(), session.blueSpawn(),
                TurnLimit.NINETY, 0.6, 1.0, session.resolvedStartIndex(), List.copyOf(session.frames()));

        final MapDefinition parsed = MapParser.parse(MapWriter.toYaml(map), Defaults.standard());
        assertEquals("world_htf", parsed.world());
        assertEquals(3, parsed.checkpoints().size());
        final MapValidator.Result result = MapValidator.validate(parsed);
        assertFalse(result.hasErrors(), "建场结果不应有 L 级问题：" + result.problems());
    }

    @Test
    @DisplayName("只有两帧时校验会拒绝（L4）")
    void twoFramesRejected() {
        final SetupSession session = new SetupSession("world_htf");
        session.recordPoint(new Vec2(-160, -32));
        session.recordPoint(new Vec2(160, -32));
        session.recordPoint(new Vec2(-160, 0));
        session.recordPoint(new Vec2(160, 0));
        session.setSpawn(true, 0, 64, -100);
        session.setSpawn(false, 0, 64, 100);

        final MapDefinition map = new MapDefinition(
                "x", "world_htf", session.redSpawn(), session.blueSpawn(),
                TurnLimit.NINETY, 0.6, 1.0, session.resolvedStartIndex(), List.copyOf(session.frames()));
        assertTrue(MapValidator.validate(map).hasErrors());
    }
}
