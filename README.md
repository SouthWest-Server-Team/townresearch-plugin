# townresearch-plugin — 城邦粘液科技研究绑定

Towny + Slimefun 集成插件：将 Slimefun 科技研究从个人级改为**城邦级**。

> 设计文档：`docs/adr/0003-townresearch-plugin.md`（AD-0003）

## 核心功能

- **研究所建筑**：城邦可标记多个地块为研究所（上限 `max-labs`，默认 5）
- **城邦研究**：市长/研究员从城邦银行支付金币 + 时间，解锁 Slimefun 科技
- **全体共享**：科技解锁后城邦所有成员自动获得
- **多研究所加速**：N 个研究所研究同一科技 = 时间 ÷ N
- **成员离城**：离开城邦后失去该城邦解锁的所有科技
- **复用 Slimefun 指南 GUI**：普通玩家只读，研究员可操作

## 命令（`/townresearch`，默认 op）

| 命令 | 说明 |
|------|------|
| `/townresearch set` | 将所在地块标记为研究所 |
| `/townresearch unset` | 取消当前地块的研究所标记 |
| `/townresearch list` | 查看本城邦研究所/研究列表 |
| `/townresearch start <科技>` | 开始研究指定 Slimefun 科技 |
| `/townresearch pause` | 暂停当前研究 |
| `/townresearch resume` | 恢复当前研究 |
| `/townresearch researcher <玩家>` | 任命/移除研究员 |
| `/townresearch buyspeed` | 用金币购买研究加速 |

## 配置（config.yml）

```yaml
max-labs: 5   # 每个城邦最多拥有的研究所数量
```

## 技术栈

- depend：Towny、Slimefun
- Bukkit/Paper API 1.20.1（Java 17 + Maven）

## 开发状态

🟡 设计/实现阶段 — 见 `docs/adr/0003-townresearch-plugin.md`
