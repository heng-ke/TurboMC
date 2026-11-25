package xyz.turbomc.client.script.action;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天文本清洗。
 *
 * <p><b>为什么需要它</b>：脚本可以直接发模型回复，而模型输出是自由文本——带换行、列表
 * 符号、`§` 格式化符、偶尔还有控制字符。这些在 HUD 上没问题，但原版服务端会直接
 * `disconnect`：现象是「非法聊天字符」，玩家被踢出存档，且客户端日志里只有一句断开原因，
 * 看不出是谁发的、发了什么。</p>
 *
 * <p>清洗放在唯一的发送出口（{@link GameActions#chat}），而不是 AI 积木的返回值上：
 * 脚本可能只想拿回复做判断（比较、取子串），改写返回值会破坏这类用法。</p>
 *
 * <p>规则保守——只做「不这么做就会被踢」的调整：</p>
 * <ol>
 *   <li>换行 / 回车 / 制表 → 空格（`\n` 是被踢的头号原因）</li>
 *   <li>删除 {@code §}（原版聊天不接受格式化符）</li>
 *   <li>删除其余控制字符与孤立代理项</li>
 *   <li>折叠连续空格、去首尾空白</li>
 *   <li>超过 {@link #MAX_LENGTH} 字符按码点安全截断</li>
 * </ol>
 *
 * <p>中文与 emoji 不动：它们本身合法（实测中文回复能正常发出）。</p>
 */
public final class ChatText {
	/**
	 * 单条聊天上限（字符数）。
	 *
	 * <p>原版客户端输入框同样是 256（{@code ChatScreen.MAX_CHAT_LENGTH}）。</p>
	 */
	public static final int MAX_LENGTH = 256;

	/**
	 * 单条聊天的 UTF-8 字节上限。
	 *
	 * <p>服务端读包走 Netty 的 {@code ByteBuf.readUtf(256)}，它内部按
	 * {@code maxLength * 3 = 768} 字节校验。只数字符会漏掉一种情况：
	 * 256 个字符里混了 emoji（4 字节/个）时字节数会超过 768，服务端读包失败直接断开——
	 * 表现和「非法聊天字符」不同，但同样是掉线。所以两个上限都要满足。</p>
	 */
	public static final int MAX_BYTES = MAX_LENGTH * 3;

	/** 截断标记：让玩家知道后半段没了，而不是以为模型只回了这么多。 */
	private static final String ELLIPSIS = "…";

	private ChatText() {
	}

	/**
	 * 只做清洗，<b>不截断</b>：长文本要先完整清洗、再交给 {@link #split} 分段，
	 * 在分段之前砍到 256 字会让它永远只有一段（真机踩过：1000 字的回复只发出 256 字，
	 * 日志里没有「已分成 N 段」）。
	 */
	public static String clean(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); ) {
			int codePoint = raw.codePointAt(i);
			int width = Character.charCount(codePoint);
			i += width;
			if (codePoint == '\u00A7') {
				// § 后面的一位是格式码（0-9 a-f 颜色、k-o/r 样式），一并丢弃，
				// 否则会留下一串孤立的字母：§c红色 → "c红色"
				if (i < raw.length() && isFormatCode(raw.charAt(i))) {
					i++;
				}
				continue;
			}
			if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
				out.append(' ');
				continue;
			}
			if (Character.isISOControl(codePoint)) {
				continue;
			}
			// 孤立代理项：UTF-16 里半截 emoji，发出去服务端会判非法
			if (width == 1 && Character.isSurrogate((char) codePoint)) {
				continue;
			}
			out.appendCodePoint(codePoint);
		}
		String text = out.toString().trim();
		// 换行折成空格后常留下「话  。」这种双空格
		return text.replaceAll(" {2,}", " ");
	}

	/** 一个码点按 UTF-8 编码后的字节数。 */
	private static int utf8Bytes(int codePoint) {
		if (codePoint < 0x80) {
			return 1;
		}
		if (codePoint < 0x800) {
			return 2;
		}
		return codePoint < 0x10000 ? 3 : 4;
	}

	private static int utf8Bytes(String text) {
		int total = 0;
		for (int i = 0; i < text.length(); ) {
			int codePoint = text.codePointAt(i);
			total += utf8Bytes(codePoint);
			i += Character.charCount(codePoint);
		}
		return total;
	}

	/**
	 * 按单条上限切段。
	 *
	 * <p>输入必须是 {@link #clean} 的结果（已无换行、<b>未截断</b>）。先截断再切段
	 * 会让长文本永远只有一段——真机上表现为「1000 字的回复只发出 256 字，
	 * 日志里也没有『已分成 N 段』」。</p>
	 *
	 * <p>切点优先落在标点上，
	 * 让每段读起来还是完整的句子；找不到标点才硬切。</p>
	 *
	 * <p>超过 {@code maxSegments} 时不再继续切，而是把最后一段的结尾换成省略号——
	 * 让玩家知道后面还有内容没发出来，而不是以为模型就说了这么多。</p>
	 */
	public static List<String> split(String text, int maxSegments) {
		List<String> segments = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return segments;
		}
		int limit = Math.max(1, maxSegments);
		String rest = text;
		while (!rest.isEmpty()) {
			if (segments.size() >= limit) {
				int last = segments.size() - 1;
				segments.set(last, withEllipsis(segments.get(last)));
				break;
			}
			int end = boundary(rest);
			if (end >= rest.length()) {
				segments.add(rest);
				break;
			}
			int cut = lastBreak(rest, end);
			if (cut <= 0) {
				// 理论上到不了这里（boundary 至少能放进一个码点）。但若 cut 为 0，
				// 下面的 substring(0) 会让 rest 原样不变 —— while 永不退出，主线程卡死。
				cut = Math.max(1, end);
			}
			String head = rest.substring(0, cut).trim();
			if (!head.isEmpty()) {
				segments.add(head);
			}
			rest = rest.substring(cut).trim();
		}
		return segments;
	}

	/** 在当前预算内能容纳的最大结束位置（码点边界，不会切开代理对）。 */
	private static int boundary(String text) {
		int chars = 0;
		int bytes = 0;
		int i = 0;
		while (i < text.length()) {
			int codePoint = text.codePointAt(i);
			int width = Character.charCount(codePoint);
			int size = utf8Bytes(codePoint);
			if (chars + width > MAX_LENGTH || bytes + size > MAX_BYTES) {
				break;
			}
			chars += width;
			bytes += size;
			i += width;
		}
		return i;
	}

	/** 优先切在标点之后；只在后半段找，避免切出「你好。」这种过短的段。 */
	private static int lastBreak(String text, int end) {
		for (int i = end - 1; i > end / 2; i--) {
			if (BREAKS.indexOf(text.charAt(i)) >= 0) {
				return i + 1;
			}
		}
		return end;
	}

	/** 末尾补省略号；放不下就先削掉末尾一个码点。 */
	private static String withEllipsis(String text) {
		if (text.endsWith(ELLIPSIS)) {
			return text;
		}
		String body = text;
		while (!body.isEmpty()) {
			String candidate = body + ELLIPSIS;
			if (candidate.length() <= MAX_LENGTH && utf8Bytes(candidate) <= MAX_BYTES) {
				return candidate;
			}
			int codePoint = body.codePointBefore(body.length());
			body = body.substring(0, body.length() - Character.charCount(codePoint));
		}
		return ELLIPSIS;
	}

	/** 切点候选：中英文标点与空格。 */
	private static final String BREAKS = "。！？；，、.!?;, ";

	/** 原版的聊天格式码：0-9 与 a-f 是颜色，k-o 与 r 是样式。 */
	private static boolean isFormatCode(char c) {
		char lower = Character.toLowerCase(c);
		return (lower >= '0' && lower <= '9')
				|| (lower >= 'a' && lower <= 'f')
				|| (lower >= 'k' && lower <= 'o')
				|| lower == 'r';
	}
}
