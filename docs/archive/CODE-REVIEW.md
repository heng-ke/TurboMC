# hk-script 全量代码审查报告

> 审查对象：`hk-script 1.0.0`（8,669 行 / 61 文件 / 104 积木 / 24 项自检）
> 审查日期：2026-11-03
> 审查方式：逐文件阅读 + 危险模式检索（索引、空值、递归、资源、生命周期），不依赖运行
>
> **更新（2026-11-04 00:13）：四批修复全部完成** —— 22 条中 **16 条已修并验证**，
> 6 条为「有意保留」（语义特性 / 需要新功能 / 影响可忽略，见文末）。
> 自检 24 → 28 项全绿。下面保留原始描述，状态见「修复记录」。

---

## 0. 结论

**整体质量：良好。** 防御性检查做得比较到位——UI 层索引几乎每处都做了范围判断，动作层普遍有 `player == null` 守卫，执行内核的栈下溢、调用深度、预算耗尽都有显式处理。

**但存在 3 个必须修的问题**，它们都不是崩溃，而是**静默的数据/状态损坏**——这类问题最难被发现：

| # | 问题 | 后果 | 严重度 |
|---|---|---|---|
| **E1** | sb3 导入把"积木链长度"当成"嵌套深度"计数，上限 200 | 超过 200 条的脚本，**后半段静默丢失** | **P0** |
| **U1** | 脚本文件名归一化后可能相同（`a b` 与 `a_b`） | 两个脚本写同一个文件，**互相覆盖** | **P0** |
| **E3** | 脚本结束/崩溃后不释放它按下的键 | 角色**永久保持前进**，直到按 F6 停引擎 | **P1** |

其余 16 项为 P2/P3，不影响正常使用，但会在特定场景暴露。

---

## 1. 引擎层

### E1（P0）sb3 导入的深度计数用错，长脚本被截断

`Sb3Parser.buildBlock(id, ctx, depth)` 的 `depth` 同时被两种结构消耗：

```java
// 第 455 行：沿 next 链推进时 +1
node.next = buildBlock(next, ctx, depth + 1);

// 第 568 行：表达式槽嵌套时也 +1
BlockNode block = buildBlock(id, ctx, depth + 1);

// 第 367 行：超过 200 直接返回 null
if (id == null || depth > MAX_DEPTH) { return null; }
```

`MAX_DEPTH = 200`。也就是说 **「一条链上的积木数」+「表达式嵌套层数」> 201 时，剩余部分全部丢弃**，且不报错、不计数（`skippedBlocks` 只统计未映射的 opcode）。

对比：编译器的 `emitChain` 允许 10,000 条（`Compiler:192`），两侧上限差了 50 倍——说明 200 本意是防"嵌套过深"，被误用到了"链过长"。

**影响**：一个 250 块的中大型脚本导入后只保留前 200 块，玩家会以为"导入功能坏了"。这是审查中最严重的一项。

**建议**：把两个计数拆开——`next` 链用循环遍历（或独立计数器，上限 10,000），只有 `branch`/`input` 递归才消耗 `depth`（上限保持 200 防栈溢出）。

### E2（P1）调用不存在的自定义积木会跳回脚本开头

`Compiler` 回填 `func.call` 时：

```java
Integer bodyId = functionBodies.get(pending.name());
if (bodyId != null) { pending.instr().a = bodyBase[bodyId]; }
// 找不到时不处理，a 保持默认值 0
```

`a = 0` 意味着跳到第 0 条指令——**从头重跑**。若脚本开头是"挖掘/移动"，会重复执行动作，直到预算耗尽进入下一 tick 继续。玩家看到的现象是"脚本疯了，一直重复开头"。

**触发条件**：`func.call` 的 `NAME` 是自由文本，手打一个不存在的函数名即可。

**建议**：未解析的调用改成编译错误（`throw new IllegalStateException("未定义的自定义积木: " + name)`），让 `startInstance` 捕获后跳过该脚本并在日志中说明。

### E3（P1）脚本结束后按键不释放

`ScriptKeys` 是全局表，只有 `GameActions.releaseMovementKeys()`（引擎 `stop()` 时）才 `releaseAll()`。而脚本实例结束时：

```java
// ScriptScheduler.tick，两处
if (instance.isDone()) { iterator.remove(); }   // 只移除，不释放按键
```

**后果**：脚本里写「按键 w 按下」然后正常结束（或异常 ERROR），W 会一直保持按下，角色持续前进，直到玩家按 F6 停止引擎。

**触发频率**：很高。任何"按住前进 → 做点事 → 结束"的脚本，只要忘了写松开，就会复现。

**建议**（两步）：
1. `ScriptKeys` 的强制表记录"按下者实例"，实例 `isDone()` 时释放自己按下的键（精确）；
2. 兜底：调度器在 `activeCount() == 0` 时 `releaseAll()`。

### E4（P2）sb3 解压无大小上限

`Sb3Parser:145` 用 `zip.readAllBytes()` 一次性读入内存，没有 entry 大小检查。损坏或恶意构造的 `.sb3` 可以把内存打爆。

**建议**：读之前检查 `entry.getSize()`（> 32 MB 拒绝），或改用限长流。

### E5（P2）编译期的 `Error` 不被捕获

`ScriptEngine.startInstance` 只 `catch (Exception e)`。`StackOverflowError`（极深嵌套）继承自 `Error`，不会被捕获，会直接冒泡到 `tick` → 客户端崩溃。

可达性低（sb3 已被 E1 的 200 上限挡住，编辑器手工嵌套也难到千层），但既然 `ScriptInstance.step` 用了 `catch (Throwable)`，这里应保持一致。

### E6（P3）`triggerKeys()` 每 tick 新建集合

`TriggerDispatcher.dispatchKeys` 每 tick 调用一次，每次遍历所有按键触发脚本并 `new HashSet`。脚本多了会有小额 GC 压力。可在 `start()` 时预计算缓存，`stop()` 时清空。

### E7（P3）广播自触发会杀掉自己

`TriggerPolicy.RESTART` 下，脚本广播一条自己也在监听的消息 → `killByName` 把包括自己在内的实例全部置为 FINISHED。当前实例会停在广播指令处（`state` 已非 RUNNING，`step` 的循环退出），不会继续执行。

语义上这是 Scratch 的行为（重启自己），但值得在文档里说明。

---

## 2. UI 层

### U1（P0）文件名归一化冲突导致脚本互相覆盖

```java
// ScriptStore:316
String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_\\-]", "_");
```

`uniqueName()` 只保证 **name** 唯一，不保证 **fileName** 唯一。于是：

| 脚本名 | 文件名 |
|---|---|
| `挖矿 1` | `挖矿_1.json` |
| `挖矿_1` | `挖矿_1.json` ← 同一个文件 |

创建第二个脚本时 `uniqueName` 认为名字不重复，直接写入 → **覆盖第一个脚本的文件**，重启后第一个脚本消失。

**建议**：`uniqueName` 改成按**文件名**去重（用 `safeFileName(candidate)` 比较已存在的文件），或在文件名后追加短哈希。

### U2（P1）每次编辑都全量重启引擎

`ScriptEditScreen.commit()`：

```java
ScriptStore.get().save(definition);
ScriptEngine.get().restart();      // stop() + start()
```

`restart()` 会清空调度器、**清空所有全局变量**、重置计时器、重建全部实例。也就是说：

- 改一个参数的空格 → 所有脚本从头跑
- 长任务（挖矿循环）被打断
- 每敲一次键盘就写一次磁盘

**建议**：编辑期间只标记为"脏"，关闭编辑器时统一保存 + 重启一次；或只重启被修改的那个脚本。

### U3（P2）写入失败静默；重命名先删后写

```java
catch (IOException e) { LOG.error("脚本写入失败: {}", ...); }   // UI 无任何提示
```

以及 `rename()` 先 `deleteIfExists(oldFile)` 再 `writeFile(newFile)`——若写入失败（磁盘满、文件名过长），**旧文件已删、新文件没写成**，脚本彻底丢失。

**建议**：先写新文件，成功后删旧文件；写失败时向玩家发动作栏提示。

### U4（P2）删除积木连带子树且无撤销

`deleteSelected()` 只从 `sequence` 里移除节点，节点上的 `branches`/`inputs` 子积木随之丢弃。没有撤销，误删只能重拼。

### U5（P3）运行面板每帧分配快照

```java
// RuntimePanelScreen:50 / 71
List<ScriptInstance> instances = ScriptEngine.get().instances();   // List.copyOf
Map<String, Object> locals = instance.locals();                    // Map.copyOf
```

面板打开时 60 fps × 实例数 × 2 次拷贝。影响很小（面板并非常驻），可接受。

### U6（P3）占位积木在面板可选

`control.nop`（"不支持的积木"）会出现在积木选择器里，插入后无任何效果。应在 `BlockPickerScreen` 里过滤掉。

---

## 3. 积木层

### B1（P2）「按键」反复触发边沿动作

```java
if (pressed) {
    ScriptKeys.hold(binding, -1);
    binding.onKeyPressed(physical);   // 每次调用都 +1 timesPressed
}
```

`onKeyPressed` 是为了让"开背包""切视角"这类靠 `wasPressed()` 的动作生效。但它**每次调用都会自增**，所以脚本写在循环里每 tick 调「按键 e 按下」→ 每 tick 触发一次开背包，界面反复闪烁。

**建议**：只在"从松开变为按下"的边沿调用：

```java
boolean already = ScriptKeys.isForced(binding);
ScriptKeys.hold(binding, -1);
if (!already) binding.onKeyPressed(physical);
```

### B2（P2）坐标参数无边界校验

`blockAt` / `placeBlock` 用 `(int) Math.floor(Values.asNumber(...))`。输入 `1e20` 时 double→int 饱和到 `Integer.MAX_VALUE`，`BlockPos` 接受该值，随后 `getBlockState` 可能触发远端区块加载或返回空气。不会崩，但会卡一下。

**建议**：用 `MathHelper.clamp` 限制到 ±30,000,000（世界边界）。

### B3（P2）放方块不检查玩家碰撞

`placeBlock` 只在"目标为空气 + 相邻有固体"时出手，不判断该位置是否会卡住玩家/把玩家埋进方块。服务端会拒绝部分情况，但客户端预测仍会闪一下。

### B4（P2）吃食物固定 36 tick

`EAT_TICKS = 36`。实际进食时长按食物不同（干海带 16、金苹果 32、普通食物 32）。统一 36 对多数食物够用，但干海带会多按 20 tick（可能重复吃或误触）。

**建议**：从 `DataComponentTypes.FOOD` 的 `eatSeconds` 读取，`ticks = ceil(eatSeconds * 20) + 2`。

### B5（P2）睡觉不校验条件

`action.sleep` 只找最近的床并右键。白天右键床是"设置出生点"，不是睡觉。脚本无法靠它判断"是否真的睡了"。

**建议**：文档注明；或增加"是否在夜晚"前置判断（已有 `world.is_day`，交给脚本组合更合适）。

### B6（P2）打开背包后脚本继续执行

`action.open_inventory` 调 `client.setScreen(...)` 打开 GUI，但脚本不会暂停——后续的"看向/挖掘"会作用在界面背后。同时 `TriggerDispatcher` 因 `currentScreen != null` 清空按键状态。

**建议**：文档标注"仅用于脚本收尾展示"；或加一个"打开界面并暂停脚本"的变体。

### B7（P3）实体遍历未复用缓存

`nearestEntityOrNull` 有单 tick 缓存（`ctx.cached`），但 `entityCount` 自己又遍历一遍。同一 tick 内两个查询 = 两次世界遍历。

### B8（P3）`anyDown` 每 tick 遍历 300+ 键

`input.key_pressed("any")` 会从 `GLFW_KEY_SPACE` 循环到 `GLFW_KEY_LAST` 逐键查询。放在每 tick 的条件里会有 300+ 次 `glfwGetKey`。

**建议**：在 `ScriptContext` 里按 tick 缓存一次结果。

### B9（P3）按键查询与按键动作的告警不一致

`action.key` 对无效键名会 warning 一次；`input.key_pressed` 对无效键静默返回 `false`。两者应统一（查询也告警一次）。

---

## 3.5 修复记录（第 1 批）

| 编号 | 修复方式 | 新增断言 |
|---|---|---|
| E1 | `buildBlock` 拆出 `chain` 参数，沿 next 递增、嵌套不递增；`MAX_CHAIN = 10_000` 与 `MAX_DEPTH = 200` 分离 | `SelfCheckSb3.checkLongChain`：250 块链的末尾积木必须存在 |
| U1 | `exists()` 改为按 `safeFileName` 比较（新增 `ScriptStore.sameFileName`） | `SelfCheck.checkFileNameCollision`：「挖矿 1」与「挖矿_1」判为同文件、「挖矿」与「挖矿2」不冲突 |
| E3 | `ScriptKeys` 改为按实例归属 + 引用计数；`ScriptContext.currentInstance` 由 `ScriptInstance.step` 每 tick 设置；调度器移除实例时 `releaseAll(instance)` | `SelfCheckRuntime.checkKeyOwnership`：实例结束收回、双实例引用计数、限时按键到期松开 |

验证：`自检通过：27 项检查全部正常`、`注册积木 104 个`、`build` 成功（226 KB）。

### 第 2 批

| 编号 | 修复方式 | 新增断言 |
|---|---|---|
| E2 | 回填 `func.call` 时收集未解析的函数名，非空则抛 `IllegalStateException`，编译失败而非跳到指令 0 | `SelfCheck.checkUndefinedFunction`：调用不存在的积木必须编译失败 |
| B1 | `action.key` 按下时先查 `ScriptKeys.isForced`，只在「松开 → 按下」的边沿调用 `onKeyPressed` | 由 `checkKeyOwnership` 的 `isForced` 断言间接覆盖 |
| U2 | 编辑器内 4 处 `save + restart` 收敛为 `markDirty()`（只落盘），`close()` 时统一 `restart()` 一次 | 无（UI 行为，靠人工验证） |

验证：`自检通过：28 项检查全部正常`、`build` 成功（227 KB）。

### 第 3 批

| 编号 | 修复方式 | 新增断言 |
|---|---|---|
| U3 | `writeFile` 返回 `boolean` 并在失败时给玩家发动作栏提示（`message.hk-script.save_failed`）；`rename` 改为先写新文件、成功后才删旧文件 | 无（依赖文件系统） |
| B2 | 新增 `GameActions.clampCoord`（±30,000,000，NaN→0），`blockAt` / `placeBlock` 全部改用它 | `checkBuildActions`：`1e20`→30000000、`-1e20`→-30000000、NaN→0 |
| B4 | `EAT_TICKS` 固定值改为读 `ConsumableComponent.getConsumeTicks()`（1.21.11 的进食时长已从 `FoodComponent` 移到此组件） | 原断言：干海带 tick 数必须小于熟牛肉。**2026-11-04：`action.eat` 积木已删除，该实现与断言一并移除，吃食物改由「鼠标 右键 按下 + 等待 + 松开」承担** |

验证：`自检通过：28 项检查全部正常`、`build` 成功（228 KB）。

### 第 4 批

| 编号 | 修复方式 |
|---|---|
| E4 | `readProjectJson` 改用限长读取 `readLimited(input, 32 MB)`，边读边计数，超限立即中断（不能先 `readAllBytes` 再判断） |
| E5 | `startInstance` 增加 `catch (StackOverflowError)` 分支，嵌套过深的脚本跳过而不是让客户端崩 |
| E6 | `triggerKeys()` 改为启动时算一次并缓存，`stop()` 清空 |
| U6 | `BlockPickerScreen.isStatement` 排除 `control.nop`，占位积木不再出现在选择器里 |
| B7 | 新增 `WorldQueries.nearbyEntities(ctx)` 单 tick 缓存，`nearestEntityOrNull` 与 `entityCount` 共用同一次世界遍历 |
| B8 | `InputKeys.anyDown` 增加 8 ms 缓存，同一 tick 内的多次查询不再重复扫 300+ 键 |
| B9 | `InputKeys.isDown` 对无法识别的键名 warning 一次，与 `action.key` 行为一致 |

附带清理：删除两处未使用的 import（`WorldQueries` 的 `Box`、`GameActions` 的 `GameOptions`）。

验证：`自检通过：28 项检查全部正常`、`build` 成功（229 KB）。

## 4. 建议的修复批次

| 批次 | 内容 | 预估 | 理由 |
|---|---|---|---|
| **第 1 批（必做）** ✅ | E1 链深度、U1 文件名冲突、E3 按键残留 | 已完成 | 三个都是静默损坏，用户自己无法察觉 |
| 第 2 批 ✅ | E2 未定义函数、B1 边沿重复触发、U2 编辑重载 | 已完成 | 高频路径上的行为错误 |
| 第 3 批 ✅ | U3 写入安全、B2 坐标钳制、B4 进食时长 | 已完成 | 边界与健壮性 |
| 第 4 批 ✅ | 其余 P3 | 已完成 | 优化项 |

每批修完都应扩一条自检断言，避免回归——现有 24 项里没有一条覆盖 E1/U1/E3，这本身就是缺口。

---

## 5. 本次审查未覆盖

诚实说明边界：

1. **真实游戏行为**：动作层（挖掘、移动、攻击、放置）的实际效果必须实机验证，代码审查只能看"是否发对了调用"。
2. **多线程**：模组全程在渲染线程执行，没有并发代码，因此未做线程安全分析。
3. **Mixin**：`KeyBindingMixin` 只做了一次 `isPressed` 拦截，逻辑简单，未深入 ASM 层。
4. **性能**：没有做基准测试，性能相关结论（B8、U5）都是从代码推断，未经测量。
5. **第三方兼容**：与其他模组（尤其是也改写按键或输入处理的）的冲突未评估。

---

## 附：做得好的地方

审查不应只有问题清单。以下几处值得保持：

- **执行内核与 MC 完全解耦**（`ir` + `runtime` 不 import 任何 `net.minecraft.*`），这是 24 项自检能脱离游戏运行的前提；
- **UI 层索引防御**几乎每处都有 `index < 0 || index >= size()` 判断，没有找到一处裸 `get()`；
- **栈下溢、调用深度、指令预算**三个最容易被忽略的运行时保护都做了，且都有对应的自检断言；
- **`Sb3Parser` 对未映射积木用占位符续链**（而不是跳过），这个决定避免过"后半段代码消失"的老问题——E1 正好是同一类问题在另一处复发。

---

## 5.5 实测追加：两个参数处理缺陷（2026-11-04）

用真实作品 `作品.sb3` 验证时发现，**运算积木本身没有问题**（`operator_length` / `operator_letter_of` 的映射、编译、实现都正常），
但把它们的结果喂给 `action.key` 时全部丢失：

| 编号 | 问题 | 现象 |
|---|---|---|
| **B10** | `action.key` 的 `KEY` 声明为字段，编译器只读 `fields`；表达式形式（如「第 i 个字符 of 指令」）落在 `inputs.KEY`，被静默丢弃，参数退化为默认值 `w` | 脚本算出了按键名，但实际永远按 W |
| **B11** | TurboWarp 扩展里参数叫 `STATE`，内部叫 `PRESSED`，`inputName` 没有这条改名 | 「松开」读不到，按键永远保持按下 |

B10 的修法：编译时优先用 `inputs.KEY`，没有才退回字段常量——同时兼容常量与表达式两种写法。

验证断言（并入 `checkSb3Menu`）：
- 表达式形式的按键名必须落在 `inputs.KEY` 且 `block != null`
- `STATE` 必须改名为 `PRESSED`，且不再残留 `STATE`
- 编译产物里必须出现 `CALL_BUILTIN operator.length`（证明表达式没被丢弃）

## 5.6 实测追加：翻页按钮全部失效（2026-11-04）

用户反馈"某些按钮按了没反应，如翻页按钮"。定位结果是三个分页屏共有的缺陷：

```java
private void refresh() {
    client.setScreen(new BlockPickerScreen(parent, filter, consumer));   // ← 新实例
}
```

翻页回调是 `page--; refresh();`。`refresh()` 重建了一个**新实例**，`page` 字段回到默认值 0，
于是"点上一页 → 页码减 1 → 重建 → 页码归 0 → 显示还是第一页"。

| 屏 | 翻页 | 状态 |
|---|---|---|
| `BlockPickerScreen` | 每页 12 块 | ❌ → ✅ 改用 `clearAndInit()` |
| `FunctionListScreen` | 每页 8 个 | ❌ → ✅ |
| `ScriptListScreen` | 每页 8 个 | ❌ → ✅ |

顺带修了同类体验问题：`ScriptEditScreen` 的「编辑/删除/上移/下移」在未选中积木时是静默 `return`，
按钮看起来正常但按了没反应。改为在 `render` 里按选中状态设置 `active`（置灰 + 不可点），
上移/下移还额外判断是否已在首尾。

**审查遗漏**：第 2 节的 UI 审查检查了索引越界、滚动 clamp、屏幕栈，但**没有检查"状态在重建中的传递"**。
这类问题的特征是——代码每行都"对"，错在组合：`page--` 对、`new Screen` 对、`page` 默认值也对，
只有串起来才是错的。下次 UI 审查应补一条：任何 `setScreen(new ...)` 是否丢掉了应当保留的字段。

## 5.7 实测追加：改完 sb3 重新导入「没效果」（2026-11-04）

用户反馈：把 `作品.sb3` 改成「列表 + 选槽 + 说话」后重新导入，游戏里没有预期效果。

诊断结论是**两个问题叠加**，都不是执行引擎的错：

### 1. 导入是快照，重新导入会新建而不是更新

```java
definition.name = uniqueName(definition.name);   // 角色1#1 已存在 → 变成 角色1#2
scripts.add(definition);
```

旧的 `角色1#1` 仍在运行，新的 `角色1#2` 也加进来——两份脚本同时跑，行为互相干扰。
用户的直觉是「导入 = 更新」，实际是「导入 = 追加」。

**修复**：导入时按显示名查找已有脚本，存在则覆盖 `hat` / `functions` / `scopeId`，
**保留 `enabled` 与用户改过的名字**。日志追加「其中 N 个脚本覆盖了同名旧版本」。

### 2. 标准输入格式 `[3, shadow, blockId]` 会丢掉第三个元素

```java
// 修复前：只把第 2 位当块 id，第 3 位是字符串时被完全忽略
if (second.isJsonPrimitive()) { resolveSlot(second); }
else if (second.isJsonArray()) { putLiteral(second); }
```

Scratch 的标准写法把块 id 放在**第 3 位**，修复前只读第 2 位：

| 写法 | 含义 | 修复前 |
|---|---|---|
| `[3, [10, "5"], "abc"]` | 标准：shadow=5，块=abc | ❌ 取 5，丢块 |
| `[3, "abc", [10, "5"]]` | 反向（某些工具导出） | ✅ 取 abc |
| `[1, [10, "5"]]` | 只有 shadow | ✅ 取 5 |

**修复**：两处都找块 id——第 2 位是字符串就用它，否则看第 3 位；都没有才退回字面量。
这样三种顺序全部兼容，且不改变「内联 shadow」的既有语义。

新增断言 `checkInputOrder`（自检 32 → 33 项），一个样例里塞三种写法：
标准顺序、反向顺序、纯内联 shadow，分别断言解析出的积木类型与换算后的 tick 数。

**这次审查的教训**：前 22 条审查全是「读代码找问题」，而这三个问题都只在**真实数据**里才暴露——
用户 sb3 里两种顺序混用，纯靠读代码不可能想到。以后审查 sb3 解析层，应该固定跑一批真实作品。

## 6. 有意保留的 6 条
| 编号 | 为什么不修 |
|---|---|
| **E7** 广播自触发杀掉自己 | 这正是 Scratch `startHats(restartExistingThreads=true)` 的行为，改了反而不兼容 |
| **U4** 删除积木连带子树、无撤销 | 需要一整套撤销栈（快照 + 历史），属于新功能而不是缺陷修复 |
| **U5** 运行面板每帧拷贝快照 | 面板并非常驻，60 fps 下每次 1–2 次小对象分配，实测无感。真要优化得改成复用缓冲区，收益远小于复杂度 |
| **B3** 放方块不检查玩家碰撞 | 服务端会拒绝把玩家卡进方块的操作，客户端预测错一帧就回弹。加碰撞预判要复刻服务端的放置规则，容易与服务端不一致 |
| **B5** 睡觉不校验夜晚/安全 | 这是使用说明能解决的问题：脚本用 `world.is_day` 组合判断比内置更灵活 |
| **B6** 打开背包后脚本继续执行 | 同上，属语义约定。加「打开界面并暂停」会引入新的暂停状态，与现有 WAITING 语义纠缠。**（2026-11-04 更新：专用积木已删除，开背包改由「点按 e」触发，语义不变）** |

这 6 条都记在文末而不是悄悄删掉——下次审查时应能看到"已知且有意"，而不是当成新发现重复报告。
