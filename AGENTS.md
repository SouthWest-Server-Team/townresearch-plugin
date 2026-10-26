# AGENTS.md — townresearch-plugin

> 采集：2026-10-25。基线 `origin/debug` = `51a2d33`（本地 `debug` 可能落后）；实测 `mvn -o -B test` → **Tests run: 48, Failures: 0, Errors: 0**（BUILD SUCCESS）。

## 定位
Towny + Slimefun 城邦科技：把 Slimefun 的**个人级**研究改成**城邦级**——研究所地块、城邦银行付费、成员共享、多研究所加速、研究所 BossBar。

## Slimefun 依赖怎么拿（重点，别去 pom 里加）
- **`pom.xml` 里没有 Slimefun 依赖**，编译期不引用 Slimefun 类：
  - `SlimefunBridge.java` 用反射探测 `Class.forName("io.github.thebusybiscuit.slimefun4.api.SlimefunAddon")`；
  - `ResearchEventListener` 也按类名字符串挂接 `io.github.thebusybiscuit.slimefun4.api.events.PlayerPreResearchEvent`。
- 运行时靠 **`plugin.yml` 的 `depend: [Towny, Slimefun]`（硬依赖：Slimefun 不在，服务端直接不加载本插件）**，`softdepend: [XiNanTownDisplay]`。
- 所以"拿 jar 依赖"= **不需要 Maven 依赖也不需要 `lib/` 自带 jar**：服务器 `plugins/Slimefun-2026.07-release.jar` 在即可；本地单测只覆盖反射失败/降级分支。**不要为了"编译通过"新增 Slimefun maven 依赖**。

## 其它依赖与构建
- `leaf-api 1.21.11-R0.1-SNAPSHOT`(provided)、`towny 0.103.1.0`(provided)、`adventure-api 4.26.1`(provided)、`bungeecord-chat`(provided)、`xinantown-display 0.1.0-SNAPSHOT`(provided)。
- ⚠️ **`xinantown-display` 必须先 `mvn install` 进本地仓库**，否则离线构建解析失败。
- 产物 `TownResearch-1.0.0-SNAPSHOT.jar`（`finalName=${artifactId}-${version}`）；部署 = 停服后覆盖运行服 `plugins/TownResearch-1.0.0-SNAPSHOT.jar`。
- 跑测试：`./mvnw -o -B test` 或本机 maven + `JAVA_HOME=<jdk21>`。

## BossBar 是直写，不走 DisplayBus（2026-09-27 用户拍板）
- 插件自己写 Bukkit BossBar。`xinantown-display` 的公开面自 2026-09-23 起已删除 BossBar 能力（只剩 ActionBar 三个方法），旧"拿得到服务就走总线"的分支会在退服/disable 抛 `NoSuchMethodError`（真服日志见过）。
- `display/ResearchDisplayBusBridge` + `ResearchBossBarDirectWriteContractTest` 是这条契约的守卫：**不要为了"复用总线"把它改回去**。

## 结构
- `TownResearchPlugin`（JavaPlugin 入口）、`ResearchService`、`ResearchLifecycleManager`（研究状态机：start / pause / resume / 完成）
- `TownDataManager`（城邦数据 + 研究所地块集合 + 持久化）、`ResearchSettings`（`research-settings.yml`）
- `ResearchEventListener`（截 Slimefun 研究事件）、`ResearchCommand`（`/townresearch set|unset|list|start|pause|resume`）、`ResearchGuiListener`
- `display/`：`ResearchBossBarPresentation`、`ResearchBossBarVisibility`（按 TownBlock 世界+区块判定，**不依赖玩家移动事件**）、`LabPresenceDebouncer`、`ResearchDisplayBusBridge`

## 数据与配置落盘

- `plugins/TownResearch/data/<城邦名小写>.yml`：**每城一个文件**，研究进度/研究员/研究所的事实来源（`TownResearchPlugin.java:23` 构造 `new File(getDataFolder(), "data")`，`TownDataManager` 按 `townName.toLowerCase() + ".yml"` 命名，`loadAll` 只扫 `data/*.yml`）。
- `plugins/TownResearch/config.yml`：`saveDefaultConfig()` 落下，本仓只读 `max-labs`（默认 5）；模板 `src/main/resources/config.yml`。
- `plugins/TownResearch/research-settings.yml`：`ResearchSettings` 用 `saveResource("research-settings.yml", false)` 落下（`ResearchSettings.java:46` / `:53`），模板 `src/main/resources/research-settings.yml`。**已存在就不覆盖** → 改默认值不会自动生效，要人工删文件。

## 对外契约与消费方

- **本仓不注册任何 provider / capability，没有对外 API**：不提供静态门面，也没有 `xinantown-api` 的 `DomainProvider` 实现。其它插件要读研究进度只能读 `plugins/TownResearch/data/*.yml`（文件通道）——**读这份落盘格式等于耦合本仓私有结构**，不推荐。
- 消费 Towny：城邦/市长身份判定、城邦银行付费、城邦成员可见性。
- 消费 Slimefun：**只有反射**（`SlimefunBridge` + `ResearchEventListener`，见上节），编译期不引用 Slimefun 类。
- 消费 `xinantown-display`：经 `ResearchDisplayBusBridge` 取 ActionBar；**BossBar 直写 Bukkit，不走 DisplayBus**（见上节，不要改回去）。
- 反向：本工作区内**没有其它插件 `softdepend: [TownResearch]`**，改 `plugin.yml` 的 name 只影响服务端加载。

## 必读文档

- 仓内：`README.md`。
- KB：`facts/townresearch-plugin/townresearch-bossbar-direct-write.md`（BossBar 直写拍板与 NoSuchMethodError 来源）、`facts/townresearch-plugin/townresearch-town-data-ownership.md`（城邦数据归属）。
- 运行侧对照：`facts/infra/deployed-jars-vs-commits.md`（运行盘 `plugins/TownResearch-1.0.0-SNAPSHOT.jar` 对应哪个提交）。

## 纪律
- `PluginYmlDisplayBusTest` 直接断言 `plugin.yml` 内容（name/main/depend/softdepend）——改 `plugin.yml` 会红，属预期。
- 权限判定走城邦身份（市长/研究员），离城玩家立即失去权限；改这块必须补测试。
- PR base = `debug`；不 push、不启服、不改运行服 `plugins/TownResearch-1.0.0-SNAPSHOT.jar`。
