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
- **BossBar 接入 DisplayBus**：装了 XiNanTownDisplay 时只走总线（不再双写 Bukkit BossBar，避免重复下发/抖动），未装时降级为直写 Bukkit BossBar（`93ef0a0`）

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

### BossBar 显示规则（2026-09-25 核实，按 `a9d72eb` 现状）

| 项 | 实际行为 | 依据 |
|---|---|---|
| 显示条件 | 玩家所在 **TownBlock 是该城邦的研究所地块** 且该城邦有进行中的研究；`world + chunkX + chunkZ` 三项全等才命中 | `ResearchBossBarManager.java:188-214`（`findNearLabPlayers` / `isNearAnyLab`）、`display/ResearchBossBarVisibility.java:23-38`（`matchesAnyLab`） |
| 谁可见 | **不要求城邦居民身份**——访客/无城邦玩家站在研究所地块上同样看到 | `ResearchBossBarVisibility.java:45-46`、同文件测试 `ResearchBossBarVisibilityTest`（`includeOnlineViewer_requiresOnlineAndNearLab_notResidency`） |
| 刷新方式 | 每 **20 tick（1 秒）** 周期性扫描全场在线玩家的所在地块；**已删除 `PlayerMoveEvent`**，不再需要「动一下才刷新」 | `TownResearchPlugin.java:48-49`（`runTaskTimer(..., 20L, 20L)`）、`ResearchBossBarManager.java:86-91`（`refreshBars`）→ `:109-141`（`updateAll`） |
| 离开地块 | **立即隐藏**（无 grace 窗口/防抖延迟） | `ResearchBossBarManager.java:132-137` |
| 退出游戏 | `PlayerQuitEvent` 隐藏 | `ResearchBossBarManager.java:216-219` |
| 总线/降级 | 存在 DisplayBus → 只走总线、**不双写** Bukkit BossBar；缺失 → 走 `LegacyBossBarSink` 直写 | `display/ResearchDisplayBusBridge.java`、`ResearchDisplayBusBridgeTest.withBusAndLegacy_usesBusOnlyNoDualWrite` |
| 下发 TTL | `ResearchBossBarPresentation.TTL_TICKS = 160`（≥ 3 个 40-tick 刷新间隔） | `display/ResearchBossBarPresentation.java:11` |

> 上一版 README 只写了「BossBar 用于显示研究进度」，未说明可见范围与刷新机制；这两笔落后提交（`93ef0a0`、`a9d72eb`）正是在改这里：先接入 DisplayBus + 进出防抖，随后又**去掉移动依赖**改为纯地块扫描。
> 注意：`display/LabPresenceDebouncer` 类仍在库里（带 5 个单测），但 `a9d72eb` 之后**主代码已无任何引用**（`grep -rn "LabPresenceDebouncer" src/main` 命中 0），属遗留类。

## 模块结构

```text
TownResearchPlugin
  ├── TownResearchCommand       — 命令与权限
  ├── ResearchLifecycleManager  — 研究启动、暂停、恢复和完成
  ├── ResearchBossBarManager    — 研究进度显示（每 20 tick 地块扫描）
  │     └── display/            — ResearchBossBarVisibility（地块判定）、ResearchDisplayBusBridge（总线/降级）、ResearchBossBarPresentation（文案与 TTL）、LabPresenceDebouncer（遗留未引用）
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
- 软依赖 `XiNanTownDisplay`（DisplayBus 下发 BossBar；未装则自动降级直写 Bukkit BossBar，`src/main/resources/plugin.yml:5-6`）

构建出的 JAR 直接放入服务端 `plugins/` 目录即可。

```bash
mvn clean package
```

> `leaf-api` 1.21.11 由本机工作区 `XiNanTown/libraries/` 提供，`pom.xml` 用相对路径 `${project.basedir}/../XiNanTown/libraries/` 引用，克隆到任意路径都无需改动。

## 测试与构建（真实运行）

2026-09-25，JDK 21，离线 Maven：

```text
mvn -o -nsu -B clean test
  Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
```

分用例：`LabPresenceDebouncerTest` 5、`ResearchBossBarPresentationTest` 6、`ResearchBossBarVisibilityTest` 5、`ResearchDisplayBusBridgeTest` 6、`PluginYmlDisplayBusTest` 1、`TownDataManagerTest` 3、`TownResearchTest` 12。

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
