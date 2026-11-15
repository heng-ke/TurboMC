# 「走向」会话被抢占：事实记录（未决策）

> 状态：**事实已核对，未做任何代码改动，结论待讨论**
> 记录日期：2026-11-08
> 适用：Minecraft 1.21.11 + Fabric Loader 0.20.1，yarn `1.21.11+build.6`，仅客户端
> 性质：本文只记录「当前实现是什么」，不含决定。§7 的评估意见供讨论，不构成方案批准。

---

## 一、被报告的问题

原始描述（Issue #2 及后续追问）分三段：

1. 脚本 B 开始「走向」会把脚本 A 的移动会话置成 `CANCELLED`；
2. `poll("move")` 把 `CANCELLED` 也当成「已完成」，于是 A 提前放行；
3. A 以为自己到了，后续的点击 / 挖掘全部错位。

衍生追问：

- 玩家只有一个，什么场景会有两条走向？
- 若走向会被抢占，A 是否在抢占后立刻执行下一块积木？

---

## 二、核对结论（逐条）

| 论断 | 结论 | 依据 |
|---|---|---|
| B 起「走向」会把 A 的会话置成 CANCELLED | **成立**（同目标除外） | `PathFollower.java:146` 同目标复用；`:149` → `closeSession()` |
| A 因这次 CANCELLED 立即放行 | **不成立**：该 `lastState` 的存活窗口为 0 帧 | `:581-582` 写 CANCELLED，同调用内 `:153` 覆盖为 FOLLOWING |
| `poll("move")` 把 CANCELLED 当已完成 | **成立** | `:163-165` `finished()` 只排除 IDLE/FOLLOWING |
| A 会被提前放行 | **成立**，但放行者是「B 的终态」，不是「B 的 start」 | 同上 |
| 放行后点击/挖掘错位 | **成立**，且无任何位置校验 | `vm-bootstrap.js:126-131` 等待只认 poll 布尔值 |
| 玩家只有一个 → 不会有两条走向 | **不成立**：并发单位是 VM 线程 | `vm.es5.js` `stepThreads()` 遍历 `runtime.threads`，`STATUS_PROMISE_WAIT` 仅跳过该条 |

---

## 三、当前行为的准确时序

### 3.1 帧内顺序（决定了 A 观测不到 CANCELLED）

```
帧 N
  __tmcStep()                              vm-bootstrap.js:474-478
    __tmcFlushAnswer()
    __tmcPumpPromises()                    ← 所有等待项在这一步检查
        A: poll("move") → finished()=false（A 自己在走）
    runtime._step()
        线程 A 已 PROMISE_WAIT，跳过
        线程 B 执行「走向 Y」
            PathFollower.start → closeSession(A) → lastState=CANCELLED
                               → lastState=FOLLOWING（同调用内覆盖）   PathFollower.java:149-153
  ScriptEngine.tick 末尾                    ScriptEngine.java:141
    PathFollower.tick()                    ← 推进 B 的会话

帧 N+1
  __tmcPumpPromises()
        A: poll("move") → lastState 已是 FOLLOWING → 继续挂起
```

关键点：`closeSession` 设的 `CANCELLED`（`:581-582`）与 `start` 覆盖回 `FOLLOWING`（`:153`）之间没有任何让出线程的调用，而 A 的观测点在帧首、B 的执行点在帧中。**A 永远看不到那次 CANCELLED。**

### 3.2 A 实际被放行的三种情形

| 触发放行者 | 时机 | 代码 |
|---|---|---|
| B 到达 / 放弃 / 超时 | B 终态后的下一帧 | `:560` `lastState = state` |
| 引擎中止（离开世界、停止全部、切工程、断开） | 下一帧 | `:176`、`:250`、`:261`、`ScriptEngine.java:111/133/261` |
| A 自己的等待上限到期（1240 tick ≈ 62 s） | 到点无条件放行 | `PathFollower.MAX_TICKS+WAIT_SLACK` → `vm-bootstrap.js:103` |

第三条常被忽略：**即使没有任何抢占，走向失败后的放行也是静默的**，因为 `poll` 只有布尔值。

---

## 四、涉及代码清单

| 位置 | 内容 |
|---|---|
| `script/action/path/PathFollower.java:108` | `private static Session session` —— 全局唯一会话 |
| `:110` | `private static State lastState` —— 全局唯一终态 |
| `:126-155` | `start()`：早退 / 同目标复用 / `closeSession` / 建新会话 |
| `:137-143` | 已在目标范围内：仅当 `session == null` 才改 lastState |
| `:163-165` | `finished()` = 非 IDLE 且非 FOLLOWING（ARRIVED/GAVE_UP/TIMEOUT/CANCELLED 全为 true） |
| `:550-573` | `finish()`：写 `lastState`、松键、日志 |
| `:575-588` | `closeSession()`：旧会话标 CANCELLED、松键、`session = null` |
| `script/action/path/PathActions.java:52-55` | 起会话后 `requestWaitUntil("move", waitLimitTicks())` |
| `script/vm/ScriptBridge.java:269-285` | `poll()`：`"move"` → `PathFollower.finished()`（注释明写「失败或放弃都算结束」） |
| `script/runtime/ScriptContext.java:24-27` | `currentInstance` 恒 `null`（作者自认无法区分脚本实例） |
| `script/ScriptEngine.java:126,141` | `vm.tick()` 在前、`PathFollower.tick()` 在后 |
| `assets/turbomc/vm/vm-bootstrap.js:83` | `__tmcPending` —— 全 VM 共享的等待数组 |
| `:97-120` | `__tmcPumpPromises()`：`poll` 返回 `=== true` 才 resolve；`until` 到期也 resolve |
| `:126-131` | `__tmcWaitUntil(checkKey, maxTicks)` |
| `:173` | 积木方法覆盖为 `function (args)` —— **util（含 thread）被丢弃** |
| `:474-478` | `__tmcStep()` 三步顺序 |
| `vm.es5.js` | `stepThreads()` 遍历全部线程；`stepThread()` 对 `STATUS_PROMISE_WAIT` 直接 return |
| `vm.es5.js` | `Thread.prototype.stopThisScript()` 存在（popStack 至 procedures_call 边界并置 DONE） |

---

## 五、并发前提（为什么单玩家也能有两条走向）

`stepThreads()` 的主循环逐条推进 `runtime.threads`，处于 `PROMISE_WAIT` 的线程只被跳过，不影响其他线程。因此「A 在等位移」不等于「引擎停了」。

能同时存在的两条移动线程，常见来源：

1. 绿旗主循环（`重复执行 { 走向 …; 挖掘 }`）+「当聊天包含」帽子里的走向；
2. 主循环里的走向 +「当受伤 / 当死亡」帽子里的逃生走向；
3. 同一角色的多个「当绿旗被点击」帽子（`greenFlag()` → `startHats` 不查重）；
4. 多个角色（sprite）各带脚本 —— `Sb3Importer.java:85-103` 遍历全部 targets；
5. 「当按下键」帽子（edge-activated，每次触发压一条新线程）。

**不会**发生的只有一种：单条线程内顺序写「走向 A → 走向 B」——走向阻塞，第二块在第一块放行后才执行。

另注：引擎同时只运行一个工程（`ScriptEngine` 的包级互斥），所以「脚本 A / B」只可能是同一工程内的两条线程，不可能是两个脚本包。

---

## 六、这条缺陷实际破坏了什么

| 被破坏的能力 | 说明 |
|---|---|
| 「走向 X 之后做 Y」的顺序保证 | 走向被抢占后脚本仍继续，「位置正确」这一前提消失；而点击/挖掘/放置都建立在它之上 |
| 多线程脚本的独立性 | 移动只有一份，多条线程退化为「最后一条生效」 |
| 失败可感知 | 四种终态折叠成一个 `true`，脚本无法区分"到了"与"没走到" |
| 可测试性 | 结果依赖 sequencer 推进顺序，同一工程两次运行可能不同 |
| 可调试性 | 抢占日志只有「中止：未知」，看不出被谁打断 |

---

## 七、待决策项（未定）

### D1. 脚本之间的走向冲突，采用哪种语义

| 选项 | 行为 | 主要代价 |
|---|---|---|
| 无条件抢占（现状） | 后来者杀掉前者 | 无 postcondition；活锁；优先级缺失 |
| 拒绝后来者 | B 的走向失败，A 继续 | 「玩家改变主意」必须显式停止 |
| 排队 | B 等 A 结束 | 队头不结束会永久等待 |
| 分层优先级 | 玩家/引擎指令可抢，脚本之间不可 | 需要定义归属与优先级 |

分析意见（非决定）：无条件抢占的优点（响应快、无死等）只在「玩家亲自下指令」时成立；在两条脚本线程之间，它的优点用不上、缺点全部生效。

### D2. 走向失败后，脚本线程的归宿

| 选项 | 效果 | 备注 |
|---|---|---|
| 继续执行（现状等价） | 立即跑下一块 | 即时错位，故障密度最高 |
| **中止这个脚本** | 线程结束 | 等价 `control_stop "this script"`，技术可行（见 §8） |
| 原地重试 | 重新 start 同目标 | 与抢占者互抢 → 活锁 |
| 停在原地 | 线程永不继续 | 脚本"失踪" |

这一项与 D1 强耦合：若只做「通知」不做「归宿」，会把低频延迟错位换成高频即时错位。

### D3. 失败是否对玩家可见

- 仅日志（现状）；
- HUD 红字；
- 新增 reporter 积木「上次走向结果」（arrived / gave_up / timeout / cancelled）。

### D4. 是否引入显式「停止走向」积木

若 D1 选「拒绝」，这类积木是打断的唯一合法手段；若选「无条件抢占」，它的语义会退化为"停止当前"，价值有限。

---

## 八、候选技术方案（均未实施，成本为估算）

| 批次 | 内容 | 估算 |
|---|---|---|
| P0 | 会话 id + 每 ticket 一条结果 + 三态 `poll`（等待/成功/失败）；抢占时给旧 ticket 写失败 | ~80 行 / 5 文件 |
| P0′ | 配套 D2：`function (args, util)` 取回 `util.thread`，失败时 `thread.stopThisScript()` | ~30 行 / 2 文件 |
| P1 | 「上次走向结果」reporter 积木 + HUD 提示 | ~40 行 |
| P2 | 动作层 reach 校验（点击/挖掘/放置超距即忽略）—— 与 D1/D2 无关的独立兜底 | ~30 行 |
| P3 | threadId 贯通到桥，按 owner 归属；`TurnSession` 一并统一 | ~200 行 |

要点：D1 的四种策略共用同一套 P0 地基，差别只在 `start()` 里十余行分支，因此**决策不阻塞 P0**。

---

## 九、复现与验证

### 9.1 最小工程

一个角色、两个帽子：

- 当绿旗被点击 → `重复执行 { 走向(100,64,100)；挖掘 }`
- 当聊天包含「回来」→ `走向(0,64,0)`

绿旗启动等 2 秒（线程 1 处于 `PROMISE_WAIT`），再发「回来」。

### 9.2 期望的日志特征

1. `[path] 中止：…`（A 被 closeSession，reason 当前为「未知」）
2. `[path] 到达：…`（B 的目标）
3. 随后出现的是线程 1 的「挖掘」——位置在 B 的终点，而非 (100,64,100)

### 9.3 建议的回归用例

新增 `vmTwoMove`（照 `build.gradle` 中 `vmChat` / `vmKeyHat` 的样式注册），断言：

- A 的等待不被 B 的终态放行；
- A 的 ticket 能拿到 `CANCELLED`；
- B 正常到达。

当前实现下该用例必然失败，可作为缺陷基线。

---

## 十、已核实但未写入其他文档的旁支结论

1. **聊天回调的线程**：`ClientReceiveMessageEvents.CHAT` 在 1.21.11 上运行于主线程，不是 Netty 线程。
   依据：`ClientConnection.channelRead0` → `ClientPlayNetworkHandler.onPlayerChatMessage` 首条即为
   `NetworkThreadUtils.forceMainThread(packet, this, client.getPacketApplyBatcher())`；非主线程时入
   `PacketApplyBatcher` 队列并抛 `PacketException`，由 `channelRead0` 吞掉，主线程下一帧 `PacketApplyBatcher.b()`
   重放。故该处不需要 `client.execute`；`DISCONNECT` 回调不同源，确实需要。
2. **`TurnSession` 是同一结构**：`session` / `lastState` 同为静态单例（`TurnSession.java:33-35`），
   `start()` 直接覆盖会话（`:66-68`），`finished()` 只认 DONE/ABORTED（`:101-103`）。修 PathFollower 时需一并评估。
3. **`__tmcPending` 是 VM 级共享数组**（`vm-bootstrap.js:83`），所有线程的等待项并列，同一个 `check="move"`
   会被同一终态同时放行——影响面是"所有正在等 move 的线程"，不是单个。

---

## 十一、本文档的边界

- 未改动任何源码；
- 未新增测试；
- 未对 D1–D4 作出选择；
- 决策落地后，本文应删除或并入 `PATHFINDING.md`（按 `docs/README.md` 的维护约定）。
