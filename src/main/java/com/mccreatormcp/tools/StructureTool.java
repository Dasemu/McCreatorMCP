package com.mccreatormcp.tools;

import com.mccreatormcp.util.BinarySource;
import com.mccreatormcp.util.Compat;
import com.mccreatormcp.util.Io;
import com.mccreatormcp.util.Names;
import com.mccreatormcp.util.UiRefresh;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import javax.annotation.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class StructureTool extends MCreatorMcpTool<StructureTool.Args> {

	public static class Args {
		@SchemaDescription("""
				LIST: structures with usages. \
				IMPORT: import a structure file (.nbt saved with a Structure Block) from base64, URL or local path. \
				IMPORT_FROM_SAVES: copy a structure saved in a test world of the client run folder (name = save/structure name). \
				REMOVE: delete a structure (refused while used unless force is true).""")
		public Action actionType;
		@SchemaDescription("Structure name (lower_snake_case)") @Nullable public String name;
		@Nullable public BinarySource source;
		@SchemaDescription("IMPORT_FROM_SAVES: name of the world save folder (optional, searches all saves if missing)")
		@Nullable public String saveName;
		@Nullable public Boolean force;

		public enum Action {
			LIST, IMPORT, IMPORT_FROM_SAVES, REMOVE
		}
	}

	public StructureTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "structure";
	}

	@Override public String getDescription() {
		return """
				Manages structure files used by structure mod elements and procedures (place structure): list, \
				import .nbt files, import structures saved with a Structure Block in the test client worlds, remove.""";
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
		File structuresDir = mcreator.getFolderManager().getStructuresDir();
		if (structuresDir == null)
			return completedError("This generator does not support structures");
		String extension = mcreator.getGeneratorConfiguration().getStructureExtension();

		switch (input.actionType) {
		case LIST -> {
			List<Map<String, Object>> result = new ArrayList<>();
			for (String structure : mcreator.getFolderManager().getStructureList()) {
				result.add(Map.of("name", structure, "usedBy",
						Compat.structureUsages(workspace, structure).stream()
								.map(ModElement::getName).sorted().toList()));
			}
			return completed(ToolResult.collection(result));
		}
		case IMPORT -> {
			if (input.source == null || input.source.isEmpty())
				return completedError("source must be provided");
			String name = Names.resourceName(
					input.name != null && !input.name.isBlank() ? input.name : input.source.suggestedBaseName());
			if (name.isBlank())
				return completedError("name must be provided");
			byte[] data = input.source.read();
			if ("nbt".equals(extension) && (data.length < 2 || (data[0] & 0xFF) != 0x1F || (data[1] & 0xFF) != 0x8B))
				return completedError("File is not a gzipped NBT structure (.nbt saved by a Structure Block)");
			File target = new File(structuresDir, name + "." + extension);
			if (target.exists() && !Boolean.TRUE.equals(input.force))
				return completedError("Structure " + name + " exists, set force to true to overwrite");
			structuresDir.mkdirs();
			Io.writeBytes(data, target);
			UiRefresh.resourcesChanged(mcreator);
			return completedText("Structure imported as " + name);
		}
		case IMPORT_FROM_SAVES -> {
			if (input.name == null || input.name.isBlank())
				return completedError("name of the saved structure must be provided");
			File found = findInSaves(mcreator.getFolderManager().getClientRunDir(), input.saveName,
					input.name.trim().replaceAll("\\." + extension + "$", ""), extension);
			if (found == null)
				return completedError("Structure " + input.name + " not found in run/saves/*/generated/*/structure(s)");
			String name = Names.resourceName(input.name);
			File target = new File(structuresDir, name + "." + extension);
			if (target.exists() && !Boolean.TRUE.equals(input.force))
				return completedError("Structure " + name + " exists, set force to true to overwrite");
			structuresDir.mkdirs();
			Io.copy(found, target);
			UiRefresh.resourcesChanged(mcreator);
			return completedText("Structure imported as " + name + " from " + found.getAbsolutePath());
		}
		case REMOVE -> {
			if (input.name == null || input.name.isBlank())
				return completedError("name must be provided");
			String name = input.name.trim();
			if (!mcreator.getFolderManager().getStructureList().contains(name))
				return completedError("Structure " + name + " does not exist");
			Set<ModElement> usages = Compat.structureUsages(workspace, name);
			if (!usages.isEmpty() && !Boolean.TRUE.equals(input.force))
				return completedError("Structure is used by: " + usages.stream().map(ModElement::getName).sorted()
						.toList() + ". Set force to true to remove anyway.");
			new File(structuresDir, name + "." + extension).delete();
			UiRefresh.resourcesChanged(mcreator);
			return completedText("Structure removed");
		}
		}
		return completedError("Unknown action");
	}

	@Nullable private static File findInSaves(File runDir, @Nullable String saveName, String name, String extension) {
		File[] saves = new File(runDir, "saves").listFiles(File::isDirectory);
		if (saves == null)
			return null;
		for (File save : saves) {
			if (saveName != null && !saveName.isBlank() && !save.getName().equals(saveName))
				continue;
			List<File> candidates = new ArrayList<>();
			candidates.add(new File(save, "structures/" + name + "." + extension));
			File[] namespaces = new File(save, "generated").listFiles(File::isDirectory);
			for (File namespace : namespaces != null ? namespaces : new File[0]) {
				candidates.add(new File(namespace, "structures/" + name + "." + extension));
				candidates.add(new File(namespace, "structure/" + name + "." + extension));
			}
			for (File candidate : candidates) {
				if (candidate.isFile())
					return candidate;
			}
		}
		return null;
	}

}
