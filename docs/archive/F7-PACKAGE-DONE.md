# F7 方案甲落地：脚本包 + 只读查看

## 一、结果

**F7 现在只做四件事**：导入包、启用一个包、改名/删除、展开看脚本树。

游戏内不再写脚本——编写与修改交给 TurboWarp。

| 指标 | 之前 | 之后 |
|---|---|---|
| 脚本相关 Java 文件 | 46 个 | **40 个** |
| 脚本相关行数 | 约 7900 | **6143** |
| 净减 | — | **约 1800 行** |
| 产物 | 2.16 MB | **2.12 MB** |

---

## 二、新结构

### 目录

```
config/hk-script/
├── packages/            ← 新：包目录
│   ├── 作品.sb3
│   ├── 挖矿.sb3
│   └── index.json       ← 只记「哪个包是启用的」
├── imports/             ← 投放入口：sb3 丢这里再点导入
│   └── imported/        ← 导入后归档（也会被扫描）
└── scripts/             ← 旧编辑器脚本，已不读取（可手动删）
```

### 数据流

```
TurboWarp → 导出 .sb3 → imports/ → F7 导入 → packages/
                                              │
                          ┌───────────────────┴────────────────────┐
                          ↓                                        ↓
                  project.json 原样交给 VM               Sb3Importer 解析成树
                  （运行这条链，零中间格式）              （查看这条链，只读）
```

关键点：**运行时不再有任何序列化**。启用的包就是 sb3 里那份 project.json，直接
`loadProject`。之前"编辑器写的和实际跑的不一致"这类问题从根上消失了。

### 包级开关是互斥的

同时只运行一个工程。因此不需要合并多个 sb3 的 targets——那会涉及舞台变量合并，
是静默失效的高风险点（方案乙再议）。

点已启用的包不做任何事：全部关掉等于让引擎空转，用户容易以为坏了。要停用请按 F6。

---

## 三、删除清单（8 个文件 / 1906 行）

| 文件 | 行数 | 原因 |
|---|---|---|
| `sb3/ProjectWriter.java` | 568 | 不再有"从积木树序列化"的需求 |
| `store/ScriptStore.java` | 464 | 由 `PackStore` 取代 |
| `ui/ParamEditScreen.java` | 319 | 无参数编辑 |
| `vm/WriteSmokeTest.java` | 204 | 测的是已删的序列化链路 |
| `ui/BlockPickerScreen.java` | 153 | 不能加积木 |
| `ui/FunctionListScreen.java` | 147 | 无自定义积木管理 |
| `DemoScripts.java` | 29 | 无"新建脚本" |
| `store/FunctionDefinition.java` | 22 | 函数体改为在树上直接展示 |

## 四、新增（2 个文件 / 约 350 行）

| 文件 | 行数 | 职责 |
|---|---|---|
| `store/PackStore.java` | 约 320 | 包列表、互斥启用、导入、改名、删除、project.json 读取、解析缓存 |
| `store/ScriptPack.java` | 33 | 一个包的视图（文件名 + 启用态） |

## 五、改写

| 文件 | 变化 |
|---|---|
| `ui/ScriptEditScreen.java` | 622 → 约 280，改为只读查看器 |
| `ui/ScriptListScreen.java` | 包分组列表，每包可展开脚本名 |
| `ScriptEngine.java` | 去掉全部序列化逻辑，只做「取启用包 → 加载 → 绿旗」 |
| `block/Blocks.java` | 新增 `procedures.definition`（自定义积木定义块的展示形态） |
| `sb3/Sb3Importer.java` | 不再跳过 `procedures_definition`，函数体有了归属 |

---

## 六、界面形态

```
┌─ 脚本包 ───────────────────────────────────┐
│ ●  作品           2 个脚本          [展开] │
│   └ 角色1   当玩家死亡                     │
│   └ 角色1-2 脚本启动时                     │
│ ○  挖矿           1 个脚本          [展开] │
│                                            │
│ 运行中：作品.sb3                            │
│ 脚本请在 TurboWarp 中编辑；这里只负责导入、启停与查看。│
│        [导入] [重命名] [删除] [运行状态] [完成]│
└────────────────────────────────────────────┘
```

点脚本名 → 只读树：

```
┌─ 角色1 ────────────────────────────────────┐
│ ▾ 当玩家死亡                               │
│     复活                                   │
│ ▾ 脚本启动时                               │
│   重复执行 9 次                             │
│     ...                                    │
│                [全部展开] [全部折叠] [返回] │
└────────────────────────────────────────────┘
```

只读界面保留：选中、滚动、折叠/展开、返回。没有添加/删除/改参数。

---

## 七、验证

```
[import] 文件: 作品.sb3，脚本 2 份
  event.death
  action.respawn
  event.tick
  control.forever
    [SUBSTACK]
      data.set fields={VAR=i, SCOPE=global} inputs={VALUE=1}
      ...
[import] 可查看=true 含自定义积木=false
[import] 步进 10 帧，线程 1，桥调用 34，未映射 0
IMPORT SMOKE PASS
```

自定义积木单独造了一份工程验证：

```
[import] 文件: custom-block.sb3，脚本 2 份
  event.tick
  func.call inputs={P1=5}
  procedures.definition fields={NAME=addscore %s}
  debug.log inputs={TEXT=in function}
[import] 可查看=true 含自定义积木=true
IMPORT SMOKE PASS
```

其余冒烟无回归：

| 冒烟 | 结果 |
|---|---|
| `vmImport`（作品 / 自定义积木） | PASS |
| `vmSmoke`（顺序 / 帽子 / 阻塞） | PASS |

---

## 八、升级路径

`PackStore.load()` 首次发现包目录为空时，会**自动把 `imports/` 与 `imports/imported/` 里的 sb3 搬进包目录**并启用第一个。所以升级后打开游戏就能直接看到之前导入的作品，不需要手动点导入。

`config/hk-script/scripts/*.json` 是旧编辑器脚本，已不再读取。它们只是留在磁盘上，不影响运行，可以手动删。

---

## 九、已知限制

| 限制 | 说明 |
|---|---|
| 一次只能跑一个包 | 方案甲的设计。要组合运行需做方案乙（合并 targets，重点是舞台变量） |
| 切包 = 整包重载 | 所有脚本从头开始，`forever` 里的状态会重置。这是 scratch-vm 的结构决定 |
| 自定义积木显示 `proccode` 原文 | 形如 `addscore %s`，没有渲染成「加 %s 到分数」的中文模板 |
| `Blocks` 与扩展是两份平行数据 | 新增积木时若忘了改 `Blocks`，查看界面会显示成未知块（红字），不影响运行 |
| 未真机验证 | 以上全部是 JVM 内验证，界面布局与交互需要在客户端实际看一遍 |
| 包很多时会溢出屏幕 | 列表不分页，超过约 15 行会画出边界 |

---

## 十、下一步建议

1. **真机跑一轮**：F7 → 看到包 → 展开 → 点进看树 → 切到另一个包确认互斥；
2. 确认交互手感（尤其"点已启用的包无反应"是否符合直觉）；
3. 需要多包同时运行时，再开方案乙——它的增量是纯新增合并器，不动已有结构。
