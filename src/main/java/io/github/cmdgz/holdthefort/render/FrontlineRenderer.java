package io.github.cmdgz.holdthefort.render;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.FrontlineSample;

/**
 * 把战线的形态摆到世界里（§3、§4）。
 *
 * <p>做两件事：维护一个不可见的 {@link Marker}（战线实体），以及沿线段撒粒子。
 * 粒子只发给附近玩家，且跳过未加载的区块。
 */
public final class FrontlineRenderer {

    /** 沿线段的采样间隔（格）。 */
    private static final double STEP = 2.0;

    /** 单个采样列最多点亮几层地面。 */
    private static final int MAX_LAYERS = 8;

    /** 距离多远以外的玩家不给发粒子。 */
    private static final double VIEW_RANGE = 96;

    /** 越过这个距离就不发（§4.4 的分档上限）。 */
    private static final double MAX_RANGE = 256;

    private final World world;
    private final FrontlineChain chain;

    private Marker marker;
    private Location lastCenter;

    public FrontlineRenderer(World world, FrontlineChain chain) {
        this.world = world;
        this.chain = chain;
    }

    /** 在世界里创建战线实体。 */
    public void spawn(FrontlineSample sample) {
        final Location at = toLocation(sample);
        marker = world.spawn(at, Marker.class, entity -> {
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setCustomNameVisible(false);
        });
        this.lastCenter = at;
    }

    /**
     * 更新战线位置与粒子。
     *
     * @param sample 当前形态
     * @param color  粒子颜色（表示趋势，见 §4.2）
     */
    public void update(FrontlineSample sample, Color color) {
        final Location at = toLocation(sample);
        if (marker == null || marker.isDead()) {
            marker = world.spawn(at, Marker.class, entity -> entity.setGravity(false));
        } else if (lastCenter == null || lastCenter.distanceSquared(at) > 1e-6) {
            marker.teleport(at);
        }
        this.lastCenter = at;

        final List<Player> audience = nearbyPlayers(at);
        if (audience.isEmpty()) {
            return;
        }
        drawParticles(sample, color, audience);
    }

    /** 移除战线实体。 */
    public void remove() {
        if (marker != null && !marker.isDead()) {
            marker.remove();
        }
        marker = null;
    }

    private List<Player> nearbyPlayers(Location center) {
        final List<Player> result = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            final double d = p.getLocation().distance(center);
            if (d <= MAX_RANGE && p.getLocation().distanceSquared(center) <= MAX_RANGE * MAX_RANGE) {
                result.add(p);
            }
        }
        return result;
    }

    private void drawParticles(FrontlineSample sample, Color color, List<Player> audience) {
        final Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        final Vector dir = new Vector(sample.direction().x(), 0, sample.direction().z());
        final double half = sample.halfLength();
        final double cx = sample.center().x();
        final double cz = sample.center().z();

        for (double t = -half; t <= half + 1e-9; t += STEP) {
            final double x = cx + dir.getX() * t;
            final double z = cz + dir.getZ() * t;
            final int bx = (int) Math.floor(x);
            final int bz = (int) Math.floor(z);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }
            drawColumn(bx, bz, dust, audience);
        }
    }

    /** 点亮该列所有「实心且头顶是空气」的方块（§4.1）。 */
    private void drawColumn(int bx, int bz, Particle.DustOptions dust, List<Player> audience) {
        int found = 0;
        final int top = world.getMaxHeight() - 1;
        final int bottom = world.getMinHeight();
        for (int y = top; y >= bottom && found < MAX_LAYERS; y--) {
            final Block block = world.getBlockAt(bx, y, bz);
            if (block.getType().isAir()) {
                continue;
            }
            if (!world.getBlockAt(bx, y + 1, bz).getType().isAir()) {
                continue;
            }
            found++;
            final Location spot = new Location(world, bx + 0.5, y + 1.1, bz + 0.5);
            for (Player p : audience) {
                final double d = p.getLocation().distance(spot);
                if (d > VIEW_RANGE) {
                    continue;
                }
                p.spawnParticle(Particle.DUST, spot, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private Location toLocation(FrontlineSample sample) {
        final int x = (int) Math.floor(sample.center().x());
        final int z = (int) Math.floor(sample.center().z());
        final int y = world.isChunkLoaded(x >> 4, z >> 4)
                ? world.getHighestBlockYAt(x, z) + 1
                : world.getMinHeight() + 1;
        return new Location(world, sample.center().x(), y, sample.center().z());
    }
}
