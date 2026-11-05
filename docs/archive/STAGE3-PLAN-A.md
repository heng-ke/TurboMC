# 阶段 3 完成：方案 A（单一执行路径）

- **日期**：2026-11-05
- **改动规模**：`+301 / −4641`（净删 4340 行），20 个文件
- **结论**：旧引擎已完全移除，编辑器产出经 `ProjectWriter` 直接交给 VM。四条冒烟全绿。

---

## 1. 删除了什么

| 组件 | 行数 | 说明 |
|---|---|---|
| `ir/`（Compiler / CompiledScript / Instr / Op） | ~700 | 树 → 指令流的编译器 |
| `runtime/ScriptInstance` + `ScriptScheduler` | ~700 | 指令解释器与调度器 |
| `sb3/Sb3Parser` | 791 | sb3 → BlockNode 树（反向职责已由 ProjectWriter 承担） |
| `SelfCheck` / `SelfCheckRuntime` / `SelfCheckSb3` | 2068 | 22 项自检全部针对旧引擎 |

保留：`BlockNode` / `Slot` / `BlockType` / `Blocks` / `BlockRegistry`（编辑器数据模型）、`ScriptContext`（动作层上下文）、`Sb3Mapping`（桥的 opcode 查询）、`ScriptStore` / `ScriptDefinition`（编辑器持久化）。

---

## 2. 新增了什么

### `sb3/ProjectWriter.java`（~380 行）

编辑器积木树 → `project.json`。与已删除的 `Sb3Parser` 互为逆过程。

- 字面量写 shadow：文本 `[1,[10,"x"]]`，数字 `[1,[4,"1"]]`
- 积木输入写 `[3, id, [10,""]]`，分支写 `[2, id]`
- 变量按名字收集进 target 的 `variables`，ID 统一为 `var-<名字>`
- 少数类型特判：`control.stop_all` → `control_stop` + `STOP_OPTION=all`、`control.broadcast_wait` → `event_broadcastandwait`、`event.broadcast` → `event_whenbroadcastreceived`、`debug.say_for` → `looks_sayforsecs`
- 无法表达的类型（`control.nop`、`control.spawn` 等）跳过并汇总到 `Result.unsupported()`，由引擎打日志

配套在 `Sb3Mapping` 增加 `opcodeOf(type)`：反向查表，复用已有的 103 条映射，不新增数据源。

### `ScriptEngine` 重写（379 → ~290 行）

保留门面 API，内部整体换实现：

| 方法 | 新行为 |
|---|---|
| `init()` | 只做注册表与脚本库加载，不再跑自检 |
| `tick()` | 判定开关/世界状态 → 必要时 `start()` → `RhinoVm.tick()` |
| `start()` | 启用脚本 → `ProjectWriter` → `loadProject` + `greenFlag`；**若存在 `hkscript-vm/project.sb3` 则优先运行它** |
| `fire()` | 映射到 `startHat(opcode)`，`matcher` 参数不再参与过滤 |
| `fireKey()` | 新增，按 `KEY_OPTION` 字段精确匹配，只有对应键的脚本启动 |
| `runningBlocks()` | 恒返回空集（VM 不提供积木级定位） |
| `broadcast()` | 空实现（广播积木由 VM 内部执行） |

`VmController` 已删除：事件与 tick 统一走 `ScriptEngine`，不再有第二个 VM 驱动源。`TriggerDispatcher` 的 VM 钩子随之移除。

---

## 3. 功能变化（方案 A 的确定代价）

| 功能 | 变化 | 原因 |
|---|---|---|
| 运行时面板 | 改为显示「线程数 / 脚本数 / 桥调用 / 变量快照」 | VM 以字节码线程运行，没有实例、pc、局部变量、错误态 |
| 编辑器指令区间提示 | 已移除 | 没有指令流 |
| 运行积木高亮 | 失效（恒空集） | 同上 |
| sb3 导入 | 不再进编辑器，直接复制为 `hkscript-vm/project.sb3` 并重启运行 | 单一数据契约：工程由 VM 解释，编辑由编辑器负责 |
| 22 项自检 | 删除，未迁移 | 全部针对旧引擎；新的等价物是 4 条冒烟 |

前四项是需要老大知晓的**能力缩减**，不是 bug。若日后需要「运行中高亮」，只能自建执行追踪，VM 不提供。

---

## 4. 验证结果

| 冒烟 | 命令 | 结果 |
|---|---|---|
| 往返：编辑器树 → JSON → VM | `./gradlew vmWrite` | `WRITE SMOKE PASS`（score=1，循环执行 3 次） |
| 顺序执行 | `./gradlew vmSmoke` | `SMOKE PASS` |
| 事件触发 | `-PvmMode=hat` | `HAT SMOKE PASS` |
| 阻塞动作 | `-PvmMode=block` | `BLOCK SMOKE PASS`（第 40 帧） |

打包：`build/libs/hk-script-1.0.0.jar` **2.14 MB**（含 Rhino 与 VM 资源）。

---

## 5. 已知缺口

| # | 缺口 | 影响 | 处置建议 |
|---|---|---|---|
| 1 | 变量初始值不透传 | ProjectWriter 一律写 0，编辑器里设定的初值丢失 | 从 `GlobalVariables` 读初值写进 variables 表 |
| 2 | 自定义积木参数未序列化 | `FunctionDefinition` 只有名字，`procedures_call` 的实参通道为空 | 补 argumentids/argumentnames |
| 3 | 聊天关键词未过滤 | 任意聊天触发所有 whenChat 脚本 | 读 hat 的 input shadow 做匹配 |
| 4 | 无积木级调试能力 | 见 §3 | 接受或自建 |
| 5 | `VmSmokeTest` / `WriteSmokeTest` 混在产物中 | 多两个带 main 的类 | 发布前移到独立 sourceSet |
| 6 | 编辑器仍有 106 个积木定义，其中约 8 个无 sb3 表达 | 用到时会被跳过并告警 | 逐个补 opcode 或从面板移除 |

---

## 6. 真机验证清单

1. `./gradlew build` 后装上 jar，进世界；
2. 按 F6 启动引擎 → 日志应出现 `[script] 工程已启动：N 个脚本`；
3. 首次启动会有 0.6–1.2 秒卡顿（VM 脚本解析），之后不再出现；
4. 在编辑器里拼一段「当启动 → 设置 score=1 → 重复 3 次 日志」→ 应能跑通；
5. 放一个 sb3 到 `run/imports/` → 引擎重启后应优先运行它，日志出现 `运行 hkscript-vm/project.sb3`；
6. 打开运行面板应看到 `runtime: rhino + scratch-vm`、`threads=`、`bridge=` 与变量快照；
7. F3 观察帧时间：持续抖动应小于 5 ms。
