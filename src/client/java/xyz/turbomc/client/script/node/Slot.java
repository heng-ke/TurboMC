package xyz.turbomc.client.script.node;

/**
 * 积木的参数槽。要么是字面量，要么是嵌套的表达式积木，二者互斥。
 */
public final class Slot {
	public String literal;
	public BlockNode block;

	public Slot() {
	}

	public static Slot literal(String value) {
		Slot slot = new Slot();
		slot.literal = value;
		return slot;
	}

	public static Slot of(BlockNode block) {
		Slot slot = new Slot();
		slot.block = block;
		return slot;
	}

	public boolean hasBlock() {
		return block != null;
	}
}
