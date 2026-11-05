# GUI 与引擎的配合审计

- **日期**：2026-11-05
- **问题**：引擎层（Compiler / IR / Scheduler / Sb3Parser）整体替换为 scratch-vm 后，GUI 是否做过配套改造
- **回答**：**只做了最小改造，审计后发现 2 个真 bug（GUI 能拼出跑不起来的脚本），已修；另有 1 处语义隔离退化待决策**

---

## 1. 先给结论

方案 A 时 GUI 只改了两处（运行时面板重写、编辑器去掉指令区间），其余未动。本轮系统审计了 GUI 对引擎的全部调用，结论如下：

| 类别 | 数量 | 说明 |
|---|---|---|
| 已适配 | 4 | 面板重写、移除指令提示、面板过滤 spawn、门面 API 保持 |
| **本轮修掉的 bug** | **2** | 局部变量被写成全局、列表名全部丢失 |
| 功能降级 | 2 | 运行高亮、自动滚到运行行 |
| 语义退化（待决策） | 1 | 局部变量跨脚本不再隔离 |

---

## 2. 两个真 bug（GUI 侧完全看不出来）

### 2.1 局部变量被写成了全局

编辑器的变量积木 `data.get` / `data.set` 默认 `SCOPE = "local"`，而 `ProjectWriter` 判断的是 `"target"`：

```java
// 修复前
boolean local = "target".equals(node.fields.get("SCOPE"));
```

编辑器从头到尾只产出 `local` / `global` 两个值，`target` 是旧引擎 `Sb3Parser` 时代的内部名。结果：**所有"局部"变量实际都进了 Stage 的全局表**。

后果很隐蔽——变量能读写、值也对，只是作用域错了。多脚本同名变量会互相覆盖。

修复：改为 `!"global".equals(...)`，默认落到当前 target。

### 2.2 列表名全部丢失

编辑器把列表名定义成普通文本参数：

```java
reg("list.contents", ..., BlockParam.text("LIST", "items"));   // field = false
```

值落在 `inputs` 里，而 sb3 要求列表名是带 ID 的 `fields.LIST`。`ProjectWriter` 只从 `fields` 读，于是：

- 生成的工程里 `lists` 表是空的；
- 所有列表积木的 `LIST` 字段缺失。

更糟的是 `LIST` 被登记进了**变量表**而不是列表表，等于凭空造了一个同名变量。

修复：序列化时把 `inputs` 中的字面量提升为 `fields`，并分流到 `lists` 表（`globalLists` / `targetLists`）。同时在 `buildInputs` 里跳过这些已提升的槽，避免重复写出。

### 验证

新增场景 5：往列表加一项 → 读长度 → 写入局部变量。

```
[write] 场景5 列表+局部变量: count=1.0 -> OK
WRITE SMOKE PASS（五场景）
```

---

## 3. 已适配的部分

| 位置 | 处理 |
|---|---|
| `RuntimePanelScreen` | 重写为 VM 面板：线程数 / 脚本数 / 桥调用 / 变量快照 |
| `ScriptEditScreen` | 移除「指令区间」提示（没有指令流了） |
| `ScriptEditScreen.isUnsupported` | 积木面板过滤 `control.spawn` |
| `ScriptEngine` 门面 | `restart` / `isRunning` / `scriptCount` 保留，GUI 的 13 处调用无一改动 |

GUI 对引擎的调用面其实很窄——只有 13 处，全部落在 `restart()` 与两个状态查询上，因此方案 A 没有产生编译期断裂。真正的风险在**数据结构约定**上（§2 的两个 bug），而不是接口。

---

## 4. 功能降级（不是 bug，是架构决定）

| 功能 | 状态 | 原因 |
|---|---|---|
| 运行积木高亮 | 失效（`runningBlocks` 恒空集） | VM 以字节码线程执行，不暴露积木级定位 |
| 打开编辑器自动滚到运行行 | 失效（同上，不会崩） | 同上 |

代码里这两处仍保留调用，只是拿到空集合后什么也不做。

---

## 5. 局部变量的语义：按 Scratch 来（结论已定）

`ProjectWriter` 把所有启用的脚本塞进**同一个 Sprite**。

我上一轮把它当成"隔离退化"，建议按 `scopeId` 分 Sprite——**这个建议是错的**。

Scratch 的局部变量是**角色级**的：同一角色的多个脚本共享同一批局部变量。脚本不会各自持有一套。因此单个 Sprite 恰恰是正确实现，按脚本分角色反而违背语义。

误判的诱因是文案：`local` 显示为"局部"，听起来像"脚本局部"——而那正是旧引擎的语义（`ScriptInstance` 有 `locals` 字段）。Scratch 从没有脚本级变量。

已修正文案，与 Scratch 官方用语对齐：

| 内部值 | 原文案 | 现文案（zh） | 现文案（en） |
|---|---|---|---|
| `local` | 局部 | **仅适用于当前角色** | for this sprite only |
| `global` | 全局 | **适用于所有角色** | for all sprites |
| `target` | 当前角色 | 保留（旧数据兼容） | this sprite |

代码无需改动：`local` 落到当前 target 的变量表，`global` 落到 Stage——这正是 Scratch 的模型。

### 由此暴露的真正缺口

Scratch 有「变量声明表」：用户能看见有哪些变量、设初值、增删。我们目前是**按积木里出现的名字隐式收集**，初值一律 0，没有管理界面。

这不是语义错误，是功能缺失。若要补齐，需要：

1. 编辑器增加变量面板（列出当前角色与舞台的变量）；
2. `ScriptDefinition` 或独立文件持久化变量声明；
3. `ProjectWriter` 从声明表读初值，而不是遇到才建。

---

## 6. 当前状态

| 冒烟 | 结果 |
|---|---|
| `vmWrite`（五场景：基本 / 聊天 / 函数 / 条件等待 / 列表+局部） | ✅ |
| `vmSmoke`（顺序执行） | ✅ |
| `vmSmoke -PvmMode=hat`（事件触发） | ✅ |
| `vmSmoke -PvmMode=block`（定时阻塞） | ✅ |

产物：`build/libs/hk-script-1.0.0.jar` 2.14 MB。
