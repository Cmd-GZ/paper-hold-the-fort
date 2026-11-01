# libs/ —— 随仓库携带的编译期依赖

`minigame-lib-1.7.3.jar` 是主仓库 `服务器小游戏插件/MiniGameLib` 的编译产物。

## 为什么放在这里

HoldTheFort 通过 `compileOnly` 依赖 MiniGameLib 的 `com.hub.api.HubAPI` /
`com.hub.world.Worlds`（见 D16：与其它 23 个游戏保持一致，`HubBridge` 已退役）。

最初的做法是让它指向主仓库的 `<主仓库>/.server/m2repo` 当本地仓库，但那个目录是
**主仓库 gitignore 的**，于是：

- 换一台机器 / 换一个 clone → 解析不到，构建失败
- IDE（VSCode Java 扩展）的 Gradle 同步若用了不同工作目录 → 同样解析不到
  （表现为 `HubAPI`、`Worlds` 全部标红，首当其冲是 `HoldTheFortPlugin` 与 `MatchManager`）

所以把 jar 收进本仓库，**任何 clone 都能离线构建**。

## 升级方法

```bash
# 在主仓库里构建并 install 出最新版 MiniGameLib
cd <主仓库> && tools/build.sh
cp .server/m2repo/com/hub/minigame-lib/<新版本>/minigame-lib-<新版本>.jar \
   paper-hold-the-fort/libs/
# 然后改 build.gradle.kts 里的文件名与版本号，删掉旧 jar
```

⚠️ 运行期**不要**把这个 jar 打进产物：它在 `compileOnly` 里，服务器上的
`plugins/MiniGameLib.jar` 负责提供这些类。
