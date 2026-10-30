package com.mccreatormcp.tools;

import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.gradle.GradleConsole;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.workspace.localhistory.HistoryCheckpoint;
import net.mcreator.workspace.localhistory.HistoryManager;

import javax.annotation.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class WorkspaceHistoryTool extends MCreatorMcpTool<WorkspaceHistoryTool.Args> {

	public static class Args {
		@SchemaDescription("""
				LIST: recent local history checkpoints, newest first. \
				CHECKPOINT: save a manual checkpoint now (do it before risky bulk changes). \
				REVERT: restore the whole workspace to the state of the checkpoint with the given hash \
				(a checkpoint of the current state is saved first, so a revert can be undone).""")
		public Action actionType;
		@SchemaDescription("REVERT: checkpoint hash (or unique prefix) from LIST") @Nullable public String hash;
		@SchemaDescription("LIST: max checkpoints (default 30)") @Nullable public Integer limit;

		public enum Action {
			LIST, CHECKPOINT, REVERT
		}
	}

	public WorkspaceHistoryTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "workspace_history";
	}

	@Override public String getDescription() {
		return """
				Undo support through MCreator local history: list checkpoints, create a checkpoint before risky \
				changes and revert the workspace to an earlier checkpoint if something went wrong. Local history \
				must be enabled in MCreator preferences.""";
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
		HistoryManager history = mcreator.getWorkspace().getHistoryManager();
		if (!history.isAvailable())
			return completedError("Local history is disabled for this workspace (Preferences > Backups)");

		switch (input.actionType) {
		case LIST -> {
			int limit = input.limit != null && input.limit > 0 ? input.limit : 30;
			List<HistoryCheckpoint> checkpoints = checkpoints(history);
			List<Map<String, Object>> result = new ArrayList<>();
			for (HistoryCheckpoint checkpoint : checkpoints.subList(0, Math.min(limit, checkpoints.size()))) {
				Map<String, Object> info = new LinkedHashMap<>();
				info.put("hash", checkpoint.hash());
				info.put("name", checkpoint.name());
				info.put("time", checkpoint.getTimestampString());
				result.add(info);
			}
			return completed(ToolResult.collection(result));
		}
		case CHECKPOINT -> {
			history.importantCheckpoint("manual");
			return completedText("Checkpoint saved");
		}
		case REVERT -> {
			if (input.hash == null || input.hash.isBlank())
				return completedError("hash must be provided");
			if (mcreator.getGradleConsole().getStatus() == GradleConsole.RUNNING)
				return completedError("Gradle is running, wait until it finishes before reverting");
			List<HistoryCheckpoint> matches = checkpoints(history).stream()
					.filter(c -> c.hash().startsWith(input.hash.trim())).toList();
			if (matches.size() != 1)
				return completedError(matches.isEmpty() ? "Checkpoint not found" : "Hash prefix is ambiguous");
			HistoryCheckpoint target = matches.getFirst();
			history.importantCheckpoint("pre_revert");
			history.revertToCheckpoint(target);
			ThreadUtil.runOnSwingThreadAndWait(mcreator::reloadWorkspaceFromFileSystem);
			history.importantCheckpoint("revert", target.getTimestampString());
			return completedText("Workspace reverted to '" + target.name() + "' (" + target.getTimestampString()
					+ "). A 'Before revert' checkpoint was created to undo this.");
		}
		}
		return completedError("Unknown action");
	}

	private static List<HistoryCheckpoint> checkpoints(HistoryManager history) throws Exception {
		CompletableFuture<List<HistoryCheckpoint>> future = new CompletableFuture<>();
		history.getCheckpoints(future::complete);
		return future.get(30, TimeUnit.SECONDS);
	}

}
