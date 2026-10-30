package com.mccreatormcp.tools;

import com.mccreatormcp.util.Compat;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.element.parts.TabEntry;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.*;
import net.mcreator.element.types.interfaces.ITabContainedElement;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.references.ReferencesFinder;
import net.mcreator.workspace.settings.WorkspaceSettings;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class QualityAuditTool extends MCreatorMcpTool<QualityAuditTool.Args> {

	// ModElementType constants are only initialized once MCreator loads mod element types, so build sets lazily
	private static Set<ModElementType<?>> obtainableTypes() {
		return setOf(ModElementType.ITEM, ModElementType.BLOCK, ModElementType.TOOL, ModElementType.ARMOR,
				ModElementType.PLANT);
	}

	private static Set<ModElementType<?>> producerTypes() {
		return setOf(ModElementType.RECIPE, ModElementType.LOOTTABLE, ModElementType.VILLAGERTRADE,
				ModElementType.PROCEDURE, ModElementType.ADVANCEMENT, ModElementType.LIVINGENTITY,
				ModElementType.FEATURE, ModElementType.STRUCTURE);
	}

	private static Set<ModElementType<?>> setOf(ModElementType<?>... types) {
		Set<ModElementType<?>> set = new HashSet<>();
		for (ModElementType<?> type : types) {
			if (type != null)
				set.add(type);
		}
		return set;
	}

	public static class Args {
		@SchemaDescription("""
				Optional categories to check (default all): metadata, references, compilation, presentation, \
				 obtainability, balance, recipes, procedures, sounds, textures, localization""")
		@Nullable public List<String> categories;
		@SchemaDescription("Include info level suggestions (default true)") @Nullable public Boolean includeInfo;
		@SchemaDescription("Max number of returned issues (default 300)") @Nullable public Integer maxIssues;
	}

	public record Issue(String severity, String category, @Nullable String element, String message, String fix) {}

	public QualityAuditTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "quality_audit";
	}

	@Override public String getDescription() {
		return """
				Audits the whole mod for quality problems and returns a score (0-100) and a prioritized issue list \
				with suggested fixes: missing mod metadata/logo, broken references, compile errors, elements without \
				display name, texture or creative tab, items/blocks that can not be obtained in survival, blocks that \
				drop nothing, unbalanced tool/armor/food stats compared to vanilla, conflicting crafting recipes, \
				unused procedures/textures/sounds, sounds without subtitles and untranslated texts. Run it after \
				making changes and before exporting the mod.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return true;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) {
		Set<String> categories = input.categories == null || input.categories.isEmpty() ?
				null :
				new HashSet<>(input.categories.stream().map(c -> c.trim().toLowerCase(Locale.ROOT)).toList());
		boolean includeInfo = input.includeInfo == null || input.includeInfo;
		int maxIssues = input.maxIssues != null && input.maxIssues > 0 ? input.maxIssues : 300;

		Workspace workspace = mcreator.getWorkspace();
		List<Issue> issues = new ArrayList<>();
		Auditor auditor = new Auditor(workspace, issues, categories);
		auditor.run();

		if (!includeInfo)
			issues.removeIf(issue -> issue.severity.equals("info"));

		Map<String, Long> counts = new LinkedHashMap<>();
		for (String severity : List.of("error", "warning", "info"))
			counts.put(severity, issues.stream().filter(i -> i.severity.equals(severity)).count());
		double score = 100 - counts.get("error") * 10 - counts.get("warning") * 3 - counts.get("info") * 0.5;

		issues.sort(Comparator.comparingInt((Issue i) -> switch (i.severity) {
			case "error" -> 0;
			case "warning" -> 1;
			default -> 2;
		}).thenComparing(Issue::category));

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("score", Math.max(0, (int) Math.round(score)));
		result.put("counts", counts);
		result.put("elementsAudited", workspace.getModElements().size());
		result.put("issues", issues.size() > maxIssues ? issues.subList(0, maxIssues) : issues);
		if (issues.size() > maxIssues)
			result.put("truncated", issues.size() - maxIssues + " more issues not shown");
		return completedObject(result);
	}

	private static final class Auditor {

		private final Workspace workspace;
		private final List<Issue> issues;
		@Nullable private final Set<String> categories;
		private final Set<ModElementType<?>> obtainableTypes = obtainableTypes();
		private final Set<ModElementType<?>> producerTypes = producerTypes();

		private Auditor(Workspace workspace, List<Issue> issues, @Nullable Set<String> categories) {
			this.workspace = workspace;
			this.issues = issues;
			this.categories = categories;
		}

		private boolean enabled(String category) {
			return categories == null || categories.contains(category);
		}

		private void add(String severity, String category, @Nullable String element, String message, String fix) {
			issues.add(new Issue(severity, category, element, message, fix));
		}

		private void run() {
			if (enabled("metadata"))
				metadata();
			if (enabled("references"))
				references();
			if (enabled("compilation"))
				compilation();

			Map<ModElement, GeneratableElement> elements = new LinkedHashMap<>();
			for (ModElement element : workspace.getModElements()) {
				try {
					GeneratableElement ge = element.getGeneratableElement();
					if (ge != null)
						elements.put(element, ge);
					else if (enabled("compilation"))
						add("error", "compilation", element.getName(), "Element definition could not be loaded",
								"Open the element in MCreator and save it again, or remove it");
				} catch (Exception e) {
					add("error", "compilation", element.getName(), "Failed to load definition: " + e.getMessage(),
							"Open the element in MCreator and fix it");
				}
			}

			for (Map.Entry<ModElement, GeneratableElement> entry : elements.entrySet()) {
				ModElement element = entry.getKey();
				GeneratableElement ge = entry.getValue();
				if (enabled("presentation"))
					presentation(element, ge);
				if (enabled("obtainability"))
					obtainability(element, ge);
				if (enabled("balance"))
					balance(element, ge);
				if (enabled("procedures"))
					procedures(element, ge);
			}

			if (enabled("recipes"))
				recipes(elements);
			if (enabled("sounds"))
				sounds();
			if (enabled("textures"))
				textures();
			if (enabled("localization"))
				localization();
		}

		private void metadata() {
			WorkspaceSettings s = workspace.getWorkspaceSettings();
			if (blank(s.getDescription()))
				add("warning", "metadata", null, "Mod has no description",
						"workspace_settings_edit description: one or two sentences about what the mod adds");
			if (blank(s.getAuthor()))
				add("warning", "metadata", null, "Mod has no author", "workspace_settings_edit author");
			if (blank(s.getModPicture()))
				add("warning", "metadata", null, "Mod has no logo/picture shown in the mod list",
						"Create or import a logo (texture_import textureType other) and set workspace_settings_edit modPicture");
			if (blank(s.getLicense()))
				add("info", "metadata", null, "Mod has no license",
						"workspace_settings_edit license, e.g. MIT or All Rights Reserved");
			if (blank(s.getWebsiteURL()))
				add("info", "metadata", null, "Mod has no website URL",
						"workspace_settings_edit websiteURL, e.g. the CurseForge/Modrinth page");
			if (s.getModName() != null && s.getModName().equalsIgnoreCase(s.getModID()))
				add("info", "metadata", null, "Mod name is the same as the mod ID",
						"Use a human friendly name with workspace_settings_edit modName");
		}

		private void references() {
			Map<ModElement, Set<String>> brokenReferences = Compat.brokenReferences(workspace);
			if (brokenReferences == null)
				return; // not available in this MCreator build
			brokenReferences.forEach((element, broken) -> add("error", "references", element.getName(),
					"Broken references: " + String.join(", ", broken),
					"Replace the missing elements/resources in the element definition or create them"));
		}

		private void compilation() {
			for (ModElement element : workspace.getModElements()) {
				if (!element.doesCompile())
					add("error", "compilation", element.getName(), "Element failed to compile in the last build",
							"Run build and read_console to see the error, then fix the definition or code");
				if (element.isCodeLocked() && element.getType() != ModElementType.CODE)
					add("info", "compilation", element.getName(),
							"Element code is locked, it will not get MCreator updates and fixes",
							"Unlock it with mod_element_definition UNLOCK_CODE if the custom code is not needed");
			}
		}

		private void presentation(ModElement element, GeneratableElement ge) {
			String displayName = stringField(ge, "name");
			if (displayName != null && displayName.isBlank() && hasField(ge, "creativeTabs"))
				add("warning", "presentation", element.getName(), "Element has no display name",
						"Set name with mod_element_definition PATCH");

			Object texture = field(ge, "texture");
			if (texture instanceof TextureHolder holder && holder.isEmpty() && !(ge instanceof Plant))
				add("warning", "presentation", element.getName(), "Element has no texture",
						"Import or create a texture and set it with mod_element_definition PATCH");

			if (ge instanceof ITabContainedElement tabbed && obtainableTypes.contains(element.getType())) {
				List<TabEntry> tabs = tabbed.getCreativeTabs();
				if (tabs == null || tabs.isEmpty())
					add("warning", "presentation", element.getName(), "Element is not in any creative tab",
							"Add creativeTabs with mod_element_definition PATCH (a custom mod tab looks best)");
			}

			if (ge instanceof Block block && !block.useLootTableForDrops && block.dropAmount == 0)
				add("info", "presentation", element.getName(), "Block drops nothing when mined",
						"If not intended (like glass), set dropAmount to 1 or define a custom drop / loot table");
		}

		private void obtainability(ModElement element, GeneratableElement ge) {
			if (!obtainableTypes.contains(element.getType()))
				return;
			if (ge instanceof Block block && block.generateFeature)
				return;
			boolean produced = ReferencesFinder.searchModElementUsages(workspace, element).stream()
					.filter(e -> !e.equals(element)).anyMatch(e -> producerTypes.contains(e.getType()));
			if (!produced)
				add("warning", "obtainability", element.getName(),
						"Not referenced by any recipe, loot table, trade, procedure, entity or world generation: only obtainable in creative",
						"Add a recipe (mod_element_definition ADD type recipe) or loot/world generation");
		}

		private void balance(ModElement element, GeneratableElement ge) {
			if (ge instanceof Tool tool) {
				if (tool.usageCount > 4000)
					add("warning", "balance", element.getName(),
							"Durability " + tool.usageCount + " is far above netherite (2031)",
							"Use minecraft_reference topic tool_tiers to pick consistent stats");
				if (tool.efficiency > 16)
					add("warning", "balance", element.getName(),
							"Mining speed " + tool.efficiency + " is far above gold (12) and netherite (9)",
							"Use minecraft_reference topic tool_tiers");
				if (tool.damageVsEntity > 15)
					add("warning", "balance", element.getName(), "Attack damage " + tool.damageVsEntity + " is very high",
							"Vanilla netherite sword deals 8. Use minecraft_reference topic tool_tiers");
				if (tool.usageCount == 0 && !"Shears".equals(tool.toolType))
					add("info", "balance", element.getName(), "Tool has 0 durability", "Set usageCount");
			} else if (ge instanceof Armor armor) {
				int total = armor.damageValueHelmet + armor.damageValueBody + armor.damageValueLeggings
						+ armor.damageValueBoots;
				if (total > 24)
					add("warning", "balance", element.getName(),
							"Total armor points " + total + " exceed netherite/diamond (20); player armor caps at 30",
							"Use minecraft_reference topic armor");
				if (armor.maxDamage > 50)
					add("warning", "balance", element.getName(),
							"Durability multiplier " + armor.maxDamage + " is far above netherite (37)",
							"Use minecraft_reference topic armor");
				if (armor.toughness > 4)
					add("warning", "balance", element.getName(), "Toughness " + armor.toughness + " above netherite (3)",
							"Use minecraft_reference topic armor");
			} else if (ge instanceof Item item && item.isFood) {
				if (item.nutritionalValue > 12)
					add("warning", "balance", element.getName(),
							"Food restores " + item.nutritionalValue + " hunger, above any vanilla food (max 10)",
							"Use minecraft_reference topic food");
				if (item.saturation > 1.5)
					add("warning", "balance", element.getName(),
							"Saturation modifier " + item.saturation + " above golden carrot (1.2)",
							"Use minecraft_reference topic food");
			} else if (ge instanceof Block block) {
				if (block.hardness > 60 && block.hardness != -1)
					add("info", "balance", element.getName(), "Hardness " + block.hardness + " is above obsidian (50)",
							"Use minecraft_reference topic blocks");
			}
		}

		private void procedures(ModElement element, GeneratableElement ge) {
			if (ge instanceof Procedure procedure && procedure.procedurexml != null && procedure.procedurexml.contains(
					"<field name=\"trigger\">no_ext_trigger</field>")) {
				boolean used = ReferencesFinder.searchModElementUsages(workspace, element).stream()
						.anyMatch(e -> !e.equals(element));
				if (!used)
					add("warning", "procedures", element.getName(),
							"Procedure has no global trigger and is not used by any element, so it never runs",
							"Attach it to an element event/trigger or a global trigger, or remove it");
			}
		}

		private void recipes(Map<ModElement, GeneratableElement> elements) {
			Map<String, List<String>> signatures = new HashMap<>();
			for (Map.Entry<ModElement, GeneratableElement> entry : elements.entrySet()) {
				if (!(entry.getValue() instanceof Recipe recipe) || recipe.recipeType == null
						|| !recipe.recipeType.equals("Crafting") || recipe.recipeSlots == null)
					continue;
				List<String> slots = new ArrayList<>();
				for (MItemBlock slot : recipe.recipeSlots)
					slots.add(slot == null || slot.getUnmappedValue() == null ? "" : slot.getUnmappedValue());
				if (slots.stream().allMatch(String::isBlank)) {
					add("error", "recipes", entry.getKey().getName(), "Crafting recipe has no ingredients",
							"Fill recipeSlots");
					continue;
				}
				String signature;
				if (recipe.recipeShapeless) {
					List<String> sorted = new ArrayList<>(slots.stream().filter(s -> !s.isBlank()).toList());
					Collections.sort(sorted);
					signature = "shapeless:" + sorted;
				} else {
					signature = "shaped:" + normalizeShape(slots);
				}
				signatures.computeIfAbsent(signature, _ -> new ArrayList<>()).add(entry.getKey().getName());
			}
			signatures.values().stream().filter(names -> names.size() > 1).forEach(
					names -> add("error", "recipes", String.join(", ", names),
							"Crafting recipes with identical ingredients conflict, only one of them will work",
							"Change the pattern or ingredients of all but one recipe"));
		}

		private void sounds() {
			for (SoundElement sound : workspace.getSoundElements()) {
				if (blank(sound.getSubtitle()))
					add("warning", "sounds", sound.getName(), "Sound has no subtitle (accessibility)",
							"sound UPDATE subtitle");
				if (Compat.soundUsages(workspace, sound).isEmpty())
					add("info", "sounds", sound.getName(), "Sound is not used by any element",
							"Use it in an element/procedure or remove it with sound REMOVE");
			}
		}

		private void textures() {
			WorkspaceSettings settings = workspace.getWorkspaceSettings();
			for (TextureType type : List.of(TextureType.BLOCK, TextureType.ITEM, TextureType.ENTITY,
					TextureType.PARTICLE, TextureType.EFFECT, TextureType.SCREEN, TextureType.OTHER)) {
				if (workspace.getFolderManager().getTexturesFolder(type) == null)
					continue;
				for (File file : workspace.getFolderManager().getTexturesList(type)) {
					String name = file.getName().replaceAll("\\.png$", "");
					if (type == TextureType.OTHER && (name.equals(settings.getModPicture()) || name.equals(
							settings.getModIcon())))
						continue;
					if (ReferencesFinder.searchTextureUsages(workspace, file, type).isEmpty())
						add("info", "textures", type.getID() + ":" + name, "Texture is not used by any element",
								"Use it or remove it with texture_edit DELETE to keep the mod small");
				}
			}
		}

		private void localization() {
			Map<String, LinkedHashMap<String, String>> languages = workspace.getLanguageMap();
			Map<String, String> english = languages.getOrDefault("en_us", new LinkedHashMap<>());
			long empty = english.values().stream().filter(v -> v == null || v.isBlank()).count();
			if (empty > 0)
				add("warning", "localization", null, empty + " en_us texts are empty",
						"localization UNTRANSLATED language en_us, then SET texts");
			if (languages.size() <= 1)
				add("info", "localization", null, "Mod is only available in English",
						"localization ADD_LANGUAGE (e.g. es_es) and translate the texts");
			for (Map.Entry<String, LinkedHashMap<String, String>> language : languages.entrySet()) {
				if (language.getKey().equals("en_us"))
					continue;
				long untranslated = english.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue()
						.isBlank()).filter(e -> {
					String t = language.getValue().get(e.getKey());
					return t == null || t.isBlank() || (t.equals(e.getValue()) && e.getValue().chars()
							.anyMatch(Character::isLetter));
				}).count();
				if (untranslated > 0)
					add("warning", "localization", language.getKey(), untranslated + " texts are not translated",
							"localization UNTRANSLATED language " + language.getKey() + ", then SET translations");
			}
		}

		private static String normalizeShape(List<String> slots) {
			// remove empty rows/columns so the same shape in different grid positions is detected as equal
			String[][] grid = new String[3][3];
			for (int i = 0; i < 9; i++)
				grid[i / 3][i % 3] = i < slots.size() ? slots.get(i) : "";
			int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
			for (int r = 0; r < 3; r++)
				for (int c = 0; c < 3; c++)
					if (!grid[r][c].isBlank()) {
						minRow = Math.min(minRow, r);
						maxRow = Math.max(maxRow, r);
						minCol = Math.min(minCol, c);
						maxCol = Math.max(maxCol, c);
					}
			StringBuilder sb = new StringBuilder();
			for (int r = minRow; r <= maxRow; r++) {
				for (int c = minCol; c <= maxCol; c++)
					sb.append(grid[r][c]).append(',');
				sb.append('|');
			}
			return sb.toString();
		}

		@Nullable private static Object field(Object object, String name) {
			try {
				Field field = object.getClass().getField(name);
				return field.get(object);
			} catch (ReflectiveOperationException e) {
				return null;
			}
		}

		private static boolean hasField(Object object, String name) {
			try {
				object.getClass().getField(name);
				return true;
			} catch (NoSuchFieldException e) {
				return false;
			}
		}

		@Nullable private static String stringField(Object object, String name) {
			Object value = field(object, name);
			if (value instanceof String s)
				return s;
			return hasField(object, name) && value == null ? "" : null;
		}

		private static boolean blank(@Nullable String value) {
			return value == null || value.isBlank();
		}

	}

}
