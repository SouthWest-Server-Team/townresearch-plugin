# townresearch-plugin — 城邦科技研究

Towny + Slimefun 集成插件，将 Slimefun 科技研究从个人级改为城邦级。

## 核心功能

- **研究所建筑**：城邦可标记多个地块为研究所，数量上限可配置
- **城邦研究**：市长或研究员从城邦银行支付费用并启动研究
- **成员共享**：科技解锁后，城邦成员可使用对应科技
- **多研究所加速**：多个研究所参与同一研究时缩短研究时间
- **离城处理**：玩家离开城邦后不再享有该城邦的研究权限
- **Slimefun 指南集成**：普通成员只读，研究员可执行研究操作

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
```

## 实现原理

插件以 Towny 城邦作为研究数据和权限边界，以 Slimefun API 获取科技定义和解锁状态。研究任务由调度器推进并持久化，研究所数量参与研究时间计算；研究完成后通过 Slimefun 集成层同步城邦成员的可用科技。BossBar 用于显示研究进度，研究员和城邦银行权限由 Towny/Vault 数据决定。

## 模块结构

```text
TownResearchPlugin
  ├── TownResearchCommand       — 命令与权限
  ├── ResearchLifecycleManager  — 研究启动、暂停、恢复和完成
  ├── ResearchBossBarManager    — 研究进度显示
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

```bash
mvnw clean package
```
