package com.mccreatormcp.util;

import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import javax.annotation.Nullable;

public final class Names {

	private Names() {}

	/**
	 * Converts any name to a valid lower_snake_case resource name (textures, sounds, structures, models)
	 */
	public static String resourceName(@Nullable String name) {
		if (name == null)
			return "";
		String trimmed = name.trim();
		// keep names that are already valid resource names (fromCamelCase would split digits: frame2 -> frame_2)
		if (trimmed.matches("[a-z0-9_.]+"))
			return trimmed.replaceAll("_+", "_").replaceAll("^[_.]+", "").replaceAll("[_.]+$", "");
		String fixed = RegistryNameFixer.fromCamelCase(trimmed.replace('-', '_')).replace("/", "_");
		return fixed.replaceAll("_+", "_").replaceAll("^[_.]+", "").replaceAll("[_.]+$", "");
	}

	@Nullable public static TextureType textureType(@Nullable String type) {
		if (type == null)
			return null;
		for (TextureType textureType : TextureType.values()) {
			if (textureType.getID().equalsIgnoreCase(type.trim()) || textureType.name().equalsIgnoreCase(type.trim()))
				return textureType;
		}
		return null;
	}

	/**
	 * Case-insensitive mod element lookup, as AI clients often get the casing slightly wrong
	 */
	@Nullable public static ModElement findModElement(Workspace workspace, @Nullable String name) {
		if (name == null)
			return null;
		ModElement exact = workspace.getModElementByName(name.trim());
		if (exact != null)
			return exact;
		for (ModElement element : workspace.getModElements()) {
			if (element.getName().equalsIgnoreCase(name.trim()))
				return element;
		}
		return null;
	}

}
