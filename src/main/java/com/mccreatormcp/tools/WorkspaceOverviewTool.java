package com.mccreatormcp.tools;

import com.mccreatormcp.McpPlusPlugin;
import net.mcreator.Launcher;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.resources.Model;
import net.mcreator.workspace.settings.WorkspaceSettings;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class WorkspaceOverviewTool extends MCreatorMcpTool<Void> {

	public WorkspaceOverviewTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Void.class);
	}

	@Override public String getName() {
		return "workspace_overview";
	}

	@Override public String getDescription() {
		return """
				One-call summary of the open workspace: mod metadata, generator and Minecraft version, number of \
				mod elements per type, code locked and non-compiling elements, resource counts (textures per type, \
				sounds, structures, models), languages and folders. Use it first to orient yourself in a workspace.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return true;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Void input) {
		Workspace workspace = mcreator.getWorkspace();
		WorkspaceSettings settings = workspace.getWorkspaceSettings();
		GeneratorConfiguration generator = workspace.getGeneratorConfiguration();

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("mcreatorVersion", Launcher.version.getFullString());
		result.put("mcpPlusVersion", McpPlusPlugin.VERSION);

		Map<String, Object> mod = new LinkedHashMap<>();
		mod.put("modId", settings.getModID());
		mod.put("modName", settings.getModName());
		mod.put("version", settings.getVersion());
		mod.put("description", settings.getDescription());
		mod.put("author", settings.getAuthor());
		mod.put("license", settings.getLicense());
		mod.put("websiteURL", settings.getWebsiteURL());
		mod.put("modPicture", settings.getModPicture());
		mod.put("package", settings.getModElementsPackage());
		mod.put("workspaceFolder", workspace.getWorkspaceFolder().getAbsolutePath());
		result.put("mod", mod);

		Map<String, Object> gen = new LinkedHashMap<>();
		gen.put("generator", generator.getGeneratorName());
		gen.put("minecraftVersion", generator.getGeneratorMinecraftVersion());
		gen.put("flavor", generator.getGeneratorFlavor().name());
		gen.put("status", generator.getGeneratorStats().getStatus().name());
		result.put("generator", gen);

		Map<String, Integer> elementsByType = new TreeMap<>();
		List<String> locked = new ArrayList<>();
		List<String> notCompiling = new ArrayList<>();
		for (ModElement element : workspace.getModElements()) {
			elementsByType.merge(element.getType().getRegistryName(), 1, Integer::sum);
			if (element.isCodeLocked())
				locked.add(element.getName());
			if (!element.doesCompile())
				notCompiling.add(element.getName());
		}
		result.put("modElementCount", workspace.getModElements().size());
		result.put("modElementsByType", elementsByType);
		result.put("codeLockedElements", locked);
		result.put("elementsWithCompileErrors", notCompiling);

		Map<String, Object> resources = new LinkedHashMap<>();
		Map<String, Integer> textures = new LinkedHashMap<>();
		for (TextureType type : TextureType.getValuesForGenerator(generator)) {
			File folder = workspace.getFolderManager().getTexturesFolder(type);
			if (folder != null)
				textures.put(type.getID(), workspace.getFolderManager().getTexturesList(type).size());
		}
		resources.put("textures", textures);
		resources.put("sounds", workspace.getSoundElements().size());
		resources.put("structures", workspace.getFolderManager().getStructureList().size());
		resources.put("models", Model.getModels(workspace).size());
		resources.put("globalVariables", workspace.getVariableElements().size());
		resources.put("tags", workspace.getTagElements().size());
		result.put("resources", resources);

		Map<String, Integer> languages = new TreeMap<>();
		workspace.getLanguageMap().forEach((language, entries) -> languages.put(language, entries.size()));
		result.put("languages", languages);

		result.put("folders", workspace.getFoldersRoot().getRecursiveFolderChildren().stream()
				.map(folder -> folder.getPath()).toList());
		result.put("hint", "Run quality_audit to find problems that reduce mod quality.");
		return completedObject(result);
	}

}
