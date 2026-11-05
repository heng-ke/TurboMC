# 新引擎适配全量审计

- **日期**：2026-11-05
- **范围**：`src/client/java`（56 个 Java 文件）+ 配置 + 语言文件
- **方法**：类级外部引用统计 → 方法级调用统计 → 扩展 opcode 与动作注册的可达性交叉计算 → 人工核对残留概念
- **结论**：**修掉 2 处功能性断裂，删除 3 个类与 5 个方法；仍有 24 个动作注册不可达（不影响运行，但属死代码）**

---

## 1. 摘要

| 类别 | 数量 | 明细 |
|---|---|---|
| **功能性断裂（已修）** | 2 | 按键触发全失效、两处死配置 |
| **死代码（已删）** | 3 类 + 5 方法 | `TriggerPolicy`、`GlobalVariables`、`TargetVariables` 等 |
| **死代码（仍存在，无害）** | 24 个注册 + 2 个类 | 见 §4 |
| **语义差异（已知）** | 2 | 无积木级调试、无变量声明表 |
| 模板残留 | 2 个 mixin | 与引擎无关 |

---

## 2. 功能性断裂（本轮修复）

### 2.1 按键触发完全失效 ⚠️

`TriggerDispatcher` 仍在调用旧门面：

```java
ScriptEngine.get().fire("event.key", hat -> key.equals(hatText(hat, "KEY")));
```

而重写后的 `ScriptEngine.fire` 只认 4 个事件：

```java
String opcode = switch (trigger) {
    case "event.chat" -> "hkscript_whenChat";
    case "event.damage" -> "hkscript_whenDamaged";
    case "event.death" -> "hkscript_whenDeath";
    case "event.tick", "event.start" -> "event_whenflagclicked";
    default -> null;          // ← event.key 落在这里
};
if (opcode != null) { ... }   // ← 静默跳过
```

后果：**编辑器里所有「当按下 X 键」脚本都不会触发，且不报错**。

讽刺的是，我上一轮专门实现了精确匹配的 `fireKey(key)`（走 `startHats` 的 `KEY_OPTION` 字段匹配），却**没有任何调用方**——统计里 `fireKey -> 外部调用 0 处`。写了没接。

修复：`TriggerDispatcher.dispatchKeys` 改调 `fireKey(key)`，删掉基于 `hatText` 的谓词。

### 2.2 两处死配置

| 配置 | 状态 | 处置 |
|---|---|---|
| `scratchVmEnabled` | 定义后**无人读取**（原使用方 `VmController` 已删） | 删除 |
| `scratchLoopThrottle` | 旧编译器的循环节流，编译器已删；UI 上还挂着一个开关 | 删除字段与配置界面项 |
| `scratchVmBudgetMs` | 在用（`ScriptEngine.tick` 传入 `setBudgetMs`） | 保留 |

---

## 3. 已删除的死代码

| 目标 | 依据 |
|---|---|
| `runtime/TriggerPolicy` | 外部引用 0（旧引擎的线程重启策略，现由 VM 决定） |
| `runtime/GlobalVariables` | 仅剩 `clear()` 调用，无任何读写（变量已由 VM 管理） |
| `runtime/TargetVariables` | 同上 |
| `ScriptContext.setCurrentInstance` | 外部调用 0（旧 `ScriptInstance` 曾负责设置） |
| `ScriptContext.requestStopAll` / `stopAllRequested` | 外部调用 0（「停止全部」现由 VM 处理） |
| `ScriptEngine.hatText` | 唯一调用方是上面的按键路径，已改 |
| `ScriptEngine.activeCount` / `stopAll` | 外部调用 0 |

清理后四条冒烟仍全绿。

---

## 4. 仍存在但无害的死代码

### 4.1 动作层 24 个不可达注册

交叉计算：扩展声明 53 个积木 → 全部能映射到动作层（无遗漏）；`NativeRegistry` 共注册 73 项，其中**49 项可达**（扩展积木经桥调用），**24 项不可达**。

不可达的 24 项，对应积木都由 VM 原生执行，Java 实现不会再被调用：

```
list.add / list.clear / list.contains / list.contents / list.delete / list.insert
list.item / list.item_num / list.length / list.replace          （列表 9 项）
operator.contains / join / length / letter_of / mathop
operator.random / round                                          （运算 7 项）
sensing.current / days_since_2000 / reset_timer / timer          （感知 4 项）
debug.say / input.key_pressed / action.broadcast                 （其余 3 项）
```

连带结果：`MathFunctions`、`ListFunctions`、`SensingActions` 三个类的注册逻辑实际已成空转（`ListFunctions.clearAll()` 仍在启动时被调用）。

**建议**：删除这三类及其 `registerAll` 调用，可再减约 400 行。风险低——但需确认没有历史 sb3 依赖这些内部 id（`Sb3Mapping` 里已无 `hkscript_*` 指向它们）。

**暂不删的理由**：它们不影响运行，删除属于净收益但需要一次完整回归；留待与真机验证一起做更稳妥。

### 4.2 `ScriptContext.currentInstance` 恒为 null

`GameActions` 有 5 处 `ctx.currentInstance()` 用于按键归属，但**没有任何地方设置它**（原设置方 `ScriptInstance` 已删）。

- 不崩：`ScriptKeys.hold/release` 对 null owner 做了空判断；
- 但语义退化：所有按键都挂在同一个 null 所有者上，两个脚本同时按住同一个键时，一个脚本松手会连带释放另一个的引用。

**建议**：把 VM 的线程标识传下来，作为按键归属的 owner。

### 4.3 其他

| 项 | 说明 |
|---|---|
| `GameActions.broadcast` | 调 `ScriptEngine.broadcast`（空实现）。该积木已由 VM 原生处理，不会走到这里 |
| `ExampleClientMixin` / `ExampleMixin` | 模版残留，注册在 mixin json 里；与引擎无关，删除需同步改 json |
| i18n `config.hk-script.loop_throttle*` | 配置项已删，文案残留，无害 |
| `VmSmokeTest` / `WriteSmokeTest` | 测试入口，已从产物排除 |

---

## 5. 语义差异（架构决定，非缺陷）

| 项 | 影响 |
|---|---|
| `runningBlocks` 恒空 | 运行积木高亮、打开编辑器自动滚到运行行失效 |
| 无变量声明表 | 变量按积木里出现的名字隐式收集，初值恒 0，无管理界面 |

---

## 6. 建议的后续动作

| 优先级 | 动作 | 预估 |
|---|---|---|
| 高 | 真机验证（按键修复后尤其需要实测） | — |
| 中 | 删除 §4.1 的 24 个不可达注册与三个类 | 约 1 小时 |
| 中 | `currentInstance` 接入 VM 线程标识，修正按键归属 | 约半天 |
| 低 | 变量声明表面板 | 1–2 天 |
| 低 | 清理模板 mixin 与残留文案 | 30 分钟 |

---

## 7. 当前验证状态

| 冒烟 | 结果 |
|---|---|
| `vmWrite`（五场景） | ✅ |
| `vmSmoke`（顺序） | ✅ |
| `vmSmoke -PvmMode=hat` | ✅ |
| `vmSmoke -PvmMode=block` | ✅ |

产物：`build/libs/hk-script-1.0.0.jar` 2.14 MB。
