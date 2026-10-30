package com.mccreatormcp.tools;

import com.mccreatormcp.util.BinarySource;
import com.mccreatormcp.util.Io;
import com.mccreatormcp.util.Names;
import com.mccreatormcp.util.UiRefresh;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.settings.WorkspaceSettings;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class WorkspaceSettingsEditTool extends MCreatorMcpTool<WorkspaceSettingsEditTool.Args> {

	public static class Args {
		@SchemaDescription("Mod display name") @Nullable public String modName;
		@SchemaDescription("Mod version, e.g. 1.0.0") @Nullable public String version;
		@SchemaDescription("Mod description shown in the mod list") @Nullable public String description;
		@SchemaDescription("Author(s), comma separated") @Nullable public String author;
		@Nullable public String websiteURL;
		@SchemaDescription("License name or SPDX identifier, e.g. MIT, All Rights Reserved") @Nullable public String license;
		@Nullable public String credits;
		@SchemaDescription("Update JSON URL (Forge/NeoForge update checker)") @Nullable public String updateURL;
		@SchemaDescription("True if the mod only needs to be installed on the server") @Nullable public Boolean serverSideOnly;
		@SchemaDescription("Name of an 'other' texture to use as the mod picture/logo. Empty string removes it.")
		@Nullable public String modPicture;
		@SchemaDescription("Name of an 'other' texture to use as the mod icon. Empty string removes it.")
		@Nullable public String modIcon;
		@SchemaDescription("Image to import as 'other' texture named <modid>_logo and set as mod picture (and mod icon if not set)")
		@Nullable public BinarySource modPictureImage;
		@SchemaDescription("Replaces list of required mod IDs (mod must be present), entries may include version ranges as modid@range")
		@Nullable public Set<String> requiredMods;
		@SchemaDescription("Replaces list of optional dependency mod IDs (load after these mods)")
		@Nullable public Set<String> dependencies;
		@SchemaDescription("Replaces list of dependant mod IDs (load before these mods)")
		@Nullable public Set<String> dependants;
	}

	public WorkspaceSettingsEditTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "workspace_settings_edit";
	}

	@Override public String getDescription() {
		return """
				Updates mod metadata in workspace settings. Only provided fields are changed. Good metadata (name, \
				description, author, license, logo) is part of a quality mod. Mod ID, Java package and generator \
				can not be changed with this tool as they require a full workspace refactor; ask the user to change \
				them in Workspace > Workspace settings. Read current values with query_workspace WORKSPACE_SETTINGS.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return false;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings(mcreator.getWorkspace().getWorkspaceSettings());
		settings.setWorkspace(mcreator.getWorkspace());
		List<String> changed = new ArrayList<>();

		if (input.modName != null) {
			if (input.modName.isBlank())
				return completedError("modName can not be empty");
			settings.setModName(input.modName.trim());
			changed.add("modName");
		}
		if (input.version != null) {
			if (!input.version.trim().matches("[0-9A-Za-z.+\\-_]+"))
				return completedError("Invalid version. Use a format such as 1.0.0");
			settings.setVersion(input.version.trim());
			changed.add("version");
		}
		if (input.description != null) {
			settings.setDescription(input.description);
			changed.add("description");
		}
		if (input.author != null) {
			settings.setAuthor(input.author);
			changed.add("author");
		}
		if (input.websiteURL != null) {
			settings.setWebsiteURL(input.websiteURL);
			changed.add("websiteURL");
		}
		if (input.license != null) {
			settings.setLicense(input.license);
			changed.add("license");
		}
		if (input.credits != null) {
			settings.setCredits(input.credits);
			changed.add("credits");
		}
		if (input.updateURL != null) {
			settings.setUpdateURL(input.updateURL.isBlank() ? null : input.updateURL);
			changed.add("updateURL");
		}
		if (input.serverSideOnly != null) {
			settings.setServerSideOnly(input.serverSideOnly);
			changed.add("serverSideOnly");
		}

		if (input.modPictureImage != null && !input.modPictureImage.isEmpty()) {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(input.modPictureImage.read()));
			if (image == null)
				return completedError("modPictureImage is not a valid image (PNG, JPG, GIF or BMP)");
			String name = Names.resourceName(settings.getModID() + "_logo");
			File target = mcreator.getFolderManager().getTextureFile(name, TextureType.OTHER);
			Io.writePng(image, target);
			settings.setModPicture(name);
			if (settings.getModIcon() == null && input.modIcon == null)
				settings.setModIcon(name);
			changed.add("modPicture (imported as other texture " + name + ")");
		}
		if (input.modPicture != null) {
			String error = setPicture(mcreator, input.modPicture, settings::setModPicture);
			if (error != null)
				return completedError(error);
			changed.add("modPicture");
		}
		if (input.modIcon != null) {
			String error = setPicture(mcreator, input.modIcon, settings::setModIcon);
			if (error != null)
				return completedError(error);
			changed.add("modIcon");
		}

		if (input.requiredMods != null) {
			settings.setRequiredMods(cleanModIds(input.requiredMods));
			changed.add("requiredMods");
		}
		if (input.dependencies != null) {
			settings.setDependencies(cleanModIds(input.dependencies));
			changed.add("dependencies");
		}
		if (input.dependants != null) {
			settings.setDependants(cleanModIds(input.dependants));
			changed.add("dependants");
		}

		if (changed.isEmpty())
			return completedError("No settings were provided to change");

		mcreator.getWorkspace().getHistoryManager().checkpoint("settings_changed");
		mcreator.getWorkspace().setWorkspaceSettings(settings);
		mcreator.getGenerator().generateBase(true);
		UiRefresh.resourcesChanged(mcreator);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("changed", changed);
		result.put("settings", mcreator.getWorkspace().getWorkspaceSettings());
		return completedObject(result);
	}

	@Nullable private static String setPicture(MCreator mcreator, String name,
			java.util.function.Consumer<String> setter) {
		if (name.isBlank()) {
			setter.accept(null);
			return null;
		}
		String fixed = name.trim().replaceAll("\\.png$", "");
		if (!mcreator.getFolderManager().getTextureFile(fixed, TextureType.OTHER).isFile())
			return "Texture '" + fixed + "' does not exist in 'other' textures. Import it first with texture_import "
					+ "using textureType other, or pass modPictureImage.";
		setter.accept(fixed);
		return null;
	}

	private static Set<String> cleanModIds(Set<String> ids) {
		Set<String> result = new LinkedHashSet<>();
		for (String id : ids) {
			if (id != null && !id.isBlank())
				result.add(id.trim());
		}
		return result;
	}

}
