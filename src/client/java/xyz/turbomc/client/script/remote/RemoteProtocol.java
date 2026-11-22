package xyz.turbomc.client.script.remote;

import java.util.List;

/**
 * 远程桥的协议常量与 Origin 校验。
 *
 * <p>协议细节见 {@code docs/ARCHITECTURE.md} §7.2。这里只放不依赖运行时状态的纯常量与
 * 纯函数，便于单独测试：{@link #originAllowed} 是唯一有分支逻辑的部分。</p>
 */
public final class RemoteProtocol {
	/** 协议版本。字段或语义不兼容时递增。 */
	public static final int VERSION = 1;

	/** 自动分配端口的起点。 */
	public static final int BASE_PORT = 35671;
	/** 自动分配时最多向后试几个端口。 */
	public static final int MAX_PORT_TRIES = 32;

	/** 快照频率：每 tick 一帧，与游戏 20 tps 对齐，同时充当浏览器侧的时钟。 */
	public static final int SNAPSHOT_HZ = 20;
	/**
	 * 快照字段版本。**加/改字段必须 +1**，扩展侧会拿它比对。
	 *
	 * <p>比的是「客户端与扩展是否同一代」：字段只增不减，所以旧客户端缺字段时不会报错，
	 * 只会让积木恒为假/为空——那种症状与「积木坏了」无法区分。有了版本号，
	 * 面板上直接写「客户端旧」，不用再靠猜。</p>
	 */
	public static final int SNAPSHOT_SCHEMA = 6;
	/** 单 tick 最多执行多少个远程请求。 */
	public static final int MAX_CALLS_PER_TICK = 64;
	/** 请求队列容量；满了丢最旧的并回错误。 */
	public static final int QUEUE_CAPACITY = 1024;
	/** 心跳超时；超过这个时间没有任何消息即视为断线。 */
	public static final long HEARTBEAT_TIMEOUT_MS = 3000L;

	// ---- 消息类型（{@code t} 字段的取值） ----

	public static final String T_HELLO = "hello";
	public static final String T_WELCOME = "welcome";
	public static final String T_CALL = "call";
	public static final String T_RET = "ret";
	public static final String T_POLL = "poll";
	public static final String T_CANCEL = "cancel";
	public static final String T_STOP = "stop";
	public static final String T_STOPPED = "stopped";
	public static final String T_SNAP = "snap";
	public static final String T_EVENT = "event";
	public static final String T_PING = "ping";
	public static final String T_PONG = "pong";
	public static final String T_EVICTED = "evicted";

	// ---- 错误码（{@code code} 字段的取值） ----

	public static final String E_UNMAPPED = "unmapped";
	public static final String E_RATE = "rate";
	public static final String E_BUDGET = "budget";
	public static final String E_NOTFOUND = "notfound";
	public static final String E_INTERNAL = "internal";

	private RemoteProtocol() {
	}

	/**
	 * Origin 是否在白名单内。
	 *
	 * <p>规则只有两种形态，刻意保持简单：</p>
	 * <ul>
	 *   <li><b>精确匹配</b>：{@code https://zzj.hengke.xyz}、{@code tw-editor://.}、{@code null}。</li>
	 *   <li><b>端口通配</b>：以 {@code :*} 结尾，去掉 {@code *} 后按前缀匹配，
	 *       例如 {@code http://localhost:*} 放行 {@code http://localhost:8080}。</li>
	 * </ul>
	 *
	 * <p>{@code Origin} 缺失时按字面量 {@code "null"} 处理——这是 {@code file://} 页面和
	 * TurboWarp Desktop 的实际情况（实测见 ARCHITECTURE.md §7.9），
	 * 白名单里必须显式写出 {@code "null"} 才会放行，不是"缺失即放行"。</p>
	 *
	 * <p><b>默认值里刻意没有 {@code "null"}</b>：它只对应「下载到本地、用浏览器直接打开」的
	 * HTML，正是最容易被顺手点开的那一类。TurboWarp Desktop 走 {@code tw-editor://.}，
	 * 自建站走 {@code http(s)://域名}，都不需要它。要从本地文件打开时，
	 * 在模组菜单的「允许的来源」里自己加一行。</p>
	 */
	public static boolean originAllowed(String origin, List<String> patterns) {
		if (patterns == null || patterns.isEmpty()) {
			return false;
		}
		String value = origin == null ? "null" : origin;
		for (String pattern : patterns) {
			if (pattern == null || pattern.isBlank()) {
				continue;
			}
			String rule = pattern.trim();
			if (rule.endsWith(":*")) {
				// "http://localhost:*" → 前缀 "http://localhost:"；
				// 保留冒号是刻意的：这样 http://localhost.example.com 不会被误放行
				if (value.startsWith(rule.substring(0, rule.length() - 1))) {
					return true;
				}
				continue;
			}
			if (rule.equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}
}
