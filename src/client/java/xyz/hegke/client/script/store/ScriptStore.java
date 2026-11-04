package xyz.hegke.client.script.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hegke.client.script.DemoScripts;
import xyz.hegke.client.script.node.BlockNode;
import xyz.hegke.client.script.sb3.Sb3Parser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 脚本仓库：负责 {@code config/hk-script/scripts/*.json} 的读写与生命周期。
 *
 * <p>文件格式与内存模型一致（{@link ScriptDefinition} 直接由 Gson 序列化），
 * 因此编辑器与引擎读取的是同一份结构，不存在格式转换层。</p>
 */
public final class ScriptStore {
	private static final Logger LOG = LoggerFactory.getLogger("hk-script/script");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final ScriptStore INSTANCE = new ScriptStore();

	private final List<ScriptDefinition> scripts = new ArrayList<>();
	private Path directory;
	private boolean loaded;
	private String lastMessage = "";
	private String lastImportDetail = "";

	private ScriptStore() {
	}

	public static ScriptStore get() {
		return INSTANCE;
	}

	public Path directory() {
		if (directory == null) {
			directory = FabricLoader.getInstance().getConfigDir().resolve("hk-script").resolve("scripts");
		}
		return directory;
	}

	public void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		scripts.clear();
		try {
			Files.createDirectories(directory());
			try (Stream<Path> files = Files.list(directory())) {
				files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
						.sorted()
						.forEach(this::readFile);
			}
		} catch (IOException e) {
			LOG.error("[script] 脚本目录读取失败: {}", directory(), e);
		}

		if (scripts.isEmpty()) {
			ScriptDefinition demo = ScriptDefinition.create("demo_mine_walk", DemoScripts.mineWalk());
			scripts.add(demo);
			writeFile(demo);
			LOG.info("[script] 已写入内置示例脚本 demo_mine_walk");
		}
		LOG.info("[script] 脚本仓库就绪：{} 个脚本", scripts.size());
	}

	private void readFile(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			ScriptDefinition definition = GSON.fromJson(reader, ScriptDefinition.class);
			if (definition != null && definition.hat != null) {
				// 旧文件没有 functions 字段，Gson 会留 null，这里补齐
				if (definition.functions == null) {
					definition.functions = new ArrayList<>();
				}
				scripts.add(definition);
			} else {
				LOG.warn("[script] 脚本格式无效，已跳过: {}", path.getFileName());
			}
		} catch (Exception e) {
			LOG.error("[script] 脚本读取失败: {}", path.getFileName(), e);
		}
	}

	public List<ScriptDefinition> all() {
		load();
		return scripts;
	}

	public ScriptDefinition create(String name) {
		load();
		ScriptDefinition definition = ScriptDefinition.create(uniqueName(name), xyz.hegke.client.script.node.BlockNode.of("event.tick"));
		scripts.add(definition);
		writeFile(definition);
		return definition;
	}

	public void save(ScriptDefinition definition) {
		writeFile(definition);
	}

	public void delete(ScriptDefinition definition) {
		scripts.remove(definition);
		try {
			Files.deleteIfExists(fileFor(definition));
		} catch (IOException e) {
			LOG.error("[script] 脚本删除失败: {}", definition.name, e);
		}
	}

	public void rename(ScriptDefinition definition, String newName) {
		Path oldFile = fileFor(definition);
		definition.name = uniqueName(newName);
		// 先落新文件，成功后再删旧文件：顺序反过来一旦写入失败就彻底丢了脚本
		if (writeFile(definition) && !oldFile.equals(fileFor(definition))) {
			try {
				Files.deleteIfExists(oldFile);
			} catch (IOException ignored) {
				// 旧文件删除失败不阻塞重命名，最多留一个孤儿文件
			}
		}
	}

	/**
	 * 复制脚本（深拷贝积木树与函数体），副本默认停用。
	 */
	public ScriptDefinition duplicate(ScriptDefinition source) {
		load();
		ScriptDefinition copy = ScriptDefinition.create(
				uniqueName(source.name + "_copy"), deepCopy(source.hat));
		copy.enabled = false;
		copy.functions = new ArrayList<>();
		if (source.functions != null) {
			for (FunctionDefinition function : source.functions) {
				FunctionDefinition cloned = new FunctionDefinition();
				cloned.name = function.name;
				cloned.body = function.body == null ? null : deepCopy(function.body);
				copy.functions.add(cloned);
			}
		}
		scripts.add(copy);
		writeFile(copy);
		return copy;
	}

	private BlockNode deepCopy(BlockNode node) {
		return GSON.fromJson(GSON.toJson(node), BlockNode.class);
	}

	private boolean writeFile(ScriptDefinition definition) {
		try {
			Files.createDirectories(directory());
			Path path = fileFor(definition);
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(definition, writer);
			}
			return true;
		} catch (IOException e) {
			LOG.error("[script] 脚本写入失败: {}", definition.name, e);
			notifyPlayer("message.hk-script.save_failed", definition.name);
			return false;
		}
	}

	/**
	 * 写入失败时提示玩家——否则界面看起来保存成功，重启后改动却消失了。
	 */
	private void notifyPlayer(String translationKey, String scriptName) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.translatable(translationKey, scriptName), false);
		}
	}

	public Path fileFor(ScriptDefinition definition) {
		return directory().resolve(safeFileName(definition.name) + ".json");
	}

	public Path exportsDir() {
		return directory().getParent().resolve("exports");
	}

	public Path importsDir() {
		return directory().getParent().resolve("imports");
	}

	/**
	 * 导出脚本到 {@code config/hk-script/exports/}，文件名由脚本名安全化得到。
	 *
	 * @return 导出后的文件路径；失败返回 {@code null}
	 */
	public Path exportTo(ScriptDefinition definition) {
		try {
			Files.createDirectories(exportsDir());
			Path target = exportsDir().resolve(safeFileName(definition.name) + ".json");
			try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
				GSON.toJson(definition, writer);
			}
			lastMessage = "已导出 " + target.getFileName();
			return target;
		} catch (IOException e) {
			LOG.error("[script] 导出失败: {}", definition.name, e);
			lastMessage = "导出失败，详见日志";
			return null;
		}
	}

	/**
	 * 扫描 {@code config/hk-script/imports/} 并导入其中的脚本。
	 *
	 * <p>导入后源文件被移入 {@code imports/imported/}，避免重复导入同一文件。</p>
	 *
	 * @return 成功导入的数量
	 */
	public int importAll() {
		load();
		int imported = 0;
		lastImportDetail = "";
		try {
			Files.createDirectories(importsDir());
			Path done = importsDir().resolve("imported");
			try (Stream<Path> files = Files.list(importsDir())) {
				for (Path path : files.toList()) {
					String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
					boolean handled = false;
					if (fileName.endsWith(".json")) {
						imported += importOne(path) ? 1 : 0;
						handled = true;
					} else if (fileName.endsWith(".sb3")) {
						imported += importSb3(path);
						handled = true;
					}
					if (handled) {
						Files.createDirectories(done);
						Files.move(path, done.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		} catch (IOException e) {
			LOG.error("[script] 导入目录处理失败: {}", importsDir(), e);
		}
		lastMessage = !lastImportDetail.isBlank() ? lastImportDetail
				: (imported > 0 ? "已导入 " + imported + " 个脚本" : "imports 目录中没有可导入的脚本");
		return imported;
	}

	/**
	 * 导入 Scratch 3 项目：一个 sb3 可能产出多个脚本（每个帽子积木一个）。
	 */
	private int importSb3(Path path) {
		try {
			Sb3Parser.ParseResult parsed = Sb3Parser.parse(Files.readAllBytes(path));
			int count = 0;
			int updated = 0;
			for (ScriptDefinition definition : parsed.scripts()) {
				// 同名脚本视为「同一脚本的新版本」：覆盖内容、保留启用状态与名字。
				// 否则改完 sb3 重新导入会多出一个 角色1#2，旧的那份还在跑，看起来像「改了没反应」。
				ScriptDefinition existing = findByName(definition.name);
				if (existing != null) {
					existing.hat = definition.hat;
					existing.functions = definition.functions;
					existing.scopeId = definition.scopeId;
					writeFile(existing);
					count++;
					updated++;
					continue;
				}
				definition.name = uniqueName(definition.name);
				scripts.add(definition);
				writeFile(definition);
				count++;
			}
			if (updated > 0) {
				LOG.info("[sb3] 其中 {} 个脚本覆盖了同名旧版本", updated);
			}
			LOG.info("[sb3] 自 {} {}", path.getFileName(), parsed.summary());
			// 把「跳过了什么」留在界面上，避免用户以为整份脚本都导入成功
			lastImportDetail = lastImportDetail.isBlank()
					? parsed.summary()
					: lastImportDetail + "；" + parsed.summary();
			return count;
		} catch (Exception e) {
			LOG.error("[sb3] 导入失败: {}", path.getFileName(), e);
			lastImportDetail = "导入失败：" + path.getFileName();
			return 0;
		}
	}

	/**
	 * 按显示名查找已有脚本（不区分大小写，与 {@link #exists} 的判定一致）。
	 */
	private ScriptDefinition findByName(String name) {
		for (ScriptDefinition script : scripts) {
			if (script.name.equalsIgnoreCase(name)) {
				return script;
			}
		}
		return null;
	}

	private boolean importOne(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			ScriptDefinition definition = GSON.fromJson(reader, ScriptDefinition.class);
			if (definition == null || definition.hat == null) {
				LOG.warn("[script] 导入文件格式无效: {}", path.getFileName());
				return false;
			}
			definition.name = uniqueName(definition.name);
			scripts.add(definition);
			writeFile(definition);
			LOG.info("[script] 已导入脚本: {}", definition.name);
			return true;
		} catch (Exception e) {
			LOG.error("[script] 导入失败: {}", path.getFileName(), e);
			return false;
		}
	}

	/**
	 * 最近一次导入/导出的结果提示，供界面展示。
	 */
	public String lastMessage() {
		return lastMessage;
	}

	public void clearMessage() {
		lastMessage = "";
	}

	private String uniqueName(String base) {
		String trimmed = base == null || base.isBlank() ? "untitled" : base.trim();
		String candidate = trimmed;
		int index = 2;
		while (exists(candidate)) {
			candidate = trimmed + "_" + index++;
		}
		return candidate;
	}

	/**
	 * 按<b>文件名</b>判重，而不是按显示名。
	 *
	 * <p>文件名会把非字母数字替换成下划线，所以「挖矿 1」和「挖矿_1」会落到同一个文件。
	 * 只比较显示名会让这两个脚本互相覆盖。</p>
	 */
	private boolean exists(String name) {
		return scripts.stream().anyMatch(script -> sameFileName(script.name, name));
	}

	/**
	 * 两个显示名是否会落到同一个文件。文件名会把非字母数字替换成下划线，
	 * 「挖矿 1」与「挖矿_1」因此冲突。判重必须按这个结果，而不是按显示名。
	 */
	public static boolean sameFileName(String a, String b) {
		return safeFileName(a).equals(safeFileName(b));
	}

	private static String safeFileName(String name) {
		// 保留各语言字母与数字，避免中文脚本名被整串替换成下划线
		String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_\\-]", "_");
		return safe.isBlank() ? "script" : safe;
	}

	public List<ScriptDefinition> sorted() {
		List<ScriptDefinition> copy = new ArrayList<>(all());
		copy.sort(Comparator.comparing(script -> script.name.toLowerCase(Locale.ROOT)));
		return copy;
	}
}
