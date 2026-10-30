package com.mccreatormcp.tools;

import com.mccreatormcp.util.ImageOps;
import com.mccreatormcp.util.Io;
import com.mccreatormcp.util.Names;
import com.mccreatormcp.util.UiRefresh;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.ReferencesFinder;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class TextureEditTool extends MCreatorMcpTool<TextureEditTool.Args> {

	public static class Args {
		@SchemaDescription("""
				LIST: textures of textureType with size, animation flag and usage count (filter optional). \
				PREVIEW: returns the texture as an upscaled PNG image so you can visually check it. \
				TRANSFORM: applies operations in order and saves to targetName (defaults to overwriting textureName). \
				COPY: copies texture to targetName / targetTextureType. \
				DELETE: deletes texture (refused while used by mod elements unless force is true). \
				ANIMATE: builds an animated texture from frames (texture names of textureType) into targetName. \
				USAGES: lists mod elements that use the texture.""")
		public Action actionType;

		@SchemaDescription("block, item, entity, effect, particle, screen, armor or other") public String textureType;
		@SchemaDescription("Existing texture name (without .png)") @Nullable public String textureName;
		@SchemaDescription("Output texture name for TRANSFORM, COPY and ANIMATE") @Nullable public String targetName;
		@SchemaDescription("Output texture type for COPY and TRANSFORM (defaults to textureType)") @Nullable public String targetTextureType;
		@SchemaDescription("Operations for TRANSFORM, applied in order") @Nullable public List<ImageOps.Operation> operations;
		@SchemaDescription("Texture to draw over the image for the overlay operation") @Nullable public String overlayTexture;
		@SchemaDescription("Type of overlayTexture (defaults to textureType)") @Nullable public String overlayTextureType;
		@SchemaDescription("ANIMATE: frame texture names in order") @Nullable public List<String> frames;
		@SchemaDescription("ANIMATE: ticks per frame (default 2)") @Nullable public Integer frameTime;
		@SchemaDescription("ANIMATE: interpolate frames") @Nullable public Boolean interpolate;
		@SchemaDescription("LIST: Java regex to filter texture names") @Nullable public String filter;
		@SchemaDescription("PREVIEW: preview size in pixels (default 256)") @Nullable public Integer previewSize;
		@SchemaDescription("DELETE: delete even if used, TRANSFORM/COPY/ANIMATE: overwrite existing target") @Nullable public Boolean force;

		public enum Action {
			LIST, PREVIEW, TRANSFORM, COPY, DELETE, ANIMATE, USAGES
		}
	}

	public TextureEditTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "texture_edit";
	}

	@Override public String getDescription() {
		return """
				Inspects and edits workspace textures with pixel-art safe operations: preview as image, recolor \
				(hue shift, colorize, tint, replace color), brightness/contrast/saturation, resize, crop, flip, rotate, \
				outline, overlay, plus copy, delete, usage lookup and building animated textures from frames. \
				Typical use: create material variants (e.g. ruby_ingot from a recolored iron ingot style texture) and \
				PREVIEW results to verify visual quality.""";
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
		TextureType type = Names.textureType(input.textureType);
		if (type == null)
			return completedError("Unknown textureType " + input.textureType);
		if (mcreator.getFolderManager().getTexturesFolder(type) == null)
			return completedError("Texture type " + type.getID() + " is not supported by this generator");

		return switch (input.actionType) {
			case LIST -> list(mcreator, type, input.filter);
			case PREVIEW -> {
				File file = existing(mcreator, type, input.textureName);
				BufferedImage image = ImageIO.read(file);
				int size = input.previewSize != null ? Math.max(16, Math.min(1024, input.previewSize)) : 256;
				yield completed(ToolResult.image(png(ImageOps.preview(ImageOps.toARGB(image), size)), "image/png"));
			}
			case TRANSFORM -> {
				if (input.operations == null || input.operations.isEmpty())
					yield completedError("operations must be provided for TRANSFORM");
				File source = existing(mcreator, type, input.textureName);
				BufferedImage overlay = null;
				if (input.overlayTexture != null && !input.overlayTexture.isBlank()) {
					TextureType overlayType = input.overlayTextureType != null ?
							Names.textureType(input.overlayTextureType) :
							type;
					if (overlayType == null)
						yield completedError("Unknown overlayTextureType " + input.overlayTextureType);
					overlay = ImageIO.read(existing(mcreator, overlayType, input.overlayTexture));
				}
				BufferedImage result = ImageOps.apply(ImageIO.read(source), input.operations, overlay);
				TextureType targetType = targetType(input, type);
				String targetName = input.targetName != null && !input.targetName.isBlank() ?
						Names.resourceName(input.targetName) :
						Names.resourceName(input.textureName);
				File target = mcreator.getFolderManager().getTextureFile(targetName, targetType);
				boolean inPlace = target.getCanonicalFile().equals(source.getCanonicalFile());
				if (!inPlace && target.exists() && !Boolean.TRUE.equals(input.force))
					yield completedError("Target texture " + targetName + " exists, set force to true to overwrite");
				write(result, target);
				copyMcmetaIfAny(source, target);
				UiRefresh.resourcesChanged(mcreator);
				yield completed(ToolResult.object(
						Map.of("saved", targetName, "textureType", targetType.getID(), "width", result.getWidth(),
								"height", result.getHeight(), "tip", "Use PREVIEW to check the result visually")));
			}
			case COPY -> {
				File source = existing(mcreator, type, input.textureName);
				if (input.targetName == null || input.targetName.isBlank())
					yield completedError("targetName must be provided for COPY");
				TextureType targetType = targetType(input, type);
				String targetName = Names.resourceName(input.targetName);
				File target = mcreator.getFolderManager().getTextureFile(targetName, targetType);
				if (target.exists() && !Boolean.TRUE.equals(input.force))
					yield completedError("Target texture " + targetName + " exists, set force to true to overwrite");
				target.getParentFile().mkdirs();
				Io.copy(source, target);
				copyMcmetaIfAny(source, target);
				UiRefresh.resourcesChanged(mcreator);
				yield completedText("Copied to " + targetType.getID() + " texture " + targetName);
			}
			case DELETE -> {
				File file = existing(mcreator, type, input.textureName);
				Set<ModElement> usages = ReferencesFinder.searchTextureUsages(mcreator.getWorkspace(), file, type);
				if (!usages.isEmpty() && !Boolean.TRUE.equals(input.force))
					yield completedError("Texture is used by: " + names(usages)
							+ ". Change those elements first or set force to true.");
				mcreator.getWorkspace().getHistoryManager().checkpoint("before_delete", file.getName());
				file.delete();
				new File(file.getParentFile(), file.getName() + ".mcmeta").delete();
				UiRefresh.resourcesChanged(mcreator);
				yield completedText("Texture deleted");
			}
			case ANIMATE -> {
				if (input.frames == null || input.frames.size() < 2)
					yield completedError("ANIMATE needs at least 2 frames");
				if (input.targetName == null || input.targetName.isBlank())
					yield completedError("targetName must be provided for ANIMATE");
				List<BufferedImage> frames = new ArrayList<>();
				for (String frame : input.frames)
					frames.add(ImageOps.toARGB(ImageIO.read(existing(mcreator, type, frame))));
				int width = frames.getFirst().getWidth();
				int height = frames.getFirst().getHeight();
				if (width != height)
					yield completedError("Animation frames must be square");
				BufferedImage strip = new BufferedImage(width, height * frames.size(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = strip.createGraphics();
				for (int i = 0; i < frames.size(); i++)
					g.drawImage(frames.get(i), 0, i * height, width, height, null);
				g.dispose();
				String targetName = Names.resourceName(input.targetName);
				File target = mcreator.getFolderManager().getTextureFile(targetName, type);
				if (target.exists() && !Boolean.TRUE.equals(input.force))
					yield completedError("Target texture " + targetName + " exists, set force to true to overwrite");
				write(strip, target);
				Io.writeString(TextureImportTool.animationMeta(input.frameTime == null ? 2 : input.frameTime,
						Boolean.TRUE.equals(input.interpolate)), new File(target.getParentFile(), target.getName() + ".mcmeta"));
				UiRefresh.resourcesChanged(mcreator);
				yield completedText("Animated texture " + targetName + " created with " + frames.size() + " frames");
			}
			case USAGES -> {
				File file = existing(mcreator, type, input.textureName);
				yield completed(ToolResult.collection(
						names(ReferencesFinder.searchTextureUsages(mcreator.getWorkspace(), file, type))));
			}
		};
	}

	private CompletableFuture<ToolResult> list(MCreator mcreator, TextureType type, @Nullable String filter) {
		Pattern pattern = filter == null || filter.isBlank() ? null : Pattern.compile(filter);
		List<Map<String, Object>> result = new ArrayList<>();
		for (File file : mcreator.getFolderManager().getTexturesList(type)) {
			String name = file.getName().replaceAll("\\.png$", "");
			if (pattern != null && !pattern.matcher(name).find())
				continue;
			Map<String, Object> info = new LinkedHashMap<>();
			info.put("name", name);
			try {
				BufferedImage image = ImageIO.read(file);
				if (image != null)
					info.put("size", image.getWidth() + "x" + image.getHeight());
			} catch (IOException ignored) {
			}
			info.put("animated", new File(file.getParentFile(), file.getName() + ".mcmeta").isFile());
			info.put("usedBy", ReferencesFinder.searchTextureUsages(mcreator.getWorkspace(), file, type).size());
			result.add(info);
		}
		return completed(ToolResult.collection(result));
	}

	private static File existing(MCreator mcreator, TextureType type, @Nullable String name) throws IOException {
		if (name == null || name.isBlank())
			throw new IOException("textureName must be provided");
		String clean = name.trim().replaceAll("\\.png$", "");
		if (clean.contains(":"))
			throw new IOException("Only workspace textures can be edited, not vanilla ones (" + clean + ")");
		File file = mcreator.getFolderManager().getTextureFile(clean, type);
		if (!file.isFile())
			throw new IOException(type.getID() + " texture " + clean + " does not exist. Use LIST to see textures.");
		return file;
	}

	private static TextureType targetType(Args input, TextureType fallback) {
		if (input.targetTextureType == null || input.targetTextureType.isBlank())
			return fallback;
		TextureType type = Names.textureType(input.targetTextureType);
		if (type == null)
			throw new IllegalArgumentException("Unknown targetTextureType " + input.targetTextureType);
		return type;
	}

	private static void copyMcmetaIfAny(File source, File target) {
		File mcmeta = new File(source.getParentFile(), source.getName() + ".mcmeta");
		File targetMcmeta = new File(target.getParentFile(), target.getName() + ".mcmeta");
		if (mcmeta.isFile() && !mcmeta.equals(targetMcmeta))
			Io.copy(mcmeta, targetMcmeta);
	}

	private static void write(BufferedImage image, File file) {
		file.getParentFile().mkdirs();
		Io.writePng(image, file);
		new ImageIcon(file.getAbsolutePath()).getImage().flush();
	}

	static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static List<String> names(Collection<ModElement> elements) {
		return elements.stream().map(ModElement::getName).sorted().toList();
	}

}
