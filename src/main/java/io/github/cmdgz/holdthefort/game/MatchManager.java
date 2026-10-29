package io.github.cmdgz.holdthefort.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import io.github.cmdgz.holdthefort.HoldTheFortPlugin;
import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.FrontlineSample;
import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.frontline.Zone;
import io.github.cmdgz.holdthefort.map.MapDefinition;
import io.github.cmdgz.holdthefort.map.Spawn;
import io.github.cmdgz.holdthefort.render.FrontlineRenderer;

/**
 * 对局流程（§7）：WAITING → STARTING → RUNNING → ENDING → WAITING。
 *
 * <p>每 tick 由插件调度一次；所有状态转换都写在这里，便于对照文档检查。
 */
public final class MatchManager {

    /** 倒计时 10 秒。 */
    private static final int COUNTDOWN_TICKS = 200;

    /** 结算展示 10 秒。 */
    private static final int ENDING_TICKS = 200;

    /** HUD 刷新间隔：每 2 tick 一次，与渲染同步（§4.4）。 */
    private static final int HUD_INTERVAL = 2;

    private static final Color COLOR_RED = Color.fromRGB(220, 60, 60);
    private static final Color COLOR_BLUE = Color.fromRGB(60, 110, 220);
    private static final Color COLOR_NEUTRAL = Color.fromRGB(220, 220, 220);

    private final HoldTheFortPlugin plugin;

    private MatchPhase phase = MatchPhase.WAITING;
    private MapDefinition map;
    private FrontlineChain chain;
    private ProgressState progress;
    private FrontlineRenderer renderer;
    private World world;

    private int countdown;
    /** 调试用的强制开局：跳过人数检查，单人也能试。 */
    private boolean forced;
    private int ending;
    private int hudCounter;

    private BossBar redBar;
    private BossBar blueBar;
    private Scoreboard scoreboard;
    private Objective sidebar;

    /**
     * 已报名本局的玩家。
     *
     * <p>**只有主动报名的人参与对局**。这是本插件能在任意服务器上与别人的大厅共存的前提：
     * 站在任何世界里都不会被自动拉进对局。
     */
    private final Set<UUID> queue = new HashSet<>();

    /** 上一 tick 每名玩家的区域，用来判断是否跨线（§4.3）。 */
    private final Map<UUID, Zone> lastZones = new HashMap<>();

    public MatchManager(HoldTheFortPlugin plugin) {
        this.plugin = plugin;
    }

    /** 启动 tick 循环。 */
    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public MatchPhase phase() {
        return phase;
    }

    /** 手动开始（调试用）：立即进入倒计时，且不受人数影响。 */
    public void forceStart() {
        if (phase == MatchPhase.WAITING) {
            forced = true;
            beginCountdown();
        }
    }

    /** 报名：{@code team} 为 {@code null} 表示先排队，开局时自动分到人少的一队（§7）。 */
    public boolean join(Player player, Team team) {
        if (phase == MatchPhase.RUNNING || phase == MatchPhase.ENDING) {
            player.sendMessage("对局进行中，请等下一局。当前不会把你拉进正在进行的对局。");
            return false;
        }
        if (team != null && countTeam(team) >= plugin.config().teamLimit()
                && plugin.teamOf(player) != team) {
            player.sendMessage(teamName(team) + "已满（" + plugin.config().teamLimit() + " 人）");
            return false;
        }
        queue.add(player.getUniqueId());
        if (team != null) {
            plugin.setTeam(player, team);
        }
        player.sendMessage(team == null
                ? "已加入等待，开局时自动分到人少的一队"
                : "已加入" + teamName(team) + "，当前 " + countTeam(team)
                        + "/" + plugin.config().teamLimit() + " 人");
        return true;
    }

    /** 退出报名。 */
    public boolean leave(Player player) {
        if (phase == MatchPhase.RUNNING) {
            player.sendMessage("对局进行中，无法退出；直接离开服务器即可");
            return false;
        }
        queue.remove(player.getUniqueId());
        plugin.forget(player);
        player.sendMessage("已退出等待");
        return true;
    }

    /** 玩家下线：立即移出队列，不造成死锁（§7）。 */
    public void onQuit(Player player) {
        queue.remove(player.getUniqueId());
    }

    // ---------------------------------------------------------------- tick

    private void tick() {
        switch (phase) {
            case WAITING -> tickWaiting();
            case STARTING -> tickStarting();
            case RUNNING -> tickRunning();
            case ENDING -> tickEnding();
            case IDLE -> {
                // 什么都不做
            }
        }
    }

    private void tickWaiting() {
        if (canStart()) {
            beginCountdown();
        }
    }

    private void tickStarting() {
        if (!canStart()) {
            // 人数掉回阈值以下：取消倒计时（§7）
            phase = MatchPhase.WAITING;
            countdown = 0;
            broadcast("人数不足，倒计时取消");
            return;
        }
        if (--countdown <= 0) {
            startMatch();
            return;
        }
        if (countdown % 20 == 0) {
            broadcast("对局将在 " + (countdown / 20) + " 秒后开始");
        }
    }

    private void tickRunning() {
        if (activeParticipants().isEmpty()) {
            // 对局中全员离开：直接结束，不计胜负（§7）
            broadcast("全员离开，对局中止");
            finish(null);
            return;
        }

        final Counts counts = countInvaders();
        final double factor = ZoneCounter.factor(counts);

        final double before = progress.u();
        progress.tick(0.05, factor);
        final double delta = progress.u() - before;

        if (hudCounter++ % HUD_INTERVAL == 0) {
            final FrontlineSample sample = chain.sample(progress.u());
            renderer.update(sample, colorFor(delta));
            updateHud(counts);
            updateCrossLineHints();
        }

        if (!progress.isRunning()) {
            finish(progress.outcome());
        }
    }

    private void tickEnding() {
        if (--ending <= 0) {
            toLobby();
            phase = MatchPhase.WAITING;
            cleanupMatch();
        }
    }

    // ------------------------------------------------------------ 阶段转换

    /**
     * 开局条件（§7）：人数达标，且**每队至少 1 人**（未选边者会自动补位）。
     *
     * <p>只统计位于游戏区域（大厅或战场世界）的玩家 —— 跑到别的地图的人不算，
     * 否则倒计时会在没人时继续推进。
     */
    private boolean canStart() {
        if (forced) {
            return true;
        }
        int red = 0;
        int blue = 0;
        int free = 0;
        for (Player player : queuedPlayers()) {
            final Team team = plugin.teamOf(player);
            if (team == Team.RED) {
                red++;
            } else if (team == Team.BLUE) {
                blue++;
            } else {
                free++;
            }
        }
        final int total = red + blue + free;
        return total >= plugin.config().minPlayers()
                && total >= 2
                && red + free >= 1
                && blue + free >= 1;
    }

    private void beginCountdown() {
        phase = MatchPhase.STARTING;
        countdown = COUNTDOWN_TICKS;
        broadcast("人数已达标，10 秒后开始");
    }

    private void startMatch() {
        forced = false;
        final Map<String, FrontlineChain> chains = plugin.chains();
        if (chains.isEmpty()) {
            broadcast("没有可用的地图，无法开局");
            phase = MatchPhase.WAITING;
            return;
        }
        final String name = chains.keySet().iterator().next();
        map = plugin.maps().get(name);
        chain = chains.get(name);

        // 战场必须是独立世界：绝不退回主世界，否则会直接在别人的大厅里开打
        world = Bukkit.getWorld(map.world());
        if (world == null) {
            broadcast("地图 " + name + " 的世界 " + map.world() + " 未加载，无法开局");
            map = null;
            chain = null;
            phase = MatchPhase.WAITING;
            return;
        }
        final World lobby = lobbyWorld();
        if (!plugin.config().allowSameWorld() && lobby != null && lobby.equals(world)) {
            broadcast("战场世界与大厅世界相同（" + world.getName()
                    + "），拒绝开局。请让地图使用独立世界，或设 match.allow_same_world: true");
            map = null;
            chain = null;
            phase = MatchPhase.WAITING;
            return;
        }

        // 未选边者自动分到人少的一队（§7）
        autoAssign();

        progress = new ProgressState(chain, map.speed(), plugin.config().acceleration());
        renderer = new FrontlineRenderer(world, chain);
        renderer.spawn(chain.sample(0));

        createHud();
        lastZones.clear();

        for (Player player : activeParticipants()) {
            preparePlayer(player);
        }

        phase = MatchPhase.RUNNING;
        hudCounter = 0;
        broadcast("对局开始！");
    }

    private void finish(MatchOutcome outcome) {
        phase = MatchPhase.ENDING;
        forced = false;
        ending = ENDING_TICKS;
        if (outcome != null) {
            broadcast(switch (outcome) {
                case RED_WIN -> "红方胜利：战线推到了蓝方基地";
                case BLUE_WIN -> "蓝方胜利：战线推到了红方基地";
                case DRAW -> "平局：双方推进路程相同";
                case ONGOING -> "";
            });
        }
        if (renderer != null) {
            renderer.remove();
            renderer = null;
        }
        clearHud();
    }

    private void cleanupMatch() {
        map = null;
        chain = null;
        progress = null;
        world = null;
        lastZones.clear();
    }

    private void toLobby() {
        final Location lobby = plugin.lobbyLocation();
        if (lobby == null) {
            return;
        }
        for (Player player : activeParticipants()) {
            player.teleport(lobby);
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void preparePlayer(Player player) {
        final Team team = plugin.teamOf(player);
        if (team == null) {
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        final Spawn spawn = team == Team.RED ? map.redSpawn() : map.blueSpawn();
        player.teleport(new Location(world, spawn.x(), spawn.y(), spawn.z()));
    }

    // ---------------------------------------------------------------- 计数

    private Counts countInvaders() {
        final List<Vec2> reds = new ArrayList<>();
        final List<Vec2> blues = new ArrayList<>();
        for (Player player : activeParticipants()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            final Vec2 pos = new Vec2(player.getLocation().getX(), player.getLocation().getZ());
            if (plugin.teamOf(player) == Team.RED) {
                reds.add(pos);
            } else {
                blues.add(pos);
            }
        }
        return ZoneCounter.count(chain, progress.u(), reds, blues);
    }

    /**
     * 已报名的在线玩家。
     *
     * <p>这是「谁参与这一局」的唯一判据 —— 与所在世界无关。没报名的人哪怕站在战场正中也不参与。
     */
    private List<Player> queuedPlayers() {
        final List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (queue.contains(player.getUniqueId())) {
                result.add(player);
            }
        }
        return result;
    }

    /** 已分队且可计数的玩家（§5.2）。 */
    private List<Player> activeParticipants() {
        final List<Player> result = new ArrayList<>();
        for (Player player : queuedPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (plugin.teamOf(player) != null) {
                result.add(player);
            }
        }
        return result;
    }

    private World lobbyWorld() {
        final Location lobby = plugin.lobbyLocation();
        return lobby == null ? null : lobby.getWorld();
    }

    private World arenaWorld() {
        final MapDefinition first = plugin.maps().values().stream().findFirst().orElse(null);
        return first == null ? null : Bukkit.getWorld(first.world());
    }

    private void autoAssign() {
        final List<Player> unassigned = new ArrayList<>();
        for (Player player : queuedPlayers()) {
            if (plugin.teamOf(player) == null) {
                unassigned.add(player);
            }
        }
        for (Player player : unassigned) {
            plugin.setTeam(player, countTeam(Team.RED) <= countTeam(Team.BLUE) ? Team.RED : Team.BLUE);
        }
    }

    private long countTeam(Team team) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.teamOf(p) == team)
                .count();
    }

    // ------------------------------------------------------------------ HUD

    private void createHud() {
        redBar = Bukkit.createBossBar("红方", BarColor.RED, BarStyle.SOLID);
        blueBar = Bukkit.createBossBar("蓝方", BarColor.BLUE, BarStyle.SOLID);
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        sidebar = scoreboard.registerNewObjective("htf", Criteria.DUMMY, "战线模式");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Player player : activeParticipants()) {
            redBar.addPlayer(player);
            blueBar.addPlayer(player);
            player.setScoreboard(scoreboard);
        }
    }

    private void updateHud(Counts counts) {
        final double toBlue = chain.pathLength(0, chain.to());
        final double toRed = Math.abs(chain.pathLength(0, chain.from()));
        final double u = progress.u();
        final double redProgress = toBlue > 0 ? clamp(chain.pathLength(0, u) / toBlue) : 0;
        final double blueProgress = toRed > 0 ? clamp(Math.abs(chain.pathLength(0, u)) / toRed) : 0;

        if (redBar != null) {
            redBar.setProgress(redProgress);
            redBar.setTitle("红方推进 " + Math.round(redProgress * 100) + "%");
        }
        if (blueBar != null) {
            blueBar.setProgress(blueProgress);
            blueBar.setTitle("蓝方推进 " + Math.round(blueProgress * 100) + "%");
        }
        if (sidebar != null) {
            final int remaining = (int) Math.max(0, plugin.config().acceleration().tmaxSeconds() - progress.elapsedSeconds());
            setLine("red", "入侵者（红方） " + counts.redInvaders());
            setLine("blue", "入侵者（蓝方） " + counts.blueInvaders());
            setLine("seg", "段号 " + (int) Math.floor(u));
            setLine("time", "剩余 " + (remaining / 60) + " 分 " + (remaining % 60) + " 秒");
        }
    }

    private void setLine(String key, String text) {
        final var entry = sidebar.getScore(key);
        entry.setScore(0);
        // 用队伍前缀显示文本，避免每个 tick 重建计分项
        final var team = scoreboard.getTeam(key);
        if (team == null) {
            final var created = scoreboard.registerNewTeam(key);
            created.addEntry(key);
            created.setPrefix(text);
        } else {
            team.setPrefix(text);
        }
        sidebar.getScore(key).setScore(0);
    }

    private void clearHud() {
        if (redBar != null) {
            redBar.removeAll();
            redBar = null;
        }
        if (blueBar != null) {
            blueBar.removeAll();
            blueBar = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getScoreboard().equals(scoreboard)) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        scoreboard = null;
        sidebar = null;
    }

    /** 跨线即时提示（§4.3）。 */
    private void updateCrossLineHints() {
        for (Player player : activeParticipants()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            final Vec2 pos = new Vec2(player.getLocation().getX(), player.getLocation().getZ());
            final Zone zone = chain.zoneAt(pos, progress.u());
            final Zone previous = lastZones.put(player.getUniqueId(), zone);
            if (previous == null || previous == zone) {
                continue;
            }
            final Team team = plugin.teamOf(player);
            final Zone enemySide = team == Team.RED ? Zone.BLUE : Zone.RED;
            if (zone == enemySide && previous != enemySide) {
                player.sendActionBar("已进入敌方区域");
            } else if (previous == enemySide && zone != enemySide) {
                player.sendActionBar("已离开敌方区域");
            }
        }
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static Color colorFor(double deltaU) {
        if (deltaU > 1e-9) {
            return COLOR_RED;
        }
        if (deltaU < -1e-9) {
            return COLOR_BLUE;
        }
        return COLOR_NEUTRAL;
    }

    private static String teamName(Team team) {
        return team == Team.RED ? "红方" : "蓝方";
    }

    private void broadcast(String message) {
        queuedPlayers().forEach(p -> p.sendMessage("[战线] " + message));
        plugin.getLogger().info(message);
    }
}
