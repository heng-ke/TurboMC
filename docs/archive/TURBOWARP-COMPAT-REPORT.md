# TurboWarp 兼容性审计报告

- **审计对象**：`hk-script` 客户端脚本引擎（`xyz.hegke.client.script`）与 `sb3` 导入链路
- **对照基准**：`scratchfoundation/scratch-vm` develop 分支源码（2026-11-03 抓取）+ Scratch File Format 规范
- **审计方式**：逐条对读实现（不凭印象），凡判定为缺陷的均附「上游源码行为」与「本地代码位置」双重证据
- **结论日期**：2026-11-03

---

## 0. 结论摘要

**格式层已经真正跑通，语义层不是。**

已经验证的事实：`.sb3` 解压、`project.json` 解析、inputs 的 1/2/3 类型码、mutation、扩展 opcode 前缀（`hkscript_*`）、扩展菜单影子积木、跳转与执行——端到端可用，用户已实测导入并执行成功。

但"能跑"和"跑得跟 Scratch 一样"是两件事。本次审计出 **6 个 P0、8 个 P1、7 类 P2 缺口**，其中三条会让脚本出现**用户完全无法自查的错误**：

| # | 最危险的三个问题 | 用户看到的假象 |
|---|---|---|
| P0-1 | 一个未映射积木会**吞掉它后面的整段积木** | "我明明写了二十块，怎么只跑了前十块" |
| P0-2 | 输入槽里内联的变量读到的是**脚本局部作用域**，与外层变量的全局作用域不通 | "变量赋值了，但下面读出来一直是 0" |
| P0-3 | 两个非数字字符串比较**恒为相等** | "如果 名字 = 石头 永远成立" |

**兼容度评分（按语义等价，不按能否导入）**

| 维度 | 评分 | 说明 |
|---|---|---|
| 文件格式 / 结构解析 | 95% | 唯一缺口：内联列表原语（type 13）未处理 |
| 控制流（if / repeat / until / stop） | 65% | 循环节奏与 Scratch 差 20 倍，repeat 次数未取整 |
| 事件与线程 | 70% | 触发模型与 Scratch 相反（并发 vs 重启） |
| 数据（变量 / 列表） | 55% | 忽略变量 ID 与 target 作用域；列表特殊索引缺失 |
| 运算 | 55% | 比较语义错、取余负数错、随机数恒整数、缺 4 个常用积木 |
| 游戏动作（自研扩展） | 100% | 这一层是原创，无可对照 |
| **综合（加权）** | **约 65%** | 能演示，不能交付"导入即可用" |

---

## 0.1 修复进度（B 方案分批推进）

| 缺陷 | 状态 | 批次 | 说明 |
|---|---|---|---|
| P0-1 未映射积木断链 | ✅ 已修 | R1 | 新增 `control.nop` 占位并继续链接 |
| P0-2 内联变量作用域 | ✅ 已修 | R1 | `[12,…]` 与变量积木共用作用域解析 |
| P0-3 字符串比较恒等 | ✅ 已修 | R2 | `ScratchCompare`：数字优先 + 小写字符串 |
| P0-4 变量/列表按名合并 | ✅ 已修 | R1 | 按 ID 定位 + 角色前缀 + `TargetVariables` |
| P0-5 线程模型相反 | ✅ 已修 | R3 | `TriggerPolicy`：广播重启 / 按键放弃 / 伤害并发 |
| P0-6 循环节奏快 20 倍 | ✅ 已修 | R3 | `LOOP.b` 节流；配置项默认关闭（快速），可切严格对齐 |
| P1-1 取模负数 | ✅ 已修 | R2 | `ScratchMath.mod` 向下取整 |
| P1-2 随机数恒整数 | ✅ 已修 | R2 | 按整数/浮点分支 |
| P1-3 缺 4 个运算 | ✅ 已修 | R2 | 新增 letter_of / length / contains / mathop |
| P1-4 repeat 未取整 | ✅ 已修 | R2 | 编译期插入 `operator.round` |
| P1-5 列表特殊索引 | ✅ 已修 | R2 | `listIndex` 支持 last/random/any/all + floor |
| P1-6 调度饥饿 | ✅ 已修 | R3 | 按剩余实例数均分预算 + 每实例保底 32 条 |
| P1-7 除零返回 0 | ⬜ 待评估 | R3 | 有意偏差，需配置化 |
| P1-8 toBoolean 多 trim | ✅ 已修 | R2 | 去掉 trim，逐字对齐 |
| P2-7 导入报告 | ✅ 已修 | R1 | `ParseResult.summary()` + 导入提示 |
| P2-8 内联列表原语 | ✅ 已修 | R1 | 新增 `list.contents` |
| P2-1 自定义积木 | ✅ 已修 | R4b | 定义不再丢弃；调用/参数/返回值按 proccode 对齐 |
| P2-2 广播并等待 | ✅ 已修 | R4c | 新增 `BROADCAST_WAIT` 指令，等待被唤醒实例结束 |
| P2-3 按键查询 | ✅ 已修 | R4a | `input.key_pressed`，支持 `any` |
| P2-4 计时器族 | ✅ 已修 | R4a | timer / reset / current / days_since_2000 |
| P2-5 `说` 被丢弃 | ✅ 已修 | R4a | 映射到动作栏 + 日志；`说 n 秒` 换算并阻塞 |
| P2-6 任意键触发 | ✅ 已修 | R4a | `InputKeys.anyDown`（排除 ESC/F6/F7） |

**自检当前 22 项全部通过，积木 83 个。**

> **2026-11-03 结项**：除 `P1-7`（除零返回 0，属有意偏差）外，本报告列出的 P0/P1/P2 全部修复。
> 期间额外发现并修复一个未被报告覆盖的运行时崩溃：**调度器在遍历实例时新建实例会抛
> `ConcurrentModificationException`**（广播与 `spawn` 都会触发）。

---

## 1. 审计方法

1. 取上游实现作为唯一判据，而非凭记忆：
   - `src/blocks/scratch3_control.js`（循环 / 等待 / 停止）
   - `src/blocks/scratch3_operators.js`（运算）
   - `src/blocks/scratch3_data.js`（变量与列表）
   - `src/util/cast.js`（类型转换与比较——**这是语义分歧的源头**）
   - `src/engine/sequencer.js`（每帧步进与循环节流）
   - `src/engine/runtime.js::startHats`（帽子触发与线程重启）
   - Scratch File Format 规范（inputs 类型码、fields 第二元素为 ID、`control_stop` 的 `mutation.hasnext`）
2. 逐条对照本地实现，记录文件与行号。
3. 只在两侧都成立时判为缺陷；对"有意偏离"单列一节，不混入缺陷表。

---

## 2. P0 缺陷（会导致静默错误或代码丢失，必须修）

### P0-1 未映射积木会截断后续整条积木链

**现象**：脚本中间只要有一块不被支持的积木（例如「广播并等待」「克隆」「说」），**它后面的所有积木都不会导入**，且不报错、界面看不出缺失。

**上游行为**：所有积木都有实现，不存在"跳过"概念。

**本地实现**：`Sb3Parser.buildBlock()` 对未映射 opcode 直接 `return null`；而链式还原是
`node.next = buildBlock(next, ...)`——返回 null 就等于把 `next` 置空，后续整条链丢失。

```java
// Sb3Parser.buildBlock()
String type = Sb3Mapping.typeOf(opcode);
if (type == null) {
    LOG.warn("[sb3] 未映射的积木，已跳过: {}", opcode);
    return null;            // ← 这里断链
}
```

**影响面**：极高。用户拼得越长，丢得越多，且日志里只有一行 warn，很容易被忽略。

**修复方案**：引入占位积木 `control.nop`（编译为空指令）。

- 语句类未映射块 → 返回 `BlockNode.of("control.nop")`，**继续解析 `next`**；
- 表达式类未映射块 → 返回 `Slot.literal("")`，让外层积木拿到空值而不是失联；
- 同时在导入结果里累计"被跳过清单"，导入完成后一次性汇总展示（见 P2-7）。

**成本**：0.5 天（含 nop 积木、编译分支、自检项）。

---

### P0-2 输入槽内联变量读到的是局部作用域

**现象**：`将 [分数] 设为 (10)` 之后，`移动 ((分数) × 2)` 里读到的仍是 0。

**上游行为**：变量只有一个作用域概念（target 作用域），不存在"内联变量与外层变量不同域"。

**本地实现**：两条通路不一致。

- 变量积木（`data_variable`）走 `buildBlock()`，命中 `data.set/data.get → SCOPE=global` 的补丁；
- **内联原语**（Scratch 把变量 reporter 序列化成 `[12, name, id]`）走 `shadowValue()`，构造的 `BlockNode` 没有 `SCOPE` 字段：

```java
// Sb3Parser.shadowValue()
if (type == 12) {
    return Slot.of(BlockNode.of("data.get").field("VAR", text));   // ← 没有 SCOPE
}
```

而编译器默认取局部：

```java
// Compiler.scopeOf()
return "global".equals(node.fieldOr("SCOPE", "local")) ? 1 : 0;
```

**影响面**：极高。Scratch 里变量出现在输入槽是**最常见**的写法（`移动 (速度)`、`如果 <(血量) < 10>`），几乎每个真实脚本都会踩到。

**修复方案**：`shadowValue()` 的 type==12 分支补 `.field("SCOPE", "global")`；再统一为 `data.get` 的默认值改为 `global`（内部编辑器新建时显式写 local，避免影响已有脚本）。

**成本**：0.2 天（3 行代码 + 1 条自检断言）。

---

### P0-3 字符串比较恒等：`=` 对任意两个非数字字符串恒为 true

**现象**：`如果 <(名称) = (石头)> 那么` 恒成立；`<(a) < (b)>` 恒为 false。

**上游行为**（`cast.js::compare`）：先尝试数字；若任一侧不能转数字，则**转小写按字符串比较**，并特判全空白/空串。

```js
const n1 = Number(v1); /* ... */
if (isNaN(n1) || isNaN(n2)) {
    const s1 = String(v1).toLowerCase();  // 大小写不敏感
    if (s1 < s2) return -1;
    ...
}
```

**本地实现**：`ScriptInstance` 的 `COMPARE` 先把两侧 `Values.asNumber()`（非数字一律变 0），再比较两个 double：

```java
case COMPARE -> {
    double right = Values.asNumber(pop());
    double left  = Values.asNumber(pop());
    boolean result = switch (instr.s) { case "<" -> left < right; ... };
}
```

于是 `"石头"` 与 `"泥土"` 都被转成 0 → `0 == 0` → **恒真**。

**影响面**：高。任何用字符串做状态判断的脚本都会走进错误分支。

**修复方案**：新增 `Values.compare(Object, Object)`，照抄上游三段逻辑（数字优先 / 空白特判 / 小写字符串），`COMPARE` 改为调用它；`list.contains` 与 `list.item_num_of` 同步复用。

**成本**：0.5 天（含自检：断言 `"apple" < "banana"` 为真、`"A" == "a"` 为真、`"10" < "9"` 为假）。

---

### P0-4 变量与列表按名字合并，忽略 ID 与 target 作用域

**现象**：多角色项目里，每个角色各自的「分数」被合并成同一个变量，互相覆盖；Stage 的全局变量与角色局部变量也不区分。

**上游行为**：`fields.VARIABLE = [name, id]`，运行期用 **ID** 查找：`lookupOrCreateVariable(args.VARIABLE.id, args.VARIABLE.name)`；查找范围是「本 target + Stage」。

**本地实现**：只取 `array.get(0)`（名字），ID 直接丢弃：

```java
node.fields.put(Sb3Mapping.fieldName(opcode, entry.getKey()), array.get(0).getAsString());
```

**影响面**：高。单角色单脚本尚可，多角色即错。

**修复方案**：导入时先把每个 target 的 `variables` / `lists` 字典（`id → [name, value]`）读出来，建立 **id → 唯一内部名** 映射：

- Stage 上的变量 → 内部名保持 `name`（全局）；
- Sprite 上的变量 → 内部名 `name@Sprite1`（脚本局部）；
- 遇到同一 id 时始终用映射表，而不是名字。

需要 `Sb3Parser` 增加 `Map<String,String> variableIds` 字段并按 target 构建。

**成本**：1 天（涉及解析器状态传递 + 已有脚本兼容：旧脚本无 ID，回退按名字）。

---

### P0-5 触发线程模型与 Scratch 相反（并发 4 个 vs 重启/放弃）

**现象**：在循环里高频广播，接收脚本**同时跑起 4 份**互相抢操作；Scratch 里同一时刻只有 1 份。

**上游行为**（`runtime.js::startHats`）：

- `restartExistingThreads: true`（绿旗、广播接收、角色点击）→ **原地重启**已有线程，永远只有 1 条；
- `restartExistingThreads: false`（按键）→ 已有**未结束**线程则**直接放弃**本次触发。

**本地实现**：每次触发都 `startInstance()` 新建实例，上限 `MAX_INSTANCES_PER_SCRIPT = 4`。

```java
// ScriptEngine.fire()
if (scheduler.countByName(definition.name) >= MAX_INSTANCES_PER_SCRIPT) continue;
startInstance(definition);
```

**影响面**：中高。表现为"动作抽搐/资源被重复消耗"，且难以归因。

**修复方案**：`fire()` 按触发类型选择策略：

- `event.broadcast` / `event.tick` → 先 `scheduler.killByName(name)` 再新建（restart 语义）；
- `event.key` → 若同名实例仍在运行则跳过（abandon 语义）；
- `event.damage` / `event.chat` → 保留并发（这两个是本模组扩展，Scratch 无对照，并发更实用）。

**成本**：0.5 天。

---

### P0-6 循环节奏比 Scratch 快约 20 倍

**现象**：`重复 100 次 [移动 1 步]` 在 Scratch 里约 3.3 秒的动画；导入后在一两个 tick（0.1 秒）内跑完。

**上游行为**（`sequencer.js::stepThread`）：循环体执行完毕后，若栈帧 `isLoop` 为真且不在 warp 模式，**直接 return 让出本帧**——即"每帧一轮"。这就是 Scratch 循环天生慢的原因。

```js
if (stackFrame.isLoop) {
    if (!isWarpMode || thread.warpTimer.timeElapsed() > Sequencer.WARP_TIME) {
        return;   // 循环体结束后让出本帧
    }
}
```

**本地实现**：`emitRepeat` / `emitForever` 只发 `LOOP` 回边，`ScriptInstance` 的 `LOOP` 分支仅判断预算，没有"每轮让出"：

```java
case LOOP -> pc = instr.a;   // 只有预算耗尽才在别处让出
```

**影响面**：高（体感层面）。所有带循环的脚本节奏都不对，尤其是"逐格挖掘/逐点移动/渐进动画"这类依赖每轮一 tick 的写法。

**修复方案**：给 `LOOP` 增加 `b` 标志位表示"每轮让出"：

- 编译期由配置 `scratchLoopThrottle`（默认 **开**）决定；
- 解释器遇到 `b==1` 时设置 `wakeTick = currentTick + 1` 并 `State.WAITING`；
- 配置为"快速模式"时行为与现在一致（适合纯自动化脚本，牺牲动画节奏）。

**成本**：0.5 天（含配置项与界面文案）。

> 说明：这一条是**唯一需要用户决策**的——严格对齐 Scratch 会让现有挖掘脚本慢 20 倍。建议默认对齐，并在导入时提示可切换。

---

## 3. P1 缺陷（特定用法下出错）

| # | 问题 | 上游行为 | 本地实现 | 修复 |
|---|---|---|---|---|
| P1-1 | **取余负数** | `mod` 用向下取整：`-3 % 5 = 2` | `BIN_OP "%"` 用 Java `%`：`-3 % 5 = -3` | `Values.mod()` 按 `result/modulus < 0 ? result+modulus : result` 实现 |
| P1-2 | **随机数恒为整数** | 两侧都是整数才取整，否则返回浮点 | 恒用 `floor(rand*(high-low+1))` | 按 `Cast.isInt` 分支；浮点分支为 `rand*(high-low)+low`。当前浮点区间会**越界**（`0.5~1.5` 只会得到 0.5 或 1.5） |
| P1-3 | **缺 4 个常用运算** | `letter_of` / `length` / `contains` / `mathop`（13 种数学函数） | `Sb3Mapping` 中无映射 → 触发 P0-1 断链 | 新增 4 个积木 + 映射；`contains` 必须大小写不敏感 |
| P1-4 | **repeat 次数未取整** | `Math.round(TIMES)` | 直接以 double 递减，`> 0` 判断 | 编译时套一层 `operator.round`，或在 `emitRepeat` 内插入取整指令 |
| P1-5 | **列表特殊索引缺失** | 支持 `last` / `random` / `any`，索引用 `floor`，`delete` 还支持 `all` | `toIndex` 用 `Math.round`，不接受字符串 | 重写 `toIndex` 对齐 `Cast.toListIndex`（含 `LIST_INVALID`/`LIST_ALL` 语义） |
| P1-6 | **调度饥饿** | 无线程预算概念 | 全局 4000 指令 / 每实例 400，`if (budget <= 0) break;` → **第 11 个起实例每 tick 永远拿不到配额** | 为每个实例保留最小配额（如 64），或每 tick 轮转起始下标 |
| P1-7 | **除零返回 0**（有意偏离） | `1/0 = Infinity`，`-1/0 = -Infinity`，`0/0 = NaN` | 返回 0 | 建议改为 Infinity/NaN 以对齐；若担心污染后续计算，做成配置项并写入文档 |
| P1-8 | **布尔转换多了一次 trim** | `''`/`'0'`/`'false'`（不 trim）为假，`"  "` 为**真** | `asBool` 先 trim，`" false "` 判假 | 去掉 trim，逐字对齐 `Cast.toBoolean` |

---

## 4. P2 能力缺口（不破坏已支持脚本，但表达力受限）

| # | 缺失 | 说明 | 成本 |
|---|---|---|---|
| P2-1 | **自定义积木（procedures）** | `procedures_definition` 是帽子积木 → **整个定义脚本被丢弃**；`procedures_call` 未映射 → 触发 P0-1 断链。这是 TurboWarp 用户组织代码的基本手段，优先级应高于表面排序 | 2 天 |
| P2-2 | **广播并等待** `event_broadcastandwait` | 需要"等待一组实例结束"的新指令 | 1 天 |
| P2-3 | **`sensing_keypressed`（按下 [键]?）** | Scratch 高频查询；目前只能用触发，不能查询 | 0.5 天 |
| P2-4 | **计时器族** `sensing_timer` / `reset_timer` / `current` / `days_since_2000` | "计时器"是 Scratch 里做节流与计时的标准手段 | 0.5 天 |
| P2-5 | **`looks_say` / `sayforsecs`** | 现在直接丢弃 → 用户的调试输出全消失 | 0.3 天（映射到动作栏/日志） |
| P2-6 | **"任意键"触发** `KEY_OPTION = any` | `translationKeyOf` 对 `"any"` 返回 null → 永不触发 | 0.5 天（需 GLFW 按键回调） |
| P2-7 | **导入报告** | 目前只有逐条 warn，用户不知道丢了多少 | 0.5 天（汇总"跳过 N 块 / 明细"） |
| P2-8 | **内联列表原语** `[13, name, id]` | `shadowValue` 只处理 12，列表内容作为输入时变字面量 | 0.2 天 |

---

## 5. 明确不建议修复的项

| 项 | 理由 |
|---|---|
| 运动（`motion_*`） | 2D 舞台 480×360 与 3D 世界坐标无对应；硬映射会产出"看着对、落点全错"的假语义 |
| 外观（`looks_*` 除 say） | 造型/大小/图层在 MC 里没有对应物 |
| 声音 / 画笔 / 背景 | 同上 |
| 克隆（`control_create_clone_of`） | 克隆有独立状态与生命周期，与 `并行分支` 不等价；错误映射比不支持更危险 |
| 角色点击 / 舞台点击 | 无对应输入语义 |
| `warp`（run without screen refresh） | 我们本就是"同 tick 连续执行"，warp 反而是**默认行为**；在 P0-6 开启节流后可考虑把 warp=true 映射为"绕过节流" |

---

## 6. 建议的修复批次

按"危险度 / 成本"排序，每批可独立验收：

| 批次 | 内容 | 工期 | 价值 |
|---|---|---|---|
| **A 不丢代码** | P0-1 占位积木、P0-2 内联变量作用域、P2-7 导入报告、P2-8 列表原语 | 1 天 | 直接消灭"静默丢代码/读错变量"两类致命问题 |
| **B 运算对齐** | P0-3 比较语义、P1-1 取余、P1-2 随机、P1-3 四个运算积木、P1-4 repeat 取整、P1-8 布尔 | 1.5 天 | 表达式层与 Scratch 等价，字符串脚本可用 |
| **C 数据作用域** | P0-4 变量/列表 ID 映射 | 1 天 | 多角色项目可用 |
| **D 线程与节奏** | P0-5 触发策略、P0-6 循环节流（含配置） | 1 天 | 行为时序与 Scratch 一致 |
| **E 能力补齐** | P2-1 自定义积木、P2-2 广播并等待、P2-3 按键查询、P2-4 计时器、P2-5 say | 3–4 天 | 从"能演示"到"能写真实脚本" |
| **F 健壮性** | P1-5 列表索引、P1-6 调度饥饿、P1-7 除零 | 1 天 | 边界正确 |

**总计约 8.5–9.5 人日**。若只做 A+B（2.5 天），兼容度可从 65% 提到约 85%，覆盖绝大多数用户脚本。

---

## 7. 验收方式（每条缺陷配一条自检）

自检目前 15 项，建议扩到 24 项，全部可在无游戏环境跑：

| 断言 | 防的是什么 |
|---|---|
| 未映射积木之后仍存在后续指令 | P0-1 断链 |
| 内联变量编译出的 `LOAD_VAR` 作用域位为 1 | P0-2 |
| `compare("apple","banana") < 0`、`compare("A","a") == 0`、`compare("10","9") < 0` | P0-3 |
| 同 id 变量不合并、不同 id 同名变量不冲突 | P0-4 |
| 广播连发两次后活动实例数为 1 | P0-5 |
| 节流开启时 `重复 3 次` 跨 3 个 tick | P0-6 |
| `mod(-3,5) == 2` | P1-1 |
| `random(0.5,1.5)` 结果落在区间内且非端点 | P1-2 |
| `letter_of(2,"abc") == "b"`、`contains("ABC","bc") == true` | P1-3 |
| `repeat 2.1` 只跑 2 轮 | P1-4 |
| `item_of_list("last")` 取到末项、`delete("all")` 清空 | P1-5 |
| 12 个实例并行时每个每 tick 都有指令推进 | P1-6 |

---

## 8. 一句话总结

**这套引擎已经能"读取 Scratch 项目"，但还不能"按 Scratch 的语义执行它"。**
最该先做的不是补功能，而是把 P0-1 / P0-2 修掉——这两条不加任何新能力，却能让现有脚本的可靠性产生质变。
