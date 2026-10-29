package io.github.cmdgz.holdthefort.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import io.github.cmdgz.holdthefort.HoldTheFortPlugin;
import io.github.cmdgz.holdthefort.game.MatchPhase;
import io.github.cmdgz.holdthefort.game.Team;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.map.Spawn;

/**
 * 对局相关的玩家事件（§7）。
 */
public final class MatchListener implements Listener {

    private final HoldTheFortPlugin plugin;

    public MatchListener(HoldTheFortPlugin plugin) {
        this.plugin = plugin;
    }

    /** 友伤关闭（§7）。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        final Team a = plugin.teamOf(attacker);
        final Team v = plugin.teamOf(victim);
        if (a != null && a == v) {
            event.setCancelled(true);
        }
    }

    /** 死亡不掉落（§7）。 */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    /**
     * 重生回本队出生点（§7）。
     *
     * <p>用 {@code HIGHEST} 优先级，确保不被其它插件的默认重生位置覆盖；
     * 只设置**这一次**的重生点，不动玩家床铺或世界出生点。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.match() == null || plugin.match().phase() != MatchPhase.RUNNING) {
            return;
        }
        final Player player = event.getPlayer();
        final Team team = plugin.teamOf(player);
        if (team == null) {
            return;
        }
        final Location spawn = plugin.match().teamSpawn(team);
        if (spawn != null && spawn.getWorld() != null) {
            event.setRespawnLocation(spawn);
        }
        // 重生等待：先转观战，5 秒后再恢复（§7）
        final int seconds = plugin.config().respawnSeconds();
        player.setGameMode(GameMode.SPECTATOR);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && plugin.match().phase() == MatchPhase.RUNNING) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }.runTaskLater(plugin, seconds * 20L);
    }

    /** 退出立即移出计数（§5.2）：队伍信息保留，靠在线状态过滤即可。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.match() != null) {
            plugin.match().onQuit(event.getPlayer());
        }
        plugin.forget(event.getPlayer());
    }

    /**
     * 进服**不**做任何传送。
     *
     * <p>本插件可能装在别人的主城服务器上，把玩家拽走是越界的。参战一律靠主动报名。
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.match() != null && plugin.match().phase() == MatchPhase.RUNNING) {
            event.getPlayer().sendMessage("[战线] 对局进行中，下一局开始时你才会参战");
        }
    }

}
