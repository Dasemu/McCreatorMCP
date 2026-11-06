package com.mccreatormcp.util;

import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers the MCreator MCP server in the user-level MCP configuration (mcp.json) of VS Code and its variants,
 * so VS Code (Copilot agent mode) can use MCreator without manual setup.
 * <p>
 * Only the "mcreator" entry is added or updated; other servers and settings in the file are kept. The file is
 * backed up to mcp.json.bak before it is changed, as rewriting it drops JSONC comments.
 */
public final class VsCodeRegistration {

	private static final Logger LOG = LogManager.getLogger("MCP Plus");

	public static final String SERVER_NAME = "mcreator";

	// VS Code variants that read the same mcp.json format from <config>/<variant>/User/mcp.json
	private static final List<String> VARIANTS = List.of("Code", "Code - Insiders", "VSCodium");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private VsCodeRegistration() {}

	public enum Result {
		CREATED, UPDATED, UNCHANGED, SKIPPED_INVALID_JSON, FAILED
	}

	public record Registration(Path file, Result result) {}

	/**
	 * Registers the server in every installed VS Code variant
	 *
	 * @param url MCP server URL, e.g. http://127.0.0.1:3025/mcp
	 */
	public static List<Registration> registerInstalledEditors(String url) {
		List<Registration> registrations = new ArrayList<>();
		Path configRoot = configRoot();
		if (configRoot == null)
			return registrations;
		for (String variant : VARIANTS) {
			Path editorDir = configRoot.resolve(variant);
			// only register in editors that are installed (have been started at least once)
			if (Files.isDirectory(editorDir))
				registrations.add(register(editorDir.resolve("User").resolve("mcp.json"), url));
		}
		return registrations;
	}

	public static Registration register(Path mcpJson, String url) {
		try {
			JsonObject root;
			String original = null;
			if (Files.isRegularFile(mcpJson)) {
				original = Files.readString(mcpJson, StandardCharsets.UTF_8);
				try {
					JsonElement parsed = original.isBlank() ? new JsonObject() : JsonParser.parseString(original);
					if (!parsed.isJsonObject())
						return log(new Registration(mcpJson, Result.SKIPPED_INVALID_JSON));
					root = parsed.getAsJsonObject();
				} catch (JsonParseException e) {
					// never overwrite a file we do not understand
					return log(new Registration(mcpJson, Result.SKIPPED_INVALID_JSON));
				}
			} else {
				root = new JsonObject();
			}

			JsonObject servers = root.has("servers") && root.get("servers").isJsonObject() ?
					root.getAsJsonObject("servers") :
					new JsonObject();
			JsonObject entry = new JsonObject();
			entry.addProperty("type", "http");
			entry.addProperty("url", url);

			if (entry.equals(servers.get(SERVER_NAME)))
				return new Registration(mcpJson, Result.UNCHANGED);

			servers.add(SERVER_NAME, entry);
			root.add("servers", servers);

			Files.createDirectories(mcpJson.getParent());
			if (original != null)
				Files.copy(mcpJson, mcpJson.resolveSibling("mcp.json.bak"), StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(mcpJson, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
			return log(new Registration(mcpJson, original == null ? Result.CREATED : Result.UPDATED));
		} catch (IOException | RuntimeException e) {
			LOG.warn("Failed to register MCP server in {}", mcpJson, e);
			return new Registration(mcpJson, Result.FAILED);
		}
	}

	private static Registration log(Registration registration) {
		if (registration.result() == Result.SKIPPED_INVALID_JSON)
			LOG.warn("Not registering MCP server in {}: the file is not valid JSON", registration.file());
		else
			LOG.info("MCP server {} in {}", registration.result().name().toLowerCase(Locale.ROOT),
					registration.file());
		return registration;
	}

	@Nullable private static Path configRoot() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String home = System.getProperty("user.home");
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			return appData != null ? Path.of(appData) : (home != null ? Path.of(home, "AppData", "Roaming") : null);
		} else if (os.contains("mac")) {
			return home != null ? Path.of(home, "Library", "Application Support") : null;
		} else {
			String xdg = System.getenv("XDG_CONFIG_HOME");
			return xdg != null && !xdg.isBlank() ? Path.of(xdg) : (home != null ? Path.of(home, ".config") : null);
		}
	}

}
