package io.github.cmdgz.holdthefort.world;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import io.github.cmdgz.holdthefort.map.CheckpointPair;
import io.github.cmdgz.holdthefort.map.MapDefinition;

/**
 * 生成一张用于调试的平整战场。
 *
 * <p>只为「能立刻进服试玩」服务：按地图里所有检查点与出生点的包围盒铺一层地板，
 * 并在两端标出基地区域。不做地形美化。
 */
public final class ArenaBuilder {

    /** 地板顶面所在的高度：出生点的 {@code y} 默认就落在这一层之上。 */
    private static final int FLOOR_Y = 63;

    private ArenaBuilder() {
    }

    /**
     * 在世界里铺出战场。
     *
     * @return 放置的方块数
     */
    public static int build(World world, MapDefinition map) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (CheckpointPair pair : map.checkpoints()) {
            minX = Math.min(minX, (int) Math.floor(Math.min(pair.p().x(), pair.q().x())));
            maxX = Math.max(maxX, (int) Math.ceil(Math.max(pair.p().x(), pair.q().x())));
            minZ = Math.min(minZ, (int) Math.floor(Math.min(pair.p().z(), pair.q().z())));
            maxZ = Math.max(maxZ, (int) Math.ceil(Math.max(pair.p().z(), pair.q().z())));
        }
        minX = Math.min(minX, (int) Math.floor(Math.min(map.redSpawn().x(), map.blueSpawn().x())));
        maxX = Math.max(maxX, (int) Math.ceil(Math.max(map.redSpawn().x(), map.blueSpawn().x())));
        minZ = Math.min(minZ, (int) Math.floor(Math.min(map.redSpawn().z(), map.blueSpawn().z())));
        maxZ = Math.max(maxZ, (int) Math.ceil(Math.max(map.redSpawn().z(), map.blueSpawn().z())));

        // 四周留 10 格余量
        minX -= 10;
        maxX += 10;
        minZ -= 10;
        maxZ += 10;

        int placed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                final Block floor = world.getBlockAt(x, FLOOR_Y, z);
                if (!floor.getType().isAir()) {
                    continue;
                }
                floor.setType(Material.GRASS_BLOCK, false);
                placed++;
                // 清掉地板上方两格，避免旧地形挡住视线
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 2; y++) {
                    final Block above = world.getBlockAt(x, y, z);
                    if (!above.getType().isAir()) {
                        above.setType(Material.AIR, false);
                        placed++;
                    }
                }
            }
        }

        markBase(world, map, true);
        markBase(world, map, false);
        world.setSpawnLocation(0, FLOOR_Y + 1, 0);
        return placed;
    }

    /** 用彩色混凝土把两端基地标出来，方便认方向。 */
    private static void markBase(World world, MapDefinition map, boolean red) {
        final double cx = red ? map.redSpawn().x() : map.blueSpawn().x();
        final double cz = red ? map.redSpawn().z() : map.blueSpawn().z();
        final Material material = red ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
        for (int x = (int) cx - 6; x <= (int) cx + 6; x++) {
            for (int z = (int) cz - 6; z <= (int) cz + 6; z++) {
                world.getBlockAt(x, FLOOR_Y, z).setType(material, false);
            }
        }
    }
}
