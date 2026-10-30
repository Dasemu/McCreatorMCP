package com.mccreatormcp.tools;

import com.mccreatormcp.util.ImageOps;
import com.mccreatormcp.util.Names;
import com.mccreatormcp.util.UiRefresh;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.NamespacedGeneratableElement;
import net.mcreator.element.types.CustomElement;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.ui.mcp.tools.utils.ModElementNameValidation;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.util.image.ImageUtils;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.FolderElement;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.ReferencesFinder;

import javax.annotation.Nullable;
import javax.swing.*;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ModElementManageTool extends MCreatorMcpTool<ModElementManageTool.Args> {

	public static class Args {
		@SchemaDescription("""
				LIST: mod elements with type, registry name, folder, code lock and compile status (filters optional). \
				DUPLICATE: copy elementName as newName (optionally into folder), keeps the full definition. \
				USAGES: mod elements that reference elementName. \
				LIST_FOLDERS: workspace folders. \
				CREATE_FOLDER: create folder (path like Ores/Tools, parents are created). \
				MOVE_TO_FOLDER: move elementNames to folder (empty folder = root). \
				DELETE_FOLDER: delete folder, its elements are moved to the parent folder. \
				OPEN_IN_EDITOR: open elementName in the MCreator editor tab for the user. \
				PREVIEW_ICON: return the element icon/preview as an image.""")
		public Action actionType;

		@Nullable public String elementName;
		@SchemaDescription("DUPLICATE: name of the copy (CamelCase)") @Nullable public String newName;
		@SchemaDescription("MOVE_TO_FOLDER: names of elements to move") @Nullable public List<String> elementNames;
		@SchemaDescription("Folder path, e.g. Ores/Tools") @Nullable public String folder;
		@SchemaDescription("LIST: filter by mod element type registry name, e.g. item, block, procedure") @Nullable public String type;
		@SchemaDescription("LIST: Java regex matched against element name") @Nullable public String filter;

		public enum Action {
			LIST, DUPLICATE, USAGES, LIST_FOLDERS, CREATE_FOLDER, MOVE_TO_FOLDER, DELETE_FOLDER, OPEN_IN_EDITOR, PREVIEW_ICON
		}
	}

	public ModElementManageTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "mod_element_manage";
	}

	@Override public String getDescription() {
		return """
				Workspace organization for mod elements: detailed listing, duplicating elements (fast way to create \
				variants such as a whole tool set from one tool), usage lookup, folders (create, move, delete), \
				opening an element in the editor for the user and previewing element icons. Use \
				mod_element_definition to read or change element definitions.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return false;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) throws Exception {
		if (input.actionType == null)
			return completedError("actionType must be provided");
		Workspace workspace = mcreator.getWorkspace();

		switch (input.actionType) {
		case LIST -> {
			Pattern pattern = input.filter == null || input.filter.isBlank() ? null : Pattern.compile(input.filter);
			List<Map<String, Object>> result = new ArrayList<>();
			for (ModElement element : workspace.getModElements()) {
				if (input.type != null && !input.type.isBlank() && !element.getType().getRegistryName()
						.equalsIgnoreCase(input.type.trim()))
					continue;
				if (pattern != null && !pattern.matcher(element.getName()).find())
					continue;
				Map<String, Object> info = new LinkedHashMap<>();
				info.put("name", element.getName());
				info.put("type", element.getType().getRegistryName());
				info.put("registryName", element.getRegistryName());
				info.put("folder", displayPath(element.getFolderPath()));
				info.put("codeLocked", element.isCodeLocked());
				info.put("compiles", element.doesCompile());
				result.add(info);
			}
			result.sort(Comparator.comparing(m -> (String) m.get("name")));
			return completed(ToolResult.collection(result));
		}
		case DUPLICATE -> {
			ModElement original = element(workspace, input.elementName);
			if (original.getType() == ModElementType.CODE || original.isCodeLocked())
				return completedError("Code mod elements and elements with locked code can not be duplicated by MCP");
			GeneratableElement originalGE = original.getGeneratableElement();
			if (originalGE == null || originalGE instanceof CustomElement)
				return completedError("Element definition could not be loaded");
			String newName;
			try {
				newName = ModElementNameValidation.normalizeAndValidateName(workspace, input.newName);
			} catch (IllegalArgumentException e) {
				return completedError(e.getMessage());
			}

			ModElement duplicate = new ModElement(workspace, original, newName);
			GeneratableElement duplicateGE = mcreator.getModElementManager().fromJSONtoGeneratableElementOrNull(
					mcreator.getModElementManager().generatableElementToJSON(originalGE), duplicate);
			if (duplicateGE == null)
				return completedError("Failed to copy element definition");
			if (duplicateGE instanceof NamespacedGeneratableElement namespaced)
				namespaced.name = RegistryNameFixer.fromCamelCase(newName);

			FolderElement folder = input.folder != null ? folder(workspace, input.folder, true) : null;
			duplicate.setParentFolder(folder != null ? folder : FolderElement.findFolderByPath(workspace,
					Objects.requireNonNullElse(original.getFolderPath(), "")));
			workspace.addModElement(duplicate);
			mcreator.getGenerator().generateElement(duplicateGE, true);
			mcreator.getModElementManager().storeModElementPicture(duplicateGE);
			mcreator.getModElementManager().storeModElement(duplicateGE);
			duplicate.reinit(workspace);
			workspace.getHistoryManager().checkpoint("mod_element_added", original.getType().getReadableName(), newName);
			UiRefresh.workspaceChanged(mcreator);
			return completedObject(Map.of("created", newName, "registryName", duplicate.getRegistryName(), "next",
					"Now change the copy with mod_element_definition PATCH (display name, texture, stats...)"));
		}
		case USAGES -> {
			ModElement element = element(workspace, input.elementName);
			return completed(ToolResult.collection(ReferencesFinder.searchModElementUsages(workspace, element).stream()
					.filter(e -> !e.equals(element)).map(e -> Map.of("name", e.getName(), "type",
							e.getType().getRegistryName())).toList()));
		}
		case LIST_FOLDERS -> {
			return completed(ToolResult.collection(workspace.getFoldersRoot().getRecursiveFolderChildren().stream()
					.map(f -> displayPath(f.getPath())).sorted().toList()));
		}
		case CREATE_FOLDER -> {
			FolderElement folder = folder(workspace, input.folder, true);
			return completedText("Folder " + displayPath(folder.getPath()) + " is ready");
		}
		case MOVE_TO_FOLDER -> {
			List<String> names = input.elementNames != null ? input.elementNames :
					(input.elementName != null ? List.of(input.elementName) : List.of());
			if (names.isEmpty())
				return completedError("elementNames must be provided");
			FolderElement folder = input.folder == null || input.folder.isBlank() ?
					workspace.getFoldersRoot() :
					folder(workspace, input.folder, true);
			List<String> moved = new ArrayList<>();
			for (String name : names) {
				ModElement element = element(workspace, name);
				element.setParentFolder(folder);
				moved.add(element.getName());
			}
			workspace.markDirty();
			UiRefresh.workspaceChanged(mcreator);
			return completedObject(Map.of("moved", moved, "folder", displayPath(folder.getPath())));
		}
		case DELETE_FOLDER -> {
			FolderElement folder = folder(workspace, input.folder, false);
			if (folder == null)
				return completedError("Folder " + input.folder + " does not exist");
			FolderElement parent = folder.getParent();
			Set<String> removedPaths = new HashSet<>();
			removedPaths.add(folder.getPath());
			folder.getRecursiveFolderChildren().forEach(f -> removedPaths.add(f.getPath()));
			for (ModElement element : workspace.getModElements()) {
				if (element.getFolderPath() != null && removedPaths.contains(element.getFolderPath()))
					element.setParentFolder(parent);
			}
			parent.removeChild(folder);
			workspace.markDirty();
			UiRefresh.workspaceChanged(mcreator);
			return completedText("Folder deleted, its elements moved to " + displayPath(parent.getPath()));
		}
		case OPEN_IN_EDITOR -> {
			ModElement element = element(workspace, input.elementName);
			AtomicReference<String> error = new AtomicReference<>();
			ThreadUtil.runOnSwingThreadAndWait(() -> {
				ModElementGUI<?> gui = element.getType().getModElementGUI(mcreator, element, true);
				if (gui == null)
					error.set("This element type has no editor");
				else
					gui.showView();
			});
			return error.get() != null ? completedError(error.get()) : completedText("Opened " + element.getName());
		}
		case PREVIEW_ICON -> {
			ModElement element = element(workspace, input.elementName);
			element.reloadElementIcon();
			ImageIcon icon = element.getElementIcon();
			if (icon == null || icon.getIconWidth() <= 0)
				return completedError("Element has no icon");
			BufferedImage image = ImageUtils.toBufferedImage(icon.getImage());
			return completed(ToolResult.image(TextureEditTool.png(ImageOps.preview(image, 256)), "image/png"));
		}
		}
		return completedError("Unknown action");
	}

	private static ModElement element(Workspace workspace, @Nullable String name) {
		ModElement element = Names.findModElement(workspace, name);
		if (element == null)
			throw new IllegalArgumentException("Mod element " + name + " not found. Names are CamelCase.");
		return element;
	}

	private static String displayPath(@Nullable String path) {
		if (path == null || path.isBlank() || path.equals(FolderElement.ROOT.getName()))
			return "";
		return path.startsWith(FolderElement.ROOT.getName() + "/") ?
				path.substring(FolderElement.ROOT.getName().length() + 1) :
				path;
	}

	@Nullable private static FolderElement folder(Workspace workspace, @Nullable String path, boolean create) {
		if (path == null || path.isBlank())
			throw new IllegalArgumentException("folder must be provided");
		FolderElement current = workspace.getFoldersRoot();
		for (String part : path.replace('\\', '/').split("/")) {
			String name = part.trim();
			if (name.isEmpty() || name.equals(FolderElement.ROOT.getName()))
				continue;
			FolderElement next = current.getDirectFolderChildren().stream().filter(f -> f.getName().equals(name))
					.findFirst().orElse(null);
			if (next == null) {
				if (!create)
					return null;
				if (!name.matches("[A-Za-z0-9 _\\-]+"))
					throw new IllegalArgumentException("Invalid folder name " + name);
				next = new FolderElement(name, current);
				current.addChild(next);
				workspace.markDirty();
			}
			current = next;
		}
		return current;
	}

}
