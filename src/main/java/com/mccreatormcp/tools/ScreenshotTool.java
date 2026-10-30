package com.mccreatormcp.tools;

import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorTabs;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.mcp.MCreatorMcpTool;

import javax.annotation.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ScreenshotTool extends MCreatorMcpTool<ScreenshotTool.Args> {

	public static class Args {
		@SchemaDescription("WINDOW: whole MCreator window. CURRENT_TAB: only the content of the open tab (default).")
		@Nullable public Target target;
		@SchemaDescription("Max width of the returned image in pixels (default 1400)") @Nullable public Integer maxWidth;

		public enum Target {
			WINDOW, CURRENT_TAB
		}
	}

	public ScreenshotTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "screenshot";
	}

	@Override public String getDescription() {
		return """
				Captures the MCreator window or the current tab as an image, so you can visually verify editors, \
				GUIs, procedures (Blockly), textures and element previews the same way the user sees them. \
				Combine with mod_element_manage OPEN_IN_EDITOR and get_current_tab.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return true;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) throws Exception {
		Args.Target target = input.target == null ? Args.Target.CURRENT_TAB : input.target;
		int maxWidth = input.maxWidth != null ? Math.max(200, Math.min(4000, input.maxWidth)) : 1400;

		AtomicReference<BufferedImage> image = new AtomicReference<>();
		AtomicReference<String> error = new AtomicReference<>();
		ThreadUtil.runOnSwingThreadAndWait(() -> {
			Component component = mcreator.getRootPane();
			if (target == Args.Target.CURRENT_TAB) {
				MCreatorTabs.Tab tab = mcreator.getTabs().getCurrentTab();
				if (tab != null && tab.getContent() != null)
					component = tab.getContent();
			}
			if (component.getWidth() <= 0 || component.getHeight() <= 0) {
				error.set("Nothing to capture, the component is not visible");
				return;
			}
			BufferedImage capture = new BufferedImage(component.getWidth(), component.getHeight(),
					BufferedImage.TYPE_INT_RGB);
			Graphics2D g = capture.createGraphics();
			component.paint(g);
			g.dispose();
			image.set(capture);
		});
		if (error.get() != null)
			return completedError(error.get());

		BufferedImage result = image.get();
		if (result.getWidth() > maxWidth) {
			int height = (int) Math.round(result.getHeight() * (maxWidth / (double) result.getWidth()));
			BufferedImage scaled = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = scaled.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(result, 0, 0, maxWidth, height, null);
			g.dispose();
			result = scaled;
		}
		return completed(ToolResult.image(TextureEditTool.png(result), "image/png"));
	}

}
