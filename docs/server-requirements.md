# 部署要求（games-server-plugins）

> 本文件记录把本插件部署到 **`games-server-plugins`** 这套 Paper 服时的环境与硬性约束。
> 玩法规则仍以 [`design.md`](design.md) 为唯一事实来源。
>
> 与通用化设计冲突时**优先满足部署需求**；是否把这个特例抽象回通用代码，由插件侧开发者判断。

## 1. 运行环境

| 项 | 值 |
|---|---|
| 服务端 | Paper **26.2**，本服实际运行 `paper-api 26.2.build.126-stable`（编译已对齐该版本） |
| Java | **JDK 25** |
| 运行期依赖 | **零**（只用 Paper API；不依赖 MiniGameLib / HubCore / GameWorlds） |
| 语言 | 玩家可见提示用中文 |

## 2. 世界

- 本服由 **GameWorlds** 统一创建/加载世界，世界名形如 `world_xxx`，目录在
  `server/world/dimensions/minecraft/<世界名>`。
- 本插件**不得创建、卸载、回退世界**；世界不存在时应拒绝开局并提示管理员。
- 战场世界固定为 **`world_htf`**（已在 `GameWorlds/config.yml` 登记）：
  - `type: FLAT`、`environment: NORMAL`
  - `pvp: true`、`mob-spawning: false`、`weather-cycle: false`、`fixed-time: 6000`
  - 战场方块由 `/htf build <地图>` 往这个空世界里铺，**不要**在其它小游戏世界（`world_gtb` / `world_duel` / `world_hp` / `world_tr` / `world_mm` / `world_fb` / `world_lp`）里铺。
- 地图在 `world_htf` 里用 `/htf setup` 现场建（落盘为 `plugins/HoldTheFort/maps/world_htf.yml`）；
  若要手写地图文件，`world` 字段必须写 `world_htf`。

## 3. 大厅与传送

- 大厅世界 `world`，坐标 **`1.66 / 57.0 / -3.08`**（与 HubCore 的 `lobby` 一致）。
- 对局结束/退出后把玩家送回该点；不要用“主世界出生点”之类的近似位置。
- 战场世界与大厅世界必须不同（`match.allow_same_world: false`）。

## 4. 命令

- 所有功能统一挂在 **`/htf`** 下（`/htf join red|blue|leave|line|build|start|stop`，建场用 `/htf setup`）。
- **不要注册 `/join`**：本服主城与其它小游戏已经共用命令空间，`/join` 会冲突。
- 控制台至少要能执行 `/htf build <地图名>`（用于开服初始化战场）。

## 5. 其它

- 本服已装 HubCore / MiniGameLib / GameWorlds。插件带 `minigame-game: true` 与 `softdepend`，
  HubCore 会自动登记到 tab/播报；**不要引入运行期依赖**，主城菜单入口由主城配置决定。
- 提交前必须 `./gradlew build` 通过（含单元测试）；本服的接入改动会先在
  `work/` 分支上提交并推送，再由插件侧开发者决定合并方式。
