package io.github.cmdgz.holdthefort.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import io.github.cmdgz.holdthefort.HoldTheFortPlugin;
import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.game.Acceleration;
import io.github.cmdgz.holdthefort.game.ProgressState;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.render.FrontlineRenderer;

/**
 * 调试命令 {@code /htf}。
 *
 * <p>当前只提供「把某张地图的战线摆出来并让它动」的能力，用于肉眼验证 §3、§4、§6。
 */
public final class HoldTheFortCommand implements CommandExecutor, TabCompleter {

    /** 渲染刷新间隔（tick）。粒子比较贵，不必每 tick 重画（§4.4）。 */
    private static final int RENDER_INTERVAL = 10;

    private final HoldTheFortPlugin plugin;

    private FrontlineRenderer renderer;
    private FrontlineChain chain;
    private ProgressState progress;
    private BukkitTask task;
    private int tickCounter;

    public HoldTheFortCommand(HoldTheFortPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("这条命令只能由玩家执行");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("用法：/htf line <地图名> | /htf stop");
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "line" -> startLine(player, args.length > 1 ? args[1] : null);
            case "stop" -> stopLine(player);
            default -> {
                player.sendMessage("未知子命令：" + args[0]);
                yield true;
            }
        };
    }

    private boolean startLine(Player player, String mapName) {
        if (mapName == null) {
            player.sendMessage("请指定地图名，可用：" + plugin.chains().keySet());
            return true;
        }
        final FrontlineChain chain = plugin.chains().get(mapName);
        final MapDefinition map = plugin.maps().get(mapName);
        if (chain == null || map == null) {
            player.sendMessage("找不到地图 " + mapName + "，可用：" + plugin.chains().keySet());
            return true;
        }

        stopLine(player);

        this.chain = chain;
        renderer = new FrontlineRenderer(player.getWorld(), chain);
        progress = new ProgressState(chain, map.speed(), Acceleration.defaults());
        renderer.spawn(chain.sample(0));
        tickCounter = 0;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick,
                1L, 1L);

        player.sendMessage("已在 " + mapName + " 上召唤战线，u = 0，正在以红方优势推进");
        return true;
    }

    private void tick() {
        if (progress == null || renderer == null || chain == null) {
            return;
        }
        progress.tick(0.05, 1.0);
        if (++tickCounter % RENDER_INTERVAL == 0) {
            // §4.2：颜色表示趋势，这里恒为红方推进
            renderer.update(chain.sample(progress.u()), Color.fromRGB(220, 60, 60));
        }
        if (!progress.isRunning()) {
            stopLine(null);
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
        if (player != null) {
            player.sendMessage("战线已移除");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("line", "stop");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("line")) {
            return new ArrayList<>(plugin.chains().keySet());
        }
        return List.of();
    }
}
