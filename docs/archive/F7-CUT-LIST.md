# F7 削减：删除清单统计

> 目标：**不在游戏内写脚本**。TurboWarp 负责编写与修改，游戏只负责「加载 sb3 并运行」。
> 本文只统计，未改动任何代码。

---

## 一、结论

| 方案 | 整文件删除 | 改写缩减 | 净减 |
|---|---|---|---|
| **A. 彻底删（推荐）** | 23 个文件 / 3742 行 | 3 个文件 / 约 593 行 | **约 4335 行** |
| B. 保留只读查看 | 7 个文件 / 1579 行 | 4 个文件 / 约 955 行 | 约 2534 行 |

A 方案与当初「方案 A 重写」的规模相当（+301 / −4641），等于把编辑器这一层整体移除，只剩执行内核。

---

## 二、A 方案：逐文件清单

### 1. 界面层（8 个文件 / 1658 行）

| 文件 | 行数 | 作用 | 删除理由 |
|---|---|---|---|
| `ui/ScriptEditScreen.java` | 622 | 积木树编辑器 | 核心编辑界面 |
| `ui/ParamEditScreen.java` | 319 | 参数编辑 | 写操作 |
| `ui/BlockPickerScreen.java` | 153 | 积木选择面板 | 写操作 |
| `ui/FunctionListScreen.java` | 147 | 自定义积木管理 | 写操作 |
| `ui/NodeChain.java` | 148 | 积木树渲染 | 只为编辑器服务 |
| `ui/ParamLabels.java` | 81 | 参数文案 | 只为编辑器服务 |
| `ui/WaitUnit.java` | 116 | 等待单位换算界面 | 只为参数编辑服务 |
| `ui/TextInputScreen.java` | 72 | 文本输入框 | 重命名脚本用 |

### 2. 积木定义（6 个文件 / 428 行）

| 文件 | 行数 | 说明 |
|---|---|---|
| `block/Blocks.java` | 267 | 106 个积木的界面定义（名称/参数/形状） |
| `block/BlockRegistry.java` | 40 | 注册表 |
| `block/BlockType.java` | 37 | 类型模型 |
| `block/BlockParam.java` | 52 | 参数模型 |
| `block/BlockShape.java` | 17 | 形状枚举 |
| `block/ParamKind.java` | 15 | 参数种类枚举 |

**这是最值得删的一块**：这套定义只为「在游戏内画出积木」。删掉之后新增积木**只需改扩展 JS + `GameActions` 两处**，不必再同步维护一份编辑器定义。

### 3. 数据结构（2 个文件 / 135 行）

| 文件 | 行数 | 说明 |
|---|---|---|
| `node/BlockNode.java` | 107 | 积木树节点 |
| `node/Slot.java` | 28 | 参数槽 |

### 4. 序列化（2 个文件 / 1061 行）

| 文件 | 行数 | 说明 |
|---|---|---|
| `sb3/ProjectWriter.java` | 568 | 积木树 → project.json |
| `sb3/Sb3Importer.java` | 493 | sb3 → 积木树（上一轮刚加的） |

### 5. 存储与示例（3 个文件 / 83 行）

| 文件 | 行数 | 说明 |
|---|---|---|
| `store/ScriptDefinition.java` | 32 | 一份脚本的数据模型 |
| `store/FunctionDefinition.java` | 22 | 自定义积木模型 |
| `DemoScripts.java` | 29 | 内置示例脚本 |

### 6. 测试（2 个文件 / 377 行）

| 文件 | 行数 | 说明 |
|---|---|---|
| `vm/WriteSmokeTest.java` | 204 | 编辑器树 → JSON → VM |
| `vm/ImportSmokeTest.java` | 173 | sb3 → 树 → JSON → VM |

两者验的都是编辑链；`vmSmoke`（顺序 / 帽子 / 阻塞）保留，它验的是执行链。

### 7. 需改写而非删除（3 个文件 / 约 −593 行）

| 文件 | 现在 | 之后 | 差异 |
|---|---|---|---|
| `store/ScriptStore.java` | 464 | 约 110 | 不再管 `ScriptDefinition` json，只做**sb3 文件管理**（列出 / 导入 / 删除 / 启用） |
| `ScriptEngine.java` | 268 | 约 120 | 不再序列化脚本，只做「读 sb3 → 加载 → greenFlag → 事件转发」 |
| `ui/ScriptListScreen.java` | 241 | 约 150 | 从「脚本列表」改为「工程列表」 |

### 8. 保留不动

| 模块 | 说明 |
|---|---|
| `vm/`（RhinoVm / ScriptBridge / VmSmokeTest） | 执行内核 |
| `action/`（GameActions / WorldQueries / ScriptKeys / InputKeys …） | 动作层 |
| `runtime/`（ScriptContext / TriggerDispatcher / Values …） | 宿主上下文 |
| `sb3/Sb3Mapping.java` | **不能删**：`ScriptBridge` 靠它把 opcode 映射到动作层 |
| `ui/RuntimePanelScreen.java` | 运行时状态面板，与编辑无关 |

---

## 三、删完之后必须补的三处（否则功能断裂）

### 1. 按键触发会失效

`ScriptEngine.triggerKeys()` 目前是**遍历编辑器脚本**、读 `event.key` 帽子的 `KEY` 字段来收集键名：

```java
if ("event.key".equals(definition.hat.type)) {
    keys.add(definition.hat.fieldOr("KEY", "f"));
}
```

编辑链删除后这里必然为空 → 「当按下 X 键」全部不触发。

两种改法：

| 改法 | 说明 |
|---|---|
| 扫 sb3 | 解析 project.json 里 `event_whenkeypressed` 的 `KEY_OPTION`。仍要一份轻量 JSON 解析 |
| **逐键触发（推荐）** | 每帧对 `InputKeys` 里的键做 `startHats("event_whenkeypressed", {KEY_OPTION: key})`，VM 自己匹配。`KEY_OPTION` 是 field，`startHats` 的字段匹配可用 |

推荐后者：不需要任何 sb3 解析，也就不必为了按键保留一个解析器。

### 2. F7 的内容要重新定义

F7 现在打开 `ScriptListScreen`。删掉编辑器后，这个界面应该变成：

```
已加载工程：作品.sb3                    [删除]
  ● 启用   导入目录：config/hk-script/imports/
  [导入]  [打开运行状态]
```

即「工程管理 + 运行状态」，不含任何积木。

要不要干脆连 F7 一起删、只留 F6 总开关？取决于您是否需要「在游戏内换工程」。

### 3. 工作流必须写清楚

删掉编辑器后，唯一入口是 TurboWarp：

```
TurboWarp 编辑 → 加载 hkscript-extension.js → 导出 .sb3
   → 放进 config/hk-script/imports/
   → 游戏内 F7 → 导入
```

`turbowarp/hkscript-extension.js` 必须随包提供，否则 TurboWarp 里扩展积木是未知块。

---

## 四、删除前的必做一步

**先导出一次现有脚本。**

`config/hk-script/scripts/*.json` 是编辑器私有格式，只有 `ProjectWriter` 能转成 sb3。删掉它之后这些文件就成了死数据。

建议顺序：

1. 打开列表 → 对每个想保留的脚本点「导出」（已改为导出 .sb3）；
2. 确认导出的 sb3 能在 TurboWarp 打开；
3. 再执行删除。

---

## 五、建议

**选 A（彻底删）**，理由不是「行数少了好看」，而是维护成本：

现在每加一个积木要改三处——扩展 JS、`GameActions`、`Blocks`（界面定义）。删掉 `block/` 后回到两处。而 `Blocks` 那一份纯粹是为了在游戏里画积木，既然不画了，它就是纯负债。

配套保留的只有 `Sb3Mapping`（一张 opcode ↔ 动作类型表），因为执行时必须靠它。

代价是游戏内看不到脚本树。这个能力在 TurboWarp 里天然更强（有搜索、有积木提示、有撤销），没有理由在游戏里再做一份。

---

## 六、附：删完后的体积估算

| 项 | 现在 | 之后 |
|---|---|---|
| 脚本相关 Java 文件 | 46 个 | 约 23 个 |
| 脚本相关行数 | 约 6900 | 约 2600 |
| 净减 | — | **约 4300 行** |
