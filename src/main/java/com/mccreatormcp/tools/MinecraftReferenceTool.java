package com.mccreatormcp.tools;

import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class MinecraftReferenceTool extends MCreatorMcpTool<MinecraftReferenceTool.Args> {

	public static final List<String> TOPICS = List.of("workflow", "quality_checklist", "progression", "tool_tiers",
			"armor", "food", "blocks", "ores", "mobs", "enchantments", "textures", "sounds", "naming");

	public static class Args {
		@SchemaDescription("""
				Topic to read: workflow, quality_checklist, progression, tool_tiers, armor, food, blocks, ores, mobs, \
				 enchantments, textures, sounds, naming. Omit to list topics.""")
		@Nullable public String topic;
	}

	public MinecraftReferenceTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "minecraft_reference";
	}

	@Override public String getDescription() {
		return """
				Reference for making balanced, vanilla-consistent mods: vanilla stats (tool tiers, armor, food, \
				block hardness, ore generation, mob stats, enchantments) mapped to MCreator element fields, plus \
				guidelines for textures, sounds, naming, progression, a release quality checklist and the \
				recommended MCP workflow. Read it before choosing stats for new content.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return true;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) throws IOException {
		if (input.topic == null || input.topic.isBlank())
			return completedText("Available topics: " + String.join(", ", TOPICS));
		String topic = input.topic.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
		if (!TOPICS.contains(topic))
			return completedError("Unknown topic " + input.topic + ". Available: " + String.join(", ", TOPICS));
		try (InputStream in = MinecraftReferenceTool.class.getResourceAsStream(
				"/mcpplus/reference/" + topic + ".md")) {
			if (in == null)
				return completedError("Reference " + topic + " is missing from the plugin");
			return completedText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

}
