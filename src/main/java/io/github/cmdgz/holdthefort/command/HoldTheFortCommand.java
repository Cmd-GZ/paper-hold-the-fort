package io.github.cmdgz.holdthefort.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
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

    /** §4.4：渲染每 tick 重算一次，让战线跟着 u 平滑移动。 */
    private static final int RENDER_INTERVAL = 1;

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
            // 控制台可以跑只读诊断与 build，其余需要玩家上下文
            if (args.length >= 1 && args[0].equalsIgnoreCase("map")) {
                return handleMapConsole(sender, args);
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("status")) {
                return showStatusConsole(sender);
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("build")) {
                return buildArena(sender, args[1]);
            }
            sender.sendMessage("控制台支持：/htf status、/htf map、/htf build <地图名>");
            return true;
        }
        final Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage("用法：/htf join [red|blue] | leave | start | stop");
            player.sendMessage("      /htf setup [spawn red|blue | undo | start <编号> | done | cancel]");
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "join" -> joinTeam(player, args);
            case "setup" -> handleSetup(player, args);
            case "map" -> handleMap(player, args);
            case "status" -> showStatus(player);
            case "leave" -> plugin.match().leave(player);
            case "red" -> setTeam(player, Team.RED);
            case "blue" -> setTeam(player, Team.BLUE);
            case "line" -> startLine(player, args.length > 1 ? args[1] : null);
            case "stop" -> stopEverything(player);
            case "build" -> buildArena(player, args.length > 1 ? args[1] : null);
            case "start" -> startMatch(player);
            default -> {
                player.sendMessage("未知子命令：" + args[0]);
                yield true;
            }
        };
    }

    /** 游戏内建场：让管理员不碰文件就能做出地图。 */
    private boolean handleSetup(Player player, String[] args) {
        if (!player.hasPermission("htf.setup")) {
            player.sendMessage("§c你没有建场权限（htf.setup）");
            return true;
        }
        if (args.length == 1) {
            plugin.setup().begin(player);
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "spawn" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法：/htf setup spawn red|blue");
                    yield true;
                }
                yield plugin.setup().recordSpawn(player, args[2].equalsIgnoreCase("red"));
            }
            case "undo" -> plugin.setup().undo(player);
            case "done" -> plugin.setup().done(player);
            case "reset" -> plugin.setup().reset(player);
            case "point" -> plugin.setup().recordTargetPoint(player);
            case "cancel" -> plugin.setup().cancel(player);
            case "start" -> args.length < 3
                    ? true
                    : plugin.setup().setStartIndex(player, args[2]);
            default -> {
                player.sendMessage("§c未知的建场子命令");
                yield true;
            }
        };
    }

    /** 控制台版：只读诊断。 */
    /** 参战者与其队伍。 */
    private void dumpTeams(org.bukkit.command.CommandSender sender) {
        final StringBuilder sb = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            final Team team = plugin.teamOf(p);
            if (team == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("§7，");
            }
            sb.append(team == Team.RED ? "§c" : "§9").append(p.getName());
        }
        sender.sendMessage("§7已分队：" + (sb.isEmpty() ? "§f（无）" : sb.toString()));
    }

    private boolean showStatusConsole(org.bukkit.command.CommandSender sender) {
        sender.sendMessage("—— 战线模式状态 ——");
        sender.sendMessage("配置的大厅世界：" + plugin.config().lobbyWorld()
                + (Bukkit.getWorld(plugin.config().lobbyWorld()) == null ? "（未加载！）" : ""));
        sender.sendMessage("当前阶段：" + plugin.match().phase());
        if (plugin.maps().isEmpty()) {
            sender.sendMessage("地图：还没有");
        } else {
            sender.sendMessage("地图：");
            plugin.maps().forEach((name, map) -> sender.sendMessage(" - " + name + " → 世界 "
                    + map.world()
                    + (Bukkit.getWorld(map.world()) == null ? "（世界未加载）" : "")
                    + (map.world().equals(plugin.config().lobbyWorld()) ? "  ← 就是大厅世界，开不了局" : "")));
        }
        return true;
    }

    /** 控制台版地图列表。 */
    private boolean handleMapConsole(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (plugin.maps().isEmpty()) {
                sender.sendMessage("还没有任何地图");
                return true;
            }
            plugin.maps().forEach((name, map) -> sender.sendMessage(
                    name + " → 世界 " + map.world() + "，" + map.checkpoints().size() + " 帧"));
            return true;
        }
        if (args[1].equalsIgnoreCase("remove") && args.length >= 3) {
            sender.sendMessage(plugin.removeMap(args[2])
                    ? "已删除地图 " + args[2] : "没有这张地图：" + args[2]);
            return true;
        }
        if (args[1].equalsIgnoreCase("setworld") && args.length >= 4) {
            final String error = plugin.rebindMap(args[2], args[3]);
            sender.sendMessage(error == null ? "已把 " + args[2] + " 绑定到世界 " + args[3] : error);
            return true;
        }
        sender.sendMessage("用法：/htf map [remove|setworld <地图名> <世界名>]");
        return true;
    }

    /** 一眼看清当前配置与地图状态。 */
    private boolean showStatus(Player player) {
        player.sendMessage("§6—— 战线模式状态 ——");
        player.sendMessage("§7配置的大厅世界：§f" + plugin.config().lobbyWorld());
        player.sendMessage("§7你当前所在世界：§f" + player.getWorld().getName());
        player.sendMessage("§7当前阶段：§f" + plugin.match().phase());
        player.sendMessage("§7你所在世界：§f" + player.getWorld().getName()
                + "§7，PVP = §f" + player.getWorld().getPVP()
                + "§7，规则 pvp = §f"
                + player.getWorld().getGameRuleValue(org.bukkit.GameRule.PVP));
        if (plugin.maps().isEmpty()) {
            player.sendMessage("§7地图：§c还没有（用 /htf setup 做一张）");
        } else {
            dumpTeams(player);
        player.sendMessage("§7地图：");
            plugin.maps().forEach((name, map) -> player.sendMessage(
                    "§7 - §e" + name + " §7→ 世界 §f" + map.world()
                            + (map.world().equals(plugin.config().lobbyWorld())
                                    ? " §c（就是大厅世界，开不了局）" : "")));
        }
        return true;
    }

    /** 已保存地图的管理。 */
    private boolean handleMap(Player player, String[] args) {
        if (args.length < 2) {
            if (plugin.maps().isEmpty()) {
                player.sendMessage("§7还没有任何地图。用 §e/htf setup §7做一张。");
                return true;
            }
            player.sendMessage("§6已保存的地图：");
            plugin.maps().forEach((name, map) -> player.sendMessage(
                    "§7 - §e" + name + " §7→ 世界 §f" + map.world()
                            + "§7，§f" + map.checkpoints().size() + "§7 帧"));
            player.sendMessage("§7删除：§e/htf map remove <地图名>");
            return true;
        }
        if (args[1].equalsIgnoreCase("setworld")) {
            if (!player.hasPermission("htf.setup")) {
                player.sendMessage("§c没有权限");
                return true;
            }
            if (args.length < 4) {
                player.sendMessage("§c用法：/htf map setworld <地图名> <世界名>");
                return true;
            }
            final String error = plugin.rebindMap(args[2], args[3]);
            if (error == null) {
                player.sendMessage("§a已把地图 " + args[2] + " 重新绑定到世界 " + args[3]);
            } else {
                player.sendMessage("§c" + error);
            }
            return true;
        }
        if (!args[1].equalsIgnoreCase("remove")) {
            player.sendMessage("§c用法：/htf map remove <地图名> | /htf map setworld <地图名> <世界名>");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§c请指定要删除的地图名，当前有：" + plugin.maps().keySet());
            return true;
        }
        if (!player.hasPermission("htf.setup")) {
            player.sendMessage("§c没有权限");
            return true;
        }
        final String name = args[2];
        if (plugin.removeMap(name)) {
            player.sendMessage("§a已删除地图 " + name + "（文件与内存都已移除）");
        } else {
            player.sendMessage("§c没有这张地图：" + name + "，当前有：" + plugin.maps().keySet());
        }
        return true;
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
        // 玩家执行 → 铺在**他所在的世界**；控制台执行 → 铺在地图声明的世界。
        // 世界由服务器或世界管理插件负责创建，本插件不建世界。
        final World target;
        if (sender instanceof Player p) {
            target = p.getWorld();
            if (!target.getName().equals(map.world())) {
                sender.sendMessage("§e注意：地图 " + mapName + " 声明的是世界 " + map.world()
                        + "，这次铺在你所在的 " + target.getName() + " 里");
            }
        } else {
            target = plugin.getServer().getWorld(map.world());
            if (target == null) {
                sender.sendMessage("世界 " + map.world() + " 不存在。"
                        + "请先用服务器或世界管理插件创建它，本插件不会替你建世界。");
                return true;
            }
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

    /** {@code /htf stop}：既有调试战线，也有正式对局，一起停掉。 */
    private boolean stopEverything(Player player) {
        final boolean abortedMatch = plugin.match() != null && plugin.match().abort();
        final boolean stoppedLine = stopLine(null);
        if (!abortedMatch && !stoppedLine) {
            player.sendMessage("当前没有进行中的对局或调试战线");
        } else {
            player.sendMessage("已停止" + (abortedMatch ? "对局" : "") 
                    + (abortedMatch && stoppedLine ? "与" : "")
                    + (stoppedLine ? "调试战线" : ""));
        }
        return true;
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
        final boolean had = progress != null || renderer != null;
        progress = null;
        chain = null;
        world = null;
        if (player != null && had) {
            player.sendMessage("战线已移除");
        }
        return had;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "setup", "map", "status", "start", "stop");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return List.of("red", "blue");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return List.of("point", "spawn", "undo", "start", "done", "reset", "cancel");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup")
                && args[1].equalsIgnoreCase("spawn")) {
            return List.of("red", "blue");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("map")) {
            return List.of("remove", "setworld");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("map")) {
            return new ArrayList<>(plugin.maps().keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("map")
                && args[1].equalsIgnoreCase("remove")) {
            return new ArrayList<>(plugin.maps().keySet());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("line") || args[0].equalsIgnoreCase("build"))) {
            return new ArrayList<>(plugin.chains().keySet());
        }
        return List.of();
    }
}
