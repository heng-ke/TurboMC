# 文档索引

整个项目只维护六份文档，每份都有明确的职责，不重复内容。

| 文档 | 职责 |
|---|---|
| [../README.md](../README.md) | 面向使用者：装什么、怎么用、有哪些积木 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 面向维护者：**当前实现的唯一事实来源**——数据流、代码结构、必须知道的约束 |
| [PATHFINDING.md](PATHFINDING.md) | 「走向」选型与实现细节：A\* 复用、分段重规划、按键节流、踩过的坑 |
| [CONTAINER.md](CONTAINER.md) | 容器交互：积木清单、槽位编号、为什么只能走 `clickSlot`、必须知道的时序 |
| [../turbowarp/README.md](../turbowarp/README.md) | TurboWarp 扩展的加载步骤与积木清单 |
| [OPEN_*.md](OPEN_MOVE_PREEMPTION.md) | **未决问题的事实记录**：已核对的现状、待决策项、候选方案。不含决定，决策落地后必须归档。现有两份：[走向抢占](OPEN_MOVE_PREEMPTION.md)、[视角平滑](OPEN_VIEW_SMOOTHING.md) |

积木与 opcode 的逐条对应表由脚本从代码生成，不在上表内：
[../turbowarp/mapping-report.md](../turbowarp/mapping-report.md)。

## 维护约定

1. **改代码就顺手改 ARCHITECTURE.md**。它是唯一事实来源，落后于实现的架构文档
   比没有更糟——后来人会照着它去理解不存在的结构。
2. **能在 ARCHITECTURE.md 里补一节，就不要另开一份文档**。
3. **阶段性的评估、审计、迁移记录不进仓库**。那些内容的价值在决策过程，
   而结论已经落进代码与 ARCHITECTURE.md；留在仓库里只会与实现逐渐背离。
   需要回溯时看 `git log`，提交信息里写清了每次变更的原因。
4. **`OPEN_*.md` 是第 3 条的唯一例外**：它记录的是「已知缺陷 + 待决策项 + 未实施的候选方案」，
   用途是避免同一问题被反复重新论证。一旦决策落地（改了代码或明确不修），
   必须把结论并入 ARCHITECTURE.md / PATHFINDING.md 并删除该文件——
   留着会变成一份描述「不存在的实现」的文档。
