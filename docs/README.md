# 文档索引

整个项目只维护四份文档，每份都有明确的职责，不重复内容。

| 文档 | 职责 |
|---|---|
| [../README.md](../README.md) | 面向使用者：装什么、怎么用、有哪些积木 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 面向维护者：**当前实现的唯一事实来源**——数据流、代码结构、必须知道的约束 |
| [CONTAINER.md](CONTAINER.md) | 容器交互：积木清单、槽位编号、为什么只能走 `clickSlot`、必须知道的时序 |
| [../turbowarp/README.md](../turbowarp/README.md) | TurboWarp 扩展的加载步骤与积木清单 |

积木与 opcode 的逐条对应表由脚本从代码生成，不在上表内：
[../turbowarp/mapping-report.md](../turbowarp/mapping-report.md)。

## 维护约定

1. **改代码就顺手改 ARCHITECTURE.md**。它是唯一事实来源，落后于实现的架构文档
   比没有更糟——后来人会照着它去理解不存在的结构。
2. **能在 ARCHITECTURE.md 里补一节，就不要另开一份文档**。
3. **阶段性的评估、审计、迁移记录不进仓库**。那些内容的价值在决策过程，
   而结论已经落进代码与 ARCHITECTURE.md；留在仓库里只会与实现逐渐背离。
   需要回溯时看 `git log`，提交信息里写清了每次变更的原因。
