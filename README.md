# TurboMC

用 Scratch 积木控制 Minecraft 的 Fabric 客户端模组（1.21.11）。

脚本在 [TurboWarp](https://turbowarp.org/) 里编写、导出成 `.sb3`，游戏内只负责加载与运行。
这样编写时能用到完整的积木编辑器（搜索、撤销、折叠），而游戏内保持轻量。

## 快速开始

1. 安装模组，以及前置 **Fabric API**；要改配置需再装 Cloth Config + Mod Menu。
2. 启动一次游戏，扩展会自动释放到 `.minecraft/config/turbomc/turbomc-extension.js`
   （模组更新后会自动覆盖成新版本）。
3. 在 TurboWarp 里加载这个 js：不加载的话，扩展积木会显示成未知块。
4. 写脚本，导出 `.sb3`，放进 `.minecraft/config/turbomc/imports/`
5. 进游戏按 **F7** → 点「导入/更新」→ 点左侧圆点启用该包
6. 按 **F6** 开启引擎，HUD 出现 `● 包名` 即表示正在运行

> F6 是**持久开关**：开着退出游戏，下次启动仍会记住。默认在退出存档或断开服务器时
> 自动关闭，所以每局都要按一次 F6；想保留上一局状态，去模组设置 → 脚本引擎 →
> 关掉「退出世界时关闭脚本引擎」。VM 在标题界面就已经预热，进世界按 F6 只花载入工程的
> 几十毫秒，不会再卡一秒。注意开发环境（`run/`）与正式实例的
> `config/turbomc.json` 是两份独立配置。

> 迭代时沿用同名文件即可：新导出的 sb3 覆盖 `imports/` 里的旧的，按 **F7** 点「导入/更新」——
> 导入对「新文件」与「同名但内容不同」一视同仁，不必先删包再导入。
> 包名后的 `↑` 表示 imports 里有内容不同的新版本；启用中的包被覆盖后会立刻重启生效。

> 仓库里的 `turbowarp/turbomc-extension.js` 是同一份文件的源，改扩展请改仓库这份，
> 再重新构建——`config/turbomc/` 下的副本每次启动都会按 jar 内版本覆盖。

> 要用「AI 对话」积木，先填好 `config/turbomc.json` 的 `ai` 段（或模组设置 → **AI 对话**）：
> `baseUrl`、`apiKey`、`model` 三项。没配时积木不报错，只是永远返回空串。

## 按键

| 键 | 作用 |
|---|---|
| F6 | 开关脚本引擎 |
| F7 | 打开脚本包界面（导入 / 启停 / 查看脚本树） |

## 能做什么

积木覆盖移动、挖掘、放置、聊天与命令、容器操作，以及一批世界查询
（方块、实体、时间、坐标等）。完整清单见 `turbowarp/mapping-report.md`。

**容器操作**可以完成自动熔炼、分类存储这类任务：打开箱子或熔炉、读某个槽位装的是什么、
按物品名定位槽位、点击与 Shift 快速移动、关闭界面。注意点击是异步的，发包后要等一 tick
再读状态。

攻击没有专用积木，用「点击左键」：走原版射线，客户端自己判定目标与距离，
不会出现「攻击无效实体」被服务端断开的情况。

脚本的「说 / 思考」会显示在 HUD 上——这两个是 scratch-vm 的原生积木，游戏内没有
渲染出口，HUD 是它们唯一的表达通道。

**AI 对话**：`最近的聊天` 取到玩家说的话，`让会话 [SESSION] 回应 [TEXT]` 发给大模型并
等回复（等待期间脚本挂起，不卡游戏）。接口走 OpenAI 兼容协议，在 `config/turbomc.json`
的 `ai` 段或模组设置 → AI 对话里配置 `baseUrl`、`apiKey`、`model`。密钥不入 sb3。

聊天有两处容易踩的细节：

- **代理服会把玩家聊天包成服务端消息下发**，此时只有打开「接收服务端消息作为聊天」，
  `当聊天包含` 才会触发。该通道默认只放行关键词能确定的帽子，避免插件广告把脚本刷起来。
- **`最近的聊天` 在聊天触发的脚本里返回「触发我的那条」**（已绑定到线程，不会被后到的消息
  覆盖）；其它脚本返回全局最新一条。要显式拿到触发消息用 `触发我的聊天`，非聊天脚本得到空串。

**AI 运行状态是可见的**：HUD 会显示每个会话当前在做什么（`等待 1.2s` / `✓ 8.3s 回复前 24 字` /
`✕ 超时`），失败会给出短原因（超时、HTTP 401、连接失败、空回复）；按 F7 打开脚本包列表、
点「运行状态」，还能看到每个会话的耗时、历史轮数与最近一次回复的完整前 80 字。
接口返回 2xx 但内容为空算失败（日志里带 `finish_reason`），因为脚本拿到的同样是空串。
HUD 的停留时长、最多行数与预览字数在模组设置 → AI 对话里可调。

## 构建

```bash
# Loom 1.19 要求 JDK 25；系统默认的 JDK 21 会在配置阶段失败
JAVA_HOME=~/.jdks/ms-25.0.5.0.2 ./gradlew build
```

产物位于 `build/libs/turbomc-<mod_version>.jar`。

冒烟测试不需要启动客户端：

```bash
./gradlew vmSmoke                  # 顺序执行
./gradlew vmSmoke -PvmMode=hat     # 事件触发
./gradlew vmSmoke -PvmMode=block   # 阻塞动作
./gradlew vmImport                 # sb3 解析 + 执行
./gradlew vmChat                   # 聊天帽子：一次事件只触发一次、关键词过滤
./gradlew vmKeyHat                 # 按键帽子：对得上起 1 个线程、对不上起 0 个
./gradlew vmList                   # HUD 读列表 + 算术积木参数名映射
./gradlew vmDepth                  # 超长积木链的深度保护
./gradlew vmNumberFormat           # 数值字符串化是 ECMAScript 格式
./gradlew vmPreload -Pmode=preload # 预热前后首次载入工程的耗时对比
./gradlew exportSmoke              # 扩展释放：创建 / 内容一致不动 / 旧版覆盖
```

## 文档

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 架构、数据流、必须知道的约束
- [docs/README.md](docs/README.md) — 文档索引
- [turbowarp/README.md](turbowarp/README.md) — 扩展加载步骤与积木清单
- [turbowarp/mapping-report.md](turbowarp/mapping-report.md) — 积木与 opcode 对应表（由代码生成）

## 许可

MIT，见 [LICENSE](LICENSE)。
