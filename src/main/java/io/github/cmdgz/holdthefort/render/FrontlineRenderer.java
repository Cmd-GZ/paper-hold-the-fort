package io.github.cmdgz.holdthefort.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.ClientOption;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.FrontlineSample;
import io.github.cmdgz.holdthefort.frontline.Vec2;

/**
 * 把战线的形态摆到世界里（§3、§4）。
 *
 * <p>严格按 §4.1 的多层规则与 §4.4 的性能约束实现：
 *
 * <ul>
 *   <li>逐列找出**所有**「自身非空气、正上方是空气」的方块，在顶面撒粒子，不取最高点</li>
 *   <li>按玩家分别发送，距离分档决定横向步长，超过 256 格不发送</li>
 *   <li>未加载区块跳过该列</li>
 *   <li>尊重玩家的「粒子可见度」设置，按档位稀释密度</li>
 * </ul>
 */
public final class FrontlineRenderer {

    /** 距离分档（到线段最近点）。 */
    private static final double NEAR_RANGE = 32;
    private static final double MID_RANGE = 96;
    private static final double MAX_RANGE = 256;

    /** 各档的横向步长。 */
    private static final double STEP_NEAR = 0.5;
    private static final double STEP_MID = 1.0;
    private static final double STEP_FAR = 2.0;

    /** 颜色平滑用的过渡步数：渲染每 2 tick 一次，10 步 ≈ 1 秒。 */
    private static final int COLOR_TRANSITION_STEPS = 5;

    private final World world;
    private final FrontlineChain chain;

    private Marker marker;
    private Location lastCenter;

    private Color shownColor;
    private Color targetColor;

    public FrontlineRenderer(World world, FrontlineChain chain) {
        this.world = world;
        this.chain = chain;
    }

    /** 创建战线实体（不可见、无碰撞、不持久化）。 */
    public void spawn(FrontlineSample sample) {
        final Location at = toLocation(sample);
        marker = world.spawn(at, Marker.class, entity -> {
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
        });
        lastCenter = at;
    }

    /**
     * 更新战线位置与粒子。
     *
     * @param sample 当前形态
     * @param target 目标颜色（表示趋势，§4.2）
     */
    public void update(FrontlineSample sample, Color target) {
        final Location at = toLocation(sample);
        if (marker == null || marker.isDead()) {
            spawn(sample);
        } else if (lastCenter == null || lastCenter.distanceSquared(at) > 1e-6) {
            marker.teleport(at);
        }
        lastCenter = at;

        targetColor = target;
        if (shownColor == null) {
            shownColor = target;
        }

        draw(sample, shownColor);
        stepColor();
    }

    /** 缓慢把显示色推向目标色，避免玩家站在线上时颜色闪个不停（§4.2）。 */
    private void stepColor() {
        if (shownColor == null || targetColor == null || shownColor.equals(targetColor)) {
            return;
        }
        shownColor = Color.fromRGB(
                approach(shownColor.getRed(), targetColor.getRed()),
                approach(shownColor.getGreen(), targetColor.getGreen()),
                approach(shownColor.getBlue(), targetColor.getBlue()));
    }

    private static int approach(int from, int to) {
        final int delta = to - from;
        if (Math.abs(delta) <= 1) {
            return to;
        }
        final int step = Math.max(1, Math.abs(delta) / COLOR_TRANSITION_STEPS);
        return from + Integer.signum(delta) * step;
    }

    public void remove() {
        if (marker != null && !marker.isDead()) {
            marker.remove();
        }
        marker = null;
    }

    private void draw(FrontlineSample sample, Color color) {
        // 每个玩家按「距离分档 ÷ 粒子可见度」得到一个有效步长，相同步长的玩家一起画
        final Map<Double, List<Player>> groups = new HashMap<>();
        for (Player player : world.getPlayers()) {
            final Vec2 pos = new Vec2(player.getLocation().getX(), player.getLocation().getZ());
            final double distance = distanceToSegment(pos, sample);
            if (distance > MAX_RANGE) {
                continue;
            }
            final double baseStep = distance < NEAR_RANGE ? STEP_NEAR
                    : distance <= MID_RANGE ? STEP_MID : STEP_FAR;
            final double step = baseStep / particleDensity(player);
            groups.computeIfAbsent(step, k -> new ArrayList<>()).add(player);
        }

        for (Map.Entry<Double, List<Player>> entry : groups.entrySet()) {
            drawForGroup(sample, color, entry.getValue(), entry.getKey());
        }
    }

    /** §4.4：粒子可见度档位对应的密度倍率。 */
    private static double particleDensity(Player player) {
        final ClientOption.ParticleVisibility visibility =
                player.getClientOption(ClientOption.PARTICLE_VISIBILITY);
        if (visibility == null) {
            return 1.0;
        }
        return switch (visibility) {
            case ALL -> 1.0;
            case DECREASED -> 0.5;
            case MINIMAL -> 0.25;
        };
    }

    private void drawForGroup(FrontlineSample sample, Color color, List<Player> audience, double step) {
        final Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        final Vec2 dir = sample.direction();
        final double half = sample.halfLength();

        for (double t = -half; t <= half + 1e-9; t += step) {
            final double x = sample.center().x() + dir.x() * t;
            final double z = sample.center().z() + dir.z() * t;
            final int bx = (int) Math.floor(x);
            final int bz = (int) Math.floor(z);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }
            drawColumn(bx, bz, dust, audience);
        }
    }

    /** §4.1：该列所有「非空气且头顶是空气」的方块，全部点亮。 */
    private void drawColumn(int bx, int bz, Particle.DustOptions dust, List<Player> audience) {
        final int top = world.getMaxHeight() - 1;
        final int bottom = world.getMinHeight();
        for (int y = top; y >= bottom; y--) {
            final Block block = world.getBlockAt(bx, y, bz);
            if (block.getType().isAir()) {
                continue;
            }
            if (!world.getBlockAt(bx, y + 1, bz).getType().isAir()) {
                continue;
            }
            final Location spot = new Location(world, bx + 0.5, y + 1.1, bz + 0.5);
            for (Player player : audience) {
                player.spawnParticle(Particle.DUST, spot, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    /** 点到线段最近点的水平距离。 */
    public static double distanceToSegment(Vec2 point, FrontlineSample sample) {
        final Vec2 v = point.sub(sample.center());
        final double along = v.dot(sample.direction());
        final double clamped = Math.max(-sample.halfLength(), Math.min(sample.halfLength(), along));
        final Vec2 closest = sample.center().add(sample.direction().scale(clamped));
        return point.sub(closest).length();
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
