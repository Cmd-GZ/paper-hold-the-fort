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
- 文档中标注为「决策记录（§9.1）」的条目是已拍板的结论，**不要**在没有明确要求时推翻它们。

动过的设计点，在文档对应位置更新，并在 §9.1 追加一条决策记录（`D` 编号递增）。

## 3. 构建与验证

```bash
./gradlew build      # 编译 + 测试 + 打包
./gradlew test       # 单测
./gradlew runServer  # 起一个本地 Paper 26.3 验证
```

- **JDK 25**（Paper 26.1+ 要求）。本机若默认 JDK 较低，需 `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`。
- 提交前**必须**保证 `./gradlew build` 通过。
- 不要用 `/reload` 验证插件 —— 它不重载类，改代码请重启服务端。

## 4. 代码约定

- **Java 25**，包根 `io.github.cmdgz.holdthefort`。
- **数学层与 Bukkit 解耦**。`frontline/FrontlineGeometry`、`ZoneCounter` 等纯计算模块
  **不得** `import org.bukkit.*`，必须能用纯 JUnit 测试覆盖。
- 坐标系：Minecraft 右手系，$x$ 东、$z$ 南、$y$ 上；战线相关计算**全部忽略 $y$**。
- 新增运行期依赖前先想清楚 —— 目前 v1 计划**零运行期依赖**（只用 Paper API）。
- 注释用中文，公开 API 写 Javadoc。

## 5. 设计不变量（实现时最容易写错的地方）

摘自 `docs/design.md`，改代码时请逐条对照：

1. **检查点在界外**：关键帧的两个点必须在地图边界之外、分处两侧（H1）。这是地图能加载的前提。
2. **$p、q$ 顺序没有语义**：交换任意一帧的两点，计算结果必须完全相同。
3. **候选 A 不是 `rot90(q−p)`**：它等于「$\mathbf m_n-\mathbf m_{n-1}$ 在 $\mathbf d_n$ 法向上的分量」，
   其中 $\mathbf m_0$ 是红方出生点。
4. **归一化是两步**：先算候选 A，再按「与上一帧夹角 $\le 90^\circ$」决定是否改用候选 B。
   永远不要因为夹角超标而拒绝地图。
5. **$\varepsilon$ 死区**：$\lvert s\rvert\le\varepsilon$ 的玩家不计数，否则战线会在原地抖。
6. **前进方向 ≠ 移动方向**：反复横跳的地图上，$u$ 增大时战线可以后退。
   进度 $u$ 与地理位移**没有**单调关系。
7. **速度归一化**：$\mathrm du/\mathrm dt=(V(t)/w_n)\operatorname{sgn}(r-b)$，
   $w_n=\lVert\Delta\mathbf m_n\rVert+R\lvert\Delta\theta_n\rvert$ —— **必须带旋转项**，
   否则纯旋转段 $w_n=0$ 会除零。
8. **战线颜色表示趋势**：$u$ 增 → 红，$u$ 减 → 蓝，停滞 → 中性。不是「归属」。

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
