# HUD 实现 + 两项失效配置清理

## 一、删除的两个配置项

| 配置项 | 原因 |
|---|---|
| `autoToolEnabled` | 无任何消费者，且与脚本形成影子路径 |
| `autoToolSearchRadius` | 依附上一项 |

同步清掉：配置界面「自动化」整个分类、5 条语言键。
**菜单从 7 项减到 4 项，每项都真实生效。**

---

## 二、HUD 显示什么

```
┌─────────────────┐
│ ● 作品           │  ← 状态行
│ 角色1：挖到钻石了 │  ← 说（白字）
│ 矿工：要不要挖？  │  ← 思考（灰字）
└─────────────────┘
```

| 项 | 规则 |
|---|---|
| 状态行 | 运行中：`● 包名`（绿）；停用：`○ 脚本已停用`（灰） |
| 气泡 | `角色名：内容`，说=白字、思考=灰字 |
| 停留时长 | **5 秒**，从该句出现算起 |
| 数量上限 | **3 条**，超出挤掉最旧的 |
| 位置 | 跟随 `hudCorner` 配置（左上/右上/左下/右下） |
| 不显示的场景 | 模组总开关关闭、F1 隐藏 HUD、打开任何界面（含 F7、聊天、背包） |

---

## 三、为什么 HUD 要显示气泡

这是本次改动最重要的发现：

**`looks_say` / `looks_think` 是 scratch-vm 的原生积木，游戏内没有任何渲染出口。**

之前的状态是：脚本里写「说 你好」，运行时什么都不发生，也不报错。`hkscript_log` 会写日志文件，但 `说` 和 `思考` 是完全静默的。

HUD 补上了这条通道，脚本才真正「能对外说话」。

---

## 四、技术路径

### 渲染入口

1.21.11 的 Fabric API 已把 `HudRenderCallback` 换成新版注册表：

```java
HudElementRegistry.addLast(HkScript.id("script_hud"), new ScriptHudElement());

interface HudElement {
    void render(DrawContext context, RenderTickCounter tickCounter);
}
```

### 气泡读取

scratch-vm 把气泡状态存在每个 target 的 `customState['Scratch.looks']` 里：

```
{ type: 'say' | 'think', text: '...', usageId: '...' }
```

新增 `__hkSpeech()` 遍历所有 target 收集，返回 JSON；`RhinoVm.speechJson()` 转给 Java。

**关键设计：用 `usageId` 判断是否是新的一句话。** 它每次说话都会重新生成，比比对文本可靠——同一句话连说两次也能识别为新消息，不会被去重吞掉。

### 状态管理

```
每帧：读 VM 气泡 → usageId 变了就记时间戳 → 清掉超过 5 秒的 → 取最近 3 条渲染
```

同一个角色只保留一条（与 Scratch 的气泡行为一致：新话覆盖旧话）。

---

## 五、验证

| 场景 | 结果 |
|---|---|
| `looks_say` 中文 | `[{"name":"角色1","text":"挖到钻石了","think":false,...}]` |
| `looks_think` 中文 | `[{"name":"矿工","text":"要不要挖？","think":true,...}]` |
| 无 say 的工程 | `[]`（不误报） |
| `vmImport` / `vmSmoke`×3 | 全 PASS |

产物 2.126 MB，已确认 `ScriptHudElement` 与 `__hkSpeech` 都进了 jar。

### 一次排查记录

测试初期遇到 `Non-ascii character in FixedAsciiString`。定位结果是**测试数据的问题**：我造的 sb3 里 `assetId` 只写了 1 个字符，而 scratch-vm 按固定 32 位 ASCII 串读取素材 ID，越界读到了非法字符。换成标准 md5 后正常。不是产品缺陷。

---

## 六、需要真机确认的三点

1. **停用时常驻显示「○ 脚本已停用」是否碍眼**。若碍眼，可以改成只显示 3 秒或干脆不显示。
2. **5 秒是否合适**。太快看不到，太慢会堆叠。
3. **四角位置的实际遮挡情况**。左上角容易和 F3 调试信息、Buff 图标打架，右上角容易和效果图标重叠。

这三点都只是常量或分支调整，不需要改结构。
