# AGENTS.md

给 AI 助手与协作者的工程约定。**动手之前先读完这份。**

---

## 1. 这个项目是什么

Minecraft Java 版 **Paper** 服务端插件，实现一个叫「战线模式」的两队对抗小游戏。
玩法、数值、边界情况全部定义在 **[`docs/design.md`](docs/design.md)**。

## 2. 唯一事实来源

> **`docs/design.md` 是唯一事实来源。代码是实现，文档是规格。**

- 改任何**玩法行为**之前，先改文档，再改代码。两者不一致时，以文档为准。
- 文档里的公式与规则不是「参考」，是**必须逐条落实**的规格，包括边界情况和报错行为。
- 文档 §9.1 是**已确定的取舍汇总**，**不要**在没有明确要求时推翻它们。
- 文档只描述**当前状态**：不要写入「原先/已取消/修复了」这类修订叙述，也不要引用已删除的概念。

动过的设计点，直接改正文对应章节，并同步 §9.1 的对应行。

## 3. 构建与验证

```bash
./gradlew build      # 编译 + 测试 + 打包
./gradlew test       # 单测
./gradlew runServer  # 起一个本地 Paper 26.2 验证
```

- **JDK 25**（Paper 26.1+ 要求）。本机若默认 JDK 较低，需 `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`。
- 提交前**必须**保证 `./gradlew build` 通过。
- 不要用 `/reload` 验证插件 —— 它不重载类，改代码请重启服务端。

## 4. 代码约定

- **Java 25**，包根 `io.github.cmdgz.holdthefort`。
- **数学层与 Bukkit 解耦**。`frontline/FrontlineGeometry`、`ZoneCounter` 等纯计算模块
  **不得** `import org.bukkit.*`，必须能用纯 JUnit 测试覆盖。
- 坐标系：Minecraft 右手系，$x$ 东、$z$ 南、$y$ 上；战线相关计算**全部忽略 $y$**。
- 依赖：编译期 `compileOnly`（= Maven 的 `provided`）只有 **paper-api + snakeyaml + MiniGameLib**；
  运行期**硬依赖** MiniGameLib + HubCore（`plugin.yml` 的 `depend`）。
  公共库的入口就两个类：`com.hub.api.HubAPI`（主城对接）与 `com.hub.world.Worlds`（世界 token）。
  新增依赖前先想清楚，**不要把 MiniGameLib 打进本插件 jar**（它是全服共用的一个 jar）。
- 注释用中文，公开 API 写 Javadoc。

## 5. 设计不变量（实现时最容易写错的地方）

摘自 `docs/design.md`，改代码时请逐条对照：

1. **战线是线段，不是直线**。两端之外是「判定范围外」：不算入侵、也不算守卫（§5.1）。
2. **$p、q$ 顺序没有语义**：交换任意一帧的两点，计算结果必须完全相同。
3. **归一化只有两步**：锚定第 `0` 帧（取使红方在后的候选）→ 向两侧传播
   （左邻/右邻各取夹角 $\le 90^\circ$ 的候选）。**不做校验、不做全局翻转**（§6.2）。
4. **只有 L 级会拒绝加载**；W 级一律只提示，包括「开局两队同侧」这种不平衡地图（§2.4）。
5. **编号以起点为 0**：帧编号 $L..U$（$L<0<U$）、进度 $u\in[L,U]$、段号 $k\in[L,U-1]$（§2.3）。
6. **$\varepsilon$ 死区**：$\lvert s\rvert\le\varepsilon$ 不计数，否则战线会在原地抖（§5.1）。
7. **前进方向 ≠ 移动方向**：反复横跳的地图上，$u$ 增大时战线可以后退。
8. **路程权重必须带旋转项**：$w_k=\lVert\Delta\mathbf m\rVert+\bar h_k\lvert\Delta\theta\rvert$，
   转角取**最短弧**、用**弧度**。纯旋转段若不记旋转项就会权重为 0 → 除零（§5.6）。
9. **战线颜色表示趋势**：$u$ 增 → 红，$u$ 减 → 蓝，停滞 → 中性。不是「归属」（§4.2）。

## 6. 提交规范

- 分支：`master`（默认分支，不要改名）。
- 提交信息用中文，格式 `<类型>: <一句话>`，类型取 `feat` / `fix` / `docs` / `refactor` / `test` / `chore`。
  例：`docs: 明确候选 A 由推进方向的法向投影定义`
- 一次提交只做一件事；不要提交 `build/`、`run/`、`.gradle/` 等产物（已在 `.gitignore`）。

## 7. 不要做的事

- ❌ 不要在未更新 `docs/design.md` 的情况下改动玩法行为
- ❌ 不要为了「让它能跑」而吞掉校验错误 —— 文档规定了哪些该报错
- ❌ 不要在数学层里引用 Bukkit 类型
- ❌ 不要手写 `gradle-wrapper.jar` 或改动 `gradle/wrapper/gradle-wrapper.properties` 的版本而不说明原因
- ❌ 不要擅自升级 Paper / Minecraft 版本（会影响 Java 版本要求与 API 可用性）

## 构建（重构期，Linux）

本仓库用 **Gradle**（不是 Maven）。wrapper 需要联网下载发行版，本机曾因此失败，
改用预装方式可离线：

```bash
# 一次性：把 gradle 发行版放到主仓库的 .server/tools/
cd ../.server/tools && curl -sSL -o gradle.zip \
  https://services.gradle.org/distributions/gradle-9.8.2-bin.zip && unzip -q gradle.zip

# 每次构建（必须指定 GRADLE_USER_HOME 到可写目录，否则 native 库初始化失败）
cd ../../paper-hold-the-fort
GRADLE_USER_HOME=$PWD/../.server/gradle-home JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ../.server/tools/gradle-9.8.2/bin/gradle --no-daemon -Dorg.gradle.native=false build -x test
# 产物：build/libs/paper-hold-the-fort-<版本>.jar（当前 0.2.0）
```

版本号只有一个来源：`build.gradle.kts` 的 `version`；`plugin.yml` 写的是 `${version}`，
由 `processResources` 展开 —— 两边不会不一致，bump 只改 `build.gradle.kts` 一处。

**MiniGameLib 依赖从本地 Maven 仓库取，不联网**：

- `build.gradle.kts` 里注册了 `maven { url = uri(file("../.server/m2repo")) }`，里面是
  `com.hub:minigame-lib:1.7.3`（**先跑过一次主仓库的 `tools/build.sh` 才会有**，
  那一步会 `MiniGameLib install` 到这个目录）。没有它就会卡在解析
  `com.hub:minigame-lib:1.7.3` —— 这不是网络问题，先补主仓库构建。
- 升级公共库（例如 1.7.4）：改 `build.gradle.kts` 里的版本号 → 重跑主仓库
  `tools/build.sh`（把新版 install 进 `.server/m2repo`）→ 再构建本仓库。
- `compileOnly` 不会把 `com.hub.*` 打进本插件 jar（验证：`unzip -l` 里 `com/hub` 计数应为 0）。

## 重构状态

- 分支 **`restruction`**（与主仓库同名，平行推进）。
- **依赖方向（主仓库决策 D16）：与其它 23 个小游戏一致。**
  `build.gradle.kts` 里 `compileOnly("com.hub:minigame-lib:1.7.3")`，
  `plugin.yml` 里 `depend: [MiniGameLib, HubCore]` + `softdepend: [GameWorlds]`。
  **本插件不再能单独跑在只有 Paper 的服务器上** —— 一致性优先，这是刻意取舍。
- **`HubBridge`（反射式兼容层）已退役删除**，换成 MiniGameLib 的直接调用：

  | 原 `HubBridge` | 现在 | 调用点 |
  | --- | --- | --- |
  | `setWaiting(player)` | `HubAPI.setGame(uuid, plugin.getName())` | `MatchManager#join` |
  | `setPlaying(player)` | `HubAPI.setPlaying(uuid, plugin.getName())` | `MatchManager#startMatch` |
  | `clear(player)` | `HubAPI.clearGame(uuid)` | `MatchManager#leave` / `#clearParticipants` |

  - 它唯一的"兜底"是 **「主城不在就静默跳过」**；这条行为在 `HubAPI` 内部**原样存在**
    （`service == null` 时全部空操作），所以退役不改任何行为。
  - `onEnable` 用 `HubAPI.isReady()` 打一行 info / warning，让"主城没就绪"可见（R3）。
  - 游戏标识**仍然传 `plugin.getName()`**（= `HoldTheFort`）：`HubCore/GameRegistry`
    按插件名登记，改成别的名字 tab 状态就查不到。
- **R2 坐标解耦：读 + 写两侧都已接 `com.hub.world.Worlds`。**
  - `HoldTheFortPlugin#resolveWorld(token)` 是唯一解析入口（内部是 `Worlds.resolve`）：
    固定世界名（旧配置，行为不变）与 `tpl:<模板键>` 都认。
    原来的 `Bukkit.getWorld(map.world())` / `Bukkit.getWorld(config.lobbyWorld())` 全都换成了它。
  - **写入侧（`/htf setup done`）写的是 token，不是世界名。**
    `SetupManager#begin` 用 `Worlds.tokenOf(player.getWorld())` 建 `SetupSession`，
    落盘时把这个 token 原样写进地图文件的 `world:`（`MapWriter`）。
    - 固定世界 → token 就是世界名，**存量地图文件一个字节不变**（不加引号也是刻意的，零 diff）；
    - 房间实例世界 → token 是 `tpl:<模板键>`，**绝不写会消失的 `room_xxx`**；
    - `tpl:` 带冒号，`MapWriter#worldToken` 写成 `world: 'tpl:<模板键>'`（与 `docs/design.md` §8.2 一致）。
    - 地图**名字**也取自 token（安全化：`tpl:htf_test` → `maps/tpl_htf_test.yml`）：
      固定世界仍是 `<世界名>.yml`（不变），同一模板重复建场会覆盖同一份文件。
  - **`SetupSession` 的字段叫 `worldToken()`（原 `worldName()`），一个值两用且必须同源**：
    ① 落盘写进 `world:`；② `/htf setup` 自己的运行期比对。两者分家就会静默改掉比较结果
    —— 上一轮"写入侧只能写世界名"的限制正是这么来的。
  - **运行期世界比对一律「两边都转 token」**：`Worlds.tokenOf(实际世界).equals(声明值)`。
    **不能**写 `world.getName().equals(声明值)`：声明值是 `tpl:x`、实际世界名是 `room_xxx`，
    永远不相等。共 5 处（前 3 处就是上一轮说的"3 处运行期比对"，都在 `/htf setup` 流程里）：

    | 位置 | 语义 | 怎么比 |
    | --- | --- | --- |
    | `SetupManager#recordTargetPoint` | 玩家正看着的方块在不在建场世界 | `tokenOf(target.getWorld()).equals(worldToken())` |
    | `SetupManager#onInteract` | 木棍点的方块在不在建场世界 | `tokenOf(clicked.getWorld()).equals(worldToken())` |
    | `SetupManager#done` | 建场世界是不是大厅世界 | `tokenOf(lobby.getWorld()).equals(worldToken())` |
    | `MatchManager#startMatch` | 这张图是不是开在大厅世界（开局硬禁区） | `tokenOf(world).equals(config.lobbyWorld())` |
    | `HoldTheFortPlugin#isLobbyWorld` | 选图过滤 / `status` / 加载告警用同一判据 | 见下 |

    两处都比"声明值"，**不比解析出来的 World 对象**：`lobbyLocation()` 在大厅世界找不到时会
    退回「第一个加载的世界」，在多世界服上会误判成同一世界（上一轮已修的坑，别再回去）。
  - **`isLobbyWorld(map)` 的语义**（改它之前先把这里读完）：
    1. `config.lobby.world` 为 `null`/空白 → `false`（大厅没配就谈不上"开在大厅里"）；
    2. `map.world()` 与 `lobby.world` **字符串相等** → `true`。固定世界名走这条，
       所以「**世界没加载时也能靠字符串认出大厅世界**」的旧行为原样保留
       —— token 相等不需要世界在线，这也是当初 `startMatch` 特意保留的行为；
    3. 否则解析 `map.world()`：解析不出来 → `false`（这张图本来就因为"世界未加载"被排除，
       不会因此被放行开局）；解析出来 → `Worlds.tokenOf(实际世界).equals(lobby.world)`。
    固定世界的判定结果与改造前**逐字一致**（`tokenOf(世界)` 对普通世界就是 `getName()`）。
  - `tpl:` 解析不出来（模板没有存活房间）一律按"场地未就绪"：选图时跳过 +
    `status` 显示"（世界未加载）"，**绝不退回大厅世界**（与 §9.1「不回退世界」一致）。
  - **还剩的限制**（都不影响固定世界，也不影响"在房间世界里建场"这件事本身）：
    - 房间的生命周期归主城管（`hub room`）：本插件不建房间、不预热、不回收；
      房间里建好的图在"该模板没有存活房间"时就是 `status` 里的"（世界未加载）"。
    - 建场会话在内存里：房间被删后老会话比不上了，点方块会提示"请一直在同一个世界里建场"，
      重新 `/htf setup` 即可（不会写坏任何文件）。
    - 地图名/文件名取自 token，所以同一模板的第二次建场会**覆盖**上一张图；
      要在同一模板里放两张不同几何的图，得手动改 `name:`/文件名（`world:` 保持同一个 `tpl:`）。
- **两个 `EventPriority.HIGHEST` 都不是空操作**，别当成历史 workaround 删掉：
  - `MatchListener#onPvpAllow`：放行不同队伍的互伤（SkyPit 已修成只在自己世界取消，
    这个放行现在理论上多余，但删掉需要重新验证门牙/高地两队的实际伤害链路）
  - `MatchListener#onRespawn`：设本队重生点（防止被别的插件覆盖）
