package xyz.turbomc.client.script.vm;

import xyz.turbomc.client.script.action.NativeRegistry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

/**
 * 预热效果测量：{@code vm.init()} 之后「首次 loadProject」要花多久，预热之后又花多久。
 *
 * <p><b>要回答的问题</b>：真机日志里首次 {@code loadProject} 是 307 ms，第二次只有 8–10 ms。
 * 如果先用一个空工程走一遍能把这 307 ms 吃掉，那么「在标题界面预热」就值得做；
 * 如果吃不掉（耗时其实跟着工程规模走），那就白付一次启动时间。</p>
 *
 * <p>用法：{@code -Pmode=cold} 只测冷启动，{@code -Pmode=preload} 先预热再测同一个工程。</p>
 */
public final class PreloadSmokeTest {
	private static final String FIXTURE = "fixture.sb3";

	public static void main(String[] args) throws Exception {
		String mode = args.length > 0 ? args[0] : "cold";
		String projectJson = readProjectJson(FIXTURE);

		RhinoVm vm = RhinoVm.get();
		vm.setBudgetMs(4);
		NativeRegistry.bootstrapOnce();

		long preloadMs = 0L;
		if ("preload".equals(mode)) {
			long start = System.nanoTime();
			vm.preload();
			preloadMs = (System.nanoTime() - start) / 1_000_000L;
			System.out.println("[preload] 预热（init + 空工程）耗时 " + preloadMs + " ms");
		} else {
			long start = System.nanoTime();
			vm.init();
			System.out.println("[preload] 仅 init 耗时 " + (System.nanoTime() - start) / 1_000_000L + " ms");
		}

		long start = System.nanoTime();
		vm.loadProject(projectJson);
		long loadMs = (System.nanoTime() - start) / 1_000_000L;
		System.out.println("[preload] mode=" + mode + "，首次载入真实工程 " + loadMs + " ms");

		// 预热过空工程之后，真工程必须能正常跑起来：空工程留下的线程/监视器不能串场
		vm.greenFlag();
		vm.takeProjectStops();
		for (int i = 0; i < 10; i++) {
			vm.tick(null);
		}
		long calls = ScriptBridge.callCount();
		System.out.println("[preload] 预热后运行 10 帧：线程 " + vm.threadCount()
				+ "，桥调用 " + calls + "，变量 " + vm.variableDump());

		if (!"preload".equals(mode)) {
			System.out.println("[preload] 冷启动基线已记录，对照 preload 模式看差值");
			System.exit(0);
			return;
		}
		boolean ok = loadMs < 150 && calls > 0;
		System.out.println(ok ? "PRELOAD PASS（已被预热吃掉）" : "PRELOAD FAIL（预热没起作用）");
		System.exit(ok ? 0 : 1);
	}

	/** 从 sb3 里取出 project.json；先按路径找，再按 classpath 找。 */
	private static String readProjectJson(String source) throws IOException {
		byte[] data;
		Path path = Path.of(source);
		if (Files.isRegularFile(path)) {
			data = Files.readAllBytes(path);
		} else {
			try (InputStream in = PreloadSmokeTest.class.getResourceAsStream("/turbomc-vm/" + source)) {
				if (in == null) {
					throw new IOException("找不到夹具: " + source);
				}
				data = in.readAllBytes();
			}
		}
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if ("project.json".equals(entry.getName())) {
					return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		}
		throw new IOException("sb3 里没有 project.json: " + source);
	}
}
