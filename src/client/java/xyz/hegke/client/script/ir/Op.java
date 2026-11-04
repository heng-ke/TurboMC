package xyz.hegke.client.script.ir;

/**
 * 指令操作码。刻意保持扁平，便于编译器直接生成跳转与预算检查。
 */
public enum Op {
	/** 压入常量，操作数 {@code value}。 */
	PUSH_CONST,
	/** 读取变量，操作数 {@code s} 为变量名。 */
	LOAD_VAR,
	/** 写入变量，操作数 {@code s} 为变量名。 */
	STORE_VAR,
	/** 二元运算，操作数 {@code s} 为运算符（+ - * / %）。 */
	BIN_OP,
	/** 比较运算，操作数 {@code s} 为运算符（< > = <= >=）。 */
	COMPARE,
	/** 逻辑与，弹两个布尔值。 */
	AND,
	/** 逻辑或，弹两个布尔值。 */
	OR,
	/** 逻辑非，弹一个布尔值。 */
	NOT,
	/** 无条件跳转，操作数 {@code a} 为目标指令下标。 */
	JUMP,
	/** 弹栈并判断，假值跳转到 {@code a}。 */
	JUMP_IF_FALSE,
	/** 挂起，弹栈取得等待 tick 数。 */
	YIELD,
	/** 循环回边，{@code a} 为目标下标；由调度器在此处实施预算让出。 */
	LOOP,
	/** 派生一个并行实例，从 {@code a} 处开始执行，直到遇到 HALT。 */
	SPAWN,
	/** 调用自定义积木，{@code a} 为函数体起始下标，{@code b} 为参数个数。 */
	CALL_FUNC,
	/** 从自定义积木返回；顶层出现时视为结束。 */
	RETURN,
	/** 调用动作，{@code s} 为动作 id，{@code a} 为参数个数。 */
	CALL_ACTION,
	/** 调用求值函数，{@code s} 为函数 id，{@code a} 为参数个数，结果压栈。 */
	CALL_BUILTIN,
	/** 脚本结束。 */
	HALT,
	/** 停止全部脚本；由引擎在本次 tick 结束后清空所有实例并停机。 */
	STOP_ALL,
	/** 广播并等待：{@code s} 为消息名，挂起直到被唤醒的实例全部结束。 */
	BROADCAST_WAIT,
	/**
	 * 等待准星方块被挖破：弹栈取得超时 tick 数，逐 tick 询问宿主，
	 * 结束时压入「是否真的挖破」。
	 */
	WAIT_BREAK
}
