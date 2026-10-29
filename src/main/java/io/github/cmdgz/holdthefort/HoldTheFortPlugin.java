package io.github.cmdgz.holdthefort;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.cmdgz.holdthefort.command.HoldTheFortCommand;
import io.github.cmdgz.holdthefort.config.ConfigParser;
import io.github.cmdgz.holdthefort.config.PluginConfig;
import io.github.cmdgz.holdthefort.game.Team;
import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.map.Defaults;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.map.MapParseException;
import io.github.cmdgz.holdthefort.map.MapParser;
import io.github.cmdgz.holdthefort.map.MapReport;
import io.github.cmdgz.holdthefort.map.MapValidator;

/**
 * 战线模式（Hold the Fort）主入口。
 *
 * <p>当前阶段做「加载 + 校验 + 打印读数」：启动时读全局配置与地图，把结果写进日志。
 * 玩法（实体、粒子、对局流程）见仓库的 {@code docs/design.md}。
 */
public final class HoldTheFortPlugin extends JavaPlugin {

    private static final String MAPS_DIR = "maps";

    private PluginConfig config;
    private final Map<String, MapDefinition> maps = new LinkedHashMap<>();
    private final Map<String, FrontlineChain> chains = new LinkedHashMap<>();
    /** 临时的分队表；正式的对局分队属于 §7 的流程，尚未实现。 */
    private final Map<UUID, Team> teams = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!loadConfig()) {
            getLogger().severe("全局配置读取失败，插件不会加载任何地图");
            return;
        }
        ensureSampleMap();
        loadMaps();
        registerCommands();
        getLogger().info("Hold the Fort v" + getPluginMeta().getVersion()
                + " 已启用，成功加载 " + maps.size() + " 张地图");
    }

    private void registerCommands() {
        final PluginCommand command = getCommand("htf");
        if (command == null) {
            getLogger().warning("plugin.yml 里没有 htf 命令");
            return;
        }
        final HoldTheFortCommand executor = new HoldTheFortCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    public void onDisable() {
        getLogger().info("Hold the Fort 已停用");
    }

    /** 已加载的地图名，供后续对局流程使用。 */
    public Map<String, FrontlineChain> chains() {
        return Map.copyOf(chains);
    }

    public Map<String, MapDefinition> maps() {
        return Map.copyOf(maps);
    }

    /** 玩家所在队伍，未分队时返回 {@code null}。 */
    public Team teamOf(Player player) {
        return teams.get(player.getUniqueId());
    }

    public void setTeam(Player player, Team team) {
        teams.put(player.getUniqueId(), team);
    }

    /** 玩家退出时清掉分队记录。 */
    public void forget(Player player) {
        teams.remove(player.getUniqueId());
    }

    private boolean loadConfig() {
        final File file = new File(getDataFolder(), "config.yml");
        try {
            config = ConfigParser.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return true;
        } catch (IOException | RuntimeException e) {
            getLogger().severe("读取 config.yml 失败：" + e.getMessage());
            return false;
        }
    }

    /** 首次运行时把示例地图放出去，免得玩家对着空目录发呆。 */
    private void ensureSampleMap() {
        final File sample = new File(getDataFolder(), MAPS_DIR + "/river_crossing.yml");
        if (sample.exists()) {
            return;
        }
        try {
            saveResource(MAPS_DIR + "/river_crossing.yml", false);
        } catch (IllegalArgumentException ignored) {
            // jar 里没有这个资源时跳过，不影响其它地图
        }
    }

    private void loadMaps() {
        final Path dir = new File(getDataFolder(), MAPS_DIR).toPath();
        if (!Files.isDirectory(dir)) {
            getLogger().warning("没有找到 " + MAPS_DIR + "/ 目录，跳过地图加载");
            return;
        }
        final Defaults defaults = config.defaults();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .forEach(path -> loadMap(path, defaults));
        } catch (IOException e) {
            getLogger().severe("列举地图目录失败：" + e.getMessage());
        }
    }

    private void loadMap(Path path, Defaults defaults) {
        final String fileName = path.getFileName().toString();
        final MapDefinition map;
        try {
            map = MapParser.parse(Files.readString(path, StandardCharsets.UTF_8), defaults);
        } catch (MapParseException e) {
            getLogger().severe(fileName + " 解析失败：" + e.getMessage());
            return;
        } catch (IOException e) {
            getLogger().severe(fileName + " 读取失败：" + e.getMessage());
            return;
        }

        final MapValidator.Result result = MapValidator.validate(map);
        for (MapValidator.Problem problem : result.problems()) {
            if (problem.severity() == MapValidator.Severity.ERROR) {
                getLogger().severe(fileName + " " + problem.message());
            } else {
                getLogger().warning(fileName + " " + problem.message());
            }
        }

        if (result.hasErrors()) {
            getLogger().severe(fileName + " 未通过校验，已跳过");
            return;
        }

        MapReport.describe(map, result.chain())
                .forEach(line -> getLogger().info("[" + map.name() + "] " + line));

        maps.put(map.name(), map);
        chains.put(map.name(), result.chain());
    }
}
