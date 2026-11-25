package xyz.turbomc.client.script.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.turbomc.client.script.runtime.PerfProbe;
import xyz.turbomc.client.script.sb3.Sb3Importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 脚本包仓库：管理 {@code config/turbomc/packages/} 下的 sb3 文件。
 *
 * <p>一个包就是一份 sb3 工程。启用状态是<b>互斥</b>的——同时只有一个包参与运行，
 * 因此不需要合并多个工程的 targets，也就不会出现舞台变量冲突这类静默问题。</p>
 *
 * <p>导入入口仍是 {@code config/turbomc/imports/}：把 sb3 丢进去，游戏内点「导入」即可。</p>
 */
public final class PackStore {
	private static final Logger LOG = LoggerFactory.getLogger("turbomc/script");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final PackStore INSTANCE = new PackStore();
	private static final String INDEX_FILE = "index.json";

	private final List<ScriptPack> packs = new ArrayList<>();
	/** 解析结果缓存：文件名 → (修改时间, 脚本)。sb3 不大，但每次打开界面都重解析没必要。 */
	private final Map<String, Cache> cache = new HashMap<>();
	private Path directory;
	private boolean loaded;
	/** 防止 {@link #reload()} 内部的读取再次回到 {@link #load()} 造成递归。 */
	private boolean loading;
	/**
	 * 上一次同步时的 {@code imports/} 指纹，用于热重载判定。
	 *
	 * <p>不用目录 mtime：Windows 上覆盖同名文件不会更新父目录的 mtime，热重载会永远不触发。
	 * 指纹取「文件名 + 大小 + 文件 mtime」，与 {@link #pendingUpdate} 的比对口径一致。</p>
	 */
	private String lastImportsFingerprint = "";
	/**
	 * 最近一次操作的结果。
	 *
	 * <p>类型是 {@link Text} 而非字符串：这些消息要显示在界面上，走翻译键才能跟随语言，
	 * 否则英文客户端会看到中文（历史问题，见批次 P2-11）。</p>
	 */
	private Text lastMessage = Text.empty();
	/** 最近一次失败的具体原因（文件名 + 原因），没有失败时为空。 */
	private Text lastDetail = Text.empty();

	private record Cache(long modifiedAt, Sb3Importer.Result result) {
	}

	/** 一次同步的结果：新增几个包、覆盖几个包、启用中的包是否被覆盖、失败明细。 */
	public record SyncResult(int added, int updated, boolean activeUpdated, List<Text> failures) {
	}

	private PackStore() {
	}

	public static PackStore get() {
		return INSTANCE;
	}

	public Path directory() {
		if (directory == null) {
			directory = FabricLoader.getInstance().getConfigDir().resolve("turbomc").resolve("packages");
		}
		return directory;
	}

	/** 投放入口：把 sb3 放到这里，再点「导入」。 */
	public Path importsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("turbomc").resolve("imports");
	}

	public Text lastMessage() {
		return lastMessage;
	}

	public Text lastDetail() {
		return lastDetail;
	}

	public void load() {
		if (loaded || loading) {
			return;
		}
		loading = true;
		try {
			reload();
			// 升级路径：老版本把 sb3 放在 imports/ 里当整体工程跑，从未建过包目录。
			// 首次没有包时自动搬一次，免得用户以为「升级后脚本全没了」。
			if (packs.isEmpty()) {
				int added = importFrom(importsDir(), new ArrayList<>());
				if (added > 0) {
					LOG.info("[pack] 首次启动：从 imports 自动接管 {} 个 sb3", added);
					reload();
				}
			}
			// 热重载的基线：以启动时的 imports 状态为起点，避免开局就白跑一次同步
			lastImportsFingerprint = importsFingerprint();
			// 全部成功后才置位：中途抛异常时下次调用能重试，而不是永久停在半载入状态
			loaded = true;
		} finally {
			loading = false;
		}
	}

	/** 重新扫描目录并恢复启用状态。 */
	public void reload() {
		packs.clear();
		cache.clear();
		try {
			Files.createDirectories(directory());
			String active = readActiveName();
			try (Stream<Path> files = Files.list(directory())) {
				files.filter(PackStore::isSb3)
						.sorted()
						.forEach(path -> {
							String name = path.getFileName().toString();
							packs.add(new ScriptPack(name, name.equals(active)));
						});
			}
			// 记录的包被删了：不留下悬空状态
			if (!packs.isEmpty() && packs.stream().noneMatch(pack -> pack.enabled)) {
				packs.get(0).enabled = true;
				writeIndex();
			}
		} catch (IOException e) {
			LOG.error("[pack] 包目录读取失败: {}", directory(), e);
		}
		LOG.info("[pack] 脚本包就绪：{} 个，启用 {}",
				packs.size(), activePack() == null ? "无" : activePack().fileName);
	}

	private static boolean isSb3(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sb3");
	}

	public List<ScriptPack> packs() {
		load();
		return packs;
	}

	/** 当前启用的包；没有包时为 {@code null}。 */
	public ScriptPack activePack() {
		load();
		for (ScriptPack pack : packs) {
			if (pack.enabled) {
				return pack;
			}
		}
		return null;
	}

	/** 启用指定包（互斥：其余包自动关闭）。传 {@code null} 表示全部停用。 */
	public void setActive(ScriptPack target) {
		for (ScriptPack pack : packs) {
			pack.enabled = pack == target;
		}
		writeIndex();
	}

	public Path pathOf(ScriptPack pack) {
		return directory().resolve(pack.fileName);
	}

	/** 读取包里的 project.json，供 VM 直接加载。 */
	public String projectJson(ScriptPack pack) {
		Path path = pathOf(pack);
		try (ZipFile zip = new ZipFile(path.toFile())) {
			ZipEntry entry = zip.getEntry("project.json");
			if (entry == null) {
				LOG.warn("[pack] {} 内缺少 project.json", pack.fileName);
				return null;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			LOG.error("[pack] 读取工程失败: {}", pack.fileName, e);
			return null;
		}
	}

	/** 解析包内的脚本（带缓存），供界面展示与按键收集。 */
	public Sb3Importer.Result scripts(ScriptPack pack) {
		Path path = pathOf(pack);
		long modified;
		try {
			modified = Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return new Sb3Importer.Result(List.of(), List.of(), "无法读取文件时间");
		}
		Cache hit = cache.get(pack.fileName);
		if (hit != null && hit.modifiedAt() == modified) {
			return hit.result();
		}
		long parseStart = PerfProbe.begin();
		Sb3Importer.Result result = Sb3Importer.read(path);
		// 缓存只在文件变动时失效，所以这里量到的是「真正解一次 sb3」的耗时
		PerfProbe.end("pack.parse", parseStart);
		cache.put(pack.fileName, new Cache(modified, result));
		return result;
	}

	/**
	 * 同步 {@code imports/}：新文件导入成新包，同名但内容不同的覆盖旧包。
	 *
	 * <p>「导入」和「更新」本来就是同一个动作——用户导出后只会做一件事，就是把 sb3 放进
	 * imports。拆成两个按钮等于逼他先判断自己是首次导入还是在迭代。</p>
	 */
	public SyncResult importAll() {
		load();
		List<Text> failures = new ArrayList<>();
		int added = importFrom(importsDir(), failures);
		if (added > 0) {
			// 新包要出现在列表里，顺便让索引回到一致状态
			reload();
		}
		ScriptPack active = activePack();
		int updated = 0;
		boolean activeUpdated = false;
		for (ScriptPack pack : packs()) {
			Path source = pendingUpdate(pack);
			if (source == null) {
				continue;
			}
			String failure = copyInto(pack, source);
			if (failure != null) {
				failures.add(Text.translatable("message.turbomc.pack.fail_item", pack.fileName, failure));
				continue;
			}
			updated++;
			if (active != null && pack.fileName.equals(active.fileName)) {
				activeUpdated = true;
			}
		}
		// 失败原因必须在界面上可见：只说「详见日志」等于让玩家自己去翻 latest.log
		if (!failures.isEmpty()) {
			lastMessage = Text.translatable("message.turbomc.pack.sync_failed", added, updated, failures.size());
			lastDetail = Text.translatable("message.turbomc.pack.fail_detail", failures.get(0));
		} else if (added == 0 && updated == 0) {
			lastMessage = Text.translatable("message.turbomc.pack.sync_empty");
			lastDetail = Text.empty();
		} else {
			lastMessage = Text.translatable("message.turbomc.pack.sync", added, updated);
			lastDetail = Text.empty();
		}
		LOG.info("[pack] 同步完成：新增 {}，更新 {}，失败 {}", added, updated, failures.size());
		return new SyncResult(added, updated, activeUpdated, List.copyOf(failures));
	}

	/**
	 * 热重载检查：{@code imports/} 的指纹变化时执行一次同步。
	 *
	 * <p>节流由调用方负责（见 {@code ScriptEngine#tick}），本方法只做「变了就同步」。
	 * 指纹没变时直接返回 {@code null}，不碰磁盘上的包文件。</p>
	 *
	 * @return 本次同步结果；无变化时为 {@code null}
	 */
	public SyncResult pollImports() {
		load();
		String current = importsFingerprint();
		if (current.equals(lastImportsFingerprint)) {
			return null;
		}
		lastImportsFingerprint = current;
		LOG.info("[pack] 检测到 imports 变化，开始同步");
		return importAll();
	}

	/**
	 * {@code imports/} 的指纹：每个 sb3 的「文件名 + 大小 + 修改时间」。
	 *
	 * <p>不用目录 mtime——Windows 上覆盖同名文件不会更新父目录的时间戳，
	 * 拿目录时间做判定的话热重载永远不会触发。</p>
	 */
	private String importsFingerprint() {
		Path dir = importsDir();
		if (!Files.isDirectory(dir)) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(PackStore::isSb3).sorted().forEach(path -> {
				try {
					sb.append(path.getFileName()).append('|')
							.append(Files.size(path)).append('|')
							.append(Files.getLastModifiedTime(path).toMillis()).append('\n');
				} catch (IOException e) {
					// 单个文件读不到就跳过：它下次仍被排除在指纹外，不会制造假变化
				}
			});
		} catch (IOException e) {
			LOG.warn("[pack] imports 指纹读取失败: {}", e.getMessage());
			return "";
		}
		return sb.toString();
	}

	private int importFrom(Path dir, List<Text> failures) {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		int added = 0;
		try {
			Files.createDirectories(directory());
		} catch (IOException e) {
			LOG.error("[pack] 创建包目录失败: {}", directory(), e);
			failures.add(Text.translatable("message.turbomc.pack.fail_item", directory().toString(), reasonOf(e)));
			return 0;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path path : files.filter(PackStore::isSb3).toList()) {
				Path target = directory().resolve(path.getFileName().toString());
				if (Files.exists(target)) {
					continue;
				}
				// 逐个文件捕获：一个文件坏掉不该让同目录其余文件也导不进来
				try {
					Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
					added++;
				} catch (IOException e) {
					LOG.error("[pack] 导入失败: {}", path, e);
					failures.add(Text.translatable("message.turbomc.pack.fail_item",
							path.getFileName().toString(), reasonOf(e)));
				}
			}
		} catch (IOException e) {
			LOG.error("[pack] 导入目录读取失败: {}", dir, e);
			failures.add(Text.translatable("message.turbomc.pack.fail_item", dir.toString(), reasonOf(e)));
		}
		return added;
	}

	/**
	 * {@code imports/} 里与包同名、但内容不同的文件；没有则返回 {@code null}。
	 *
	 * <p>按字节比对，而不是看时间戳：导出与复制会保留源文件的 mtime，FAT 类文件系统
	 * 的时间精度也只有 2 秒，用时间判断会漏掉更新。sb3 只有 KB 量级，直接比内容更可靠。</p>
	 */
	public Path pendingUpdate(ScriptPack pack) {
		Path source = importsDir().resolve(pack.fileName);
		Path target = pathOf(pack);
		if (!Files.isRegularFile(source) || !Files.isRegularFile(target)) {
			return null;
		}
		try {
			// 大小不同必然有更新；相同才付一次全量比对
			if (Files.size(source) != Files.size(target)) {
				return source;
			}
			return Files.mismatch(source, target) == -1L ? null : source;
		} catch (IOException e) {
			LOG.warn("[pack] 更新检测失败：{} - {}", pack.fileName, e.getMessage());
			return null;
		}
	}

	/** 用 {@code imports/} 里的同名文件覆盖包。成功返回 {@code null}，失败返回原因。 */
	private String copyInto(ScriptPack pack, Path source) {
		Path target = pathOf(pack);
		Path temp = null;
		try {
			// 先写临时文件再原子替换：写到一半崩掉或被杀毒软件读到，都不会留下半截 sb3。
			// 与 ExtensionExporter 同一套写法。
			temp = Files.createTempFile(target.getParent(), ".turbomc-pack", ".tmp");
			Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicUnsupported) {
				// 部分网络盘 / 非 NTFS 文件系统不支持原子移动，退回普通覆盖
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// 临时文件清理失败不影响结论
				}
			}
			LOG.error("[pack] 更新失败: {}", pack.fileName, e);
			return reasonOf(e);
		}
		// 解析缓存以 mtime 为键：保留时间戳的复制不会改 mtime，必须显式失效
		cache.remove(pack.fileName);
		LOG.info("[pack] 已用 imports 的新版本覆盖 {}", pack.fileName);
		return null;
	}

	/** 异常转一句人能读的原因；{@code getMessage()} 为空的异常不在少数。 */
	private static String reasonOf(Throwable e) {
		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}

	/** 重命名包（同时改文件名）。重名时自动加后缀。 */
	public boolean rename(ScriptPack pack, String newName) {
		String safe = newName == null ? "" : newName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
		if (safe.isEmpty()) {
			lastMessage = Text.translatable("message.turbomc.pack.name_empty");
			lastDetail = Text.empty();
			return false;
		}
		if (!safe.toLowerCase(Locale.ROOT).endsWith(".sb3")) {
			safe = safe + ".sb3";
		}
		// 改成同名不该生成 name_2.sb3：那不是重命名，只是确认
		if (safe.equalsIgnoreCase(pack.fileName)) {
			lastMessage = Text.translatable("message.turbomc.pack.renamed", pack.fileName);
			lastDetail = Text.empty();
			return true;
		}
		Path from = pathOf(pack);
		Path to = directory().resolve(uniqueName(safe));
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOG.error("[pack] 重命名失败: {}", pack.fileName, e);
			lastMessage = Text.translatable("message.turbomc.pack.rename_failed", reasonOf(e));
			lastDetail = Text.empty();
			return false;
		}
		boolean wasActive = pack.enabled;
		reload();
		if (wasActive) {
			// reload 会按索引恢复启用态，但索引里记的还是旧名字；这里显式改到新文件上，
			// 否则可能出现「旧名已失效、另有包被自动接管」导致两个包同时启用
			String renamed = to.getFileName().toString();
			for (ScriptPack item : packs) {
				item.enabled = item.fileName.equals(renamed);
			}
			writeIndex();
		}
		lastMessage = Text.translatable("message.turbomc.pack.renamed", to.getFileName().toString());
		lastDetail = Text.empty();
		return true;
	}

	private String uniqueName(String name) {
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String ext = dot > 0 ? name.substring(dot) : "";
		String candidate = name;
		int index = 2;
		while (Files.exists(directory().resolve(candidate))) {
			candidate = base + "_" + index + ext;
			index++;
		}
		return candidate;
	}

	public boolean delete(ScriptPack pack) {
		try {
			Files.deleteIfExists(pathOf(pack));
		} catch (IOException e) {
			LOG.error("[pack] 删除失败: {}", pack.fileName, e);
			lastMessage = Text.translatable("message.turbomc.pack.delete_failed", reasonOf(e));
			lastDetail = Text.empty();
			return false;
		}
		cache.remove(pack.fileName);
		// reload 在「没有任何启用包」时会自动接管第一个并写索引，这里不再重复一次
		reload();
		lastMessage = Text.translatable("message.turbomc.pack.deleted", pack.fileName);
		lastDetail = Text.empty();
		return true;
	}

	/** 打开包目录（用于把 sb3 放进去）。 */
	public void ensureDirectories() {
		try {
			Files.createDirectories(directory());
			Files.createDirectories(importsDir());
		} catch (IOException e) {
			LOG.warn("[pack] 创建目录失败: {}", e.getMessage());
		}
	}

	private String readActiveName() {
		Path index = directory().resolve(INDEX_FILE);
		if (!Files.isRegularFile(index)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return root.has("active") && !root.get("active").isJsonNull()
					? root.get("active").getAsString()
					: null;
		} catch (Exception e) {
			LOG.warn("[pack] 索引读取失败，按无启用处理: {}", e.getMessage());
			return null;
		}
	}

	private void writeIndex() {
		JsonObject root = new JsonObject();
		ScriptPack active = null;
		for (ScriptPack pack : packs) {
			if (pack.enabled) {
				active = pack;
				break;
			}
		}
		root.addProperty("active", active == null ? null : active.fileName);
		try (Writer writer = Files.newBufferedWriter(
				directory().resolve(INDEX_FILE), StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		} catch (IOException e) {
			LOG.error("[pack] 索引写入失败", e);
		}
	}

}
