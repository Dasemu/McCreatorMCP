package com.mccreatormcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mccreatormcp.util.BinarySource;
import com.mccreatormcp.util.Compat;
import com.mccreatormcp.util.Io;
import com.mccreatormcp.util.UiRefresh;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlReader;
import de.javagl.obj.MtlWriter;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.action.impl.workspace.resources.ModelImportActions;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.ReferencesFinder;
import net.mcreator.workspace.resources.Model;
import net.mcreator.workspace.resources.TexturedModel;

import javax.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelImportTool extends MCreatorMcpTool<ModelImportTool.Args> {

	private static final Pattern JSON_TEXTURE_REF = Pattern.compile("\"#(.*?)\"");

	public static class Args {
		@SchemaDescription("""
				LIST: workspace models with type and usages. \
				INSPECT: for a JSON or OBJ model file (source), returns texture slots that need a textureMapping. \
				IMPORT_JSON: Java Edition block/item JSON model (e.g. from Blockbench) with textureMapping. \
				IMPORT_OBJ: OBJ model (source) + MTL (mtlSource) with textureMapping keyed by material names. \
				IMPORT_JAVA: Java entity model class (e.g. Blockbench Java export) for living entities. \
				IMPORT_BEDROCK: Bedrock .geo.json model (used by animated entities when supported by the generator). \
				REMOVE: remove a model by name (refused while used unless force is true).""")
		public Action actionType;

		@SchemaDescription("Model file") @Nullable public BinarySource source;
		@SchemaDescription("IMPORT_OBJ: MTL material file") @Nullable public BinarySource mtlSource;
		@SchemaDescription("IMPORT_JAVA: Java model source code as text (alternative to source)") @Nullable public String code;
		@SchemaDescription("Model name (file name base, normalized to lower_snake_case). For REMOVE use the name from LIST.")
		@Nullable public String name;
		@SchemaDescription("Texture slot -> block texture name (workspace texture name or minecraft:block/stone). Required for JSON/OBJ models.")
		@Nullable public Map<String, String> textureMapping;
		@SchemaDescription("Name of the texture mapping variant (default: default)") @Nullable public String textureMappingName;
		@Nullable public Boolean force;

		public enum Action {
			LIST, INSPECT, IMPORT_JSON, IMPORT_OBJ, IMPORT_JAVA, IMPORT_BEDROCK, REMOVE
		}
	}

	public ModelImportTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "model_import";
	}

	@Override public String getDescription() {
		return """
				Imports custom 3D models into the workspace: Java Edition JSON block/item models and OBJ+MTL models \
				(with texture mapping to workspace block textures), Java entity model classes and Bedrock geometry. \
				Custom models make blocks, items and entities look unique. Imported models are selectable in \
				mod element definitions (list them with query_workspace MODELS_JSON / MODELS_OBJ / MODELS_JAVA).""";
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
		File modelsDir = mcreator.getFolderManager().getModelsDir();

		switch (input.actionType) {
		case LIST -> {
			List<Map<String, Object>> result = new ArrayList<>();
			for (Model model : Model.getModels(workspace)) {
				Map<String, Object> info = new LinkedHashMap<>();
				info.put("name", model.getReadableName());
				info.put("type", model.getType().name());
				info.put("file", model.getFile().getName());
				Map<String, TexturedModel.TextureMapping> mappings = TexturedModel.getTextureMappingsForModel(workspace,
						model);
				if (mappings != null)
					info.put("textureMappings", mappings.keySet());
				info.put("usedBy", ReferencesFinder.searchModelUsages(workspace, model).stream()
						.map(ModElement::getName).sorted().toList());
				result.add(info);
			}
			return completed(ToolResult.collection(result));
		}
		case INSPECT -> {
			String content = text(input);
			Set<String> slots = content.contains("minecraft:geometry") ? Set.of() : jsonTextureSlots(content);
			if (slots.isEmpty() && content.contains("mtllib"))
				return completedText("OBJ model: texture slots are the material names (newmtl) of the MTL file");
			return completedObject(Map.of("textureSlots", slots));
		}
		case IMPORT_JSON -> {
			String content = text(input);
			JsonObject json;
			try {
				json = JsonParser.parseString(content).getAsJsonObject();
			} catch (Exception e) {
				return completedError("Invalid JSON model: " + e.getMessage());
			}
			if (!json.has("elements") && !json.has("parent"))
				return completedError("Not a Java Edition JSON model (no elements or parent)");
			Set<String> slots = jsonTextureSlots(content);
			String fileName = fileName(input, "json");
			File target = new File(modelsDir, fileName);
			if (target.exists() && !Boolean.TRUE.equals(input.force))
				return completedError("Model " + fileName + " already exists, set force to true to overwrite");
			Map<String, TexturedModel.TextureMapping> mapping = mapping(workspace, input, slots);
			modelsDir.mkdirs();
			Io.writeString(content, target);
			if (mapping != null)
				Io.writeString(TexturedModel.getJSONForTextureMapping(mapping),
						new File(modelsDir, fileName + ".textures"));
			Compat.invalidateModelCache(workspace, target);
			UiRefresh.resourcesChanged(mcreator);
			return completedObject(Map.of("model", fileName.replace(".json", ""), "textureSlots", slots));
		}
		case IMPORT_OBJ -> {
			if (input.mtlSource == null || input.mtlSource.isEmpty())
				return completedError("mtlSource must be provided for OBJ models");
			String obj = text(input);
			String fileName = fileName(input, "obj");
			File target = new File(modelsDir, fileName);
			if (target.exists() && !Boolean.TRUE.equals(input.force))
				return completedError("Model " + fileName + " already exists, set force to true to overwrite");
			List<Mtl> materials = MtlReader.read(new ByteArrayInputStream(input.mtlSource.read()));
			Set<String> slots = new LinkedHashSet<>();
			for (Mtl material : materials) {
				slots.add(material.getName());
				material.setMapKd(null); // textures come from texture mapping
			}
			Map<String, TexturedModel.TextureMapping> mapping = mapping(workspace, input, slots);
			String base = fileName.substring(0, fileName.lastIndexOf('.'));
			modelsDir.mkdirs();
			// point the OBJ to the MTL stored next to it
			obj = obj.replaceAll("(?m)^mtllib .*$", "mtllib " + base + ".mtl");
			Io.writeString(obj, target);
			try (OutputStream out = new FileOutputStream(new File(modelsDir, base + ".mtl"))) {
				MtlWriter.write(materials, out);
			}
			if (mapping != null)
				Io.writeString(TexturedModel.getJSONForTextureMapping(mapping),
						new File(modelsDir, fileName + ".textures"));
			Compat.invalidateModelCache(workspace, target);
			UiRefresh.resourcesChanged(mcreator);
			return completedObject(Map.of("model", base, "materials", slots));
		}
		case IMPORT_JAVA -> {
			String code = input.code != null && !input.code.isBlank() ? input.code : text(input);
			try {
				ModelImportActions.importJavaModel(null, workspace, code);
			} catch (RuntimeException e) {
				return completedError("Java model import failed: " + e.getMessage() + ". The model must be a Java "
						+ "entity model compatible with " + workspace.getGeneratorConfiguration()
						.getCompatibleJavaModelKeys() + " mappings (Blockbench: File > Export > Export Java Entity)");
			}
			Compat.invalidateModelCache(workspace, null);
			UiRefresh.resourcesChanged(mcreator);
			return completedText("Java model imported. List it with query_workspace MODELS_JAVA.");
		}
		case IMPORT_BEDROCK -> {
			String content = text(input);
			String identifier;
			try {
				identifier = JsonParser.parseString(content).getAsJsonObject().getAsJsonArray("minecraft:geometry")
						.get(0).getAsJsonObject().getAsJsonObject("description").get("identifier").getAsString();
			} catch (Exception e) {
				return completedError("Invalid Bedrock geometry: minecraft:geometry[0].description.identifier missing");
			}
			String fileName = RegistryNameFixer.fix(identifier.replace("geometry.", "")) + ".geo.json";
			File target = new File(modelsDir, fileName);
			if (target.exists() && !Boolean.TRUE.equals(input.force))
				return completedError("Model " + fileName + " already exists, set force to true to overwrite");
			modelsDir.mkdirs();
			Io.writeString(content, target);
			Compat.invalidateModelCache(workspace, target);
			UiRefresh.resourcesChanged(mcreator);
			return completedText("Bedrock model imported as " + fileName);
		}
		case REMOVE -> {
			if (input.name == null || input.name.isBlank())
				return completedError("name must be provided");
			Model model = Model.getModels(workspace).stream()
					.filter(m -> m.getReadableName().equalsIgnoreCase(input.name.trim()) || m.getFile().getName()
							.equalsIgnoreCase(input.name.trim())).findFirst().orElse(null);
			if (model == null)
				return completedError("Model " + input.name + " not found. Use LIST to see models.");
			Set<ModElement> usages = ReferencesFinder.searchModelUsages(workspace, model);
			if (!usages.isEmpty() && !Boolean.TRUE.equals(input.force))
				return completedError("Model is used by: " + usages.stream().map(ModElement::getName).sorted().toList()
						+ ". Set force to true to remove anyway.");
			workspace.getHistoryManager().checkpoint("before_delete", model.getReadableName());
			for (File file : model.getFiles())
				file.delete();
			new File(modelsDir, model.getFile().getName() + ".textures").delete();
			Compat.invalidateModelCache(workspace, null);
			UiRefresh.resourcesChanged(mcreator);
			return completedText("Model removed");
		}
		}
		return completedError("Unknown action");
	}

	private static String text(Args input) throws Exception {
		if (input.source == null || input.source.isEmpty())
			throw new IllegalArgumentException("source must be provided");
		return new String(input.source.read(), StandardCharsets.UTF_8);
	}

	private static String fileName(Args input, String extension) {
		String base = input.name != null && !input.name.isBlank() ?
				input.name :
				(input.source != null ? input.source.suggestedBaseName() : null);
		if (base == null || base.isBlank())
			throw new IllegalArgumentException("name must be provided");
		base = RegistryNameFixer.fix(base.trim().replaceAll("\\." + extension + "$", ""));
		if (base.isBlank())
			throw new IllegalArgumentException("Invalid model name");
		return base + "." + extension;
	}

	private static Set<String> jsonTextureSlots(String content) {
		Set<String> slots = new LinkedHashSet<>();
		Matcher matcher = JSON_TEXTURE_REF.matcher(content);
		while (matcher.find())
			slots.add(matcher.group(1));
		return slots;
	}

	@Nullable private static Map<String, TexturedModel.TextureMapping> mapping(Workspace workspace, Args input,
			Set<String> slots) {
		if (slots.isEmpty())
			return null;
		if (input.textureMapping == null || !input.textureMapping.keySet().containsAll(slots)) {
			Set<String> missing = new LinkedHashSet<>(slots);
			if (input.textureMapping != null)
				missing.removeAll(input.textureMapping.keySet());
			throw new IllegalArgumentException(
					"textureMapping must map every texture slot to a block texture. Missing slots: " + missing);
		}
		Map<String, TextureHolder> map = new LinkedHashMap<>();
		for (String slot : slots) {
			String texture = input.textureMapping.get(slot).trim().replaceAll("\\.png$", "");
			if (!texture.contains(":") && !workspace.getFolderManager().getTextureFile(texture, TextureType.BLOCK)
					.isFile())
				throw new IllegalArgumentException("Block texture " + texture + " for slot " + slot
						+ " does not exist. Import it with texture_import (textureType block) first.");
			map.put(slot, new TextureHolder(workspace, texture));
		}
		String mappingName = input.textureMappingName == null || input.textureMappingName.isBlank() ?
				"default" :
				input.textureMappingName.trim();
		Map<String, TexturedModel.TextureMapping> result = new LinkedHashMap<>();
		result.put(mappingName, new TexturedModel.TextureMapping(mappingName, map));
		return result;
	}

}
