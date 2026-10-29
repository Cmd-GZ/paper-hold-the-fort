# Hold the Fort · 战线模式

Minecraft Java 版 **Paper** 服务端插件。两队（红 / 蓝）在一片战场上围绕一条横贯全图的**战线**
对抗：玩家越过战线进入敌方区域，战线就会按双方「渗透人数」的差值朝对方基地推进，
推到任一端点即分出胜负。

**玩家侧零安装** —— 全部逻辑跑在服务端，原版客户端直接进服就能玩。

> **第一次用？看 [docs/快速上手.md](docs/快速上手.md)** —— 只用游戏内命令，5 分钟做出一张地图。
>
> 部署到 `games-server-plugins` 服时的硬性约束（战场世界、大厅坐标、命令空间）见
> [docs/server-requirements.md](docs/server-requirements.md)。

---

## 状态

| 阶段 | 状态 |
|---|---|
| 玩法设计 | ✅ 机制已完整形式化，见 [docs/design.md](docs/design.md) |
| 工程骨架 | ✅ 可编译、可打包、可起服 |
| 玩法实现 | ⏳ 未开始 |

---

## 环境要求

- **JDK 25** —— Paper 26.1+ 的硬性要求（若改用 1.20 ~ 1.21.11，则 JDK 21 即可）
- 构建工具无需手动安装：仓库自带 Gradle Wrapper

> 首次构建需要联网下载 Gradle 发行版与 Paper API 依赖。

---

## 常用命令

```bash
./gradlew build      # 编译 + 测试 + 打包 → build/libs/
./gradlew test       # 只跑单元测试
./gradlew runServer  # 下载并启动 Paper 26.2，自动装载刚构建的插件
```

`runServer` 首次运行会在 `run/` 下生成服务端目录（已在 gitignore 中）。
启动后用原版客户端连接 `localhost:25565` 即可进服。

开发期改完代码不必手动搬 jar —— `runServer` 会自动把构建产物装进 `plugins/`。
**注意不要用 `/reload`：它不会重载类定义，改代码请重启服务端。**

---

## 目录结构

```
.
├── docs/
│   └── design.md              # 玩法设计文档（唯一事实来源）
├── src/main/
│   ├── java/io/github/cmdgz/holdthefort/
│   │   └── HoldTheFortPlugin.java
│   └── resources/
│       └── plugin.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── AGENTS.md                  # 给 AI / 协作者的约定
```

规划中的代码分层（见 design.md §12）：

| 模块 | 职责 |
|---|---|
| `map/` | 地图数据加载、`MapValidator` 分级校验 |
| `frontline/` | 战线几何、关键帧归一化、区域判定、粒子渲染 |
| `game/` | 对局状态机、进度推进、胜负判定 |
| `player/` | 玩家侧状态（连杀 / 敌区驻留） |

---

## 玩法速览

- **战线是一条横贯战场的直线**，由地图上的「检查点对」定义 —— 设计者只需填坐标。
- 战线前面是**蓝方区域**、后面是**红方区域**。
- 红方在蓝方区域的人数比蓝方在红方区域的人数多，战线就朝蓝方基地推进，反之亦然。
- 进度条 $u$ 推到底即分胜负；$u$ 的推进速度随时间递增（8 分钟起加速，15 分钟兜底）。

完整定义（含地图校验规则、速度归一化、边界情况）见 [docs/design.md](docs/design.md)。

---

## 许可

尚未确定。

## 快速试玩

> 部署到 `games-server-plugins` 服时的硬性约束（战场世界 `world_htf`、大厅坐标、命令空间）
> 见 [docs/server-requirements.md](docs/server-requirements.md)。

插件**不自带地图**，第一次启动只会生成 `config.yml`。在战场世界里照下面走一遍就能开玩：

1. 起服（`./gradlew runServer`），OP 进入战场世界
   （`games-server-plugins` 服用 `/gw tp world_htf`；其它服由自己的世界管理插件进入）
2. `/htf setup` 拿「战线标尺」：左键点一下、右键点一下 = 一帧，至少 3 帧；
   `/htf setup spawn red` / `/htf setup spawn blue` 记两边出生点；
   `/htf setup done` 落盘并热加载
3. 场地还没铺的话，`/htf build <地图名>` 会在**你当前所在的世界**里铺一层平整地面
   （控制台执行时铺在地图声明的世界；自己搭场地则不需要）
4. 玩家 `/htf join red`、`/htf join blue`；两队都有人报名后自动倒计时开局，
   单人测试可 `/htf start`

对局中站到战线对面就会推线：红方越线让战线朝蓝方基地走，反之亦然。
