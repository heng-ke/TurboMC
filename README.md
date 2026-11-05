# TurboMC

用 Scratch 积木控制 Minecraft 的 Fabric 客户端模组（1.21.11）。

脚本在 [TurboWarp](https://turbowarp.org/) 里编写、导出成 `.sb3`，游戏内只负责加载与运行。
这样编写时能用到完整的积木编辑器（搜索、撤销、折叠），而游戏内保持轻量。

## 快速开始

1. 安装模组，以及前置 **Fabric API**；要改配置需再装 Cloth Config + Mod Menu。
2. 在 TurboWarp 里加载扩展：`turbowarp/turbomc-extension.js`
   不加载的话，扩展积木会显示成未知块。
3. 写脚本，导出 `.sb3`，放进 `.minecraft/config/turbomc/imports/`
4. 进游戏按 **F7** → 点「导入」→ 点左侧圆点启用该包
5. 按 **F6** 开启引擎，HUD 出现 `● 包名` 即表示正在运行

## 按键

| 键 | 作用 |
|---|---|
| F6 | 开关脚本引擎 |
| F7 | 打开脚本包界面（导入 / 启停 / 查看脚本树） |

## 能做什么

积木覆盖移动、挖掘、放置、攻击、聊天与命令、容器操作，以及一批世界查询
（方块、实体、时间、坐标等）。完整清单见 `turbowarp/mapping-report.md`。

**容器操作**可以完成自动熔炼、分类存储这类任务：打开箱子或熔炉、读某个槽位装的是什么、
按物品名定位槽位、点击与 Shift 快速移动、关闭界面。注意点击是异步的，发包后要等一 tick
再读状态。

脚本的「说 / 思考」会显示在 HUD 上——这两个是 scratch-vm 的原生积木，游戏内没有
渲染出口，HUD 是它们唯一的表达通道。

## 构建

```bash
# Loom 1.19 要求 JDK 25；系统默认的 JDK 21 会在配置阶段失败
JAVA_HOME=~/.jdks/ms-25.0.5.0.2 ./gradlew build
```

产物位于 `build/libs/turbomc-1.0.0.jar`。

冒烟测试不需要启动客户端：

```bash
./gradlew vmSmoke                  # 顺序执行
./gradlew vmSmoke -PvmMode=hat     # 事件触发
./gradlew vmSmoke -PvmMode=block   # 阻塞动作
./gradlew vmImport                 # sb3 解析 + 执行
./gradlew vmChat                   # 聊天帽子：一次事件只触发一次、关键词过滤
```

## 文档

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 架构、数据流、必须知道的约束
- [docs/README.md](docs/README.md) — 文档索引
- [turbowarp/README.md](turbowarp/README.md) — 扩展加载步骤与积木清单
- [turbowarp/mapping-report.md](turbowarp/mapping-report.md) — 积木与 opcode 对应表（由代码生成）

## 许可

CC0
