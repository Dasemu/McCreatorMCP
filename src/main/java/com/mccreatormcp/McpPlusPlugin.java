package com.mccreatormcp;

import com.mccreatormcp.tools.*;
import net.mcreator.io.mcp.tool.IMcpTool;
import net.mcreator.io.mcp.transport.HttpMcpTransport;
import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.ApplicationClosedEvent;
import net.mcreator.plugin.events.ApplicationFullyLoadedEvent;
import net.mcreator.plugin.events.MCreatorMcpLoadedEvent;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.mcp.MCreatorMcp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Entry point of the MCreator MCP Plus plugin.
 * <p>
 * When the built-in MCreator MCP server is enabled (Preferences > Integrations), the plugin registers its tools
 * into that server. When it is disabled, the plugin starts its own MCP server (built-in tools + plugin tools) on
 * a separate port, so the plugin works in both cases.
 */
public class McpPlusPlugin extends JavaPlugin {

	private static final Logger LOG = LogManager.getLogger("MCP Plus");

	public static final String VERSION = "1.0.0";

	public static final int DEFAULT_STANDALONE_PORT = 3026;

	// MCreatorMcpLoadedEvent is fired for every MCreatorMcp instance, track which ones were already extended
	private final Set<MCreatorMcp> extendedServers = ConcurrentHashMap.newKeySet();

	@Nullable private MCreatorMcp standaloneServer;

	public McpPlusPlugin(Plugin plugin) {
		super(plugin);

		addListener(MCreatorMcpLoadedEvent.class, event -> {
			MCreatorMcp mcp = event.getMCreatorMcp();
			if (extendedServers.add(mcp)) {
				for (IMcpTool tool : createTools(mcp.currentMCreator()))
					event.registerTool(tool);
				LOG.info("Registered MCP Plus tools into MCP server");
			}
		});

		addListener(ApplicationFullyLoadedEvent.class, event -> {
			if (PreferencesManager.PREFERENCES.integrations.mcpEnable.get()) {
				LOG.info("Built-in MCP server is enabled, MCP Plus tools are served by it");
				return;
			}
			if ("false".equalsIgnoreCase(setting("MCREATOR_MCP_PLUS_STANDALONE", "true"))) {
				LOG.info("Built-in MCP server is disabled and MCP Plus standalone server is turned off");
				return;
			}
			// Start on a separate thread: MCreatorMcp constructor fires MCreatorMcpLoadedEvent synchronously and
			// the listener for it runs on this plugin's event queue, which is busy running this listener
			MCreatorApplication application = event.getMCreatorApplication();
			Thread thread = new Thread(() -> startStandaloneServer(application), "MCP-Plus-Standalone");
			thread.setDaemon(true);
			thread.start();
		});

		addListener(ApplicationClosedEvent.class, _ -> {
			if (standaloneServer != null) {
				try {
					standaloneServer.close();
				} catch (IOException e) {
					LOG.warn("Failed to stop MCP Plus standalone server", e);
				}
			}
		});
	}

	private void startStandaloneServer(MCreatorApplication application) {
		int port = DEFAULT_STANDALONE_PORT;
		try {
			port = Integer.parseInt(setting("MCREATOR_MCP_PLUS_PORT", String.valueOf(DEFAULT_STANDALONE_PORT)));
		} catch (NumberFormatException e) {
			LOG.warn("Invalid MCREATOR_MCP_PLUS_PORT, using default {}", DEFAULT_STANDALONE_PORT);
		}

		try {
			standaloneServer = new MCreatorMcp(new HttpMcpTransport(port), new ActiveMCreatorSupplier(application));
			standaloneServer.start();
			LOG.info("MCP Plus standalone MCP server started at http://127.0.0.1:{}/mcp", port);
		} catch (Exception e) {
			LOG.error("Failed to start MCP Plus standalone MCP server on port {}", port, e);
		}
	}

	public static List<IMcpTool> createTools(Supplier<MCreator> mcreator) {
		List<IMcpTool> tools = new ArrayList<>();
		tools.add(new WorkspaceOverviewTool(mcreator));
		tools.add(new WorkspaceSettingsEditTool(mcreator));
		tools.add(new LocalizationTool(mcreator));
		tools.add(new TextureImportTool(mcreator));
		tools.add(new TextureEditTool(mcreator));
		tools.add(new SoundTool(mcreator));
		tools.add(new ModelImportTool(mcreator));
		tools.add(new StructureTool(mcreator));
		tools.add(new ModElementManageTool(mcreator));
		tools.add(new QualityAuditTool(mcreator));
		tools.add(new WorkspaceHistoryTool(mcreator));
		tools.add(new WorkspaceActionsTool(mcreator));
		tools.add(new ScreenshotTool(mcreator));
		tools.add(new MinecraftReferenceTool(mcreator));
		return tools;
	}

	private static String setting(String name, String defaultValue) {
		String value = System.getProperty(name.toLowerCase().replace('_', '.'));
		if (value == null)
			value = System.getenv(name);
		return value != null ? value : defaultValue;
	}

	/**
	 * Picks the MCreator window the user is working with: the focused one, or the last focused one, or the first open
	 */
	private static final class ActiveMCreatorSupplier implements Supplier<MCreator> {

		private final MCreatorApplication application;
		@Nullable private volatile MCreator lastActive;

		private ActiveMCreatorSupplier(MCreatorApplication application) {
			this.application = application;
		}

		@Override @Nullable public MCreator get() {
			Collection<MCreator> open = application.getOpenMCreators();
			for (MCreator mcreator : open) {
				if (mcreator.isActive()) {
					lastActive = mcreator;
					return mcreator;
				}
			}
			MCreator last = lastActive;
			if (last != null && open.contains(last))
				return last;
			return open.isEmpty() ? null : open.iterator().next();
		}
	}

}
