package com.mccreatormcp.util;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorTabs;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.modgui.ModElementGUI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class UiRefresh {

	private static final Logger LOG = LogManager.getLogger("MCP Plus");

	private UiRefresh() {}

	/**
	 * Reloads workspace panels and data lists of the open mod element editor, so new resources show up in the UI
	 */
	public static void resourcesChanged(MCreator mcreator) {
		run(() -> {
			mcreator.reloadWorkspaceTabContents();
			MCreatorTabs.Tab tab = mcreator.getTabs().getCurrentTab();
			if (tab != null && tab.getContent() instanceof ModElementGUI<?> modElementGUI)
				modElementGUI.reloadDataLists();
		});
	}

	/**
	 * Reloads workspace panels after mod elements or folders changed
	 */
	public static void workspaceChanged(MCreator mcreator) {
		run(mcreator::reloadWorkspaceTabContents);
	}

	private static void run(Runnable runnable) {
		// the change itself is already done at this point, a failed UI refresh must not fail the tool call
		try {
			ThreadUtil.runOnSwingThreadAndWait(runnable);
		} catch (Throwable e) {
			LOG.warn("Failed to refresh MCreator UI", e);
		}
	}

}
