package com.mccreatormcp.util;

import net.mcreator.io.mcp.protocol.SchemaDescription;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

/**
 * Binary input that tools accept for imported resources. Exactly one of the fields should be set.
 */
public class BinarySource {

	private static final long MAX_SIZE = 64L * 1024 * 1024;

	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15)).build();

	@SchemaDescription("File content encoded as base64 (a data: URI prefix is accepted)")
	@Nullable public String base64;

	@SchemaDescription("http(s) URL to download the file from")
	@Nullable public String url;

	@SchemaDescription("Absolute path of a file on the computer running MCreator")
	@Nullable public String localPath;

	public boolean isEmpty() {
		return isBlank(base64) && isBlank(url) && isBlank(localPath);
	}

	public byte[] read() throws IOException {
		if (!isBlank(base64)) {
			String data = base64.trim();
			int comma = data.indexOf(',');
			if (data.startsWith("data:") && comma > 0)
				data = data.substring(comma + 1);
			try {
				return Base64.getMimeDecoder().decode(data);
			} catch (IllegalArgumentException e) {
				throw new IOException("Invalid base64 data: " + e.getMessage());
			}
		} else if (!isBlank(url)) {
			URI uri = URI.create(url.trim());
			if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
				throw new IOException("Only http and https URLs are supported");
			try {
				HttpResponse<InputStream> response = HTTP.send(
						HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("User-Agent", "MCreator-MCP-Plus")
								.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
				if (response.statusCode() / 100 != 2)
					throw new IOException("Download failed with HTTP status " + response.statusCode());
				try (InputStream in = response.body()) {
					return readLimited(in);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Download interrupted");
			}
		} else if (!isBlank(localPath)) {
			Path path = Path.of(localPath.trim());
			if (!Files.isRegularFile(path))
				throw new IOException("File not found: " + path);
			if (Files.size(path) > MAX_SIZE)
				throw new IOException("File is too large");
			return Files.readAllBytes(path);
		}
		throw new IOException("No file source provided: set base64, url or localPath");
	}

	/**
	 * @return file name suggested by the source (URL or path), without extension, or null
	 */
	@Nullable public String suggestedBaseName() {
		String source = !isBlank(localPath) ? localPath : (!isBlank(url) ? URI.create(url.trim()).getPath() : null);
		if (source == null)
			return null;
		String name = source.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		int dot = name.indexOf('.');
		return dot > 0 ? name.substring(0, dot) : (name.isBlank() ? null : name);
	}

	private static byte[] readLimited(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[16384];
		long total = 0;
		int read;
		while ((read = in.read(buffer)) != -1) {
			total += read;
			if (total > MAX_SIZE)
				throw new IOException("File is too large");
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static boolean isBlank(@Nullable String s) {
		return s == null || s.isBlank();
	}

}
