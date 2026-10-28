package io.github.cmdgz.holdthefort;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 战线模式（Hold the Fort）主入口。
 *
 * <p>当前仅为工程骨架：加载时打印版本，不注册任何玩法。
 * 玩法机制见仓库根目录的 {@code docs/design.md}。
 */
public final class HoldTheFortPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        final String version = getPluginMeta().getVersion();
        getLogger().info("Hold the Fort v" + version + " 已启用（骨架版本，暂无玩法）");
    }

    @Override
    public void onDisable() {
        getLogger().info("Hold the Fort 已停用");
    }
}
