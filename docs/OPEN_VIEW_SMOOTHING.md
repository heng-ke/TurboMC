# 视角抖动：方案乙实施设计（提案 · 未实施）

> 状态：**已实施（批 1–3），待实机验收**。批 4 的文档改写已完成，参数也已开放为配置项
> （见 `PATHFINDING.md` §12.1），只剩「默认值该不该调」需要实机数据；验收后归档。
> 决策：**D5 = B**（「看向」接管视角，走向照原目标走）、**D6 = A**（只跳过当前帧）、**D7 = 同批**。
> 实现：`script/view/ViewSmoother` + `TurnMath.frameStep`，注册于 `WorldRenderEvents.START_MAIN`。
> 记录日期：2026-11-08
> 目标问题：`走向` 期间第一视角每 tick 一跳（20 Hz 台阶），拐弯时观感明显。
> 参考实现：`C:\Users\13692\Desktop\areaautominer-1.21.11`（同版本 1.21.11 / yarn build.6，**零 mixin**）。

---

## 一、参考实现凭什么平滑（已核实）

| 事实 | 出处 |
|---|---|
| 视角写入挂在**渲染帧**事件，不是 tick | `AreaAutoMinerClient.java:42` `WorldRenderEvents.START_MAIN.register(ctx -> miningController.onRenderFrame())` |
| 全局只有两处 `setYaw`，都在渲染帧回调里 | `CameraHelper.java:107`、`:122` |
| dt 取自渲染帧进度，帧率无关 | `CameraHelper.java:91-96` |
| 指数平滑 `alpha = 1 - exp(-k·dt)` + 按秒限速 | `:97-99` |
| 鼠标位移 > 2 px 就跳过本帧写入 | `:80-90` |
| 无任何 mixin | `areaautominer.mixins.json` 的 `"mixins": []` |

结论：**它没有解决「原版不插值」，而是让写入频率等于读取频率**（相机每帧读一次 yaw）。这是本方案的全部依据。

---

## 二、目标与不变量

### 目标

1. 走向 / 转向期间，第一视角连续，无 20 Hz 台阶；
2. 玩家动鼠标能立刻夺回视角（脚本不锁镜头）；
3. 不引入 mixin，不改原版行为；
4. 保留现有角速度上限（240°/s），不靠"转得更快"伪装平滑。

### 不变量（不可破坏）

| 不变量 | 原因 |
|---|---|
| 移动决策仍在 tick 层 | 前进/跳跃键是 tick 语义，搬到帧层会破坏 `ScriptKeys` 的 tick 到期机制 |
| `PathFollower` 的到达/卡住/分段逻辑不变 | 它们只依赖位置与 tick 计数，与视角无关 |
| `isArrived` 判据不变 | 水平 0.6 / 垂直 0.6 |
| 引擎停止必须清空视角目标 | 否则脚本停了镜头还在自己转 |
| 帧回调只能主线程 | 与 tick 同线程，不加锁 |

---

## 三、设计总览

```
tick 层（20 Hz，决策）                     帧层（60–144 Hz，表现）
─────────────────────────                 ─────────────────────────
PathFollower.tick()                       ViewSmoother.onRenderFrame()
  ├ 算节点、判到达、判卡住                    ├ 取 mode / targetYaw
  ├ 按 diff 决定前进 / 站定 / 跳跃             ├ dt = 真实帧间隔（clamp 0–50 ms）
  └ ViewSmoother.setWalkTarget(yaw)  ──────▶  ├ 鼠标接管检测
                                               ├ yaw += clamp(diff*alpha, ±v·dt)
TurnSession.tick()                             └ player.setYaw(...)
  ├ 判到角、判超时
  └ ViewSmoother.setTurnTarget(yaw, pitch) ──▶ 同上（pitch 一并）

GameActions.look / lookAt                  （瞬时写 + 占用 MANUAL）
```

要点：**tick 只产出"目标"，帧层负责"怎么过去"**。目标在两 tick 之间保持不变，帧层持续收敛——这正是参考实现的做法。

---

## 四、新增类 `ViewSmoother`

路径：`src/client/java/xyz/turbomc/client/script/view/ViewSmoother.java`（新包 `view`）。

### 4.1 字段与常量

```java
public final class ViewSmoother {
    public enum Mode { NONE, WALK, TURN, MANUAL }

    private static Mode mode = Mode.NONE;
    private static float targetYaw;
    private static float targetPitch;
    private static boolean pitchActive;

    private static long lastFrameNanos;      // 0 表示首帧
    private static double lastMouseX = Double.NaN;
    private static double lastMouseY = Double.NaN;

    /** 与 TurnMath 保持一致，避免两套死区。 */
    private static final float DEAD_ZONE = TurnMath.DEAD_ZONE;
    /** 角速度上限：240°/s，与现状 12°/tick 等价。 */
    private static final float MAX_YAW_SPEED = 240f;
    private static final float MAX_PITCH_SPEED = 300f;
    /** 指数收敛系数：走路慢一点，显式转向快一点。 */
    private static final float GAIN_WALK = 10f;
    private static final float GAIN_TURN = 14f;
    /** 单帧 dt 上限：卡顿帧不产生大幅跳变。 */
    private static final float MAX_DT = 0.05f;
    private static final double MOUSE_OVERRIDE_PX = 2.0;
}
```

### 4.2 对外接口

| 方法 | 调用方 | 语义 |
|---|---|---|
| `setWalkTarget(float yaw)` | `PathFollower.tick` | mode=WALK（若当前不是 TURN）；只更新 yaw 目标 |
| `setTurnTarget(float yaw, float pitch)` | `TurnSession.tick` | mode=TURN；yaw+pitch 同时有效 |
| `applyManual(float yaw, float pitch)` | `GameActions.look/lookAt` | 立即写值 + mode=MANUAL |
| `resumeWalkIfManual()` | `PathFollower.start` | 新走向开始时把 MANUAL 交还给 WALK |
| `clear(String reason)` | 引擎停止 / 断连 / 会话结束 | mode=NONE，丢弃目标 |
| `onRenderFrame(MinecraftClient client)` | 帧回调 | 实际的平滑写入 |

### 4.3 帧回调实现（伪代码）

```java
public static void onRenderFrame(MinecraftClient client) {
    if (client.player == null || client.isPaused()) { lastFrameNanos = 0L; return; }

    long now = System.nanoTime();
    float dt = lastFrameNanos == 0L ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
    lastFrameNanos = now;
    dt = MathHelper.clamp(dt, 0f, MAX_DT);

    if (mode == Mode.NONE || mode == Mode.MANUAL) return;

    if (mouseMoved(client)) return;   // 本帧让位给玩家，下一帧继续

    float yawDiff = TurnMath.yawDiff(client.player.getYaw(), targetYaw);
    if (Math.abs(yawDiff) > DEAD_ZONE) {
        float gain = mode == Mode.TURN ? GAIN_TURN : GAIN_WALK;
        float alpha = 1f - (float) Math.exp(-gain * dt);
        float maxStep = MAX_YAW_SPEED * dt;
        client.player.setYaw(client.player.getYaw()
                + MathHelper.clamp(yawDiff * alpha, -maxStep, maxStep));
    }
    if (pitchActive) { /* 同上，用 MAX_PITCH_SPEED / 无死区外判 */ }
}
```

**dt 用 `System.nanoTime()` 而非 `getTickProgress()`**：参考实现的回绕兜底（`frameDelta <= 0` 时用 `progress`）不够精确；nanoTime 没有跨 tick 回绕问题，配合 `isPaused()` 跳过即可覆盖暂停场景。

### 4.4 参数验算（60 fps）

| 剩余角度 | 本帧步长 | 收敛时间 |
|---|---|---|
| 90° | 4.0°（限速） | ≈0.37 s |
| 60° | 4.0°（限速） | ≈0.25 s |
| 20° | 3.07° | ≈0.10 s |
| 5° | 0.77° | ≈0.10 s |
| <1.5° | 0 | — |

与现状（12°/tick，90° 约 8 tick = 0.4 s）速度相当，但每帧都在动。

---

## 五、修改点（逐文件）

### 5.1 `TurboMCClient.onInitializeClient`

新增一行注册（放在现有 `WorldRenderEvents.AFTER_ENTITIES` 附近）：

```java
WorldRenderEvents.START_MAIN.register(ctx -> ViewSmoother.onRenderFrame(MinecraftClient.getInstance()));
```

选 `START_MAIN` 的理由：它是每渲染帧最早能拿到的点（`AFTER_ENTITIES` 更晚，画面已经在画）。代价固定 1 帧延迟，见 §8。

### 5.2 `PathFollower`

| 位置 | 现状 | 改法 |
|---|---|---|
| `:326-329` | `if (!TurnSession.isActive()) player.setYaw(...)` | 删掉写入，改为 `ViewSmoother.setWalkTarget(yawTo(player, node))` |
| `:331-337` 站定分支 | 直接 return | 同样先 `setWalkTarget` 再 return（站定时也要转） |
| `:305-312` 贴住节点但高度未对齐 | 已 setForward(false) | 保持；yaw 目标继续保持当前节点 |
| `finish()` / `abort()` / `closeSession()` | — | 末尾 `ViewSmoother.clear("...")` |
| `start()` | — | `ViewSmoother.resumeWalkIfManual()` |

注意：`:315` 的 `diff` 仍用 `player.getYaw()` 计算（现在由帧层推进），`turning` 判定逻辑不变。

### 5.3 `TurnSession`

| 位置 | 改法 |
|---|---|
| `:95-97` `setYaw` / `setPitch` | 删除，改为 `ViewSmoother.setTurnTarget(s.yaw, s.pitch)` |
| `:86` 完成判定 | 不变（读 `player.getYaw()`，即帧层最新值） |
| `finish()` | `ViewSmoother.clear("turn")` |

副作用：完成判定最多延后 1 tick（因为帧层比 tick 细）。`waitLimitTicks` 有 20 tick 余量，不受影响。

### 5.4 `GameActions.look / lookAt`

```java
// look
ViewSmoother.applyManual((float) Values.asNumber(args[0]), (float) Values.asNumber(args[1]));
// lookAt 计算完 yaw/pitch 后
ViewSmoother.applyManual(yaw, pitch);
```

`applyManual` 内含：直接 `player.setYaw/setPitch` + `mode = MANUAL`。

### 5.5 引擎收尾

`ScriptEngine.stop()` / `restart()` / `halted` 分支、`TurboMCClient` 的 DISCONNECT 回调，各加一行 `ViewSmoother.clear(reason)`。

---

## 六、决策记录（已定）

### D5 = B：「看向」接管视角，走向照原目标走

实现：`applyManual` 写值后置 `mode = MANUAL`，`setWalkTarget` 在 MANUAL 期间只更新目标、不夺回归属，
直到该走向结束（`finish`/`abort`/`closeSession` 里的 `clear`）或下一次会话开始。

已知代价（选择时已确认）：移动方向由 yaw 派生，所以走向会把角色带向新朝向，可能偏离路径甚至撞墙。
若日后要改成"视线自由、移动不歪"，唯一可行的做法是 MANUAL 期间释放前进键（等价于 `turning` 分支），
那会变成 D5-C 的近似语义。

### D6 = A：鼠标只跳过当前帧

实现：帧回调比对「上一帧我们写下的 yaw」与当前 yaw，差 > 0.05° 即认定有外部写入
（鼠标 / 其它模组），本帧让位并把基准跟过去，下一帧继续。不用 `mouse.getX()`：
锁光标（FPS 模式）下光标坐标不一定变化，yaw 的变化才是可靠信号。

### D7 = 同批：`TurnSession` 一并改

已改：`tick` 不再写视角，改为 `setTurnTarget`；完成判定读帧层推进后的 yaw，最多延后 1 tick。

---

## 七、分批实施计划

| 批次 | 内容 | 验收 |
|---|---|---|
| 批 1 | `ViewSmoother` + `PathFollower` 接入 + 帧回调注册 + `clear` 收尾 | 走向拐弯无台阶；直线静止不抖 |
| 批 2 | `TurnSession` 接入 | 「平滑转向」观感与走向一致 |
| 批 3 | `look/lookAt` 仲裁（按 D5）+ 鼠标接管（按 D6） | 三处写入互不打架 |
| 批 4 | 参数调优 + 文档更新（`ARCHITECTURE.md:285-289`、`PATHFINDING.md:233` 两段要改写，它们现在描述的是"每 tick 写一次"） | 文档与实现一致 |

每批之间跑 `gradlew build` + 现有冒烟（`vmSmoke`、`vmKeyHat`、`vmChat`），确保 VM 侧无回归。

---

## 八、风险清单

| 风险 | 说明 | 缓解 |
|---|---|---|
| 1 帧延迟 | `START_MAIN` 在相机 update 之后，写入下一帧才被读到 | 16 ms，参考实现实测无感；若明显可改在 `START_CLIENT_TICK` 后的首个帧事件，但收益有限 |
| tick 与帧两套频率 | `turning` 阈值用的是帧层推进后的 yaw，可能比现在更早/更晚翻转 | 实测出现绕圈后已把 `STAND_TURN_DEG` 降到 30°（见下） |
| 绕圈（实机发现） | 边走边转的圆弧半径 R≈1.03 格，与 `NODE_REACH=0.5` 冲突，目标在 1 格附近时绕圈 | 站定阈值 75°→30°：速度归零则 R=0。详见 `PATHFINDING.md` §5.4.1 |
| 低帧率 | dt 被 clamp 到 50 ms，转向滞后 | 与现状相比不会更差（现状是 tick 固定步长） |
| 骑乘状态 | 原版在 `hasVehicle()` 时走插值分支，再叠加帧层写入 → 可能过冲 | 骑乘时降 GAIN 或直接不写；实测后定 |
| 「看向」语义 | 见 D5 | 待决策 |
| 第三人称 | 不影响逻辑，但观感差异需实测 | — |
| 帧回调线程 | 与 tick 同为主线程，无并发 | 不需要锁；若将来引入异步需重新评估 |

---

## 九、验证方案

### 9.1 手动验收（必须）

1. 直线长距离走向：视角应完全静止（`yawStep` 在死区内为 0）；
2. 90° 拐角：连续转动，无"3 帧一跳"；
3. 180° 回头：站定转向应连续，且不出现来回摆；
4. 走向中动鼠标：视角立即跟手，松手后恢复自动；
5. 引擎停止 / 断开连接：视角不再自动转。

### 9.2 可自动化的部分

新增 `ViewSmootherTest`（纯 JVM，不依赖 MC）：给定 dt 序列与初始角差，断言

- 单帧步长不超过 `MAX_YAW_SPEED * dt`；
- 剩余角差单调收敛；
- 死区内不再写入；
- dt 为 0 或负值时不动。

`gradle` 里挂到现有 `test` 任务即可，不需要新冒烟任务。

### 9.3 回归关注点

- `PathFollower` 的 `turning` / `checkStuck` / 分段重规划行为是否变化（视角写入被移除后，tick 里读到的是帧层值）；
- `TurnSession` 的完成判定是否延后导致 `waitLimitTicks` 不够（余量 20 tick，应该够）；
- 引擎停止后 yaw 是否残留（`clear` 是否覆盖所有退出路径）。

---

## 十、成本

| 项 | 量 |
|---|---|
| 新增文件 | 1（`ViewSmoother.java`，约 100 行含注释） |
| 修改文件 | 4（`TurboMCClient`、`PathFollower`、`TurnSession`、`GameActions`）+ 1（`ScriptEngine` 收尾） |
| 净增代码 | 约 140 行 |
| 新增测试 | 1（`ViewSmootherTest`，约 60 行） |
| 文档更新 | 2 处（ARCHITECTURE 285-289、PATHFINDING 233） |
| 分批 | 4 批，批 1 即可见效 |
