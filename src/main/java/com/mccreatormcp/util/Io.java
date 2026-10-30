package com.mccreatormcp.util;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * File helpers based on the JDK only, as signatures of MCreator's FileIO changed between MCreator builds
 */
public final class Io {

	private Io() {}

	public static void writeString(String content, File file) {
		try {
			mkdirs(file);
			Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write " + file, e);
		}
	}

	public static void writeBytes(byte[] content, File file) {
		try {
			mkdirs(file);
			Files.write(file.toPath(), content);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write " + file, e);
		}
	}

	public static void writePng(RenderedImage image, File file) {
		try {
			mkdirs(file);
			if (!ImageIO.write(image, "png", file))
				throw new IOException("No PNG writer available");
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write " + file, e);
		}
	}

	public static void copy(File from, File to) {
		try {
			mkdirs(to);
			Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to copy " + from + " to " + to, e);
		}
	}

	private static void mkdirs(File file) {
		File parent = file.getAbsoluteFile().getParentFile();
		if (parent != null)
			parent.mkdirs();
	}

}
