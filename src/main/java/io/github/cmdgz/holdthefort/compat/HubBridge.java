package io.github.cmdgz.holdthefort.compat;

import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 与主城（HubCore / MiniGameLib）的可选对接，**用反射实现、不产生编译期依赖**。
 *
 * <p>没装 HubCore 时所有调用都静默跳过，插件仍可独立运行。
 *
 * <p>为什么必须对接：HubCore 用「有没有登记游戏」判断玩家在不在大厅
 * （{@code isInLobby(uuid) == gameMap.get(id) == null}）。不登记的话，
 * 玩家一重生就会在 1 tick 后被它拉回主城 —— 表现就是「死后没在战场出生」。
 */
public final class HubBridge {

    private final Plugin plugin;
    private final boolean available;
    private Method setGame;
    private Method setPlaying;
    private Method clearGame;

    public HubBridge(Plugin plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("MiniGameLib") != null
                && Bukkit.getPluginManager().getPlugin("HubCore") != null;
        if (available) {
            try {
                final Class<?> api = Class.forName("com.hub.api.HubAPI");
                setGame = api.getMethod("setGame", UUID.class, String.class);
                setPlaying = api.getMethod("setPlaying", UUID.class, String.class);
                clearGame = api.getMethod("clearGame", UUID.class);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("检测到 MiniGameLib，但 HubAPI 签名不匹配，已跳过主城对接："
                        + e.getMessage());
            }
        }
    }

    public boolean available() {
        return available && setPlaying != null;
    }

    /** 已报名（等待中）。 */
    public void setWaiting(Player player) {
        invoke(setGame, player.getUniqueId(), plugin.getName());
    }

    /** 对局中。 */
    public void setPlaying(Player player) {
        invoke(setPlaying, player.getUniqueId(), plugin.getName());
    }

    /** 退出游戏。 */
    public void clear(Player player) {
        invoke(clearGame, player.getUniqueId());
    }

    private void invoke(Method method, Object... args) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("调用 HubAPI 失败：" + e.getMessage());
        }
    }
}
