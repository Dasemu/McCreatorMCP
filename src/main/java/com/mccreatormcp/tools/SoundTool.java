package com.mccreatormcp.tools;

import com.mccreatormcp.util.BinarySource;
import com.mccreatormcp.util.Compat;
import com.mccreatormcp.util.Io;
import com.mccreatormcp.util.UiRefresh;
import net.mcreator.generator.LocalizationUtils;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.minecraft.ElementUtil;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SoundTool extends MCreatorMcpTool<SoundTool.Args> {

	public static class Args {
		@SchemaDescription("""
				LIST: workspace sounds with files, category, subtitle and usage count. \
				LIST_CATEGORIES: valid sound categories. \
				IMPORT: create a sound event from one or more OGG files (several files = random variations). \
				UPDATE: change category/subtitle and optionally add more variation files to an existing sound. \
				REMOVE: delete a sound and its files (refused while used unless force is true).""")
		public Action actionType;

		@SchemaDescription("Sound registry name, lowercase, dots allowed, e.g. ruby_ore_break or entity.ruby_golem.hurt")
		@Nullable public String name;
		@SchemaDescription("OGG Vorbis files (mono for positional sounds). Each one is a random variation.")
		@Nullable public List<BinarySource> files;
		@SchemaDescription("Sound category, e.g. master, music, record, weather, block, hostile, neutral, player, ambient, voice")
		@Nullable public String category;
		@SchemaDescription("Subtitle text shown when subtitles are enabled (accessibility)") @Nullable public String subtitle;
		@Nullable public Boolean force;

		public enum Action {
			LIST, LIST_CATEGORIES, IMPORT, UPDATE, REMOVE
		}
	}

	public SoundTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "sound";
	}

	@Override public String getDescription() {
		return """
				Manages workspace sounds (sound events): import OGG files from base64, URL or local path, set \
				category and subtitle, add variations, list and remove. Imported sounds can be referenced in \
				mod elements and procedures as <modid>:<name>. Always provide a subtitle for quality mods.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return false;
	}

	@Override protected Boolean getDestructiveHint() {
		return true;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) throws Exception {
		if (input.actionType == null)
			return completedError("actionType must be provided");
		Workspace workspace = mcreator.getWorkspace();
		File soundsDir = mcreator.getFolderManager().getSoundsDir();
		if (soundsDir == null && input.actionType != Args.Action.LIST_CATEGORIES)
			return completedError("This generator does not support sounds");

		switch (input.actionType) {
		case LIST -> {
			List<Map<String, Object>> result = new ArrayList<>();
			for (SoundElement sound : workspace.getSoundElements()) {
				Map<String, Object> info = new LinkedHashMap<>();
				info.put("name", sound.getName());
				info.put("id", workspace.getWorkspaceSettings().getModID() + ":" + sound.getName());
				info.put("files", sound.getFiles());
				info.put("category", sound.getCategory());
				info.put("subtitle", sound.getSubtitle());
				info.put("usedBy", Compat.soundUsages(workspace, sound).stream()
						.map(ModElement::getName).sorted().toList());
				result.add(info);
			}
			return completed(ToolResult.collection(result));
		}
		case LIST_CATEGORIES -> {
			return completed(ToolResult.collection(categories()));
		}
		case IMPORT -> {
			String name = soundName(input.name);
			if (find(workspace, name) != null)
				return completedError("Sound " + name + " already exists, use UPDATE to change it");
			if (input.files == null || input.files.isEmpty())
				return completedError("files must contain at least one OGG file");
			String category = category(input.category);
			List<String> fileNames = writeFiles(soundsDir, name, input.files, 0);
			String subtitle = input.subtitle == null ? "" : input.subtitle;
			workspace.addSoundElement(new SoundElement(name, fileNames, category, subtitle));
			if (!subtitle.isBlank())
				workspace.setLocalization("subtitles." + name, subtitle);
			workspace.getHistoryManager().checkpoint("mod_element_added", "sound", name);
			regenerate(mcreator);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("name", name);
			result.put("id", workspace.getWorkspaceSettings().getModID() + ":" + name);
			result.put("files", fileNames);
			result.put("category", category);
			if (subtitle.isBlank())
				result.put("note", "No subtitle set. Subtitles are important for accessibility, set one with UPDATE.");
			return completedObject(result);
		}
		case UPDATE -> {
			SoundElement sound = find(workspace, soundName(input.name));
			if (sound == null)
				return completedError("Sound " + input.name + " does not exist");
			if (input.category != null)
				sound.setCategory(category(input.category));
			if (input.subtitle != null) {
				sound.setSubtitle(input.subtitle);
				workspace.setLocalization("subtitles." + sound.getName(), input.subtitle);
			}
			if (input.files != null && !input.files.isEmpty()) {
				List<String> files = new ArrayList<>(sound.getFiles());
				files.addAll(writeFiles(soundsDir, sound.getName(), input.files, files.size()));
				sound.setFiles(files);
			}
			workspace.markDirty();
			workspace.getHistoryManager().checkpoint("mod_element_edited", sound.getName());
			regenerate(mcreator);
			return completedObject(Map.of("name", sound.getName(), "files", sound.getFiles(), "category",
					sound.getCategory(), "subtitle", Objects.requireNonNullElse(sound.getSubtitle(), "")));
		}
		case REMOVE -> {
			SoundElement sound = find(workspace, soundName(input.name));
			if (sound == null)
				return completedError("Sound " + input.name + " does not exist");
			Set<ModElement> usages = Compat.soundUsages(workspace, sound);
			if (!usages.isEmpty() && !Boolean.TRUE.equals(input.force))
				return completedError("Sound is used by: " + usages.stream().map(ModElement::getName).sorted().toList()
						+ ". Set force to true to remove anyway.");
			workspace.getHistoryManager().checkpoint("before_delete", sound.getName());
			workspace.removeSoundElement(sound);
			workspace.removeLocalizationEntryByKey("subtitles." + sound.getName());
			regenerate(mcreator);
			return completedText("Sound " + sound.getName() + " removed");
		}
		}
		return completedError("Unknown action");
	}

	private static List<String> writeFiles(File soundsDir, String name, List<BinarySource> sources, int offset)
			throws IOException {
		// read and validate everything first so a bad file does not leave half imported sounds behind
		List<byte[]> data = new ArrayList<>();
		for (BinarySource source : sources) {
			byte[] bytes = source.read();
			if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S')
				throw new IOException("Sound files must be OGG Vorbis (.ogg). Convert other formats first, e.g. "
						+ "ffmpeg -i in.wav -ac 1 -c:a libvorbis -q:a 5 out.ogg");
			data.add(bytes);
		}
		String baseFileName = name.replace('.', '_').replace('/', '_');
		List<String> fileNames = new ArrayList<>();
		for (int i = 0; i < data.size(); i++) {
			int index = offset + i;
			String fileName = index == 0 ? baseFileName : baseFileName + "_" + (index + 1);
			File file = new File(soundsDir, fileName + ".ogg");
			int suffix = index + 2;
			while (file.exists())
				file = new File(soundsDir, (fileName = baseFileName + "_" + suffix++) + ".ogg");
			soundsDir.mkdirs();
			Io.writeBytes(data.get(i), file);
			fileNames.add(fileName);
		}
		return fileNames;
	}

	@Nullable private static SoundElement find(Workspace workspace, String name) {
		for (SoundElement sound : workspace.getSoundElements()) {
			if (sound.getName().equals(name))
				return sound;
		}
		return null;
	}

	private static String soundName(@Nullable String name) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("name must be provided");
		String clean = name.trim();
		if (clean.contains(":"))
			clean = clean.substring(clean.indexOf(':') + 1);
		String fixed = RegistryNameFixer.fix(clean);
		if (fixed.isBlank())
			throw new IllegalArgumentException("Invalid sound name");
		return fixed;
	}

	private static List<String> categories() {
		return Arrays.asList(ElementUtil.getDataListAsStringArray("soundcategories"));
	}

	private static String category(@Nullable String category) {
		List<String> categories = categories();
		if (category == null || category.isBlank())
			return categories.contains("neutral") ? "neutral" : categories.getFirst();
		for (String c : categories) {
			if (c.equalsIgnoreCase(category.trim()))
				return c;
		}
		throw new IllegalArgumentException("Unknown sound category " + category + ". Valid: " + categories);
	}

	private static void regenerate(MCreator mcreator) throws Exception {
		mcreator.getGenerator().generateBase(true);
		LocalizationUtils.generateLanguageFiles(mcreator.getGenerator(), mcreator.getWorkspace(),
				mcreator.getGeneratorConfiguration().getLanguageFileSpecification());
		UiRefresh.resourcesChanged(mcreator);
	}

}
