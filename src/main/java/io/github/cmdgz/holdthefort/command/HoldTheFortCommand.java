package io.github.cmdgz.holdthefort.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import io.github.cmdgz.holdthefort.HoldTheFortPlugin;
import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.FrontlineSample;
import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.game.Acceleration;
import io.github.cmdgz.holdthefort.game.Counts;
import io.github.cmdgz.holdthefort.game.ProgressState;
import io.github.cmdgz.holdthefort.game.Team;
import io.github.cmdgz.holdthefort.game.ZoneCounter;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.world.ArenaBuilder;
import io.github.cmdgz.holdthefort.render.FrontlineRenderer;

/**
 * 调试命令 {@code /htf}。
 *
 * <p>提供的最小闭环：{@code /htf red|blue} 分队，{@code /htf line <地图>} 摆出战线，
 * 之后战线会根据**真实玩家**的越线人数推进或后退（§5.2、§5.3）。
 */
public final class HoldTheFortCommand implements CommandExecutor, TabCompleter {

    /** §4.4：渲染每 2 tick 重算一次。 */
    private static final int RENDER_INTERVAL = 2;

    private static final Color COLOR_RED = Color.fromRGB(220, 60, 60);
    private static final Color COLOR_BLUE = Color.fromRGB(60, 110, 220);
    private static final Color COLOR_NEUTRAL = Color.fromRGB(220, 220, 220);

    private final HoldTheFortPlugin plugin;

    private FrontlineRenderer renderer;
    private FrontlineChain chain;
    private ProgressState progress;
    private World world;
    private BukkitTask task;
    private int tickCounter;
    private Counts lastCounts = Counts.NONE;

    public HoldTheFortCommand(HoldTheFortPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            // 控制台只允许 build：地图自带世界，不需要玩家上下文
            if (args.length >= 2 && args[0].equalsIgnoreCase("build")) {
                return buildArena(sender, args[1]);
            }
            sender.sendMessage("控制台只支持 /htf build <地图名>，其余命令需要玩家执行");
            return true;
        }
        final Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage("用法：/htf join [red|blue] | leave | line <地图> | build <地图> | start | stop");
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "join" -> joinTeam(player, args);
            case "leave" -> plugin.match().leave(player);
            case "red" -> setTeam(player, Team.RED);
            case "blue" -> setTeam(player, Team.BLUE);
            case "line" -> startLine(player, args.length > 1 ? args[1] : null);
            case "stop" -> stopLine(player);
            case "build" -> buildArena(player, args.length > 1 ? args[1] : null);
            case "start" -> startMatch(player);
            default -> {
                player.sendMessage("未知子命令：" + args[0]);
                yield true;
            }
        };
    }

    /** {@code /htf join [red|blue]}（§7）。不给队伍则先排队，开局自动分边。 */
    private boolean joinTeam(Player player, String[] args) {
        if (args.length < 2) {
            plugin.match().join(player, null);
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "red" -> setTeam(player, Team.RED);
            case "blue" -> setTeam(player, Team.BLUE);
            default -> {
                player.sendMessage("队伍只有 red 与 blue");
                yield true;
            }
        };
    }

    private boolean setTeam(Player player, Team team) {
        plugin.match().join(player, team);
        return true;
    }

    /** 生成一张平整的测试战场。 */
    private boolean buildArena(org.bukkit.command.CommandSender sender, String mapName) {
        if (mapName == null) {
            sender.sendMessage("请指定地图名，可用：" + plugin.maps().keySet());
            return true;
        }
        final MapDefinition map = plugin.maps().get(mapName);
        if (map == null) {
            sender.sendMessage("找不到地图 " + mapName);
            return true;
        }
        World target = plugin.getServer().getWorld(map.world());
        if (target == null) {
            // 战场的世界不存在就建一个超平坦空岛，保证插件在任何服上都能自测
            sender.sendMessage("世界 " + map.world() + " 不存在，正在创建…");
            target = new org.bukkit.WorldCreator(map.world())
                    .type(org.bukkit.WorldType.FLAT)
                    .generateStructures(false)
                    .createWorld();
        }
        if (target == null) {
            sender.sendMessage("无法创建世界 " + map.world());
            return true;
        }
        sender.sendMessage("正在生成战场，稍等…");
        final long start = System.currentTimeMillis();
        final int placed = ArenaBuilder.build(target, map);
        sender.sendMessage("战场生成完毕：放置 " + placed + " 个方块，用时 "
                + (System.currentTimeMillis() - start) + " ms");
        plugin.getLogger().info("战场 " + mapName + " 已生成于世界 " + target.getName());
        return true;
    }

    /** 立刻开始一局（调试用，跳过等待人数）。 */
    private boolean startMatch(Player player) {
        plugin.match().forceStart();
        player.sendMessage("已请求开局");
        return true;
    }

    private boolean startLine(Player player, String mapName) {
        if (plugin.match() != null && plugin.match().phase() != io.github.cmdgz.holdthefort.game.MatchPhase.WAITING) {
            player.sendMessage("对局进行中，不能摆调试战线");
            return true;
        }
        if (mapName == null) {
            player.sendMessage("请指定地图名，可用：" + plugin.chains().keySet());
            return true;
        }
        final FrontlineChain selected = plugin.chains().get(mapName);
        final MapDefinition map = plugin.maps().get(mapName);
        if (selected == null || map == null) {
            player.sendMessage("找不到地图 " + mapName + "，可用：" + plugin.chains().keySet());
            return true;
        }

        stopLine(player);

        this.chain = selected;
        this.world = player.getWorld();
        this.progress = new ProgressState(selected, map.speed(), Acceleration.defaults());
        this.renderer = new FrontlineRenderer(world, selected);
        this.lastCounts = Counts.NONE;
        this.tickCounter = 0;

        final FrontlineSample start = selected.sample(0);
        renderer.spawn(start);
        renderer.update(start, COLOR_NEUTRAL);

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);

        player.sendMessage("已在 " + mapName + " 上摆出战线，u = 0 —— 越线的人会让它移动");
        return true;
    }

    private void tick() {
        if (progress == null || renderer == null || chain == null || world == null) {
            return;
        }

        lastCounts = countInvaders();
        final double factor = ZoneCounter.factor(lastCounts);

        final double before = progress.u();
        progress.tick(0.05, factor);
        final double delta = progress.u() - before;

        if (tickCounter++ % RENDER_INTERVAL == 0) {
            renderer.update(chain.sample(progress.u()), colorFor(delta));
        }

        if (!progress.isRunning()) {
            broadcastOutcome();
            stopLine(null);
        }
    }

    /** 收集双方「可计数」玩家（§5.2）：同世界、存活、非观战。 */
    private Counts countInvaders() {
        final List<Vec2> reds = new ArrayList<>();
        final List<Vec2> blues = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            final Team team = plugin.teamOf(p);
            if (team == null) {
                continue;
            }
            final Vec2 pos = new Vec2(p.getLocation().getX(), p.getLocation().getZ());
            if (team == Team.RED) {
                reds.add(pos);
            } else {
                blues.add(pos);
            }
        }
        return ZoneCounter.count(chain, progress.u(), reds, blues);
    }

    /** §4.2：颜色表示趋势，不表示归属。 */
    private static Color colorFor(double deltaU) {
        if (deltaU > 1e-9) {
            return COLOR_RED;
        }
        if (deltaU < -1e-9) {
            return COLOR_BLUE;
        }
        return COLOR_NEUTRAL;
    }

    private void broadcastOutcome() {
        final String message = switch (progress.outcome()) {
            case RED_WIN -> "红方把战线推到了蓝方基地";
            case BLUE_WIN -> "蓝方把战线推到了红方基地";
            case DRAW -> "时间到，双方路程相同";
            case ONGOING -> "";
        };
        if (!message.isEmpty()) {
            plugin.getServer().broadcastMessage(message);
        }
    }

    private boolean stopLine(Player player) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (renderer != null) {
            renderer.remove();
            renderer = null;
        }
        progress = null;
        chain = null;
        world = null;
        if (player != null) {
            player.sendMessage("战线已移除");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "line", "build", "start", "stop");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return List.of("red", "blue");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("line") || args[0].equalsIgnoreCase("build"))) {
            return new ArrayList<>(plugin.chains().keySet());
        }
        return List.of();
    }
}
