# 缺口处理记录（阶段 3 收尾）

- **日期**：2026-11-05
- **输入**：`STAGE3-PLAN-A.md` §5 列出的 6 个已知缺口
- **结果**：4 项已修并验证、1 项不存在（原判断有误）、1 项架构决定不可修

---

## 1. 缺口核对结果

| # | 原缺口 | 结论 | 证据 |
|---|---|---|---|
| 1 | 变量初始值不透传 | **不存在**（原判断有误） | 编辑器从未提供「变量初值」设置，旧引擎每次启动也 `GlobalVariables.clear()` 归 0。ProjectWriter 写 0 与旧行为一致 |
| 2 | 自定义积木参数未序列化 | ✅ 已修 | 场景 3：调用传 5，函数体写入 `score=5` |
| 3 | 聊天关键词未过滤 | ✅ 已修 | 场景 2：`bye bye` 触发 0，`say hello world` 触发 1 |
| 4 | 无积木级调试能力 | ❌ 不可修（架构决定） | VM 以字节码线程执行，不暴露积木级定位 |
| 5 | 测试类混在产物中 | ✅ 已修 | jar 内 `SmokeTest` 计数为 0 |
| 6 | 部分积木无 sb3 表达 | ✅ 已修至 2 个 | 见 §3 |

---

## 2. 三个真实缺陷的根因与修复

### 2.1 自定义积木（缺口 2）

三处独立问题叠加，缺一不可：

1. **函数体丢失**：`emitFunction` 把函数体写进临时缓冲 `ctx.blocks`，但没搬进目标字典——生成的工程里根本没有函数体块，VM 找不到可执行内容，静默不报错。
2. **原型缺输入槽**：`procedures_prototype.inputs` 为空时，VM 绑定实参找不到位置，`getProcedureParamNamesIdsAndDefaults` 返回后调用直接 return。
3. **参数读取用错积木**：编辑器把函数参数表达为 `data.get($p1, local)`，而 sb3 里必须用 `argument_reporter_string_number`。已加特判：`$pN` → `VALUE=[pN, null]`，与 mutation 的 `argumentnames` 对齐。

另补：`procedures_call` 现在携带 mutation（proccode + argumentids），参数槽由 `P1..Pn` 改名为 `arg0..arg(n-1)`；参数个数由函数体里实际用到的 `$pN` 反推（编辑器不存参数表）。

### 2.2 聊天过滤（缺口 3）

`runtime.startHats` 的字段匹配读 `hatFields[matchField].value`，而扩展把关键词声明为 input，传 matchFields 会抛 `Cannot read property "value" from undefined`。

改为自建入口 `__hkStartHatFiltered(opcode, inputName, matchText)`：遍历顶层块 → 读关键词（fields 与 inputs 两种形态都兼容）→ 未命中则跳过 → 命中才 `runtime._pushThread`。

**过程中发现更隐蔽的问题**：扩展的 HAT 默认 `edgeActivated: true`，VM 每帧自动 `startHats`，**完全绕过上面的过滤**。表现为「初始值 0，跑 5 帧后变 1」。已在扩展的 3 个 HAT 上显式声明 `isEdgeActivated: false`。

### 2.3 反向映射歧义（连带发现）

`Sb3Mapping.opcodeOf` 用 `Map.ofEntries` 的迭代顺序取首个，而该顺序不保证——实测 `data.set` 被反查成 `data_changevariableby`。已加 `PREFERRED` 表钉死 3 个歧义类型：

| 类型 | 多个 opcode | 固定选择 |
|---|---|---|
| `data.set` | `data_setvariableto` / `data_changevariableby` | `data_setvariableto` |
| `debug.say` | `looks_say` / `looks_think` | `looks_say` |
| `debug.say_for` | `looks_sayforsecs` / `looks_thinkforsecs` | `looks_sayforsecs` |

### 2.4 缺口 6 的实际范围

初版报告说「约 8 个积木无表达」，实测是 **5 个**，其中 3 个已补齐：

| 积木 | 处理 |
|---|---|
| `list.contents` | 补映射 `data_listcontents` |
| `world.is_mining` | 补映射 + 扩展积木 `isMining` |
| `world.mining_progress` | 补映射 + 扩展积木 `miningProgress` |
| `action.break_wait` | **仍缺**：语义是「等挖破」，需要条件型阻塞，暂缓 |
| `control.spawn` | **仍缺**：sb3 无「派生线程」概念，并发应改用多个帽子 |

当前覆盖率：**104 / 106**。

---

## 3. 验证

`./gradlew vmWrite` 现在覆盖三个场景：

```
[write] 场景1 基本结构: score=1 桥调用=3 -> OK
[write] 场景2 聊天过滤: 不匹配=0 匹配=1 -> OK
[write] 场景3 函数参数: score=5 -> OK
WRITE SMOKE PASS
```

全套回归：

| 冒烟 | 结果 |
|---|---|
| `vmWrite`（编辑器树 → JSON → VM，三场景） | ✅ |
| `vmSmoke`（顺序执行） | ✅ |
| `vmSmoke -PvmMode=hat`（事件触发） | ✅ |
| `vmSmoke -PvmMode=block`（阻塞动作） | ✅ |

产物：`build/libs/hk-script-1.0.0.jar` **2.14 MB**，`SmokeTest` 类已排除。

---

## 4. 收尾两项（本轮追加处理）

### 4.1 `action.break_wait`：条件型等待

「等待方块被挖破」没有固定时长，原有机制（`requestWait(n)`）表达不了。做法是把等待从「帧数」扩展为「条件」：

```
动作 ctx.requestWaitUntil("break", 200)
  → ScriptBridge 暂存检查键
  → JS 侧 __hkWaitUntil 返回 Promise，线程挂起
  → 每帧 __hkPumpPromises 调 ScriptBridge.poll("break")
  → WorldQueries.probeBreak 判定：BROKEN / GIVE_UP 即放行，WAITING 继续
```

三个设计要点：

1. **放弃即放行**：`GIVE_UP`（没进世界、准星没对上方块）也结束等待，否则脚本会永久挂住。
2. **自动重置**：用 `breakDone` 标志在下一轮等待开始时清空锁定的目标方块，不需要动作层显式初始化。
3. **上限 200 帧**（约 10 秒）兜底。

验证：场景 4 在无客户端环境下 `probeBreak` 立即返回 `GIVE_UP`，脚本在 20 帧内继续执行完 `score=1`——证明不会卡死。

顺带修掉一个会波及所有桥调用的隐患：`ClassShutter` 只放行了桥所在的包，导致 JS 传字符串参数时 Rhino 包装 `java.lang.String` 被拦下，报 `Access to Java class "java.lang.String" is prohibited`。已补 6 个装箱类型的精确白名单（不放开整个 `java.lang`，避免 `Runtime` 之类被触达）。

### 4.2 `control.spawn`：从面板移除

Scratch 没有「派生线程」概念，并发应改用多个帽子积木。处理方式：

- 积木选择面板过滤掉 `control.spawn`（`ScriptEditScreen.isUnsupported`），不再能新增；
- 定义保留在 `BlockRegistry`，旧脚本仍可打开——编辑器对未知积木有兜底渲染；
- `ProjectWriter` 遇到它仍会记入 `unsupported` 并跳过。

---

## 5. 最终状态

| 冒烟 | 结果 |
|---|---|
| `vmWrite`（四场景：基本 / 聊天过滤 / 函数参数 / 条件等待） | ✅ |
| `vmSmoke`（顺序执行） | ✅ |
| `vmSmoke -PvmMode=hat`（事件触发） | ✅ |
| `vmSmoke -PvmMode=block`（定时阻塞） | ✅ |

产物：`build/libs/hk-script-1.0.0.jar` **2.14 MB**，`SmokeTest` 类已排除。

积木覆盖率：**105 / 106**（仅 `control.spawn` 无 sb3 表达，已从面板隐藏）。

---

## 6. 剩余事项

| # | 事项 | 说明 |
|---|---|---|
| 1 | 真机验证 | 见 `STAGE3-PLAN-A.md` §6，尚未在真实客户端跑过 |
| 2 | 无积木级调试 | 架构决定，接受 |
| 3 | 多脚本共享 `breakState` | 两个脚本同时「等待挖破」会互相干扰；单脚本场景无影响 |

