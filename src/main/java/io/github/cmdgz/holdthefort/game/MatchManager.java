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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
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
import io.github.cmdgz.holdthefort.world.ArenaWorldConfig;

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
    private static final int HUD_INTERVAL = 1;

    /** 对波条的判定死区：不到这个幅度就显示中性色，避免来回闪。 */
    private static final double TUG_DEADBAND = 0.02;

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

    /** 开局失败后的冷却截止时刻（毫秒）。避免条件一直成立却开不了局时无限刷屏。 */
    private long retryBlockedUntil;

    /** 开局失败后的冷却时长。 */
    private static final long RETRY_COOLDOWN_MS = 30_000L;
    private int ending;
    private int hudCounter;

    /** 单条「对波」进度条：中点 = 势均力敌，越靠右红方越接近胜利。 */
    private BossBar tugBar;
    private Scoreboard scoreboard;
    private Objective sidebar;

    /**
     * 已报名本局的玩家。
     *
     * <p>**只有主动报名的人参与对局**。这是本插件能在任意服务器上与别人的大厅共存的前提：
     * 站在任何世界里都不会被自动拉进对局。
     */
    private final Set<UUID> queue = new HashSet<>();

    /** 开局临时打开 PvP 时记下的原值，结束时还原（§7）。 */
    private World pvpWorld;
    private boolean savedPvp;
    private boolean hasSavedPvp;

    /** 每名玩家手里那枚「战线罗盘」的标记，用来识别与回收（§7.1）。 */
    private NamespacedKey compassKey;

    /** 罗盘指向的刷新间隔：战线每秒只挪不到一格，用不着每 tick 刷。 */
    private static final int COMPASS_INTERVAL = 20;

    private int compassCounter;

    /** 上一 tick 每名玩家的区域，用来判断是否跨线（§4.3）。 */
    private final Map<UUID, Zone> lastZones = new HashMap<>();

    public MatchManager(HoldTheFortPlugin plugin) {
        this.plugin = plugin;
        this.compassKey = new NamespacedKey(plugin, "frontline_compass");
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

    /**
     * 强行中止当前对局（{@code /htf stop}）：倒计时、对局、结算都会被终止，回到 WAITING。
     *
     * @return 是否真的中止了什么
     */
    public boolean abort() {
        if (phase == MatchPhase.WAITING || phase == MatchPhase.IDLE) {
            return false;
        }
        if (renderer != null) {
            renderer.remove();
            renderer = null;
        }
        clearHud();
        toLobby();
        clearParticipants();
        forced = false;
        countdown = 0;
        ending = 0;
        retryBlockedUntil = 0;
        phase = MatchPhase.WAITING;
        cleanupMatch();
        broadcast("对局已被手动终止");
        return true;
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
        plugin.hub().setWaiting(player);
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
        plugin.hub().clear(player);
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

        if (compassCounter++ % COMPASS_INTERVAL == 0) {
            updateCompasses();
        }

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
            // 清空报名与队伍：否则排队人数依然达标，下一 tick 就会立刻重新开局（§7）
            clearParticipants();
            phase = MatchPhase.WAITING;
            cleanupMatch();
        }
    }

    /**
     * 对局结束后清空报名队列与队伍归属。
     *
     * <p>玩家需要重新 {@code /htf join} 才会进入下一局；不清的话「人数达标」条件
     * 一直成立，倒计时会无缝重来一次。
     */
    private void clearParticipants() {
        for (UUID id : new ArrayList<>(queue)) {
            final Player player = Bukkit.getPlayer(id);
            if (player != null) {
                plugin.forget(player);
                plugin.hub().clear(player);
            }
        }
        queue.clear();
        lastZones.clear();
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
        if (System.currentTimeMillis() < retryBlockedUntil) {
            return false;
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

    /** 找一张世界已加载、且不与大厅世界相同的地图。 */
    private String pickPlayableMap() {
        final String lobbyName = plugin.config().lobbyWorld();
        for (String candidate : plugin.maps().keySet()) {
            final MapDefinition candidateMap = plugin.maps().get(candidate);
            if (Bukkit.getWorld(candidateMap.world()) == null) {
                continue;
            }
            if (!plugin.config().allowSameWorld()
                    && candidateMap.world().equals(lobbyName)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private String describeUnusableMaps() {
        if (plugin.maps().isEmpty()) {
            return "还没有任何地图，先在战场世界里执行 /htf setup 做一张。";
        }
        final StringBuilder sb = new StringBuilder("现有地图：");
        plugin.maps().forEach((name, m) -> {
            sb.append(name).append("→世界 ").append(m.world());
            if (Bukkit.getWorld(m.world()) == null) {
                sb.append("（未加载）");
            } else if (!plugin.config().allowSameWorld()
                    && m.world().equals(plugin.config().lobbyWorld())) {
                sb.append("（就是大厅世界）");
            }
            sb.append("；");
        });
        return sb.toString();
    }

    /** 开局失败：回到 WAITING，并冷却一段时间，避免每 tick 重试。 */
    private void failStart(String reason) {
        broadcast(reason + "（" + (RETRY_COOLDOWN_MS / 1000) + " 秒内不再重试）");
        map = null;
        chain = null;
        world = null;
        phase = MatchPhase.WAITING;
        retryBlockedUntil = System.currentTimeMillis() + RETRY_COOLDOWN_MS;
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
        // 选一张真正能开的地图（世界已加载、且不是大厅世界），而不是盲取第一张
        final String name = pickPlayableMap();
        if (name == null) {
            failStart("没有可用的地图。" + describeUnusableMaps());
            return;
        }
        map = plugin.maps().get(name);
        chain = chains.get(name);

        // 战场必须是独立世界：绝不退回主世界，否则会直接在别人的大厅里开打
        world = Bukkit.getWorld(map.world());
        if (world == null) {
            failStart("地图 " + name + " 的世界 " + map.world()
                    + " 未加载。请先创建该世界，或在游戏内执行 /htf setup 重新指定场地。");
            return;
        }
        if (plugin.config().configureWorld()) {
            // 世界已经由服务器/世界管理插件建好，这里只做局部配置
            ArenaWorldConfig.apply(world, map);
        }
        enableWorldPvp();

        // 只比较**配置里的世界名**。此前是拿解析出来的 World 对象比较，
        // 而大厅世界找不到时会退回「第一个加载的世界」，在多世界服上会误判成同一世界。
        final String lobbyWorldName = plugin.config().lobbyWorld();
        if (!plugin.config().allowSameWorld()
                && lobbyWorldName != null
                && !lobbyWorldName.isBlank()
                && world.getName().equals(lobbyWorldName)) {
            failStart("地图 " + name + " 声明的世界（" + world.getName()
                    + "）就是配置里的大厅世界。请把地图建在大厅之外的世界，"
                    + "或改 config.yml 的 lobby.world");
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
            giveCompass(player);
            plugin.hub().setPlaying(player);
        }
        compassCounter = 0;

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

    /** 给玩家一枚指向战线中心的罗盘（§7.1）。只在有空槽时才给，不覆盖手上的物品。 */
    /**
     * 开局临时开启战场世界的 PvP，结束时还原。
     *
     * <p>与 KnockbackBattle / HillKing 等现有小游戏保持一致：不依赖世界配置里
     * 是否开了 PvP，游戏自己保证对局期间能互相伤害。
     */
    private void enableWorldPvp() {
        if (world == null) {
            return;
        }
        pvpWorld = world;
        savedPvp = world.getPVP();
        hasSavedPvp = true;
        world.setPVP(true);
        plugin.getLogger().info("场地世界 PvP 临时开启：" + world.getName() + "（原值 " + savedPvp + "）");
    }

    /** 把 PvP 还原成开局前的值。 */
    private void restoreWorldPvp() {
        if (hasSavedPvp && pvpWorld != null) {
            pvpWorld.setPVP(savedPvp);
            plugin.getLogger().info("场地世界 PvP 已还原：" + pvpWorld.getName() + " -> " + savedPvp);
        }
        pvpWorld = null;
        hasSavedPvp = false;
    }

    /** 给玩家一枚指向战线中心的罗盘（§7.1）。 */
    private void giveCompass(Player player) {
        if (findCompass(player) >= 0) {
            return;
        }
        final int slot = firstEmptySlot(player);
        if (slot < 0) {
            return;
        }
        player.getInventory().setItem(slot, buildCompass());
    }

    /** 把罗盘指向当前的战线中心。 */
    private void updateCompasses() {
        if (chain == null || world == null || progress == null) {
            return;
        }
        for (Player player : activeParticipants()) {
            final int slot = findCompass(player);
            if (slot < 0) {
                giveCompass(player);
                continue;
            }
            player.getInventory().setItem(slot, buildCompass());
        }
    }

    private int firstEmptySlot(Player player) {
        final var inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack buildCompass() {
        final FrontlineSample sample = chain.sample(progress.u());
        final double x = sample.center().x();
        final double z = sample.center().z();

        final ItemStack compass = new ItemStack(Material.COMPASS);
        final CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setLodestoneTracked(false); // 不要求真的存在磁石方块
        meta.setLodestone(new Location(world, x,
                world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1, z));
        meta.displayName(net.kyori.adventure.text.Component.text("战线罗盘")
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(net.kyori.adventure.text.Component
                .text("指向战线中心").color(net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        return compass;
    }

    /** 找到玩家背包里那枚罗盘所在的槽位，没有返回 -1。 */
    private int findCompass(Player player) {
        final var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != Material.COMPASS || !stack.hasItemMeta()) {
                continue;
            }
            if (stack.getItemMeta().getPersistentDataContainer()
                    .has(compassKey, PersistentDataType.BYTE)) {
                return slot;
            }
        }
        return -1;
    }

    /** 收走所有人的罗盘。 */
    private void removeCompasses() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            final int slot = findCompass(player);
            if (slot >= 0) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private void cleanupMatch() {
        restoreWorldPvp();
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

    /**
     * 当前对局中某队的出生点；不在对局中或地图缺失时返回 {@code null}。
     *
     * <p>只用于 {@link org.bukkit.event.player.PlayerRespawnEvent} 的单次重生位置，
     * **不会**改动玩家床铺或世界出生点。
     */
    /** 给定世界是不是当前对局的战场世界。 */
    public boolean isArenaWorld(org.bukkit.World other) {
        return world != null && world.equals(other);
    }

    public Location teamSpawn(Team team) {
        if (map == null || world == null) {
            return null;
        }
        final Spawn spawn = team == Team.RED ? map.redSpawn() : map.blueSpawn();
        return new Location(world, spawn.x(), spawn.y(), spawn.z());
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
        tugBar = Bukkit.createBossBar("战线", BarColor.WHITE, BarStyle.SOLID);
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        sidebar = scoreboard.registerNewObjective("htf", Criteria.DUMMY, "战线模式");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Player player : activeParticipants()) {
            tugBar.addPlayer(player);
            player.setScoreboard(scoreboard);
        }
    }

    private void updateHud(Counts counts) {
        final double toBlue = chain.pathLength(0, chain.to());
        final double toRed = Math.abs(chain.pathLength(0, chain.from()));
        final double u = progress.u();

        // 带符号的推进程度：红方领先为正、蓝方领先为负，各自归一化到 ±1。
        // 注意不能对蓝方取绝对值 —— 那会让红方推进时蓝条也跟着涨。
        final double signed = progress.signedDistance();
        double net = signed >= 0
                ? (toBlue > 0 ? signed / toBlue : 0)
                : (toRed > 0 ? signed / toRed : 0);
        net = Math.max(-1, Math.min(1, net));

        if (tugBar != null) {
            tugBar.setProgress(0.5 + 0.5 * net);
            if (net > TUG_DEADBAND) {
                tugBar.setColor(BarColor.RED);
            } else if (net < -TUG_DEADBAND) {
                tugBar.setColor(BarColor.BLUE);
            } else {
                tugBar.setColor(BarColor.WHITE);
            }
            tugBar.setTitle(String.format("红 %d%%  ←→  蓝 %d%%",
                    Math.round(clamp(Math.max(0, net)) * 100),
                    Math.round(clamp(Math.max(0, -net)) * 100)));
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
        removeCompasses();
        if (tugBar != null) {
            tugBar.removeAll();
            tugBar = null;
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
