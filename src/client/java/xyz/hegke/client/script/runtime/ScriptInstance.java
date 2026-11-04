package xyz.hegke.client.script.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.action.NativeRegistry;
import xyz.hegke.client.script.ir.CompiledScript;
import xyz.hegke.client.script.ir.Instr;
import xyz.hegke.client.script.ir.Op;
import xyz.hegke.client.script.node.BlockNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个脚本实例：一段程序计数器、一个求值栈、一组局部变量。
 *
 * <p>每次 {@link #step} 最多执行预算条指令，遇到 YIELD 或预算耗尽即让出主线程，
 * 因此任意长度的脚本都不会造成单帧卡顿。</p>
 */
public final class ScriptInstance {
	public enum State {
		RUNNING,
		WAITING,
		FINISHED,
		ERROR
	}

	private final CompiledScript script;
	private Map<String, Object> locals = new HashMap<>();
	private final Deque<Object> stack = new ArrayDeque<>();
	private final Deque<Frame> callStack = new ArrayDeque<>();
	private final List<Integer> pendingSpawns = new ArrayList<>();
	/** 「广播并等待」时正在等待的实例集合；为空表示不在等待。 */
	private List<ScriptInstance> waitingOn;
	/** 挖破等待：宿主目标的透传槽，null 表示还没锁定到方块。 */
	private Object breakState;
	/** 挖破等待的截止 tick，由积木参数换算而来。 */
	private int breakDeadline;
	/** 是否已经取过超时参数：只弹一次栈，之后每 tick 只询问宿主。 */
	private boolean breakArmed;
	private int pc;
	private int wakeTick;
	private State state = State.RUNNING;
	private String error = "";

	private static final Logger LOG = LoggerFactory.getLogger("hk-script/script");
	private static final int MAX_CALL_DEPTH = 32;

	public ScriptInstance(CompiledScript script) {
		this.script = script;
	}

	/**
	 * 从指定 pc 开始执行的子实例，用于 {@code spawn} 派生。
	 */
	public ScriptInstance(CompiledScript script, int startPc) {
		this.script = script;
		this.pc = startPc;
	}

	public CompiledScript script() {
		return script;
	}

	public State state() {
		return state;
	}

	public String error() {
		return error;
	}

	public boolean isDone() {
		return state == State.FINISHED || state == State.ERROR;
	}

	/**
	 * 由调度器强制终止（广播重启、停止全部等）。置为完成态，
	 * 让正在等待它的「广播并等待」可以醒来，避免死锁。
	 */
	public void kill() {
		state = State.FINISHED;
	}

	public boolean isWaiting() {
		return state == State.WAITING;
	}

	public Object local(String name) {
		return locals.get(name);
	}

	/**
	 * 读取局部变量快照，供运行面板与调试使用。
	 */
	public Map<String, Object> locals() {
		return Map.copyOf(locals);
	}

	public int pc() {
		return pc;
	}

	/**
	 * 取走本 tick 由 {@code spawn} 产生的派生请求（目标 pc），并清空内部队列。
	 */
	public List<Integer> drainSpawns() {
		if (pendingSpawns.isEmpty()) {
			return List.of();
		}
		List<Integer> copy = new ArrayList<>(pendingSpawns);
		pendingSpawns.clear();
		return copy;
	}

	/**
	 * 推进执行。
	 *
	 * @return 本次实际消耗的指令条数
	 */
	public int step(int budget, int currentTick, ScriptContext ctx) {
		// 动作层按实例归属按键，结束时才能精确收回
		ctx.setCurrentInstance(this);
		if (state == State.WAITING) {
			if (currentTick < wakeTick) {
				return 0;
			}
			// 「广播并等待」：被唤醒的实例还没全部结束就继续挂起
			if (waitingOn != null) {
				boolean alive = false;
				for (ScriptInstance other : waitingOn) {
					if (!other.isDone()) {
						alive = true;
						break;
					}
				}
				if (alive) {
					wakeTick = currentTick + 1;
					return 0;
				}
				waitingOn = null;
			}
			state = State.RUNNING;
		}
		if (state != State.RUNNING) {
			return 0;
		}

		int used = 0;
		try {
			while (used < budget && state == State.RUNNING) {
				if (pc < 0 || pc >= script.size()) {
					fail("程序计数器越界: " + pc);
					break;
				}
				Instr instr = script.code().get(pc);
				execute(instr, ctx, currentTick);
				used++;
			}
		} catch (Throwable t) {
			fail(t.getClass().getSimpleName() + ": " + t.getMessage());
			LOG.error("[script:{}] 执行异常", script.name(), t);
		}
		return used;
	}

	private void execute(Instr instr, ScriptContext ctx, int currentTick) {
		switch (instr.op) {
			case PUSH_CONST -> {
				push(instr.value);
				pc++;
			}
			case LOAD_VAR -> {
				push(switch (instr.a) {
					case 1 -> GlobalVariables.get(instr.s);
					case 2 -> TargetVariables.get(script.scopeId(), instr.s);
					default -> locals.getOrDefault(instr.s, 0d);
				});
				pc++;
			}
			case STORE_VAR -> {
				Object value = pop();
				switch (instr.a) {
					case 1 -> GlobalVariables.set(instr.s, value);
					case 2 -> TargetVariables.set(script.scopeId(), instr.s, value);
					default -> locals.put(instr.s, value);
				}
				pc++;
			}
			case BIN_OP -> {
				double right = Values.asNumber(pop());
				double left = Values.asNumber(pop());
				push(switch (instr.s) {
					case "+" -> left + right;
					case "-" -> left - right;
					case "*" -> left * right;
					case "/" -> right == 0d ? 0d : left / right;
					case "%" -> ScratchMath.mod(left, right);
					default -> 0d;
				});
				pc++;
			}
			case COMPARE -> {
				Object right = pop();
				Object left = pop();
				int order = ScratchCompare.compare(left, right);
				boolean result = switch (instr.s) {
					case "<" -> order < 0;
					case ">" -> order > 0;
					case "<=" -> order <= 0;
					case ">=" -> order >= 0;
					case "!=" -> order != 0;
					default -> order == 0;
				};
				push(result);
				pc++;
			}
			case JUMP -> pc = instr.a;
			case AND -> {
				boolean right = Values.asBool(pop());
				boolean left = Values.asBool(pop());
				push(left && right);
				pc++;
			}
			case OR -> {
				boolean right = Values.asBool(pop());
				boolean left = Values.asBool(pop());
				push(left || right);
				pc++;
			}
			case NOT -> {
				push(!Values.asBool(pop()));
				pc++;
			}
			case JUMP_IF_FALSE -> {
				boolean condition = Values.asBool(pop());
				pc = condition ? pc + 1 : instr.a;
			}
			case YIELD -> {
				int ticks = (int) Values.asNumber(pop());
				state = State.WAITING;
				wakeTick = currentTick + Math.max(0, ticks);
				pc++;
			}
			case LOOP -> {
				pc = instr.a;
				// b==1 表示"每轮让出一 tick"，对齐 Scratch 的「循环每帧一轮」
				if (instr.b == 1) {
					state = State.WAITING;
					wakeTick = currentTick + 1;
				}
			}
			case SPAWN -> {
				pendingSpawns.add(instr.a);
				pc++;
			}
			case CALL_FUNC -> {
				if (callStack.size() >= MAX_CALL_DEPTH) {
					throw new IllegalStateException("自定义积木调用层级过深: " + callStack.size());
				}
				Object[] args = popArgs(instr.b);
				Map<String, Object> frameLocals = new HashMap<>();
				for (int index = 0; index < args.length; index++) {
					frameLocals.put("$p" + (index + 1), args[index]);
				}
				callStack.push(new Frame(pc + 1, locals));
				locals = frameLocals;
				pc = instr.a;
			}
			case RETURN -> {
				Frame frame = callStack.poll();
				if (frame == null) {
					state = State.FINISHED;
				} else {
					locals = frame.locals();
					pc = frame.returnPc();
				}
			}
			case CALL_ACTION -> {
				Object[] args = popArgs(instr.a);
				NativeRegistry.Action action = NativeRegistry.getAction(instr.s);
				if (action == null) {
					throw new IllegalStateException("未注册的动作: " + instr.s);
				}
				action.run(ctx, args);
				pc++;
			}
			case CALL_BUILTIN -> {
				Object[] args = popArgs(instr.a);
				NativeRegistry.Function function = NativeRegistry.getFunction(instr.s);
				if (function == null) {
					throw new IllegalStateException("未注册的函数: " + instr.s);
				}
				push(function.apply(ctx, args));
				pc++;
			}
			case HALT -> state = State.FINISHED;
			case STOP_ALL -> {
				ctx.requestStopAll();
				state = State.FINISHED;
			}
			case BROADCAST_WAIT -> {
				String message = Values.asString(pop());
				List<ScriptInstance> started = ctx.broadcast(message);
				pc++;
				if (!started.isEmpty()) {
					waitingOn = started;
					state = State.WAITING;
					wakeTick = currentTick + 1;
				}
			}
			case WAIT_BREAK -> {
				// 超时参数只在进入等待时求值一次；之后 pc 不动，每 tick 只问宿主
				if (!breakArmed) {
					int timeout = (int) Values.asNumber(pop());
					breakArmed = true;
					breakState = null;
					breakDeadline = currentTick + Math.max(0, timeout);
				}
				ScriptContext.BreakResult result = ctx.probeBreak(breakState);
				breakState = result.state();
				boolean broken = result.status() == ScriptContext.BreakResult.BROKEN;
				boolean giveUp = result.status() == ScriptContext.BreakResult.GIVE_UP;
				if (broken || giveUp || currentTick >= breakDeadline) {
					// 超时与放弃都压 false：C 形积木据此跳过后续，脚本不会卡死
					push(broken);
					breakArmed = false;
					breakState = null;
					pc++;
				} else {
					state = State.WAITING;
					wakeTick = currentTick + 1;
				}
			}
		}
	}

	private void push(Object value) {
		stack.push(value);
	}

	private Object pop() {
		if (stack.isEmpty()) {
			throw new IllegalStateException("求值栈下溢 @pc=" + pc);
		}
		return stack.pop();
	}

	private Object[] popArgs(int count) {
		Object[] args = new Object[count];
		for (int i = count - 1; i >= 0; i--) {
			args[i] = pop();
		}
		return args;
	}

	private void fail(String message) {
		state = State.ERROR;
		error = message;
		// 反查当前 pc 对应的积木，让日志能定位到编辑器里的具体一行
		BlockNode block = script.blockAt(pc);
		if (block == null) {
			LOG.warn("[script:{}] {} @pc={}", script.name(), message, pc);
		} else {
			LOG.warn("[script:{}] {} @pc={} 积木={}", script.name(), message, pc, block.type);
		}
	}

	/**
	 * 自定义积木的调用帧：保存返回地址与调用方的局部变量表。
	 */
	private record Frame(int returnPc, Map<String, Object> locals) {
	}
}
