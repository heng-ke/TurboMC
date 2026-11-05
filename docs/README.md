# 文档索引

## 现在该看哪一份

| 文档 | 内容 |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | **当前架构的唯一事实来源**：数据流、代码结构、必须知道的约束、构建与调试 |
| [../README.md](../README.md) | 项目简介与快速开始 |
| [../turbowarp/README.md](../turbowarp/README.md) | TurboWarp 扩展的加载步骤 |
| [../turbowarp/mapping-report.md](../turbowarp/mapping-report.md) | 扩展积木与 opcode 的逐条对应表 |

---

## 归档（`archive/`）

这里的文档是开发过程中的阶段性记录。**其中多数描述的是已经被移除的架构**
（自研编译器、IR、可写编辑器、单脚本开关），不要据此理解现有代码。

保留它们的原因只有一个：记录「为什么变成了现在这样」，避免重复踩坑。

### 引擎演进

| 文档 | 主题 | 状态 |
|---|---|---|
| `ENGINE-REFACTOR-PLAN.md` | 早期引擎重构评估（当时打算保留自研编译器） | 方案已废弃，改为直接嵌入 scratch-vm |
| `ENGINE-COMPAT-ASSESSMENT.md` | sb3 翻译层兼容性评估 | 同上 |
| `COMPILER-COMPAT-GAP.md` | 自研编译器与 TurboWarp 的行为差异 | 编译器已删除，差异随之消失 |
| `NODE-BRIDGE-ASSESSMENT.md` | 外挂 Node.js 跑 scratch-vm 的可行性 | 结论仍成立：技术上最优，产品上不作主后端 |
| `STAGE2-VM-BRIDGE.md` | 嵌入 Rhino + scratch-vm 的桥接实现 | 已完成，结构沿用至今 |
| `STAGE3-PLAN-A.md` | 删除自研引擎、单一执行路径 | 已完成 |
| `GAPS-RESOLVED.md` | 当时的缺口处理记录 | 部分结论已被后续改动取代 |

### 界面与 F7

| 文档 | 主题 | 状态 |
|---|---|---|
| `GUI-FIDELITY-ASSESSMENT.md` | GUI 与 Scratch 的对应性评估 | 针对已删除的可写编辑器 |
| `GUI-ENGINE-AUDIT.md` | GUI 与引擎的配合审计 | 针对已删除的可写编辑器 |
| `GUI-BUTTONS-AUDIT.md` | 按钮功能逐项核对 | 针对已删除的可写编辑器 |
| `F7-CUT-LIST.md` | F7 削减第一版评估（彻底删） | 被后续两版取代 |
| `F7-CUT-REEVAL.md` | 第二版评估（保留查看 + 单脚本开关） | 被第三代取代 |
| `F7-PACKAGE-EVAL.md` | 第三版评估（按包分组） | 已完成，结论生效 |
| `F7-PACKAGE-DONE.md` | 包架构落地记录 | 与当前实现一致，细节见 ARCHITECTURE |
| `HUD-IMPLEMENTATION.md` | HUD 实现与两项失效配置清理 | 与当前实现一致 |

### 审计与评估

| 文档 | 主题 | 状态 |
|---|---|---|
| `PROGRESS.md` | 开发进度（停在 2026-11-03） | 已过时 |
| `PROJECT-ASSESSMENT.md` | 项目技术评估 | 已过时（描述可写编辑器时代） |
| `CODE-REVIEW.md` | 全量代码审查 | 已过时 |
| `TURBOWARP-COMPAT-REPORT.md` | TurboWarp 语义兼容性审计 | 结论仍有参考价值，具体缺口已处理 |
| `FULL-AUDIT.md` | 新引擎适配全量审计 | 已修复项不再适用 |
| `SB3-IMPORT-FIX.md` | sb3 导入与聊天/等待修复 | 与当前实现一致，问题已修复 |

---

## 维护约定

1. **新增设计文档前先看 ARCHITECTURE.md**，能在那里补充就不要另开一份。
2. 阶段性报告写完后放进 `archive/`，并在上表登记状态。
3. 如果某份归档的结论被推翻，把状态改成「已废弃」并注明被谁取代，不要直接删——
   推翻的脉络本身有价值。
