package xyz.hegke.client.script.runtime;

/**
 * 触发策略：对应 Scratch {@code runtime.startHats} 的三种语义。
 *
 * <p>Scratch 的帽子积木分两类：广播与绿旗是 {@code restartExistingThreads=true}，
 * 再次触发会<b>原地重启</b>已有线程；按键是 {@code false}，若已有线程在跑则<b>放弃本次触发</b>。
 * 这两种行为都与"每次触发都新建一个实例"完全不同——后者会在高频广播下跑出多份并发脚本，
 * 互相抢同一个角色。</p>
 */
public enum TriggerPolicy {
	/** 终止同名实例后重新开始（广播、常驻）。 */
	RESTART,
	/** 已有实例在跑则忽略本次触发（按键）。 */
	ABANDON,
	/** 允许并发，受每脚本实例上限约束（受伤、聊天等本模组扩展事件）。 */
	CONCURRENT;

	public static TriggerPolicy of(String trigger) {
		if (trigger == null) {
			return CONCURRENT;
		}
		return switch (trigger) {
			case "event.tick", "event.start", "event.broadcast" -> RESTART;
			case "event.key" -> ABANDON;
			default -> CONCURRENT;
		};
	}
}
