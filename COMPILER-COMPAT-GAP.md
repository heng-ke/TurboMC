# 编译器与 TurboWarp 原生体验的差异分析

> 触发点：实测 `作品.sb3` 时发现"运算积木像没实现"，追查后确认运算积木本身正常，
> 真正的问题是**参数落位模型与 Scratch 不一致**。
> 分析日期：2026-11-04

---

## 0. 一句话结论

我们用一个布尔值 `BlockParam.field` 表达了两件事——"参数默认值存在哪"和"参数能否被积木替换"。
Scratch 里这是两个独立概念（**shadow** 与 **input**）。这个压缩导致：**凡是"我们声明为字段、但 TurboWarp 允许接表达式"的参数，表达式会被静默丢弃。**

当前受影响的只有 2 个参数（1 个已修、1 个待修），但**模型缺陷是系统性的**——每新增一个可接表达式的文本参数，就会再中一次。

---

## 1. 根源：两个模型的结构差异

### Scratch / TurboWarp 的模型

```
每个 input = { shadow（默认值）, block（可选，遮蔽 shadow） }
field     = 独立概念，只能是下拉/常量，不能接积木
```

- `operator_join` 的 `STRING1`：input，shadow 是空串，可以放任何 reporter
- `sensing_current` 的 `CURRENTMENU`：field，只能选菜单项
- **扩展积木的 `Scratch.ArgumentType.STRING` 参数**：是 input，天然可以接表达式

### 我们的模型

```java
record BlockParam(String name, boolean field, ParamKind kind, String defaultValue, List<String> options)
```

- `field = true` → 值存在 `node.fields`，编译期 `fieldOr()` 读
- `field = false` → 值存在 `node.inputs`，编译期 `pushInput()` 读

**一个参数只能属于一边**，且这个归属由积木定义决定，而不是由 sb3 里实际是什么决定。

### 错位怎么发生的

`Sb3Parser.putSlot`：

```java
// 非块的值 + 内部声明为字段 → 落 fields
if (!slot.hasBlock() && isFieldParam(node.type, name)) {
    node.fields.put(name, slot.literal);
    return;
}
node.inputs.put(name, slot);   // 是块 → 落 inputs
```

编译期：

```java
add(new Instr(Op.PUSH_CONST, node.fieldOr("KEY", "w")));   // 只读 fields
```

于是：

| sb3 里的实际内容 | 落位 | 编译期读 | 结果 |
|---|---|---|---|
| 字面量 `"w"` | `fields.KEY` | `fields` | ✅ 正常 |
| 表达式（`第 i 个字符`） | `inputs.KEY` | `fields` | ❌ **读到默认值，表达式丢失** |

**两处代码共享了同一个错误假设**："声明为字段的参数不会接表达式"。对原生 Scratch 积木这成立（真字段都不允许接块），对**扩展积木的文本参数不成立**。

---

## 2. 缺陷清单

### C1（严重）字段型参数遇到表达式就丢值

已确认的受影响参数：

| 积木 | 参数 | sb3 来源 | 状态 |
|---|---|---|---|
| `action.key` | `KEY` | 扩展 `STRING`（可接块） | ✅ 已修（编译优先读 inputs） |
| `event.chat` | `MATCH` | 扩展 `STRING`（可接块） | ❌ **未修** |
| `input.key_pressed` | `KEY` | 原生 `sensing_keypressed`（真 field） | ✅ 安全 |
| `event.key` | `KEY` | 原生 `KEY_OPTION`（真 field） | ✅ 安全 |
| `data.get/set` | `VAR` | 原生变量下拉（真 field） | ✅ 安全 |
| `sensing.current` | `UNIT` | 原生 `CURRENTMENU`（真 field） | ✅ 安全 |
| `operator.mathop` | `OP` | 原生 `OPERATOR`（真 field） | ✅ 安全 |
| `func.call` | `NAME`/`ARGC` | 编辑器内部生成 | ✅ 安全 |

**`event.chat` 的具体后果**：`当聊天包含 [MATCH]` 的 `MATCH` 若在 TurboWarp 里接了表达式（比如一个变量），导入后 `MATCH` 为空 → `isBlank()` 被视为"匹配任意聊天" → **任何一句聊天都会触发脚本**。

这是比 `action.key` 更隐蔽的失效：不是没反应，而是**乱触发**。

### C2（中）编辑器不允许把字段参数换成表达式

`ParamEditScreen`：

```java
if (!row.field()) { ... }   // 只有输入型参数才提供"选择表达式"的入口
```

即使编译期修好了（C1），**用户在游戏内也无法构造** `按键 (第 i 个字符) 按下` 这种结构——只能在 TurboWarp 里做。

这造成一种割裂：导入能跑，但打开编辑器改不动，改一次就退化成常量。

### C3（中）循环执行节奏不同

| | Scratch | 我们的默认值 |
|---|---|---|
| `重复执行` 每轮之间 | 让出一帧 | 同一 tick 内连续跑，直到预算耗尽 |

配置项 `scratchLoopThrottle` 可以对齐，但**默认关闭**（产品取向：自动化效率优先）。

后果：同样一个"重复 10 次、每次挖一下"的脚本，Scratch 要 10 帧，我们在 1 帧内发 10 次挖掘——服务端可能只认最后一次。

### C4（低）未支持积木用占位符代替

映射不到 `operator_*` / `motion_*` 等积木时插入 `control.nop`。

- 好处：不截断后续积木链（曾修过"后半段消失"的问题）
- 代价：编辑器里出现"（不支持的积木）"，用户不知道原本是什么

### C5（低）除零返回 0

Scratch 返回 `Infinity`。我们返回 0（避免 NaN 污染后续计算）。这是**有意偏差**，但对"用 `1/0` 探测"这类写法不兼容。

### C6（低）编辑器是点选式，不是拖拽式

没有拖拽、没有撤销、没有右键菜单。功能上等价，体验上差距明显。属于 UI 工作量，不是缺陷。

---

## 3. 解决方案

### 方案 A：统一取参助手（1 小时，治标）

新增一个助手，所有"可能接表达式"的参数都走它：

```java
/** 参数在 inputs 就用表达式，否则退回字段常量。 */
private void pushParam(BlockNode node, String name, String fallback) {
    if (node.inputs.containsKey(name)) {
        pushInput(node, name, fallback);
    } else {
        add(new Instr(Op.PUSH_CONST, node.fieldOr(name, fallback)));
    }
}
```

然后 `event.chat` 的 `MATCH` 也改成 `pushParam`（`action.key` 已用同样写法）。

- 优点：改动极小，立刻消除静默丢值
- 缺点：治标。新积木如果忘了用 `pushParam`，还会中招

### 方案 B：BlockParam 增加 `acceptsReporter`（半天，治本）✅ 已落地

```java
record BlockParam(String name, boolean field, ParamKind kind,
                  String defaultValue, List<String> options,
                  boolean acceptsReporter)
```

语义与 Scratch 的 `acceptReporters` 对齐：

| `acceptsReporter` | 解析期 | 编译期 | 编辑器 |
|---|---|---|---|
| false（真字段） | 一律落 `fields` | `fieldOr` | 只能改文本/选菜单 |
| true（可替换） | 字面量落 `fields`、表达式落 `inputs` | `pushParam` | **提供"切换为表达式"按钮** |

改动面：`Blocks` 的 14 个字段声明、`Compiler` 的 9 处读取、`Sb3Parser.putSlot`、`ParamEditScreen`。

- 优点：概念与上游一致；新增积木时由类型系统兜住，不靠记性
- 缺点：要动 4 个文件

**落地情况（2026-11-04）**

| 层 | 改动 |
|---|---|
| `BlockParam` | 新增第 6 个组件 `acceptsReporter`；新增工厂 `fieldOrExpr(name, default)`（字段 + 允许表达式） |
| `Blocks` | `action.key.KEY`、`event.chat.MATCH` 改用 `fieldOrExpr`；其余 12 个字段保持 `fieldText`/`fieldOption` |
| `Sb3Parser` | `isFieldParam` → `paramOf`；`putSlot` 三态判断：字面量落 fields、可替换的积木落 inputs、纯字段收到积木则**记入 `droppedExpressions`** |
| `ParseResult` | 新增 `droppedExpressions`，`summary()` 输出"丢弃 N 处表达式参数（该位置只接受常量：…）" |
| `Compiler` | 新增 `pushParam`（inputs 优先），`action.key` 已改用它 |
| `ParamEditScreen` | `ParamRow` 带 `acceptsReporter`；`fx` 按钮对这类字段也开放，可在游戏内切换为积木 |

**断言** `checkParamSpecs`：`action.key.KEY` / `event.chat.MATCH` 必须为 `true`，
`data.set.VAR` / `sensing.current.UNIT` / `operator.mathop.OP` 必须为 `false`。

这条断言当场抓到一个漏改——`event.chat` 的替换串多写了一个逗号，`sed` 静默没匹配，自检第一次启动就报错。
如果只靠肉眼审查，这个漏改会一直潜伏到下次导入。

### 方案 C：inputs 统一为 `Slot{shadow, block}`（2–3 天，彻底）

把 `BlockNode.inputs` 从 `Map<String, Slot>`（block 或 literal 二选一）改成 Scratch 的结构：每个 input 都带 shadow，block 可选遮蔽。

- 优点：与 sb3 一一对应，解析/序列化不再有"落位"判断，反向导出也更容易
- 缺点：IR、编辑器、编译期全改，风险高

**只有在"要做反向导出"或"要完整支持 Scratch 项目"时才值得做。**

---

## 4. 推荐路径

| 阶段 | 动作 | 工作量 | 收益 |
|---|---|---|---|
| **立即** | 方案 A：`event.chat` 改 `pushParam`，并补断言 | 1 小时 | 消除最后一个静默丢值点 |
| **下一迭代** | 方案 B：`acceptsReporter` + 编辑器"切换为表达式" | 半天 | 从"靠记性"变成"靠类型"，并补上 C2 |
| 视需求 | 方案 C | 2–3 天 | 与 Scratch 结构完全对齐 |

**不建议**跳过 A 直接做 B——A 是 1 小时的保险，B 改造期间代码可能长期处于中间状态。

---

## 5. 立即可做的补丁清单

| # | 内容 | 状态 |
|---|---|---|
| 1 | `event.chat` 的 `MATCH` 改用 `hatText`（字段 → 字面量输入 → 表达式返回 null） | ✅ 已完成 |
| 2 | `event.key` 的 `KEY` 同样改走 `hatText`，防止扩展侧将来允许表达式 | ✅ 已完成 |
| 3 | 断言 `SelfCheck.checkHatParams`：字段优先、字面量回退、表达式返回 null 而非空串 | ✅ 已加入（自检 29 项） |
| 4 | `Blocks` 的 `fieldText` 声明标注"是否可能接表达式" | 待办（为方案 B 铺路） |
| 5 | 把"参数落位"归为专题写进 `TURBOWARP-COMPAT-REPORT.md` | 待办 |

### 已落地的具体实现

```java
// ScriptEngine.hatText：HAT 参数的唯一读取入口
public String hatText(BlockNode hat, String name) {
    String field = hat.fields.get(name);
    if (field != null) return field;          // 真字段
    Slot slot = hat.inputs.get(name);
    if (slot == null) return "";
    if (slot.hasBlock()) {
        warnOnce(...);                        // 表达式无法在触发时求值
        return null;                          // 交给调用方按「不匹配」处理
    }
    return slot.literal == null ? "" : slot.literal;
}
```

关键决定：**表达式返回 `null` 而不是空串**。空串在聊天触发里会被 `isBlank()` 判为"匹配任意"，
那比"不触发"危险得多——用户会看到脚本无故乱跑。

`event.chat` 的旧写法正是这样：

```java
String match = hat.fieldOr("MATCH", "");   // 表达式 → ""
return match.isBlank() || message.contains(match);   // → 永远 true
```

---

## 6. 为什么会连续犯两次

同一类错误在 24 小时内出现两次：

| 时间 | 积木 | 错法 |
|---|---|---|
| R4c | `control.broadcast_wait` | 输入参数被当字段读（反向） |
| 今天 | `action.key` | 字段参数被当唯一来源读（正向） |

表面是"读错位置"，根子是：**参数位置是隐式约定，编译器和解析器各自硬编码，没有单一事实来源。**

方案 B 的价值就在这里——把"这个参数能不能接表达式"变成**数据**而不是**约定**，编译器和解析器都从同一处读取。
