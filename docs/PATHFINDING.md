# 「走向」积木接入寻路（定稿 v4 · 已实现）

> 状态：**已实现**（批 0–5），随模组发布。§10.1 的 22 项验收清单是当时的记录，
> 未逐条回填结论——它描述的是「要验什么」，需求本身已由实现与日常使用覆盖。
> 适用：Minecraft 1.21.11 + Fabric Loader，yarn `1.21.11+build.6`，仅客户端。
> 正文所有「原版行为」均以本地 jar 字节码反查为准（`javap -c`），出处见 §3.1。

---

## 一、结论

把 `走向 x y z` 从「直线 + 估算时长」改成「**可中断的移动会话**」：

| 决策 | 采用 |
|---|---|
| 找路 | **手写 `LandPathNodeMaker` + `PathNodeNavigator` + `ChunkCache`**（路线 B），不用 `MobNavigation` |
| 走路 | 自写跟随器：原地转向 → 前进键 → 按住跳跃；玩家没有 `MoveControl`，这段躲不掉 |
| 到达 | 路径走完 + 水平 0.6 + 垂直 0.6（以路径终点为准，见 §5） |
| 失败 | 60 秒超时 / 卡住重算 3 次 / 半截路连续 3 次不前进 / 区块未加载 / 氧气不足 → 返回并松键 |
| 路线显示 | 沿路径画折线 + 当前节点框 + 终点框；会话中实时显示，结束后保留 3 秒（§6.5） |
| 够不到 | 半截路照走，走完以当前位置为新起点再算下一段；连续 3 次停在同一格才放弃（§6.3.1） |
| 积木 | `走向 x:[X] y:[Y] z:[Z]`，`opcode` 仍是 `turbomc_moveToward`（批 0 已完成） |

三条选型依据（详细对比见附录 A）：

1. 参考项目 areaautominer 的注释写明「**回退自 navigation.findPathTo 方案**」——路线 A 被实际踩塌过。
2. 路线 A 要打三个补丁（`setOnGround` / `nav.stop` / `setMaxFollowRange`），每个漏掉都是**静默**返回 null 或旧路径。
3. 路线 B 的唯一代价是多依赖两个内部类，换来"参数直给、无隐式前置条件"。

---

## 二、目标与不变量

### 目标

1. 到得了：绕墙、上 1 格台阶、走桥、游泳。
2. 说得出：返回值是**到达判定**，走不到时能放弃并让脚本继续。
3. 停得下：`停止移动`、关引擎、切工程、脚本里"停止全部"都能立刻收回控制权。

### 不变量

| 不变量 | 原因 |
|---|---|
| `opcode` 不变 | 已导出的 `.sb3` 全部依赖 `turbomc_moveToward` |
| 键统一走 `ScriptKeys` | `KeyBindingMixin` 从读取端接管；绕过它会被原版每 tick 刷新覆盖 |
| 一次会话只 `hold` 一次前进键 | 见 §八 第 1 条，否则键永远松不开 |
| 引擎停止必须 `releaseEveryKey()` | 否则角色继续走且无报错（ARCHITECTURE 约束 7） |
| 单帧不做多次 A\* | A\* 在 Java 侧，不占 Rhino 预算，直接叠加到该帧耗时 |
| 单会话 | `ScriptContext.currentInstance()` 恒 null，多会话会互相踩键 |

---

## 三、复用边界

```
        ┌──────────── 可复用 ────────────┐   ┌────── 不可复用 ──────┐
目标 ──▶│ PathNodeNavigator (A*)         │──▶│ MoveControl          │──▶ 实体位移
        │   + LandPathNodeMaker (地形)    │   │   + JumpControl      │
        └────────────────────────────────┘   └──────────────────────┘
                    ↓ Path 节点序列                  ↑ 玩家没有这两个东西
```

`PathNodeNavigator` / `LandPathNodeMaker` 是纯计算：输入 `ChunkCache` + `MobEntity`（只读碰撞箱与尺寸），输出节点序列。`MoveControl` 直接给实体写 `forwardSpeed / sidewaysSpeed / upwardSpeed`，而 `ClientPlayerEntity` 的位移来自 `KeyBinding` → `tickMovement`。

**复用边界：找路全借，走路全写。**

### 3.1 原版实现事实（字节码核对）

| 事实 | 依据 | 影响 |
|---|---|---|
| `PathNodeNavigator.findPathToAny(cache, mob, targets, maxRange, accuracy, multiplier)` 内部自己调 `nodeMaker.init(cache, mob)` 与 `nodeMaker.clear()` | 字节码：`PathMinHeap.clear` → `PathNodeMaker.init` → `getStart()` → 重载 → `PathNodeMaker.clear()` | 调用方**不需要**手动 init；`nodeMaker` 可跨次复用 |
| A\* 访问节点上限 = `(int)(navigator.range × multiplier)`，触顶即 `break` | 字节码：`getfield range; i2f; fload5; fmul; f2i` → 循环里 `if (++visited >= max) break` | 预算必须**按距离给**，给成"距离本身"会在 30 格绕行时撞顶 |
| `findPathToAny` 的第 4 个 float 参数是**"节点离起点的最大距离"**，不是路径长度上限 | 字节码：`node.getDistance(start)` 与该参数比较，超出就不再展开这个节点（`iflt` 跳去展开，否则回到循环头） | 传直线距离会把绕墙路径剪断——绕行必然比直线远。必须传缓存半径 |
| 触顶返回的是**部分路径**，不是 null | 同上：`break` 后照样组装 `Path` | 必须检查 `reachesTarget()`，否则"走半截 → 重算"死循环 |
| `MobNavigation.findPathTo` 路径上有三道隐式门槛 | `isAtValidPosition()` 假则 null；`getWorldChunk(cx,cz)` 为 null 则 null；搜索盒半径 = `(int)(max(FOLLOW_RANGE, maxFollowRange) + 8)` | 这是放弃路线 A 的直接原因（见附录 A） |
| 相同目标会复用 `currentPath` | `currentPath != null && !isFinished() && targets.contains(currentTarget)` → 返回旧 Path | 路线 A 必须每次 `nav.stop()`；路线 B 无此问题 |
| 节点足迹 = `floor(width+1) × floor(height+1)` | `PathNodeMaker.init` | 僵尸 `1×2`、玩家 `1×2` → **可行走判定完全相同** |
| `getSafeFallDistance()` 是常量 3 | `Entity.getSafeFallDistance`: `iconst_3; ireturn` | 玩家与僵尸一致；> 3 格落差无解，要改得 mixin |
| `Path.getNodePosition(entity,i)`：xz 格中心，y 就是节点 y | 字节码：`x + (int)(width+1)*0.5`，y 取 `node.y` | 瞄准点 = 脚部高度 |
| `Path.next()` / `isFinished()` / `getCurrentNodePos()` 是原版的游标 | `Path` 公开方法 | 路线 B 下每次规划都是新 `Path`，可放心用 `next()` |

### 3.2 外部依赖（升级时逐条复核）

| 用途 | 签名 |
|---|---|
| 节点生成 | `LandPathNodeMaker` + `setCanSwim` / `setCanWalkOverFences` |
| A\* | `PathNodeNavigator(PathNodeMaker, int)` + `setRange(int)` + `findPathToAny(ChunkCache, MobEntity, Set<BlockPos>, float, int, float)` |
| 世界快照 | `ChunkCache(BlockView, BlockPos, BlockPos)` |
| 读路径 | `Path.getLength()` / `getNodePos(int)` / `getCurrentNodePos()` / `next()` / `isFinished()` / `getEnd()` / `reachesTarget()` |
| 幻影实体 | `EntityType.create(World, SpawnReason)` / `refreshPositionAndAngles(...)` / `discard()` |
| 判断 | `Entity.getSafeFallDistance()` / `WorldView.isChunkLoaded(BlockPos)` |

---

## 四、类与职责

新包 `xyz.turbomc.client.script.action.path`：

| 类 | 职责 | 估计行数 |
|---|---|---|
| `MoveTarget` | 目标 `BlockPos` + 到达范围 + 超时 | 40 |
| `PathPlanner` | 幻影僵尸、`nodeMaker`/`navigator` 缓存、`findPathToAny`、区块守卫、耗时统计、节点打印 | 110 |
| `PathFollower` | 会话状态机、每 tick 驱动、原地转向、节点推进、按住跳跃、窗口式卡住、节点快照、中止 | 255 |
| `PathActions` | 解析三参数、参数校验、注册 `action.move_toward` | 70 |
| `PathRenderer`（`script/render`） | 世界渲染回调：读会话的节点快照，画折线与框 | 90 |

**单会话**：后来者顶掉前者（先 `abort()` 再建新会话）。

---

## 五、状态机与每 tick

```
IDLE ──start()──▶ FOLLOWING ──┬─到达───▶ ARRIVED
                              ├─走不到─▶ GAVE_UP     四态都让 poll 返回 true，
                              ├─超时───▶ TIMEOUT     脚本继续往下跑
                              └─被打断─▶ CANCELLED
```

### 5.1 会话字段

| 字段 | 含义 |
|---|---|
| `target` | `BlockPos`（x/y/z 全来自积木） |
| `path` / `nodeIndex` | 当前路径与游标（**路线 B 用 `path.next()`，不另存游标**） |
| `ticks` | 存活 tick 数 |
| `anchor` | 卡住检测锚点（窗口式，见 §5.6） |
| `stuckTicks` / `replans` / `lastReplan` | 卡住与重算计数 |
| `turning` | 正在原地转向（卡住检测豁免） |
| `forwardHeld` | 前进键当前是否持有（保证 hold/release 成对） |
| `state` | 五态之一 |

### 5.2 每 tick

> 1.21.11 的 `Entity` 没有返回 `Vec3d` 的 `getPos()`（只有 `getX/getY/getZ`），
> 下文用 `posOf(p)` 表示 `new Vec3d(p.getX(), p.getY(), p.getZ())`。实现里就是这么写的。

```java
void tick(MinecraftClient client) {
    if (session == null) return;
    ClientPlayerEntity p = client.player;
    if (p == null || client.world == null) { finish(CANCELLED); return; }

    session.ticks++;
    if (session.ticks > MAX_TICKS) { finish(TIMEOUT); return; }

    if (session.arrived(p)) { finish(ARRIVED); return; }
    if (posOf(p).squaredDistanceTo(session.lastPos) > 64) { finish(CANCELLED); return; }  // 传送

    if (session.path == null || session.path.isFinished() || session.needReplan()) {
        Path path = PathPlanner.plan(client.world, p, session.target);
        if (path == null || !path.reachesTarget()) { finish(GAVE_UP); return; }
        session.attach(path);
    }

    // ① 朝向：大角度先站定，小角度边走边转；转多少由比例收敛决定
    BlockPos node = session.path.getCurrentNodePos();
    if (node == null) { finish(GAVE_UP); return; }
    float diff = TurnMath.yawDiff(p.getYaw(), yawTo(p, node));
    if (Math.abs(diff) > STAND_TURN_DEG) {
        session.turning = true;
        setForward(client, false);                       // 松键，站定转
    } else {
        session.turning = false;
        setForward(client, true);
    }
    // 转向积木接管视角时让出 yaw，否则两边每 tick 各写一次、互相抵消
    if (!TurnSession.isActive()) {
        p.setYaw(p.getYaw() + TurnMath.yawStep(diff));
    }
    if (session.turning) return;

    // ② 节点到达 → 推进
    if (horizontalDistanceTo(p, node) < NODE_REACH) {
        session.path.next();
        if (session.path.isFinished()) { finish(ARRIVED); return; }
        node = session.path.getCurrentNodePos();
        if (node == null) { finish(GAVE_UP); return; }
    }

    setForward(client, true);
    setJump(client, shouldJump(p, node));
    checkStuck(p);

    session.lastPos = posOf(p);
}
```

> 前进键与跳跃键都用 `setForward/setJump` 包一层，内部判断状态再 hold/release。
> `hold` 现在是幂等的，重复调用不会卡键；保留这层包装是为了少做无谓的 `setPressed`，
> 也让 `forwardHeld` 这类状态位始终等于真实按键状态（原 §八 第 1 条已随之关闭）。

### 5.3 到达判定

```java
boolean arrived(ClientPlayerEntity p) {
    if (path == null) return false;
    PathNode end = path.getEnd();
    double dx = end.x + 0.5 - p.getX(), dz = end.z + 0.5 - p.getZ();
    return dx * dx + dz * dz <= 0.36 && Math.abs(p.getY() - end.y) <= 0.6;
}
```

以 `path.getEnd()` 而非用户给的 `target`：A\* 会把终点落在它认为可站的那一格，可能与用户给的坐标差一格；拿 `target` 硬比会「永远差一点」，一路重算到超时。

注意路线 B **没有** `MobNavigation.retargetToSolidBlock`（那是 `MobNavigation` 特有的向下吸附）。但到达判定用的是曼哈顿距离（`accuracy`），所以 y 偏 1 格时相邻格仍算命中，偏 2 格以上才真的够不着——§6.2 末尾的诊断日志是为后者准备的。

`start()` 另有幂等快判（0 tick 返回）：`isArrived(player, target)` 为真，即水平 0.6 且垂直 0.6 内。

### 5.4 转向与节点推进（原地转向）

```java
void setForward(MinecraftClient c, boolean want) {
    if (want == session.forwardHeld) return;              // 幂等：状态没变就不动
    if (want) ScriptKeys.hold(null, c.options.forwardKey, -1);
    else      ScriptKeys.release(null, c.options.forwardKey);
    session.forwardHeld = want;
}
```

转向的数学放在 `action/motion/TurnMath`，与「平滑转到视角」积木共用一份：每 tick 走掉剩余角度的
`GAIN`（45%），限幅 `MAX_YAW_STEP`（12°），剩余小于 `DEAD_ZONE`（1.5°）就不动。

| 参数 | 值 | 理由 |
|---|---|---|
| `STAND_TURN_DEG` | 75° | 超过 75° 先站定。90° 拐角改成边走边转，只有接近回头才停下身 |
| `TurnMath.GAIN` | 0.45 | 差 90° 时 40→限幅 12；差 20° 时 9；差 5° 时 2.2，自然减速 |
| `TurnMath.MAX_YAW_STEP` | 12° | 240°/s，接近正常人快速回头；30°/tick（600°/s）是明显的机器速度 |
| `TurnMath.DEAD_ZONE` | 1.5° | 节点方向在贴近时有噪声，没有死区会左右微摆 |
| `NODE_REACH` | 0.5 | 小于参考的 1.0（那会切角），大于 0.35（那会在拐角抖） |

**为什么必须比例收敛而不是固定角速度**：`ClientPlayerEntity.getYaw(float tickDelta)` 直接返回原始 yaw，
第一人称相机**不做 tick 内插值**，所以每一次 `setYaw` 的步长都当场可见；而恒定 30.000°/tick 在
反作弊里就是一条直线特征。

转向期间 `turning = true`，卡住检测直接豁免——否则"站定转身"会被判成"没在走"。

### 5.5 跳跃（按住，不是点按）

```java
boolean shouldJump(ClientPlayerEntity p, BlockPos node) {
    // 水下优先，且不受 3 格限制
    if (p.isTouchingWater()) return node.getY() >= p.getY() - 0.2;
    if (horizontalTo(p, node) > 3.0) return false;
    return node.getY() > p.getBlockPos().getY() && p.isOnGround();
}
```

与 v2 的 `hold(jump, 2)` + 冷却相比，**持续按住**更贴合原版"按住空格自动连跳"，上连续台阶不会一顿一顿。

水下的三档判断（详见 §5.7）：节点更高 → 上浮；同高 → 按住抵消下沉；低 0.2 以上 → 松手沉下去。

**不做冲刺跳**：A\* 按"僵尸不会冲刺跳"规划，路径里不会出现 3 格空档。

### 5.6 卡住检测（窗口式）

```java
void checkStuck(ClientPlayerEntity p) {
    if (session.turning) { session.anchor = posOf(p); session.stuckTicks = 0; return; }

    double sinceAnchor = horizontalDistance(session.anchor, posOf(p));
    if (sinceAnchor >= 0.5) {                              // 窗口内确有进展
        session.anchor = posOf(p);
        session.stuckTicks = 0;
        return;
    }
    if (session.lastPos.distanceTo(posOf(p)) < 0.02) session.stuckTicks++;

    if (session.stuckTicks >= STUCK_TICKS) {
        if (!session.wannaReplan()) { finish(GAVE_UP); return; }
        session.anchor = posOf(p);
        session.stuckTicks = 0;
        session.replans++;
        session.path = null;                               // 下一 tick 重算
    }
}
```

窗口式（锚点 + 累计 0.5 格）比"单 tick 位移"更抗缓慢蠕动：贴着墙一点点蹭的时候，单 tick 位移可能每 tick 都超过 0.02，但 10 tick 累计不到半格。

---

### 5.7 水下移动与氧气

玩家在水下**不能悬停**：要么按跳跃上浮，要么松开下沉。这决定了三条行为：

| 情形 | 行为 |
|---|---|
| 节点比玩家高 | 按住跳跃上浮 |
| 节点与玩家同高 | **按住跳跃**抵消下坠（否则沿水面水平游会一路沉底） |
| 节点比玩家低 0.2 以上 | 松开跳跃，靠重力下沉 |

第 2 条是必须的：原版水中只要不按跳跃，`travel` 的重力分支就会持续把人往下带，水平游等于边游边沉。

**氧气守卫**（每 tick 检查，仅 `isSubmergedInWater()` 时生效）：

```java
深度 = 水面 y − 玩家脚部格 y
余量 = clamp(深度 × 10 + 40, 80, 280)      // 满氧 300，所以上限压到 280
if (player.getAir() < 余量) → 中止并上浮
```

- 水面高度从玩家所在列向上找第一个非液体方块；每 10 tick 重采一次，不逐 tick 扫描。
- 每格深度按 10 tick 上浮（0.1 格/tick，保守），另加 40 tick 反应时间。
- 中止后进入**紧急上浮**：独立于会话，按住跳跃直到 `!isSubmergedInWater()` 或 200 tick。会话结束不等于安全——水里不按跳跃就继续下沉。

> **到达水下目标后不会自动上浮**。目标就是水下，浮起来等于没到；后续要不要换气由脚本自己决定。
> 注意玩家停在水中会持续耗氧，脚本若在水下作业需自行控制节奏。

## 六、规划器（路线 B）

### 6.0 参数与坐标

积木签名固定为三参数：

```js
{
    opcode: 'moveToward',                 // 不改：改名等于让所有已导出的 sb3 失效
    text: T('go to x:[X] y:[Y] z:[Z]', '走向 x:[X] y:[Y] z:[Z]'),
    arguments: { X: …, Y: …, Z: … }       // 顺序决定桥里 args 的下标
}
```

| 规则 | 说明 |
|---|---|
| `y` 的语义 | 目标**站立格**（玩家脚底所在方块格），不是脚下方块、也不是精确高度 |
| 参数顺序 | 桥按积木文本里 `[X] [Y] [Z]` 的顺序取值；改文本必须同步 `PathActions`，否则 Z 会读到 Y 上 |
| 缺参数 | 参数个数由积木签名固定为 3，长度检查几乎不触发；真正要防的是 **input 存在但为空**——桥给空串、`toNumber("")` 是 0，会让角色朝 y=0 走。三个值任一为空就忽略并警告一次，不猜高度 |
| 范围钳制 | x/z 钳到世界边界，y 钳到 ±1024，避免 `1e20` 造出荒谬的 `BlockPos` |

> 路线 B 的隐性代价：没有 `MobNavigation.retargetToSolidBlock`（目标在空气中时向下吸附）。
> 但到达判定只看曼哈顿距离，y 偏 1 格时相邻格仍算命中，偏 2 格以上才够不着。这里**不改目标**（y 由脚本作者给定，静默改掉更难查），
> 只在日志里点明"目标格不是可站立位置（本格空/上方空/脚下实心）"。

### 6.1 幻影僵尸

```java
private static ZombieEntity phantom;
private static World phantomWorld;

static ZombieEntity phantom(ClientWorld world, ClientPlayerEntity p) {
    if (phantom == null || phantomWorld != world) {
        phantom = EntityType.ZOMBIE.create(world, SpawnReason.MOB_SUMMONED);
        if (phantom == null) return null;
        phantomWorld = world;
        phantom.setSilent(true);
    }
    phantom.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0f);
    return phantom;
}
```

- 不进世界、不 tick、不参与碰撞、不发包，只被 `PathNodeMaker` 读尺寸与位置。
- **不需要 `setOnGround(true)`**：那是 `EntityNavigation.isAtValidPosition()` 的要求，路线 B 不经过它。
- 只造一次：构造会跑 `initGoals`。
- 尺寸差不用补偿：节点足迹 `1×2` 与玩家完全相同（§3.1）。
- 换世界（进下界/回主世界）时 `discard()` 旧的再重建。

### 6.2 规划调用

```java
private static LandPathNodeMaker nodeMaker;
private static PathNodeNavigator navigator;

static Path plan(ClientWorld world, ClientPlayerEntity p, BlockPos target) {
    if (!world.isChunkLoaded(target)) { log("目标区块未加载"); return null; }

    double dist = Math.sqrt(p.squaredDistanceTo(Vec3d.ofCenter(target)));
    if (dist > MAX_DISTANCE) { log("目标太远 " + (int) dist); return null; }

    ZombieEntity mob = phantom(world, p);
    if (mob == null) return null;

    // 这个数同时是 ChunkCache 的覆盖半径和 A* 的 maxRange（既限欧氏距离，也限累计路径代价）。
    // 按"路径能有多长"给，不按"目标有多远"给：绕行+起伏的单步代价能到 3~4。
    int span = MathHelper.clamp((int) Math.ceil(dist * 2.5) + 32, 48, 200);
    BlockPos base = p.getBlockPos();
    ChunkCache cache = new ChunkCache(world,
            base.add(-span, -span, -span), base.add(span, span, span));

    if (nodeMaker == null) {
        nodeMaker = new LandPathNodeMaker();
        nodeMaker.setCanSwim(true);              // 玩家会游泳（参考项目设的是 false，矿区不需要）
        nodeMaker.setCanWalkOverFences(false);   // 玩家翻不过栅栏，与原版一致
    }
    int budget = MathHelper.clamp((int) (dist * NODE_BUDGET_PER_BLOCK), NODE_BUDGET_MIN, NODE_BUDGET_MAX);
    if (navigator == null) navigator = new PathNodeNavigator(nodeMaker, budget);
    else if (navigatorBudget != budget) navigator.setRange(budget);
    navigatorBudget = budget;

    long t0 = System.nanoTime();
    // 第 4 个参数是「节点离起点的最大距离」，不是路径长度上限。
    // 传直线距离会把绕墙的路径剪断（绕行必然比直线远），必须给够半径。
    Path path = navigator.findPathToAny(cache, mob, Set.of(target),
            (float) span, ACCURACY, 1.0f);
    logPlan(path, (System.nanoTime() - t0) / 1_000_000L);
    return path;
}
```

| 参数 | 值 | 理由 |
|---|---|---|
| `NODE_BUDGET_PER_BLOCK` | 14 | 预算随距离线性放大。给"距离本身"（参考项目）只有原版 1/10，撞顶概率高 |
| `NODE_BUDGET_MIN/MAX` | 128 / 3000 | 近目标不至于太小，远目标不至于吃满帧 |
| `SPAN_PER_BLOCK / MARGIN` | 2.5 / 32 | **覆盖半径 = 距离×2.5+32，同时是 A\* 的 `maxRange`**。`maxRange` 不只限欧氏距离，还限 `successor.pathLength`（累计路径长度）；按 1.6 倍给会在起伏/绕行地形中途剪断，表现为"明明有路却停在中途" |
| `SPAN_MIN/MAX` | 48 / 200 | 三者必须一致：缓存覆盖半径、欧氏上限、路径代价上限。缓存小于代价上限时，缓存外全是空气，A\* 会从那里穿墙 |
| `ACCURACY` | 1 | 到达容差（曼哈顿）。**不再按距离放宽**：远处目标本来就分段靠近，放宽只会让终点停在离目标两格处，与精确坐标语义冲突 |
| `SLOW_PLAN_MS` | 15 | 单次规划超过它就打 warn，这是唯一会掉帧的地方 |
| `setRange` 复用 navigator | — | 避免每次规划都 new，构造没有别的状态 |

> 路线 B 的隐性代价：没有了 `MobNavigation.retargetToSolidBlock`（"目标在空气中时向下吸附"）。
> 但到达判定只看曼哈顿距离，y 偏 1 格仍可能命中，偏 2 格以上才够不着。实现里**不改目标**（y 由脚本作者给定，
> 静默改掉更难查），只在日志里点明"目标格不是可站立位置（本格空/上方空/脚下实心）"。

### 6.3 重算策略

**只在事件触发时重算，不做周期性全量重算。**

| 触发 | 说明 |
|---|---|
| 会话开始 | `Session.wantReplan` 初值为 true，第一 tick 必算一次 |
| 卡 10 tick | 局部与预期不符。重算前要求距上次 ≥ 20 tick，否则这一轮跳过、10 tick 后再试 |
| **走完半截路** | `Plan.reached() == false` 时走到最后一个节点不算到达，置 `wantReplan` 接着算下一段 |

不做周期性重算的原因：跟随器每 tick 都朝着当前节点转向，玩家被推开几格后会自己走回原路线；而每 40 tick 重算一次会让 `index` 归零、路线起点标记跳回开头，视觉上反而更乱。

两次规划间隔 ≥ **20 tick**。A\* 不占 Rhino 的 JS 预算，是直接叠加到该帧的耗时；每 tick 都算会让画面周期性顿一下。

#### 6.3.1 半截路先走（分段寻路）

`PathPlanner.plan` 返回 `Plan{nodes, reached}`。`reached == false` 时 A\* 只给了"够得着的一段"，
跟随器照走，到段尾以当前位置为新起点再算下一段：

- **省在搜索量上**：每段的覆盖半径按**剩余距离**缩放（`span = 剩余 × 2.5 + 32`），
  探索量是分段累加而不是一次性铺开一整条 128 格的路径；走一段之后起点前移，下一段的半径和预算都同比变小。
- **必须区分完整与半截**：少了 `reached` 标志，走到半截最后一格会被 `isArrived(player, end)` 判成到达。
- **收敛靠进度闸**：连续 `MAX_SAME_END = 3` 次停在同一格 → 放弃（说明 A\* 已经卡在那面墙前）；
  总段数上限 `MAX_PARTIAL_REPLANS = 16`，总时长仍由 `MAX_TICKS = 1200` 兜底。
- **一步没迈出去的半截**（`nodes.size() <= 1`）直接判无解，不让调用方空转。

日志分工：`[path] 半截路：… 先走这一段` 是分段开始，`[path] 到达：… 分段 N 段` 是收尾，
`[path] 放弃：… 原因：半截路不再前进` 是撞墙。

### 6.4 未加载区块

原版 `ChunkCache` 对未收到的区块给空区块（全空气），A\* 会把未加载区当开阔平地，给出穿墙路径。三层守卫：

1. 目标：`isChunkLoaded(target)` 为假 → 放弃（并为日志留下原因）。
2. 距离：`MAX_DISTANCE = 128`，保证目标落在搜索半径内、且在客户端已加载区域。
3. 节点：逐节点查 `isChunkLoaded`，遇到第一个未加载节点 → **放弃**（不截断）。

第 3 条不截断的原因不是"会循环"（现在半截路会照走），而是**截断点之后的地形是空的**：
客户端把未加载区当空气，走过去会掉进虚空。代价是"路径擦过未加载区块"时放弃整段而不是走前半段——这是有意的保守。

### 6.5 路线显示

参考项目是把**每个节点画成一个 1×1×1 的框**（`RegionRenderer.renderPath`，青色节点 + 黄色终点）。能看清节点，但 30 个节点就是 30 个方框，密的路径会糊成一片。本方案改成**折线为主、框为辅**。

#### 画什么

| 元素 | 样式 | 用途 |
|---|---|---|
| 折线 | 相邻节点中心的连线，青色 `0xFF55FFFF` | 一眼看出整条路线 |
| 当前节点 | 1×1×1 框，白色 | 看出跟随器卡在哪一节 |
| 终点 | 1×1×1 框，黄色 `0xFFFFFF00` | 与用户给的目标对照（A\* 可能吸附过） |

线层用 `RenderLayers.lines()`，**带深度测试**：路线被墙挡住的部分看不见——这是有意的，能直接暴露"路径穿墙"。

#### 数据流

```
PathPlanner 出 Path
   → PathFollower.attach() 时构建 float[] 快照（每节点 x/y/z，已含 +0.5 中心）
   → PathRenderer 每帧只读这个数组，不在渲染线程构造对象
```

**不在渲染里遍历 `Path`**：`Path` 的节点数组是可变对象，且渲染线程与客户端线程异步；快照是唯一安全且零分配的读法。

#### 渲染回调

```java
// TurboMCClient.onInitializeClient()
WorldRenderEvents.AFTER_ENTITIES.register(PathRenderer::render);
```

```java
public static void render(WorldRenderContext ctx) {
    PathFollower.NodeView view = PathFollower.nodeView();     // 不可变快照 + 状态
    if (view == null || ctx.consumers() == null) return;
    if (view.world() != MinecraftClient.getInstance().world) return;

    Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
    if (cam.squaredDistanceTo(view.center()) > RENDER_DISTANCE * RENDER_DISTANCE) return;

    MatrixStack matrices = ctx.matrices();
    matrices.push();
    matrices.translate(-cam.x, -cam.y, -cam.z);

    drawPolyline(ctx, matrices, view.points());                        // 折线
    drawOutline(ctx, matrices, view.currentNode(), 0xFFFFFFFF);        // 当前节点
    drawOutline(ctx, matrices, view.endNode(),   0xFFFFFF00);          // 终点

    matrices.pop();
}

private static void drawPolyline(WorldRenderContext ctx, MatrixStack m, float[] pts) {
    VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.lines());
    MatrixStack.Entry e = m.peek();
    for (int i = 0; i + 5 < pts.length; i += 3) {
        segment(vc, e, pts[i], pts[i+1], pts[i+2], pts[i+3], pts[i+4], pts[i+5], 0xFF55FFFF);
        if (i > 0) segment(vc, e, pts[i], pts[i+1], pts[i+2], pts[i-3], pts[i-2], pts[i-1], 0xFF55FFFF);
    }
}

private static void segment(VertexConsumer vc, MatrixStack.Entry e,
                            float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
    vc.vertex(e, x1, y1, z1).color(argb).normal(e, 0f, 1f, 0f);
    vc.vertex(e, x2, y2, z2).color(argb).normal(e, 0f, 1f, 0f);
}

private static void drawOutline(WorldRenderContext ctx, MatrixStack m, BlockPos pos, int argb) {
    if (pos == null) return;
    VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.lines());
    VertexRendering.drawOutline(m, vc, VoxelShapes.cuboid(new Box(pos)), 0d, 0d, 0d, argb, LINE_WIDTH);
}
```

已核对的 API（本机 fabric-rendering-v1 16.2.10 / 1.21.11）：

| 用途 | 签名 |
|---|---|
| 事件 | `WorldRenderEvents.AFTER_ENTITIES`（`net.fabricmc.fabric.api.client.rendering.v1.world`） |
| 上下文 | `WorldRenderContext.matrices()` / `.consumers()` |
| 写顶点 | `VertexConsumer.vertex(MatrixStack.Entry, float, float, float)` / `.color(int)` / `.normal(Entry, float, float, float)` |
| 线层 | `RenderLayers.lines()`（还有 `linesTranslucent()`） |
| 画框 | `VertexRendering.drawOutline(MatrixStack, VertexConsumer, VoxelShape, double, double, double, int, float)` |

#### 显示时机

| 状态 | 显示 |
|---|---|
| 会话进行中 | 显示，每 tick 跟随当前节点 |
| 到达 / 放弃 / 超时 / 中止 | **再保留 60 tick（3 秒）**，然后消失 |
| 无会话 | 不渲染 |

保留 3 秒是为了能回头看"刚才走的是哪条路"——到达瞬间就消失等于没法验证。实现上 `finish()` 记一个 `fadeTicks`，`tick()` 里递减，归零则清空快照。

#### 开关与开销

- 配置项 `showPath`（默认 `true`），关掉后 `render` 首行直接 return。
- 每帧开销：一条 ≤64 段的折线（约 130 个顶点）+ 2 个框，可忽略。
- 距离剔除 128 格；跨维度不渲染（快照记录所属 `World`，与当前世界不相等即跳过）。
- 折线在**渲染线程**读快照，快照在**客户端线程**写入：用不可变对象整体替换（`volatile NodeView`），不做字段级共享。

---

### 6.6 朝向平滑积木

两个新增积木与寻路共用同一套步进数学（`TurnMath`），区别只是**由谁提供目标角度**：

| 积木 | opcode | 目标角度来源 |
|---|---|---|
| 平滑转到视角 水平:[YAW] 俯仰:[PITCH] | `turbomc_lookSmooth` | 参数直接给 |
| 平滑看向 x:[X] y:[Y] z:[Z] | `turbomc_lookAtSmooth` | 眼睛到目标点的向量算出来 |

两者都**阻塞**：`requestWaitUntil("turn", 60)`，收敛到死区或 40 tick 超时后返回。不阻塞的话，
下一块积木会立刻把朝向覆盖掉，等于白转。

`TurnSession` 的要点：

1. **只走最短路径，且不归一化**。目标 yaw 换算成 `current + wrapDegrees(want - current)`，
   yaw 保持连续累加。若收敛后把它 wrap 回 [-180, 180]，yaw 从 370 变 10 会被服务端看成一次
   360° 瞬转——那正是我们要避免的特征。
2. **pitch clamp 到 [-90, 90]**，并按 10°/tick 限速。
3. **目标与眼睛重合时不动**。零向量归一化得到 NaN，而 `setYaw` 遇到 NaN 会走
   `Util.logErrorOrPause`（开发环境直接抛异常）。瞬时的「看向」也补了同样的保护。
4. **与寻路互斥**：`TurnSession.isActive()` 为真时，`PathFollower` 不再写 yaw，只继续走位。

### 6.7 HUD 状态行

寻路进行时，HUD 在引擎状态行下面加两行（`showPathHud`，默认开）：

```
● 你的脚本
寻路 42.3 格 → (120, 65, -38)
节点 18/37 · 重算 0 · 分段 3 · 规划 3ms · 跟随
```

| 项 | 取值 |
|---|---|
| 第一行 | 剩余水平距离（0.1 格精度）+ 目标坐标 |
| 第二行 | 节点进度 / 重算次数 / 分段次数 / 上次规划耗时 / 阶段 |
| 阶段 | `planning` `follow` `turning` `arrived` `gave_up` `timeout` `cancelled` |
| 颜色 | 进行中青色 `0xFF55FFFF`；到达绿色；其余橙色 |

三条实现约束：

1. **数据只读快照**。`PathFollower.status()` 返回 record，HUD 不进去问会话状态机；
   阶段标识是英文 key，人类可读文本走翻译键 `hud.turbomc.path.phase.*`。
2. **文本变化才重建**。距离每 tick 都在变，但取到 0.1 格后约 5 tick 才变一次；
   中间这些帧复用同一对 `Text` 对象，否则每帧几十个临时对象会喂 GC。
3. **没有会话就不画**，不显示「空闲」。结束后会话在淡出期仍存在，所以自然保留 3 秒。
4. **「重算」与「分段」是两回事**，必须分开显示：`replans` 是卡住重算（原地打转），
   `partialReplans` 是半截路的分段次数（够不到目标、分段靠近）。只显示前者会出现
   「重算 0」却其实已经走了好几段，看不出发生过什么。分段数为 0 时用不带该字段的
   短文案（`hud.turbomc.path.progress` / `hud.turbomc.path.progress.partial`）。

---

## 七、接线

### 7.1 阻塞与 poll

```java
// PathActions.moveToward
if (!PathFollower.start(client, target)) return;   // 已在目标内 / 目标在未加载区
ctx.requestWaitUntil("move", MAX_TICKS + 40);       // 比 Java 侧多 40 tick
```

**JS 上限要比 Java 大**：`__tmcWaitUntil` 到点会**自行 resolve**（不通知 Java）。两边相等时 JS 先放行，脚本继续跑而角色还在走；多留 40 tick 让 Java 的 `TIMEOUT` 先发生。

```java
public static boolean poll(String key) {
    if ("move".equals(key)) return PathFollower.finished();
    if (!"break".equals(key)) return true;
    return pollBreak();
}
```

`finished()` **读后不复位**，终态保持到下一次 `start()`。

### 7.2 驱动时机

```java
// ScriptEngine.tick 末尾
vm.tick(client);
PathFollower.tick(client);   // 本 tick 新启动的会话立刻走第一步
```

`ScriptEngine.tick` 挂在 `START_CLIENT_TICK`，早于 `tickMovement`，按键本 tick 生效；放 `vm.tick` 之后是因为会话在 `vm.tick` 里启动。

### 7.3 中止

| 入口 | 处理 |
|---|---|
| `停止移动` 积木 | `abort("停止移动")` |
| `ScriptEngine.stop()` | `abort()`（`releaseEveryKey()` 只是兜底，会话状态必须清） |
| `ScriptEngine.restart()` | `abort()` |
| `halted`（脚本"停止全部"） | `tick` 的 `halted` 分支补一次 `abort()`——该分支直接 return，不补就是键永不松开 |
| 死亡 / 掉线 / 换维度 | `player == null`、位置突变判定 |

`abort()`：`setForward(false)`、`setJump(false)`、状态置 `CANCELLED`。**用 setter 而不是裸 release**，让 `forwardHeld` 这类状态位同步复位。

### 7.4 阻塞时长的变化

| 场景 | 现在 |
|---|---|
| 已在目标 | 0 tick 返回 |
| 直线可达 | 按实际行走，通常比原估算短（实际 0.216 格/tick） |
| 走不到 | 60 秒后放弃并松键，不再"估算时长走完就放行" |
| `重复执行直到 <到达> { 走向 }` | 第二次仍在范围内则立刻返回，不重建会话 |

---

## 八、必须避开的 7 个坑

1. ~~**`ScriptKeys.hold` 是引用计数，不能每 tick 调。**~~ **（2026-11-08 已关闭）** 原来每调一次 `refs++`、`ticks=-1` 又不会自然到期，跑满 400 tick 就是 400 次引用，`abort` 只减 1 → 键永远按着。现在 `hold` 改为幂等（重复按下只延长到期时间），`refs` 字段随之删除；`setForward/setJump` 的边沿式调用保留，理由变成"少做无谓的 `setPressed`"。
2. **`reachesTarget()` 必须带出来。** 触顶返回的是半截路径，不区分就会把半截终点当成到达；现在照走（§6.3.1），靠进度闸防打转。
3. **重算必须节流。** A\* 不受 Rhino 预算约束，每 tick 都算等于把几十毫秒压进单帧。
4. **JS 等待上限必须大于 Java 超时。** 否则 JS 先放行，脚本往下跑而角色还在走。
5. **`halted` 分支要 abort。** 该分支直接 return，跟随器不再被驱动。
6. **转向期间要豁免卡住检测。** 否则"站定转身"被算成"没在走"，10 tick 后误判卡住。
7. **不要用 `Path.setCurrentNodeIndex` 与自己的游标并行。** 路线 B 只用 `path.next()`，两套索引会打架。

---

## 九、改动清单

| 文件 | 改动 | 估计 |
|---|---|---|
| `turbowarp/turbomc-extension.js` | **已完成**：文本 x/y/z + 参数表加 Y | 完成 |
| `assets/turbomc/vm/turbomc-extension.es5.js` | **已完成**：同上 | 完成 |
| `block/Blocks.java` | **已完成**：`走向 ({X}, {Y}, {Z})` | 完成 |
| `action/GameActions.java` | **已完成**：`moveToward` 移交给 `PathActions` | 完成 |
| `action/path/MoveTarget.java` | 新增 | +40 |
| `action/path/PathPlanner.java` | 新增 | +110 |
| `action/path/PathFollower.java` | 新增（含节点快照 + 3 秒保留） | +255 |
| `action/path/PathActions.java` | 新增 | +70 |
| `script/render/PathRenderer.java` | 新增：折线 + 当前节点框 + 终点框 | +90 |
| `TurboMCClient.java` | 注册 `WorldRenderEvents.AFTER_ENTITIES` | +3 |
| `config/TurboMCConfig.java` | 加 `showPath = true` | +6 |
| `config/TurboMCConfigScreen.java` | 加一个开关项 | +8 |
| `action/GameActions.java` | `moveToward` 改为委托 `PathActions.start` | -35 / +5 |
| `vm/ScriptBridge.java` | `poll` 加 `"move"` / `"turn"` 分支 | +8 |
| `ScriptEngine.java` | tick 末尾驱动；`stop` / `restart` / `halted` 三处 abort | +10 |
| `Sb3Mapping.java` | **不改**（opcode 未变） | 0 |
| 文档 | 本文件；`ARCHITECTURE.md` 加约束 13 + 渲染出口 | +40 |
| **合计（未完成部分）** | | **约 +640 / -35** |

---

## 十、批次与验收

| 批 | 内容 | 交付 |
|---|---|---|
| 批 0 ✅ | 积木三参数、`Blocks.java`、`GameActions` 读三参数 | 已通过 `./gradlew build` |
| 批 1 ✅ | 跟随器：原地转向 + 按住跳跃 + 窗口卡住 + 可中断 + 节点快照接口 | 已实现（`PathFollower`） |
| 批 2 ✅ | 接 A\*：`PathPlanner` + 节点驱动 | 已实现（`PathPlanner`） |
| 批 3 ✅ | 路线显示：`PathRenderer` + 事件注册 + `showPath` 配置 | 已实现（`PathRenderer`） |
| 批 4 ✅ | 半截路先走：`Plan.reached` + 分段重算 + 进度闸 | 已实现（`PathFollower`） |
| 批 5 ✅ | 水下：水中三档跳跃 + 按深度的氧气守卫 + 紧急上浮 | 已实现（`PathFollower`） |

批 3 只依赖批 1 定下的 `nodeView()` 快照接口，**与批 2 无耦合**，两条可并行开发。
批 4 依赖批 2 的 `Path` 结果，改的是「拿不到完整路径时怎么办」。
批 5 独立于前四批，只改 `PathFollower` 的按键与水下线。

### 10.1 验收

| # | 场景 | 期望 | 批 |
|---|---|---|---|
| 1 | 12 格平地 | ≤ 4 秒到达，重算 0 次 | 1 |
| 2 | 连续 3 个 1 格台阶 | 到达，不出现"走走停停" | 1 |
| 3 | 目标在身后 180° | **先站定转身再走**，不撞墙 | 1 |
| 4 | 目标 20 格外、四面围死 | ≤ 60 秒放弃（或卡住重算 3 次）、松键、脚本继续 | 1 |
| 5 | 走路中按 F6 关引擎 | 立刻停步 | 1 |
| 6 | 2 格高墙，目标在墙后 8 格 | 绕行到达 | 2 |
| 7 | 目标 60 格外 | 立刻放弃，日志「目标太远」 | 2 |
| 8 | 单次规划耗时 | 近距离 < 3 ms；> 15 ms 会在日志里 warn | 2 |
| 9 | 目标 y 在玩家上方 4 格且可达 | 上到该层才判到达 | 2 |
| 10 | 目标 y 在玩家下方 20 格 | 无解 → 放弃（落差上限 3） | 2 |
| 11 | 手改坏的三参数积木 | 忽略并警告一次，不乱走 | 0 ✅ |
| 12 | 绕墙行走 | 折线贴着墙走，被遮挡段看不见（深度测试生效） | 3 |
| 13 | 到达后 | 路线保留 3 秒再消失 | 3 |
| 14 | `showPath=false` | 完全不画，帧率无变化 | 3 |
| 15 | 走远到 128 格外 | 路线不再渲染（距离剔除） | 3 |
| 16 | **半截路分段**：目标 60 格外、地形遮挡 | 分段 2~3 次后到达；日志有多次「半截路 … 先走这一段」与一条「到达：… 分段 N 段」 | 4 |
| 17 | **半截路不再前进**：目标在封闭空间 | 连续 3 次同一终点后放弃，日志「半截路不再前进」；不空转、不抖动 | 4 |
| 18 | 分段行走过程 | 段与段之间不出现明显停顿（每段最多 1 tick 转向） | 4 |
| 19 | **浅水横渡**（4~6 格宽、2 格深） | 不下沉、不抖动，正常穿过 | 5 |
| 20 | **水下目标**（水下 6 格） | 沉到目标层并判到达；氧气未耗尽 | 5 |
| 21 | **深水目标**（水下 25 格以上） | 氧气余量不足时中止，日志写明深度与剩余氧气；随后自动上浮出水 | 5 |
| 22 | 中止后观察 3 秒 | 玩家浮出水面并停住，跳跃键已松开（不再继续上飘） | 5 |

### 10.2 性能预算

- A\*：< 3 ms/次，≥ 20 tick 一次 → 平均 < 0.15 ms/tick。
- 跟随器：每 tick 约 30 次浮点运算，可忽略。
- 路线显示：每帧 ≤64 段折线（约 130 顶点）+ 2 个框；不在渲染线程分配对象，可忽略。
- 内存：幻影僵尸 1 个 + `Path` 1 条 + 每次规划一个 `ChunkCache`（半径 64 → 约 100 个区块引用）+ 节点快照 `float[3N]`。
- 与实体数量无关。
- 结论须附实测：**1.21.11 + Windows 客户端 + 单脚本**，5 次规划取中位数。

---

## 十一、风险与不支持

| 风险 | 影响 | 应对 |
|---|---|---|
| `LandPathNodeMaker` / `PathNodeNavigator` / `ChunkCache` 签名随版本变 | 编译失败（好）或语义变（坏） | 依赖只有 §3.2，逐条复核 |
| 客户端世界 ≠ 服务端世界 | 竹子、细雪、脚手架判定不一致，能走却被拉回 | 重算 3 次放弃 + 日志 |
| 反作弊特征 | 等间隔跳、每 tick 精确朝向 | 转向限速 + 只在需要时按跳 |
| 传送 / 换维度 | 路径前提失效 | 位置突变 > 8 格 → `CANCELLED` |
| 落差 > 3 格 | 「跳下去就能到」却放弃 | `getSafeFallDistance()` 常量 3，僵尸同样走不了 |
| 目标 y 填在空中 / 墙里 | 偏差 1 格内仍算命中（曼哈顿容差），2 格以上够不着 | 以 `path.getEnd()` 判到达 + `explainTarget` 日志 |
| **水下目标** | 玩家不能悬停，必然沉底耗氧；深水可能还没到底就中止 | 按深度给氧气余量（§5.7），不足即中止并自动上浮；超过约 28 格深的目标本质上到不了 |

**v1 不做**：开门、爬梯、坐船/矿车、冲刺跳、逐帧重规划。

---

## 十二、常量

| 常量 | 值 | 依据 |
|---|---|---|
| `MAX_TICKS` | 1200 | 60 秒。128 格 × 0.216 ≈ 592 tick；路径长度按直线 1.6 倍计 ≈ 950，再留分段停顿 |
| `MAX_DISTANCE` | 128 | 客户端视距 16 区块 = 256 格，128 一定已加载 |
| `SPAN_PER_BLOCK / MARGIN` | 2.5 / 32 | 覆盖半径 = 距离×2.5+32，同时是 `maxRange`（欧氏上限 + 路径代价上限） |
| `SPAN_MIN / MAX` | 48 / 200 | 与 ChunkCache 覆盖范围严格一致 |
| `NODE_BUDGET_PER_BLOCK` | 14 | 预算随距离线性放大 |
| `NODE_BUDGET_MIN / MAX` | 128 / 3000 | 下限够近目标分支，上限不超单帧预算 |
| `ACCURACY` | 1 | 到达容差（曼哈顿），不是节点粒度 |
| `NODE_REACH` | 0.5 | 参考 1.0 会切角，0.35 会在拐角抖 |
| `STAND_TURN_DEG` | 75 | 超过即站定转身，否则边走边转 |
| `TurnMath.GAIN / MAX_YAW_STEP` | 0.45 / 12° | 比例收敛；90° 拐角逐 tick 为 12,12,11.8,9.2,5.4,2.4,0 |
| `TurnMath.MAX_PITCH_STEP` | 10° | 俯仰视觉更敏感，限得低一档 |
| `STUCK_TICKS` / `MAX_REPLANS` | 10 / 3 | 10 tick 无进展重算，3 次放弃 |
| `REPLAN_INTERVAL` | 20 | 两次规划最小间隔 |
| `PATH_RENDER_DISTANCE` | 128 | 超过就不画，避免远处小方框糊成噪点 |
| `PATH_KEEP_TICKS` | 60 | 结束后保留 3 秒，便于回看路线 |
| `LINE_WIDTH` | 2.0 | 与参考实现一致 |

---

## 十三、决策记录

**2026-11-07：全部按推荐通过，并追加 D9（路线显示）。**

| # | 决策 | 结论 | 备选（已否决） | 影响 |
|---|---|---|---|---|
| D1 | 寻路入口 | ✅ 路线 B（手写 navigator） | 路线 A（`MobNavigation` + 3 个补丁） | 少 3 个静默失败点 |
| D2 | 转向 | ✅ 先站定转身（>30°） | 边走边转 | 反向时不撞墙 |
| D3 | 跳跃 | ✅ 持续按住 | 点按 2 tick + 冷却 | 连续台阶更平顺 |
| D4 | 卡住判定 | ✅ 窗口式（锚点 + 0.5 格） | 单 tick 位移 | 抗贴墙蠕动 |
| D5 | 超时 | ✅ 1200 tick（60 秒） | 600（30 秒） | 128 格走完就要 592 tick |
| D6 | 放弃时弹「说」气泡 | ✅ 不弹 | 弹一次带原因 | 不刷 HUD |
| D7 | 玩家手动干预 | ✅ 不中止 | 立刻中止 | 行为可预测 |
| D8 | `停止移动` | ✅ 同步中止会话 | 只松键 | 不会被跟随器按回去 |
| D9 | **寻路时显示路线** | ✅ 折线 + 当前节点框 + 终点框，结束后保留 3 秒 | 逐节点框（参考做法）/ 不显示 | 见 §6.5 |

D1–D9 全部已写进正文，无待定项。

---

## 附录 A：与参考实现（areaautominer-1.21.11）的对比

参考代码只有两处：`helper/PathfindingHelper.java`（94 行，找路）、`helper/MovementHelper.java`（199 行，走路）。其类注释原文：**「该实现为回归验证过的可用基线（回退自 navigation.findPathTo 方案）」**。

### A.1 寻路入口

| 维度 | 参考 | 旧版 v2（路线 A，已否决） | 结论 |
|---|---|---|---|
| 入口 | 手写 `LandPathNodeMaker`+`PathNodeNavigator`+`ChunkCache` | `MobNavigation.findPathTo` | 参考结构性绕开 3 个隐式条件 |
| 要绕的坑 | 无 | `setOnGround` / `nav.stop` / `setMaxFollowRange` | 路线 A 的补丁漏一个就静默失败 |
| 搜索半径 | `max(32, 距离+2)` clamp `[16,64]` | 固定 40，搜索盒由原版算 48 | 参考可到 64 |
| A\* 节点预算 | **等于距离（16~64）** | 原版 `followRange×16 ≈ 640` | 参考只有原版 1/10，30 格绕行必撞顶 |
| 精度 | 1 | 1 | 一致 |

### A.2 执行与判定

| 维度 | 参考 | v2 | 结论 |
|---|---|---|---|
| 驱动 | 非阻塞，每 tick 取 `WalkResult` | 阻塞，Promise + `poll` | 场景不同 |
| 游标 | `path.next()` | 自维护 `nodeIndex` | 路线 B 下 `next()` 更省事 |
| 节点阈值 | 1.0 | 0.35 | 取中间 0.5 |
| 转向 | 先站定转身 + `turningInPlace` 豁免 | 边走边转 | **参考更好，已采纳** |
| 跳跃 | 按住 | 点按 + 冷却 | **参考更顺，已采纳** |
| 到达 | 纯水平 < 1.2 | 路径终点 + 三维 | 参考会误判楼层 |
| 卡住 | 窗口式（锚点 + 0.5 格） | 单 tick 位移 | **参考更抗蠕动，已采纳** |
| 超时 | `maxWalkTicks=200` **未被使用** | 400 tick | 参考实际只靠卡住退出 |

### A.3 可靠性

| 维度 | 参考 | v2 | 结论 |
|---|---|---|---|
| 未加载区块 | 无守卫 | 三层 | **本方案** |
| 半截路径 | 不查 `reachesTarget` | 查 | **本方案** |
| 输入层 | `setPressed(bool)` 幂等 | 幂等（原为引用计数） | 它独占输入才能直接写 |
| 代码量 | 293 行 | ≈480 行 | 参考紧凑，因不做守卫 |
| 可调试性 | `getDebugPathNodes()` + `showPath` | 只有日志 | **参考**，本方案补节点打印 |

**融合结果**：路线 B 的入口 + 本方案的守卫/超时/三维到达 + 参考的转向/跳跃/卡住窗口 + 按距离给节点预算。

---

## 附录 B：日志关键字

| 日志 | 含义 |
|---|---|
| `[path] 目标区块未加载 (x,y,z)` | §6.4 防线 1 |
| `[path] 目标太远 N 格` | 超过 `MAX_DISTANCE` |
| `[path] 无解` | `findPathToAny` 返回 null |
| `[path] 只有半截路（节点 N/预算 M）` | `reachesTarget()` 为 false，预算触顶 |
| `[path] 路径节点 N 落在未加载区块` | 防线 3 |
| `[path] 规划 N.N ms / 节点 N / 预算 M / 终点 (x,y,z)` | 性能与终点观测 |
| `[path] 到达：N tick，节点 N，重算 N 次` | 正常结束 |
| `[path] 放弃：重算 N 次，距终点 N 格` | 走不到 |
| `[path] 中止：原因` | 停止移动 / 关引擎 / 停止全部 / 位置突变 |
