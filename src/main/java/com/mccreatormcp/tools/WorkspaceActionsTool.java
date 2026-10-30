package com.mccreatormcp.tools;

import com.mccreatormcp.util.Io;
import net.mcreator.gradle.GradleResultCode;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.io.zip.ZipIO;
import net.mcreator.plugin.MCREvent;
import net.mcreator.plugin.events.workspace.WorkspaceBuildStartedEvent;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.action.impl.workspace.RegenerateCodeAction;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.gradle.GradleConsole;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.workspace.settings.WorkspaceSettings;

import javax.annotation.Nullable;
import javax.swing.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class WorkspaceActionsTool extends MCreatorMcpTool<WorkspaceActionsTool.Args> {

	private static final int WAIT_SECONDS = 50;

	private static final String[] ZIP_EXCLUDES = { ".eclipse/", ".gradle/", ".mcreator/", "build/", "gradle/",
			"#build.gradle", "#gradlew", "#gradlew.bat", "#mcreator.gradle", "#.classpath", "#.project", ".idea/",
			".settings/", ".git/", "run/", "runs/" };

	public static class Args {
		@SchemaDescription("""
				SAVE: write the workspace to disk now. \
				REGENERATE_CODE: regenerate code of all elements and build (fixes stale generated code after big \
				changes; waits up to 50 s, then poll build IS_GRADLE_RUNNING). \
				EXPORT_MOD: build the distributable mod file (.jar) and copy it to outputFolder. \
				EXPORT_WORKSPACE: save a shareable .zip backup of the workspace (sources and resources) to outputFolder.""")
		public Action actionType;
		@SchemaDescription("Folder for exported files (absolute path). Defaults to an exports folder next to the workspace folder") @Nullable public String outputFolder;

		public enum Action {
			SAVE, REGENERATE_CODE, EXPORT_MOD, EXPORT_WORKSPACE
		}
	}

	public WorkspaceActionsTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "workspace_actions";
	}

	@Override public String getDescription() {
		return """
				Workspace level actions: save, regenerate all code, export the finished mod as a distributable \
				.jar file and export the workspace as a .zip backup. Run quality_audit before EXPORT_MOD.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return false;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) {
		if (input.actionType == null)
			return completedError("actionType must be provided");

		switch (input.actionType) {
		case SAVE -> {
			mcreator.getWorkspace().getFileManager().saveWorkspaceDirectlyAndWait();
			return completedText("Workspace saved");
		}
		case REGENERATE_CODE -> {
			if (isGradleBusy(mcreator))
				return completedError("Gradle is busy, try again later");
			CompletableFuture<ToolResult> future = new CompletableFuture<>();
			SwingUtilities.invokeLater(() -> RegenerateCodeAction.regenerateCode(mcreator, false, false,
					result -> future.complete(ToolResult.object(
							Map.of("finished", true, "result", result.name(), "hint",
									result == GradleResultCode.STATUS_OK ?
											"Code regenerated and build succeeded" :
											"Build failed, use read_console to see errors")))));
			return future.completeOnTimeout(ToolResult.object(Map.of("finished", false, "message",
					"Regeneration and build still running. Poll build IS_GRADLE_RUNNING, then read_console.")),
					WAIT_SECONDS, TimeUnit.SECONDS);
		}
		case EXPORT_MOD -> {
			if (isGradleBusy(mcreator))
				return completedError("Gradle is busy, try again later");
			File outputFolder = outputFolder(mcreator, input.outputFolder);
			WorkspaceSettings settings = mcreator.getWorkspaceSettings();
			String exportFile = mcreator.getGeneratorConfiguration().getGradleTaskFor("export_file");
			File builtFile = new File(mcreator.getWorkspaceFolder(), exportFile);
			String extension = exportFile.substring(exportFile.lastIndexOf('.') + 1);
			File target = new File(outputFolder,
					settings.getModID() + "-" + settings.getVersion() + "-" + settings.getCurrentGenerator() + "."
							+ extension);

			CompletableFuture<ToolResult> future = new CompletableFuture<>();
			GradleConsole console = mcreator.getGradleConsole();
			console.markRunning();
			try {
				mcreator.getGenerator().runResourceSetupTasks();
				mcreator.getGenerator().generateBase(true);
				MCREvent.event(new WorkspaceBuildStartedEvent(mcreator));
				ThreadUtil.runOnSwingThreadAndWait(() -> console.exec("build", result -> {
					if (result == GradleResultCode.STATUS_OK && builtFile.isFile()) {
						outputFolder.mkdirs();
						Io.copy(builtFile, target);
						future.complete(ToolResult.object(
								Map.of("finished", true, "exportedFile", target.getAbsolutePath(), "size",
										builtFile.length())));
					} else {
						future.complete(ToolResult.error("Build failed (" + result.name()
								+ "), the mod was not exported. Use read_console to see errors."));
					}
				}));
			} catch (Exception e) {
				console.markReady();
				return completedError("Failed to start export build: " + e.getMessage(), e);
			}
			return future.completeOnTimeout(ToolResult.object(Map.of("finished", false, "message",
					"Build still running. When build IS_GRADLE_RUNNING reports false the mod will be at "
							+ target.getAbsolutePath() + " (if the build succeeded).")), WAIT_SECONDS,
					TimeUnit.SECONDS);
		}
		case EXPORT_WORKSPACE -> {
			File outputFolder = outputFolder(mcreator, input.outputFolder);
			String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
			File target = new File(outputFolder, mcreator.getWorkspaceSettings().getModID() + "-" + timestamp + ".zip");
			try {
				outputFolder.mkdirs();
				mcreator.getWorkspace().getFileManager().saveWorkspaceDirectlyAndWait();
				// exports folder itself must not end up in the archive
				List<String> excludes = new ArrayList<>(List.of(ZIP_EXCLUDES));
				if (outputFolder.toPath().startsWith(mcreator.getWorkspaceFolder().toPath()))
					excludes.add(mcreator.getWorkspaceFolder().toPath().relativize(outputFolder.toPath()).toString()
							.replace(File.separatorChar, '/') + "/");
				ZipIO.zipDir(mcreator.getWorkspaceFolder().getAbsolutePath(), target.getAbsolutePath(),
						excludes.toArray(new String[0]));
				mcreator.getWorkspace().getHistoryManager().importantCheckpoint(() -> false, "export_workspace");
			} catch (Exception e) {
				return completedError("Failed to export workspace: " + e.getMessage(), e);
			}
			return completedObject(Map.of("exportedFile", target.getAbsolutePath(), "size", target.length()));
		}
		}
		return completedError("Unknown action");
	}

	private static File outputFolder(MCreator mcreator, @Nullable String outputFolder) {
		if (outputFolder == null || outputFolder.isBlank())
			// outside of the workspace folder, so exports do not end up in local history or workspace backups
			return new File(mcreator.getWorkspaceFolder().getAbsoluteFile().getParentFile(), "exports");
		File folder = new File(outputFolder.trim());
		if (!folder.isAbsolute())
			folder = new File(mcreator.getWorkspaceFolder(), outputFolder.trim());
		return folder;
	}

	private static boolean isGradleBusy(MCreator mcreator) {
		return mcreator.getGradleConsole().getStatus() == GradleConsole.RUNNING || mcreator.getGradleConsole()
				.isGradleSetupTaskRunning();
	}

}
