# sb3 导入可查看 + 聊天/等待修复

## 一、两个真实 bug（用户报「启动后一条聊天都没出现」）

### Bug 1：chat 的参数名被改写，消息被静默丢弃

`ProjectWriter.scratchInputName` 里有一条无条件的名字替换：

```java
case "TEXT" -> "MESSAGE";   // 这是 looks_say / looks_sayforsecs 的参数名
```

但扩展的 `chat` 积木参数**就叫 `TEXT`**。导出后 sb3 里变成 `MESSAGE`，扩展读 `args.TEXT` 得到 `undefined`，经 `Values.asString` 变空串，命中 `if (text.isBlank()) return;` 直接返回——**不发、不报错、日志也没有**。

修复：只对 `looks_*` 生效。

```java
case "TEXT" -> opcode.startsWith("looks_") ? "MESSAGE" : "TEXT";
```

### Bug 2：wait 的时间单位被放大 20 倍

编辑器 `control.wait` 的参数叫 `TICKS`（tick），sb3 的 `control_wait` 参数叫 `DURATION`（**秒**）。导出只改了名字、没换算值，于是「等待 20 tick（1 秒）」变成「等待 20 秒」。

旧的反向解析器（已删的 `Sb3Parser`）是做了换算的，新的导出方向漏了——这类不对称 bug 单测不覆盖就很难发现。

修复：`needsSeconds()` + `toSeconds()`。字面量直接 ÷20（20 → 1、10 → 0.5），表达式则包一层 `operator.divide`，避免把变量当固定值。

## 二、导入后看不到脚本树（用户核心诉求）

原实现（方案 A 之后）：

```java
Files.copy(path, dir.resolve("project.sb3"));
ScriptEngine.get().restart();
```

整包丢给 VM 独占运行，编辑器里没有任何入口——**看不到、改不了、列表里也没有**。

### 新增 `sb3/Sb3Importer`

与 `ProjectWriter` 互为逆过程的解析器，把 sb3 的 blocks 字典还原成编辑器积木树：

| sb3 结构 | 编辑器结构 |
|---|---|
| `next` 引用 | `BlockNode.next` |
| `SUBSTACK` / `SUBSTACK2` 输入 | `BlockNode.branches` |
| `inputs` 内联字面量 `[1,[10,"文字"]]` | `Slot.literal` |
| `inputs` 嵌套积木 `[3,"blockId",...]` | `Slot.of(BlockNode)` |
| `inputs` **变量引用** `[3,[12,"i","id"],...]` | `data.get(VAR=i, SCOPE=…)` |
| `fields` | `BlockNode.fields`（带作用域还原） |
| 每个 `topLevel && !shadow` 块 | 一份 `ScriptDefinition` |

四个易错点，都在实测里踩过：

1. **字面量值在内层数组的第二项**。`[1, [10, "文字"]]` 里 `10` 是类型码，取第一项会让所有文本参数变成 `10`/`12` 这类数字。
2. **类型码 12 是变量引用，不是文本**。`[3, [12, "i", "id"], [10, "w"]]` 表示「接的是变量 i，shadow 值是 w」，必须还原成 `data.get`；当成字面量会把变量名变成一段文字。
3. **全局/局部要查 ID**。变量字段的第二项是 ID，据此判断落在自身 `variables`（局部）还是舞台 `variables`（全局）。
4. **秒 → tick** 与导出方向对称，×20。

### 导入行为变化

| | 之前 | 现在 |
|---|---|---|
| 列表 | 不出现（只有一行提示） | **每个顶层脚本一条**，可点进去查看/修改 |
| 运行 | 整体跑 `project.sb3` | 走编辑器脚本，与 GUI 搭建的完全同路 |
| 原 sb3 | 复制成 `project.sb3` 独占 | 备份在 `imports/imported/`，不独占 |
| 重复导入 | 覆盖 | 同名同角色跳过，不产生副本 |
| `project.sb3` | — | 有新脚本导入时自动清掉（否则它会覆盖编辑器脚本） |

`importAll` 现在也会扫描 `imports/imported/`，把**早期只当整体工程运行过、从未生成脚本的存量 sb3 补成可查看脚本**（已存在的跳过）。

## 三、验证

新增 `vmImport` 任务：

```
[import] 文件: 作品.sb3，脚本 2 份
  event.death
  action.respawn
  event.tick
  control.forever
    [SUBSTACK]
      data.set fields={VAR=i, SCOPE=global} inputs={VALUE=1}
      control.repeat inputs={TIMES=9}
        [SUBSTACK]
          action.tap inputs={KEY=<data.get>, HOLD=1}
          action.chat inputs={TEXT=<data.get>}
          data.set fields={VAR=i, SCOPE=global} inputs={VALUE=1}
[import] chat 参数名正确=true wait 秒换算正确=true
[import] 编辑器脚本: chat=参数名正确 wait=换算正确
[import] 步进 10 帧，线程 1，桥调用 20，未映射 0
IMPORT SMOKE PASS
```

其余冒烟无回归：

| 冒烟 | 结果 |
|---|---|
| `vmWrite`（五场景） | PASS |
| `vmSmoke`（顺序 / 帽子 / 阻塞） | PASS |
| `vmImport`（导入往返） | PASS |

产物 2.16 MB。

## 四、已知限制

| 限制 | 说明 |
|---|---|
| 自定义积木未还原成「函数定义」 | `procedures_definition` 目前被跳过；积木本身保留，但函数体不会变成编辑器里的自定义积木 |
| 造型/声音/坐标不导入 | 编辑器不维护这些，原始 sb3 仍在 `imports/imported/` 可整体运行 |
| 多角色会合并 | 导出的 `ProjectWriter` 只写一个 Sprite。导入时按角色命名脚本（`角色-1`），但再导出时角色边界丢失 |
| 无法从编辑器导出多角色工程 | 同上，属于方案 A 的单角色模型 |
