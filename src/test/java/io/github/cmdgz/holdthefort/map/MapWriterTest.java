package io.github.cmdgz.holdthefort.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.cmdgz.holdthefort.frontline.Vec2;

@DisplayName("MapWriter：建场结果能原样读回来")
class MapWriterTest {

    private static MapDefinition map() {
        return new MapDefinition("arena", "world_htf",
                new Spawn(0, 64, -100), new Spawn(0, 64, 100),
                TurnLimit.NINETY, 0.6, 1.0, 1,
                List.of(
                        new CheckpointPair(new Vec2(-160, -32), new Vec2(160, -32)),
                        new CheckpointPair(new Vec2(-160, 0), new Vec2(160, 0)),
                        new CheckpointPair(new Vec2(-160, 32), new Vec2(160, 32))));
    }

    @Test
    @DisplayName("写出来再读回去，字段一致")
    void roundTrip() {
        final String yaml = MapWriter.toYaml(map());
        final MapDefinition parsed = MapParser.parse(yaml, Defaults.standard());

        assertEquals("arena", parsed.name());
        assertEquals("world_htf", parsed.world());
        assertEquals(1, parsed.startIndex());
        assertEquals(3, parsed.checkpoints().size());
        assertEquals(map().redSpawn(), parsed.redSpawn());
        assertEquals(map().blueSpawn(), parsed.blueSpawn());
        assertEquals(TurnLimit.NINETY, parsed.turnLimit());
    }

    @Test
    @DisplayName("写出的地图能通过校验")
    void writtenMapIsValid() {
        final MapDefinition parsed = MapParser.parse(MapWriter.toYaml(map()), Defaults.standard());
        assertFalse(MapValidator.validate(parsed).hasErrors());
    }

    @Test
    @DisplayName("数字去掉多余的零")
    void numberFormatting() {
        assertEquals("64", MapWriter.number(64.0));
        assertEquals("-100", MapWriter.number(-100.0));
        assertEquals("0.5", MapWriter.number(0.5));
        assertEquals("1.25", MapWriter.number(1.25));
    }
}
