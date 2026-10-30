package com.mccreatormcp.util;

import net.mcreator.io.mcp.protocol.SchemaDescription;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * Pixel-art friendly image operations (no smoothing, alpha preserved)
 */
public final class ImageOps {

	private ImageOps() {}

	public static class Operation {
		@SchemaDescription("""
				One of: resize (width,height; nearest neighbor), scale (value = factor), crop (x,y,width,height), \
				 hue_shift (value = degrees), saturation (value = factor, 0 = grayscale, 1 = unchanged), \
				 brightness (value = factor), contrast (value = factor), tint (color, value = strength 0-1), \
				 colorize (color; keeps shading, replaces hue and saturation), grayscale, invert, flip_horizontal, \
				 flip_vertical, rotate (value = 90, 180 or 270), outline (color; 1px outline around opaque pixels), \
				 replace_color (color = from, color2 = to, value = tolerance 0-255), opacity (value = factor), \
				 overlay (texture drawn over the image, see overlayTexture/overlayTextureType of the tool)""")
		public String op;
		@Nullable public Double value;
		@SchemaDescription("Color as #RRGGBB or #AARRGGBB") @Nullable public String color;
		@Nullable public String color2;
		@Nullable public Integer x;
		@Nullable public Integer y;
		@Nullable public Integer width;
		@Nullable public Integer height;
	}

	public static BufferedImage apply(BufferedImage image, List<Operation> operations,
			@Nullable BufferedImage overlay) {
		BufferedImage result = toARGB(image);
		for (Operation operation : operations) {
			if (operation == null || operation.op == null)
				throw new IllegalArgumentException("Each operation needs op");
			result = apply(result, operation, overlay);
		}
		return result;
	}

	private static BufferedImage apply(BufferedImage img, Operation o, @Nullable BufferedImage overlay) {
		String op = o.op.trim().toLowerCase(Locale.ROOT);
		return switch (op) {
			case "resize" -> resize(img, require(o.width, "width"), require(o.height, "height"));
			case "scale" -> {
				double f = requireValue(o);
				yield resize(img, Math.max(1, (int) Math.round(img.getWidth() * f)),
						Math.max(1, (int) Math.round(img.getHeight() * f)));
			}
			case "crop" -> crop(img, require(o.x, "x"), require(o.y, "y"), require(o.width, "width"),
					require(o.height, "height"));
			case "hue_shift" -> hsb(img, (float) (requireValue(o) / 360.0), 1, 1);
			case "saturation" -> hsb(img, 0, (float) requireValue(o), 1);
			case "brightness" -> hsb(img, 0, 1, (float) requireValue(o));
			case "contrast" -> contrast(img, requireValue(o));
			case "tint" -> tint(img, parseColor(o.color), o.value == null ? 0.5 : o.value);
			case "colorize" -> colorize(img, parseColor(o.color));
			case "grayscale" -> hsb(img, 0, 0, 1);
			case "invert" -> map(img, (a, r, g, b) -> argb(a, 255 - r, 255 - g, 255 - b));
			case "flip_horizontal" -> flip(img, true);
			case "flip_vertical" -> flip(img, false);
			case "rotate" -> rotate(img, (int) Math.round(requireValue(o)));
			case "outline" -> outline(img, parseColor(o.color));
			case "replace_color" -> replaceColor(img, parseColor(o.color), parseColor(o.color2),
					o.value == null ? 0 : o.value);
			case "opacity" -> {
				double f = requireValue(o);
				yield map(img, (a, r, g, b) -> argb(clamp((int) Math.round(a * f)), r, g, b));
			}
			case "overlay" -> {
				if (overlay == null)
					throw new IllegalArgumentException("overlay operation requires overlayTexture");
				BufferedImage copy = copy(img);
				Graphics2D g = copy.createGraphics();
				g.drawImage(overlay, 0, 0, img.getWidth(), img.getHeight(), null);
				g.dispose();
				yield copy;
			}
			default -> throw new IllegalArgumentException("Unknown operation: " + o.op);
		};
	}

	public static BufferedImage toARGB(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB)
			return image;
		BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = converted.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return converted;
	}

	public static BufferedImage copy(BufferedImage image) {
		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return copy;
	}

	public static BufferedImage resize(BufferedImage img, int width, int height) {
		if (width <= 0 || height <= 0 || width > 4096 || height > 4096)
			throw new IllegalArgumentException("Invalid size " + width + "x" + height);
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(img, 0, 0, width, height, null);
		g.dispose();
		return out;
	}

	private static BufferedImage crop(BufferedImage img, int x, int y, int w, int h) {
		if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > img.getWidth() || y + h > img.getHeight())
			throw new IllegalArgumentException("Crop rectangle is outside of the image");
		return copy(img.getSubimage(x, y, w, h));
	}

	private static BufferedImage hsb(BufferedImage img, float hueShift, float satFactor, float briFactor) {
		return map(img, (a, r, g, b) -> {
			if (a == 0)
				return 0;
			float[] hsb = Color.RGBtoHSB(r, g, b, null);
			float h = (hsb[0] + hueShift) % 1f;
			if (h < 0)
				h += 1f;
			int rgb = Color.HSBtoRGB(h, Math.min(1f, Math.max(0f, hsb[1] * satFactor)),
					Math.min(1f, Math.max(0f, hsb[2] * briFactor)));
			return (a << 24) | (rgb & 0xFFFFFF);
		});
	}

	private static BufferedImage contrast(BufferedImage img, double factor) {
		return map(img, (a, r, g, b) -> argb(a, clamp((int) Math.round((r - 128) * factor + 128)),
				clamp((int) Math.round((g - 128) * factor + 128)), clamp((int) Math.round((b - 128) * factor + 128))));
	}

	private static BufferedImage tint(BufferedImage img, Color color, double strength) {
		double s = Math.max(0, Math.min(1, strength));
		return map(img, (a, r, g, b) -> argb(a, clamp((int) Math.round(r * (1 - s) + r * color.getRed() / 255.0 * s)),
				clamp((int) Math.round(g * (1 - s) + g * color.getGreen() / 255.0 * s)),
				clamp((int) Math.round(b * (1 - s) + b * color.getBlue() / 255.0 * s))));
	}

	private static BufferedImage colorize(BufferedImage img, Color color) {
		float[] target = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		float targetBrightness = target[2];
		// average brightness of the source so the result keeps relative shading around the target color
		double sum = 0;
		int count = 0;
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y);
				if ((p >>> 24) > 0) {
					sum += Color.RGBtoHSB((p >> 16) & 255, (p >> 8) & 255, p & 255, null)[2];
					count++;
				}
			}
		double average = count == 0 ? 0.5 : sum / count;
		double shift = targetBrightness - average;
		return map(img, (a, r, g, b) -> {
			if (a == 0)
				return 0;
			float bri = Color.RGBtoHSB(r, g, b, null)[2];
			int rgb = Color.HSBtoRGB(target[0], target[1], (float) Math.min(1, Math.max(0, bri + shift)));
			return (a << 24) | (rgb & 0xFFFFFF);
		});
	}

	private static BufferedImage flip(BufferedImage img, boolean horizontal) {
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++)
				out.setRGB(horizontal ? img.getWidth() - 1 - x : x, horizontal ? y : img.getHeight() - 1 - y,
						img.getRGB(x, y));
		return out;
	}

	private static BufferedImage rotate(BufferedImage img, int degrees) {
		int d = ((degrees % 360) + 360) % 360;
		if (d % 90 != 0)
			throw new IllegalArgumentException("rotate supports only 90, 180 and 270 degrees");
		BufferedImage result = img;
		for (int i = 0; i < d / 90; i++) {
			BufferedImage out = new BufferedImage(result.getHeight(), result.getWidth(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < result.getHeight(); y++)
				for (int x = 0; x < result.getWidth(); x++)
					out.setRGB(result.getHeight() - 1 - y, x, result.getRGB(x, y));
			result = out;
		}
		return result;
	}

	private static BufferedImage outline(BufferedImage img, Color color) {
		BufferedImage out = copy(img);
		int w = img.getWidth(), h = img.getHeight();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				if ((img.getRGB(x, y) >>> 24) != 0)
					continue;
				boolean border = false;
				for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
					int nx = x + d[0], ny = y + d[1];
					if (nx >= 0 && ny >= 0 && nx < w && ny < h && (img.getRGB(nx, ny) >>> 24) != 0) {
						border = true;
						break;
					}
				}
				if (border)
					out.setRGB(x, y, color.getRGB());
			}
		return out;
	}

	private static BufferedImage replaceColor(BufferedImage img, Color from, Color to, double tolerance) {
		return map(img, (a, r, g, b) -> {
			if (a != 0 && Math.abs(r - from.getRed()) <= tolerance && Math.abs(g - from.getGreen()) <= tolerance
					&& Math.abs(b - from.getBlue()) <= tolerance)
				return argb(to.getAlpha() == 255 ? a : to.getAlpha(), to.getRed(), to.getGreen(), to.getBlue());
			return argb(a, r, g, b);
		});
	}

	public static Color parseColor(@Nullable String color) {
		if (color == null || color.isBlank())
			throw new IllegalArgumentException("color must be provided (#RRGGBB)");
		String c = color.trim().replace("#", "").replace("0x", "");
		try {
			if (c.length() == 6)
				return new Color(Integer.parseInt(c, 16));
			if (c.length() == 8)
				return new Color((int) Long.parseLong(c, 16), true);
		} catch (NumberFormatException ignored) {
		}
		throw new IllegalArgumentException("Invalid color " + color + ", use #RRGGBB or #AARRGGBB");
	}

	/**
	 * Scales the image up with nearest neighbor so small pixel art textures are visible for review
	 */
	public static BufferedImage preview(BufferedImage img, int targetSize) {
		int factor = Math.max(1, targetSize / Math.max(img.getWidth(), img.getHeight()));
		return factor == 1 ? img : resize(img, img.getWidth() * factor, img.getHeight() * factor);
	}

	private interface PixelMapper {
		int map(int a, int r, int g, int b);
	}

	private static BufferedImage map(BufferedImage img, PixelMapper mapper) {
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < img.getHeight(); y++)
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y);
				out.setRGB(x, y, mapper.map(p >>> 24, (p >> 16) & 255, (p >> 8) & 255, p & 255));
			}
		return out;
	}

	private static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static int require(@Nullable Integer value, String name) {
		if (value == null)
			throw new IllegalArgumentException(name + " must be provided for this operation");
		return value;
	}

	private static double requireValue(Operation o) {
		if (o.value == null)
			throw new IllegalArgumentException("value must be provided for operation " + o.op);
		return o.value;
	}

}
