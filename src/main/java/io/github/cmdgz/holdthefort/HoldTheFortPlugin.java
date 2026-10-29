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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.cmdgz.holdthefort.command.HoldTheFortCommand;
import io.github.cmdgz.holdthefort.command.SetupManager;
import io.github.cmdgz.holdthefort.listener.MatchListener;
import io.github.cmdgz.holdthefort.config.ConfigParser;
import io.github.cmdgz.holdthefort.config.PluginConfig;
import io.github.cmdgz.holdthefort.game.MatchManager;
import io.github.cmdgz.holdthefort.game.Team;
import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.map.Defaults;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.map.MapParseException;
import io.github.cmdgz.holdthefort.map.MapParser;
import io.github.cmdgz.holdthefort.map.MapReport;
import io.github.cmdgz.holdthefort.map.MapWriter;
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
    private MatchManager matchManager;
    private SetupManager setupManager;
    private boolean warnedAboutLobby;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!loadConfig()) {
            getLogger().severe("全局配置读取失败，插件不会加载任何地图");
            return;
        }
        loadMaps();
        registerCommands();

        matchManager = new MatchManager(this);
        matchManager.start();
        setupManager = new SetupManager(this);
        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        getServer().getPluginManager().registerEvents(setupManager, this);
        if (maps.isEmpty()) {
            getLogger().info("还没有任何地图。OP 在战场世界里执行 /htf setup 即可现场做一张。");
        }
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

    public PluginConfig config() {
        return config;
    }

    public MatchManager match() {
        return matchManager;
    }

    public SetupManager setup() {
        return setupManager;
    }

    /** 重新读取 maps/ 目录（游戏内建场落盘后调用）。 */
    public void reloadMaps() {
        maps.clear();
        chains.clear();
        loadMaps();
    }

    /** 大厅传送点；配置的世界不存在时退回主世界出生点。 */
    public Location lobbyLocation() {
        final var world = Bukkit.getWorld(config.lobbyWorld());
        if (world == null) {
            final var first = getServer().getWorlds().getFirst();
            if (!warnedAboutLobby) {
                warnedAboutLobby = true;
                getLogger().warning("config.yml 里的大厅世界 " + config.lobbyWorld()
                        + " 不存在，暂时用 " + first.getName() + " 的世界出生点代替。"
                        + "请把 lobby.world 改成实际的大厅世界名");
            }
            return first.getSpawnLocation();
        }
        return new Location(world, config.lobby().x(), config.lobby().y(), config.lobby().z());
    }

    /**
     * 把已保存的地图重新绑定到另一个世界，并写回文件。
     *
     * <p>几何不变、只换世界 —— 用于建场时站错世界、或战场世界改名的情况。
     *
     * @return {@code null} 表示成功，否则是失败原因
     */
    public String rebindMap(String name, String newWorld) {
        final MapDefinition old = maps.get(name);
        if (old == null) {
            return "没有这张地图：" + name;
        }
        final MapDefinition updated = new MapDefinition(
                old.name(), newWorld, old.redSpawn(), old.blueSpawn(), old.turnLimit(),
                old.speed(), old.epsilon(), old.startIndex(), old.checkpoints());
        final MapValidator.Result result = MapValidator.validate(updated);
        if (result.hasErrors()) {
            return "校验失败：" + result.of(MapValidator.Severity.ERROR).getFirst().message();
        }
        final File file = new File(getDataFolder(), MAPS_DIR + "/" + name + ".yml");
        try {
            Files.writeString(file.toPath(), MapWriter.toYaml(updated), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "写文件失败：" + e.getMessage();
        }
        maps.put(name, updated);
        chains.put(name, result.chain());
        return null;
    }

    /** 删除一张已保存的地图（连同文件）。 */
    public boolean removeMap(String name) {
        final File file = new File(getDataFolder(), MAPS_DIR + "/" + name + ".yml");
        if (!file.isFile() || !file.delete()) {
            return false;
        }
        maps.remove(name);
        chains.remove(name);
        return true;
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

    private void loadMaps() {
        final Path dir = new File(getDataFolder(), MAPS_DIR).toPath();
        if (!Files.isDirectory(dir)) {
            // 首次安装还没有 maps/ 是正常的，下面会提示怎么建第一张地图
            getLogger().info("尚未创建 " + MAPS_DIR + "/ 目录，跳过地图加载");
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

        if (map.world().equals(config.lobbyWorld())) {
            getLogger().warning("地图 " + map.name() + " 声明的世界（" + map.world()
                    + "）就是大厅世界，这张图无法开局。请在大厅之外的世界用 /htf setup 重建，"
                    + "或用 /htf map setworld " + map.name() + " <战场世界> 改绑定");
        } else if (Bukkit.getWorld(map.world()) == null) {
            getLogger().warning("地图 " + map.name() + " 声明的世界（" + map.world()
                    + "）当前未加载，开局前请先加载它");
        }

        maps.put(map.name(), map);
        chains.put(map.name(), result.chain());
    }
}
