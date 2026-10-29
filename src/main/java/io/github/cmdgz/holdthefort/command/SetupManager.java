package io.github.cmdgz.holdthefort.command;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.cmdgz.holdthefort.HoldTheFortPlugin;
import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.map.CheckpointPair;
import io.github.cmdgz.holdthefort.map.Defaults;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.map.MapValidator;
import io.github.cmdgz.holdthefort.map.MapWriter;
import io.github.cmdgz.holdthefort.map.TurnLimit;

/**
 * 游戏内建场（{@code /htf setup}）：让管理员不碰任何文件就能做出一张地图。
 *
 * <p>用法：拿标尺木棍**左键点一下、右键点一下**组成一帧，重复若干次；
 * 再站在两端各执行一次 {@code /htf setup spawn red|blue}；最后 {@code /htf setup done} 落盘。
 */
public final class SetupManager implements Listener {

    private final HoldTheFortPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, SetupSession> sessions = new HashMap<>();

    public SetupManager(HoldTheFortPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "setup_wand");
    }

    /** 开始（或重来）建场：发一根标尺木棍。 */
    public void begin(Player player) {
        final SetupSession session = new SetupSession(player.getWorld().getName());
        sessions.put(player.getUniqueId(), session);

        final ItemStack wand = new ItemStack(Material.STICK);
        final ItemMeta meta = wand.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("战线标尺")
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("左键 = 本帧第一个检查点")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                net.kyori.adventure.text.Component.text("右键 = 本帧第二个检查点")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);

        player.sendMessage("§6[建场] §f已开始，世界：§e" + session.worldName());
        player.sendMessage("§7用左键/右键各点一下方块 = 记一帧，至少 3 帧。");
        player.sendMessage("§7然后：§e/htf setup spawn red§7、§e/htf setup spawn blue§7，最后 §e/htf setup done");
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** 记录一个出生点。 */
    public boolean recordSpawn(Player player, boolean red) {
        final SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c先执行 /htf setup 开始建场");
            return true;
        }
        session.setSpawn(red,
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ());
        player.sendMessage("§a已记录" + (red ? "红方" : "蓝方") + "出生点："
                + player.getLocation().getBlockX() + ", "
                + player.getLocation().getBlockY() + ", "
                + player.getLocation().getBlockZ());
        return true;
    }

    /** 撤销最后一个点或最后一帧。 */
    public boolean undo(Player player) {
        final SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c先执行 /htf setup 开始建场");
            return true;
        }
        player.sendMessage("§e" + session.undo());
        return true;
    }

    /** 指定起始帧编号（0 基）。 */
    public boolean setStartIndex(Player player, String raw) {
        final SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c先执行 /htf setup 开始建场");
            return true;
        }
        try {
            session.setStartIndex(Integer.parseInt(raw));
            player.sendMessage("§a起始帧编号设为 " + raw);
        } catch (NumberFormatException e) {
            player.sendMessage("§c起始帧编号必须是整数（从 0 开始数）");
        }
        return true;
    }

    /** 清空当前世界的所有已记录内容，重新开始。 */
    public boolean reset(Player player) {
        final SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c没有进行中的建场，先执行 /htf setup");
            return true;
        }
        session.reset();
        player.sendMessage("§a已清空本世界的关键帧与出生点，可以重新点线了");
        player.sendMessage("§7已经保存过的地图文件不受影响；要删掉它用 §e/htf map remove <地图名>");
        return true;
    }

    /** 落盘。 */
    public boolean done(Player player) {
        final SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c没有进行中的建场");
            return true;
        }
        final String missing = session.missing();
        if (missing != null) {
            player.sendMessage("§c还不能完成：" + missing);
            return true;
        }

        final var lobby = plugin.lobbyLocation();
        if (!plugin.config().allowSameWorld() && lobby != null
                && lobby.getWorld().getName().equals(session.worldName())) {
            player.sendMessage("§c你现在站在大厅世界（" + session.worldName() + "）里建场。");
            player.sendMessage("§7战场必须在大厅之外的世界。用 /gw tp <战场世界> 之类的方式过去再来。");
            return true;
        }

        final String name = session.worldName().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        final Defaults defaults = plugin.config().defaults();
        final MapDefinition map = new MapDefinition(
                name,
                session.worldName(),
                session.redSpawn(),
                session.blueSpawn(),
                TurnLimit.NINETY,
                defaults.speed(),
                defaults.epsilon(),
                session.resolvedStartIndex(),
                session.frames());

        final MapValidator.Result result = MapValidator.validate(map);
        if (result.hasErrors()) {
            player.sendMessage("§c地图校验失败：");
            result.of(MapValidator.Severity.ERROR)
                    .forEach(p -> player.sendMessage("§7 - " + p.message()));
            player.sendMessage("§7可以用 §e/htf setup undo §7撤销后重来");
            return true;
        }

        final File dir = new File(plugin.getDataFolder(), "maps");
        final File file = new File(dir, name + ".yml");
        try {
            Files.createDirectories(dir.toPath());
            Files.writeString(file.toPath(), MapWriter.toYaml(map), StandardCharsets.UTF_8);
        } catch (IOException e) {
            player.sendMessage("§c写入地图文件失败：" + e.getMessage());
            return true;
        }

        sessions.remove(player.getUniqueId());
        takeBackWand(player);
        plugin.reloadMaps();

        player.sendMessage("§a地图已保存：" + file.getName() + "（" + session.frames().size()
                + " 帧）");
        player.sendMessage("§7  绑定世界：§f" + session.worldName()
                + "§7（建场开始时你所在的：§f" + session.worldName()
                + "§7；现在你在：§f" + player.getWorld().getName() + "§7）");
        player.sendMessage("§7现在可以用 §e/htf join red §7开始测试了");
        return true;
    }

    /** 放弃建场。 */
    public boolean cancel(Player player) {
        sessions.remove(player.getUniqueId());
        takeBackWand(player);
        player.sendMessage("§7已放弃本次建场");
        return true;
    }

    /** 把标尺木棍从背包里收掉，免得玩家带着一根没用的棍子。 */
    private void takeBackWand(Player player) {
        final var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || !stack.hasItemMeta()) {
                continue;
            }
            if (stack.getItemMeta().getPersistentDataContainer()
                    .has(wandKey, PersistentDataType.BYTE)) {
                inventory.setItem(slot, null);
            }
        }
    }

    // ------------------------------------------------------------- 木棍点击

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        final ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        if (!item.getItemMeta().getPersistentDataContainer()
                .has(wandKey, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);

        final Player player = event.getPlayer();
        final SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        if (!event.getClickedBlock().getWorld().getName().equals(session.worldName())) {
            player.sendMessage("§c请一直在同一个世界里建场");
            return;
        }

        final Vec2 point = new Vec2(
                event.getClickedBlock().getX() + 0.5,
                event.getClickedBlock().getZ() + 0.5);
        final CheckpointPair completed = session.recordPoint(point);

        if (completed == null) {
            player.sendMessage("§7已记录一个点，再点一下组成一帧");
        } else {
            player.sendMessage("§a第 " + session.frames().size() + " 帧已记录");
        }
    }
}
