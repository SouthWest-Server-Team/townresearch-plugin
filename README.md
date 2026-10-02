# townresearch-plugin — 城邦科技研究

Towny + Slimefun 集成插件，将 Slimefun 科技研究从个人级改为城邦级。

## 核心功能

- **研究所建筑**：城邦可标记多个地块为研究所，数量上限可配置
- **城邦研究**：市长或研究员从城邦银行支付费用并启动研究
- **成员共享**：科技解锁后，城邦成员可使用对应科技
- **多研究所加速**：多个研究所参与同一研究时缩短研究时间
- **离城处理**：玩家离开城邦后不再享有该城邦的研究权限
- **Slimefun 指南集成**：普通成员只读，研究员可执行研究操作
- **研究所 BossBar**：仅在**站在本城邦研究所地块上**且该城邦研究进行中时显示，离开地块立即隐藏；判定按地块（TownBlock 世界+区块坐标），**不依赖玩家移动事件**（`a9d72eb`）
- **BossBar 直写（案 1，2026-09-27 用户拍板）**：插件自己写 Bukkit BossBar，**不再借 DisplayBus**——display 的公开面已在 2026-09-23 随旧总线删掉 BossBar 能力（只剩 ActionBar 三个方法），旧「拿得到服务就只走总线」的分支会让退服/disable 抛 `NoSuchMethodError`（真服日志已见）；display 现行实现也不接管 BOSS_BAR 包，直写不会被拦

## 命令

| 命令 | 说明 |
|---|---|
| `/townresearch set` | 将当前地块标记为研究所 |
| `/townresearch unset` | 取消当前地块的研究所标记 |
| `/townresearch list` | 查看本城邦研究所和研究列表 |
| `/townresearch start <科技>` | 开始研究指定 Slimefun 科技 |
| `/townresearch pause` | 暂停当前研究 |
| `/townresearch resume` | 恢复当前研究 |
| `/townresearch researcher <玩家>` | 任命或移除研究员 |
| `/townresearch buyspeed` | 使用金币购买研究加速 |

## 配置

```yaml
max-labs: 5   # 每个城邦最多拥有的研究所数量

# 研究 BossBar 调试日志（nearLab / show / hide）
bossbar-debug: true
```

> `bossbar-debug` 由 `a9d72eb` 引入（`src/main/resources/config.yml:4-5`）。代码里的默认值是 `false`
> （`ResearchBossBarManager.java:56-57` 的 `getConfig().getBoolean("bossbar-debug", false)`），但**随包发布的 config.yml 写的是 `true`**——
> 即新装服默认会输出 `[BossBarDebug] nearLab scan / show / hide` 日志，量较大，正式服建议改回 `false`。

## 实现原理

插件以 Towny 城邦作为研究数据和权限边界，以 Slimefun API 获取科技定义和解锁状态。研究任务由调度器推进并持久化，研究所数量参与研究时间计算；研究完成后通过 Slimefun 集成层同步城邦成员的可用科技。BossBar 用于显示研究进度，研究员和城邦银行权限由 Towny/Vault 数据决定。

### BossBar 显示规则（2026-09-27 复核：按 `a9d72eb` 现状 + 案 1 直写改动）

| 项 | 实际行为 | 依据 |
|---|---|---|
| 显示条件 | 玩家所在 **TownBlock 是该城邦的研究所地块** 且该城邦有进行中的研究；`world + chunkX + chunkZ` 三项全等才命中 | `ResearchBossBarManager.java:188-214`（`findNearLabPlayers` / `isNearAnyLab`）、`display/ResearchBossBarVisibility.java:23-38`（`matchesAnyLab`） |
| 谁可见 | **不要求城邦居民身份**——访客/无城邦玩家站在研究所地块上同样看到 | `ResearchBossBarVisibility.java:45-46`、同文件测试 `ResearchBossBarVisibilityTest`（`includeOnlineViewer_requiresOnlineAndNearLab_notResidency`） |
| 刷新方式 | 每 **20 tick（1 秒）** 周期性扫描全场在线玩家的所在地块；**已删除 `PlayerMoveEvent`**，不再需要「动一下才刷新」 | `TownResearchPlugin.java:48-49`（`runTaskTimer(..., 20L, 20L)`）、`ResearchBossBarManager.java:86-91`（`refreshBars`）→ `:109-141`（`updateAll`） |
| 离开地块 | **立即隐藏**（无 grace 窗口/防抖延迟） | `ResearchBossBarManager.java:132-137` |
| 退出游戏 | `PlayerQuitEvent` 隐藏 | `ResearchBossBarManager.java:216-219` |
| 下发通道 | **一律直写** Bukkit BossBar（`LegacyBossBarSink`）；不再查询/调用 DisplayBus（其公开面已无 BossBar 能力，调用点会抛 `NoSuchMethodError`）。跨仓零引用由守护测试钉住 | `display/ResearchDisplayBusBridge.java`、`ResearchBossBarDirectWriteContractTest.mainSourcesNoLongerReferenceTheDeletedDisplayBusBossBarApi` |
| 下发 TTL | `ResearchBossBarPresentation.TTL_TICKS = 160`（≥ 3 个 40-tick 刷新间隔） | `display/ResearchBossBarPresentation.java:11` |

> 上一版 README 只写了「BossBar 用于显示研究进度」，未说明可见范围与刷新机制；这两笔落后提交（`93ef0a0`、`a9d72eb`）正是在改这里：先接入 DisplayBus + 进出防抖，随后又**去掉移动依赖**改为纯地块扫描。
> 注意：`display/LabPresenceDebouncer` 类仍在库里（带 5 个单测），但 `a9d72eb` 之后**主代码已无任何引用**（`grep -rn "LabPresenceDebouncer" src/main` 命中 0），属遗留类。

## 模块结构

```text
TownResearchPlugin
  ├── TownResearchCommand       — 命令与权限
  ├── ResearchLifecycleManager  — 研究启动、暂停、恢复和完成
  ├── ResearchBossBarManager    — 研究进度显示（每 20 tick 地块扫描）
  │     └── display/            — ResearchBossBarVisibility（地块判定）、ResearchDisplayBusBridge（直写适配，案 1 后不再查总线）、ResearchBossBarPresentation（文案与 TTL）、LabPresenceDebouncer（遗留未引用）
  ├── ResearchSettings           — 配置加载
  ├── SlimefunBridge             — Slimefun 科技查询与解锁
  └── 数据管理                    — 城邦研究所、研究状态持久化
```

## 依赖与构建

- Java 21
- Leaf/Paper API 1.21.11
- Towny
- Slimefun
- Maven
- `plugin.yml` 仍写着 `softdepend: [XiNanTownDisplay]`（`src/main/resources/plugin.yml:5-6`），但**代码里已无任何 display 引用**（案 1 后）；`pom.xml:52-57` 那份 `xinantown-display`（`provided`）因此成了惰性依赖 —— 是否连 `softdepend` 一起清掉属独立决定，本轮未动

构建出的 JAR 直接放入服务端 `plugins/` 目录即可。

```bash
mvn clean package
```

> `leaf-api` 1.21.11 由本机工作区 `XiNanTown/libraries/` 提供，`pom.xml` 用相对路径 `${project.basedir}/../XiNanTown/libraries/` 引用，克隆到任意路径都无需改动。

## 测试与构建（真实运行）

2026-09-27，JDK 26（`--release 21`），离线 Maven 3.9.6：

```text
mvn -o -B clean test
  Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
```

分用例：`ResearchBossBarDirectWriteContractTest` 6（案 1 守护）、`ResearchDisplayBusBridgeTest` 9（直写契约）、`LabPresenceDebouncerTest` 5、`ResearchBossBarPresentationTest` 6、`ResearchBossBarVisibilityTest` 5、`PluginYmlDisplayBusTest` 1、`TownDataManagerTest` 3、`TownResearchTest` 12。

> 案 1 之前这份测试**连编译都过不去**：`mvn clean test` 在 `ResearchDisplayBusBridge.java:[4]`（`com.xinantown.display.core.BossBarRequest` 不存在）、`:[71]`、`:[88]`（`clearSource` 不存在）上直接报「找不到符号」。增量编译残留的旧 class 曾让 `mvn test` 变成 4 个运行期错误（`NoClassDefFoundError: com/xinantown/display/core/BossBarRequest` ×3、`NoSuchMethodError: ...DisplayBusService.clearSource` ×1）—— 这正是真服日志里那条退服报错的同一形状。

## 分支与推送状态

（2026-09-25 以本地 remote-tracking ref 核实，未执行任何 git 写操作）

| 分支 | 提交 | 与工作分支 `dev_GINGSHAN` 的关系 |
|---|---|---|
| `dev_GINGSHAN`（当前 HEAD） | `a9d72eb` | 基准；`origin/dev_GINGSHAN` 指向同一提交（reflog 显示 update by push）→ **已推送** |
| `debug` | `a9d72eb` | 与 HEAD 同一提交，`origin/debug` 亦相同 |
| `main` | `82989f5` | **未同步**：`origin/main` 不含 dev 分支最新的 **8** 笔提交（另有 2 笔只在 main 上） |

即：**开发分支（dev_GINGSHAN / debug）已同步到远端；`main` 仍未合并最新的 8 笔改动**。如需对外发布，需另做 main 的合并/PR。

## 文档同步记录

- 本 README 已核对到 HEAD `a9d72eb`（2026-09-19）。
- 此前落后 2 笔提交，均为 BossBar 相关，已收录进「BossBar 显示规则」与功能列表：
  - `93ef0a0` *研究 BossBar 接入 DisplayBus 并增加进出防抖* — 新增 `display/` 子模块（`ResearchDisplayBusBridge`、`ResearchBossBarPresentation`、`LabPresenceDebouncer`），`plugin.yml` 增 `softdepend: XiNanTownDisplay`，缺失时降级直写。
  - `a9d72eb` *研究 BossBar 判定改为仅按研究所地块并去掉移动依赖* — 删掉 `PlayerMoveEvent` 与防抖，改每 20 tick 地块扫描、离开即隐藏；有总线时不再双写；TTL 由 100 改 160；新增 `bossbar-debug` 配置与 `ResearchBossBarVisibility` + 5 个单测。
- 上表的测试数与分支状态是本次实测结果，**不是**历史承诺值。
- 2026-09-27 案 1（用户拍板）：**收回 DisplayBus 接入** —— `ResearchDisplayBusBridge` 删掉 `core.BossBarRequest` import 与 `requestBossBar` / `clearSource` 两个调用点，不再查询服务，`usesBus()` 移除；`show`/`hide`/`cleanup` 一律落到直写 `LegacyBossBarSink`。新增 `ResearchBossBarDirectWriteContractTest` 6 条结构性守护（display 零引用、红线①不进 ActionBar、红线②不做居民判定、非居民站在 lab 地块仍可见、退服仍走 `hide`）。红线不变：研究进度只走 BossBar。
