package xyz.hegke.client.script.ir;

/**
 * 一条指令。除 {@link Op} 外字段可变，便于编译期回填跳转目标。
 */
public final class Instr {
	public final Op op;
	public int a;
	public int b;
	public String s;
	public Object value;

	public Instr(Op op) {
		this.op = op;
	}

	public Instr(Op op, Object value) {
		this.op = op;
		this.value = value;
	}

	public static Instr var(Op op, String name) {
		Instr instr = new Instr(op);
		instr.s = name;
		return instr;
	}

	/**
	 * 运算符指令（BIN_OP / COMPARE）。运算符必须走 {@code s} 字段，与常量 {@code value} 区分。
	 */
	public static Instr operator(Op op, String operator) {
		Instr instr = new Instr(op);
		instr.s = operator;
		return instr;
	}

	public static Instr action(String id, int argc) {
		Instr instr = new Instr(Op.CALL_ACTION);
		instr.s = id;
		instr.a = argc;
		return instr;
	}

	public static Instr builtin(String id, int argc) {
		Instr instr = new Instr(Op.CALL_BUILTIN);
		instr.s = id;
		instr.a = argc;
		return instr;
	}

	public static Instr jump(Op op, int target) {
		Instr instr = new Instr(op);
		instr.a = target;
		return instr;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(op.name());
		if (s != null) {
			sb.append(' ').append(s);
		}
		if (value != null) {
			sb.append(' ').append(value);
		}
		if (a != 0) {
			sb.append(" a=").append(a);
		}
		return sb.toString();
	}
}
