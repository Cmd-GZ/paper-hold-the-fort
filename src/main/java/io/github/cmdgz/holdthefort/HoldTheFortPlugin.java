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

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.hub.api.HubAPI;
import com.hub.world.Worlds;

import io.github.cmdgz.holdthefort.command.HoldTheFortCommand;
import io.github.cmdgz.holdthefort.command.SetupManager;
import io.github.cmdgz.holdthefort.listener.DamageDebugListener;
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

        // 主城对接：直接走 MiniGameLib 的 HubAPI（plugin.yml 已 depend MiniGameLib + HubCore）。
        // HubAPI 内部在 service == null 时是空操作，所以"主城没就绪"的降级行为与改造前一致。
        if (HubAPI.isReady()) {
            getLogger().info("已连上主城（HubAPI），主城对接已启用（tab 状态、重生不回主城）");
        } else {
            getLogger().warning("HubAPI 未就绪：tab 状态与「重生不回主城」暂时不会生效");
        }
        matchManager = new MatchManager(this);
        matchManager.start();
        setupManager = new SetupManager(this);
        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        getServer().getPluginManager().registerEvents(setupManager, this);
        // 对局中默认输出玩家间伤害的最终判定，便于排查「打不掉血」
        getServer().getPluginManager().registerEvents(new DamageDebugListener(this), this);
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

    /**
     * 世界 token → {@link World}（R2 坐标解耦的唯一解析入口）。
     *
     * <p>两种 token 都认：
     * <ul>
     *   <li>{@code world_htf} —— 固定世界名，旧配置写法，行为与改造前完全相同；</li>
     *   <li>{@code tpl:<模板键>} —— 模板作用域，由 MiniGameLib 解析成该模板当前房间实例的世界
     *       （HTF 还没有房间化，所以现在解析不出来是正常的）。</li>
     * </ul>
     *
     * <p>⚠️ 解析不出来（世界没加载 / 模板没有存活房间）一律返回 {@code null}，
     * 调用方必须按"场地未就绪"处理 —— 绝不能退化到别的世界（尤其不能退回大厅世界）。
     */
    public World resolveWorld(String token) {
        return Worlds.resolve(token);
    }

    /**
     * 地图声明的世界是否就是大厅世界（开局的硬性禁区，§9.1「不回退世界」）。
     *
     * <p>判定规则是**两边都转成世界 token 再比**（token 由 {@code com.hub.world.Worlds} 定义）：
     * <ol>
     *   <li>两个 token 字符串相等 → 是同一处世界。固定世界名（{@code world_htf}）走这条，
     *       所以「世界没加载时也能靠字符串认出大厅世界」的旧行为原样保留；</li>
     *   <li>写法不同（一边 {@code tpl:<模板键>}、一边固定世界名）才去解析实际世界，
     *       把解析出来的世界**再转回 token** 比；解析不出来 = 无法判定 = 不是大厅世界
     *       （这张图本来就会因为「世界未加载」被排除，不会因此被放行开局）。</li>
     * </ol>
     *
     * <p>⚠️ 不要退回 {@code world.getName().equals(lobbyWorld)}：地图声明 {@code tpl:x} 时，
     * 实际世界名是房间实例名（{@code room_xxx}），拿它和大厅世界名比永远不相等。
     */
    public boolean isLobbyWorld(MapDefinition map) {
        final String declared = map == null ? null : map.world();
        if (declared == null) {
            return false;
        }
        final String lobbyWorld = config.lobbyWorld();
        if (lobbyWorld == null || lobbyWorld.isBlank()) {
            return false;
        }
        if (declared.equals(lobbyWorld)) {
            return true;
        }
        final World actual = Worlds.resolve(declared);
        return actual != null && Worlds.tokenOf(actual).equals(lobbyWorld);
    }

    /** 重新读取 maps/ 目录（游戏内建场落盘后调用）。 */
    public void reloadMaps() {
        maps.clear();
        chains.clear();
        loadMaps();
    }

    /** 大厅传送点；配置的世界不存在时退回主世界出生点。 */
    public Location lobbyLocation() {
        final var world = resolveWorld(config.lobbyWorld());
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

        if (isLobbyWorld(map)) {
            getLogger().warning("地图 " + map.name() + " 声明的世界（" + map.world()
                    + "）就是大厅世界，这张图无法开局。请在大厅之外的世界用 /htf setup 重建，"
                    + "或用 /htf map setworld " + map.name() + " <战场世界> 改绑定");
        } else if (resolveWorld(map.world()) == null) {
            getLogger().warning("地图 " + map.name() + " 声明的世界（" + map.world()
                    + "）当前未加载，开局前请先加载它");
        }

        maps.put(map.name(), map);
        chains.put(map.name(), result.chain());
    }
}
