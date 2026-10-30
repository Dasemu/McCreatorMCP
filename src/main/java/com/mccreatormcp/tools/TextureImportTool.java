package com.mccreatormcp.tools;

import com.mccreatormcp.util.BinarySource;
import com.mccreatormcp.util.ImageOps;
import com.mccreatormcp.util.Io;
import com.mccreatormcp.util.Names;
import com.mccreatormcp.util.UiRefresh;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.ui.workspace.resources.TextureType;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class TextureImportTool extends MCreatorMcpTool<TextureImportTool.Args> {

	public static class Args {
		@SchemaDescription("Textures to import (one or more)") public List<TextureEntry> textures;
	}

	public static class TextureEntry {
		@SchemaDescription("Texture name (lower_snake_case recommended; it is normalized). For armor: base name without _layer_1")
		public String name;
		@SchemaDescription("block, item, entity, effect, particle, screen, armor or other") public String textureType;
		@SchemaDescription("Image source (PNG preferred, JPG/GIF/BMP converted to PNG). For armor this is layer 1.")
		public BinarySource source;
		@SchemaDescription("Armor only: image source for layer 2 (leggings layer)") @Nullable public BinarySource layer2Source;
		@SchemaDescription("Optional: resize to this width (nearest neighbor, keeps pixel art crisp), e.g. 16")
		@Nullable public Integer resizeWidth;
		@SchemaDescription("Optional: resize to this height, defaults to resizeWidth for square images") @Nullable public Integer resizeHeight;
		@SchemaDescription("For vertical animation strips (height = N * width): frame time in ticks; writes .png.mcmeta")
		@Nullable public Integer animationFrameTime;
		@SchemaDescription("Interpolate between animation frames") @Nullable public Boolean animationInterpolate;
		@SchemaDescription("Overwrite existing texture with the same name (default false)") @Nullable public Boolean overwrite;
	}

	public TextureImportTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "texture_import";
	}

	@Override public String getDescription() {
		return """
				Imports image files as workspace textures from base64 data, an http(s) URL or a local file path. \
				Supports all texture types including armor (2 layers), optional pixel-art resizing and animated \
				textures (vertical strips with .mcmeta). Use the resulting texture name in mod element definitions. \
				Use create_texture for procedurally generated textures and texture_edit to modify textures.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return false;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) {
		if (input.textures == null || input.textures.isEmpty())
			return completedError("textures must contain at least one entry");

		List<Map<String, Object>> results = new ArrayList<>();
		boolean anyImported = false;
		for (TextureEntry entry : input.textures) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("name", entry.name);
			try {
				result.putAll(importTexture(mcreator, entry));
				result.put("success", true);
				anyImported = true;
			} catch (Exception e) {
				result.put("success", false);
				result.put("error", e.getMessage());
			}
			results.add(result);
		}

		if (anyImported)
			UiRefresh.resourcesChanged(mcreator);

		return completed(ToolResult.collection(results));
	}

	private Map<String, Object> importTexture(MCreator mcreator, TextureEntry entry) throws IOException {
		TextureType type = Names.textureType(entry.textureType);
		if (type == null)
			throw new IllegalArgumentException("Unknown textureType " + entry.textureType);
		if (mcreator.getFolderManager().getTexturesFolder(type) == null)
			throw new IllegalArgumentException("Texture type " + type.getID() + " is not supported by this generator");
		if (entry.source == null || entry.source.isEmpty())
			throw new IllegalArgumentException("source must be provided");

		String name = Names.resourceName(entry.name != null && !entry.name.isBlank() ?
				entry.name :
				entry.source.suggestedBaseName());
		if (type == TextureType.ARMOR)
			name = name.replaceAll("_?layer_[12]$", "");
		if (name.isBlank())
			throw new IllegalArgumentException("Texture name must be provided");

		boolean overwrite = entry.overwrite != null && entry.overwrite;
		List<String> notes = new ArrayList<>();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("name", name);
		result.put("textureType", type.getID());

		if (type == TextureType.ARMOR) {
			if (entry.layer2Source == null || entry.layer2Source.isEmpty())
				throw new IllegalArgumentException("Armor textures need source (layer 1) and layer2Source (layer 2)");
			File[] files = mcreator.getFolderManager().getArmorTextureFilesForName(name);
			if (!overwrite && (files[0].exists() || files[1].exists()))
				throw new IllegalArgumentException("Armor texture " + name + " already exists, set overwrite to true");
			BufferedImage layer1 = process(readImage(entry.source), entry, notes);
			BufferedImage layer2 = process(readImage(entry.layer2Source), entry, notes);
			if (layer1.getWidth() != layer1.getHeight() * 2 || layer2.getWidth() != layer2.getHeight() * 2)
				notes.add("Armor layers are usually 64x32 (width = 2 * height)");
			write(layer1, files[0]);
			write(layer2, files[1]);
			result.put("files", List.of(files[0].getName(), files[1].getName()));
		} else {
			File file = mcreator.getFolderManager().getTextureFile(name, type);
			if (!overwrite && file.exists())
				throw new IllegalArgumentException("Texture " + name + " already exists, set overwrite to true or use another name");
			BufferedImage image = process(readImage(entry.source), entry, notes);
			write(image, file);

			File mcmeta = new File(file.getParentFile(), file.getName() + ".mcmeta");
			boolean strip = image.getHeight() > image.getWidth() && image.getHeight() % image.getWidth() == 0;
			if (entry.animationFrameTime != null) {
				if (!strip)
					throw new IllegalArgumentException(
							"Animated textures must be vertical strips with height a multiple of width");
				Io.writeString(animationMeta(entry.animationFrameTime,
						entry.animationInterpolate != null && entry.animationInterpolate), mcmeta);
				result.put("animationFrames", image.getHeight() / image.getWidth());
			} else if (strip && (type == TextureType.BLOCK || type == TextureType.ITEM)) {
				notes.add("Image looks like an animation strip; pass animationFrameTime to make it animated");
			}
			if ((type == TextureType.BLOCK || type == TextureType.ITEM) && !isPowerOfTwo(image.getWidth()))
				notes.add("Block and item textures should be 16x16 (or 32, 64, ... power of two) to match vanilla style");
			result.put("width", image.getWidth());
			result.put("height", image.getHeight());
		}

		if (!notes.isEmpty())
			result.put("notes", notes);
		return result;
	}

	static String animationMeta(int frameTime, boolean interpolate) {
		return "{\n  \"animation\": {\n    \"frametime\": " + Math.max(1, frameTime) + (interpolate ?
				",\n    \"interpolate\": true" :
				"") + "\n  }\n}\n";
	}

	private static BufferedImage readImage(BinarySource source) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(source.read()));
		if (image == null)
			throw new IOException("Data is not a supported image (PNG, JPG, GIF or BMP)");
		return ImageOps.toARGB(image);
	}

	private static BufferedImage process(BufferedImage image, TextureEntry entry, List<String> notes) {
		if (entry.resizeWidth != null) {
			int height = entry.resizeHeight != null ?
					entry.resizeHeight :
					(int) Math.round(image.getHeight() * (entry.resizeWidth / (double) image.getWidth()));
			notes.add("Resized from " + image.getWidth() + "x" + image.getHeight() + " to " + entry.resizeWidth + "x"
					+ height);
			return ImageOps.resize(image, entry.resizeWidth, height);
		}
		return image;
	}

	private static void write(BufferedImage image, File file) {
		file.getParentFile().mkdirs();
		Io.writePng(image, file);
		new ImageIcon(file.getAbsolutePath()).getImage().flush();
	}

	private static boolean isPowerOfTwo(int value) {
		return value > 0 && (value & (value - 1)) == 0;
	}

}
