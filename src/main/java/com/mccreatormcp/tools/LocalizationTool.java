package com.mccreatormcp.tools;

import com.mccreatormcp.util.Compat;
import com.mccreatormcp.util.UiRefresh;
import net.mcreator.generator.LocalizationUtils;
import net.mcreator.io.mcp.protocol.SchemaDescription;
import net.mcreator.io.mcp.tool.ToolResult;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.mcp.MCreatorMcpTool;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import javax.annotation.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LocalizationTool extends MCreatorMcpTool<LocalizationTool.Args> {

	private static final String DEFAULT_LANGUAGE = "en_us";
	private static final int DEFAULT_LIMIT = 500;

	public static class Args {
		@SchemaDescription("""
				LIST_LANGUAGES: languages with entry counts and number of untranslated entries. \
				GET: entries of language (filter optional). \
				SET: add or update entries (key -> text) in language. Setting a new key in en_us also adds it to other languages as placeholder. \
				REMOVE_KEYS: remove keys from all languages. \
				ADD_LANGUAGE: add language (e.g. es_es, de_de, fr_fr, pt_br, ru_ru, zh_cn, ja_jp) initialized with en_us texts. \
				REMOVE_LANGUAGE: remove a language (en_us can not be removed). \
				UNTRANSLATED: keys of language that are missing or still identical to en_us text, with en_us text to translate.""")
		public Action actionType;

		@SchemaDescription("Language code in Minecraft format, lowercase with underscore, e.g. en_us, es_es. Defaults to en_us.")
		@Nullable public String language;

		@SchemaDescription("For SET: map of localization key to text")
		@Nullable public Map<String, String> entries;

		@SchemaDescription("For REMOVE_KEYS: keys to remove") @Nullable public List<String> keys;

		@SchemaDescription("Optional Java regex matched against key and text (GET, UNTRANSLATED)") @Nullable public String filter;

		@SchemaDescription("Max number of returned entries (default 500)") @Nullable public Integer limit;

		public enum Action {
			LIST_LANGUAGES, GET, SET, REMOVE_KEYS, ADD_LANGUAGE, REMOVE_LANGUAGE, UNTRANSLATED
		}
	}

	public LocalizationTool(Supplier<MCreator> currentMCreator) {
		super(currentMCreator, Args.class);
	}

	@Override public String getName() {
		return "localization";
	}

	@Override public String getDescription() {
		return """
				Manages workspace translations (language files). Mod element names are localized automatically from \
				their definitions (keys like item.<modid>.<registry_name>, block.<modid>.<name>), so use this tool to \
				translate those keys to other languages, to add texts used by procedures (e.g. chat messages, \
				tooltips) and to find untranslated texts. Translating the mod to several languages improves quality.""";
	}

	@Override protected Boolean getReadOnlyHint() {
		return false;
	}

	@Override protected Boolean getDestructiveHint() {
		return true;
	}

	@Override protected CompletableFuture<ToolResult> call(MCreator mcreator, Args input) {
		if (input.actionType == null)
			return completedError("actionType must be provided");

		Workspace workspace = mcreator.getWorkspace();
		Map<String, LinkedHashMap<String, String>> languages = workspace.getLanguageMap();
		String language = normalizeLanguage(input.language);
		int limit = input.limit != null && input.limit > 0 ? input.limit : DEFAULT_LIMIT;

		Pattern filter;
		try {
			filter = input.filter == null || input.filter.isBlank() ? null : Pattern.compile(input.filter);
		} catch (PatternSyntaxException e) {
			return completedError("Invalid filter regex: " + e.getMessage());
		}

		LinkedHashMap<String, String> english = languages.getOrDefault(DEFAULT_LANGUAGE, new LinkedHashMap<>());

		switch (input.actionType) {
		case LIST_LANGUAGES -> {
			List<Map<String, Object>> result = new ArrayList<>();
			for (Map.Entry<String, LinkedHashMap<String, String>> entry : new TreeMap<>(languages).entrySet()) {
				Map<String, Object> info = new LinkedHashMap<>();
				info.put("language", entry.getKey());
				info.put("entries", entry.getValue().size());
				if (!entry.getKey().equals(DEFAULT_LANGUAGE))
					info.put("untranslated", untranslated(english, entry.getValue(), null).size());
				result.add(info);
			}
			return completed(ToolResult.collection(result));
		}
		case GET -> {
			LinkedHashMap<String, String> data = languages.get(language);
			if (data == null)
				return completedError("Language " + language + " does not exist. Existing: " + languages.keySet());
			Map<String, String> result = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : data.entrySet()) {
				if (matches(filter, entry.getKey(), entry.getValue())) {
					if (result.size() >= limit)
						break;
					result.put(entry.getKey(), entry.getValue());
				}
			}
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("language", language);
			response.put("totalEntries", data.size());
			response.put("returnedEntries", result.size());
			response.put("entries", result);
			return completedObject(response);
		}
		case SET -> {
			if (input.entries == null || input.entries.isEmpty())
				return completedError("entries must be provided for SET");
			if (!languages.containsKey(language))
				return completedError("Language " + language + " does not exist. Add it first with ADD_LANGUAGE.");
			List<String> invalid = input.entries.keySet().stream()
					.filter(key -> key == null || key.isBlank() || !key.matches("[a-zA-Z0-9_.:\\-/]+")).toList();
			if (!invalid.isEmpty())
				return completedError("Invalid localization keys (use only letters, digits, _ . : - /): " + invalid);

			int added = 0, updated = 0;
			for (Map.Entry<String, String> entry : input.entries.entrySet()) {
				String value = entry.getValue() == null ? "" : entry.getValue();
				if (language.equals(DEFAULT_LANGUAGE)) {
					if (english.containsKey(entry.getKey()))
						updated++;
					else
						added++;
					workspace.setLocalization(entry.getKey(), value);
				} else {
					String previous = languages.get(language).put(entry.getKey(), value);
					if (previous == null)
						added++;
					else
						updated++;
				}
			}
			workspace.getHistoryManager().checkpoint("mod_element_edited", "localization");
			workspace.markDirty();
			regenerate(mcreator);
			return completedObject(Map.of("language", language, "added", added, "updated", updated));
		}
		case REMOVE_KEYS -> {
			if (input.keys == null || input.keys.isEmpty())
				return completedError("keys must be provided for REMOVE_KEYS");
			Set<ModElement> usages = Compat.localizationKeyUsages(workspace, input.keys);
			for (String key : input.keys)
				workspace.removeLocalizationEntryByKey(key);
			workspace.getHistoryManager().checkpoint("mod_element_edited", "localization");
			regenerate(mcreator);
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("removed", input.keys.size());
			if (!usages.isEmpty())
				response.put("warning", "Removed keys were used by these mod elements: " + usages.stream()
						.map(ModElement::getName).sorted().toList()
						+ ". Element names are re-added when the element is regenerated.");
			return completedObject(response);
		}
		case ADD_LANGUAGE -> {
			if (!language.matches("[a-z]{2,3}_[a-z0-9]{2,3}"))
				return completedError("Invalid language code. Use Minecraft format such as es_es, de_de, pt_br");
			if (languages.containsKey(language))
				return completedError("Language " + language + " already exists");
			workspace.addLanguage(language, english);
			regenerate(mcreator);
			return completedObject(Map.of("added", language, "entries", english.size(), "next",
					"Use UNTRANSLATED with language " + language + " and then SET translated texts"));
		}
		case REMOVE_LANGUAGE -> {
			if (language.equals(DEFAULT_LANGUAGE))
				return completedError("en_us is the default language and can not be removed");
			if (!languages.containsKey(language))
				return completedError("Language " + language + " does not exist");
			workspace.getHistoryManager().checkpoint("mod_element_edited", "localization");
			workspace.removeLocalizationLanguage(language);
			regenerate(mcreator);
			return completedText("Language " + language + " removed");
		}
		case UNTRANSLATED -> {
			if (language.equals(DEFAULT_LANGUAGE)) {
				// for the default language, report empty texts
				Map<String, String> empty = new LinkedHashMap<>();
				english.forEach((key, value) -> {
					if ((value == null || value.isBlank()) && matches(filter, key, "") && empty.size() < limit)
						empty.put(key, "");
				});
				return completedObject(Map.of("language", language, "emptyTexts", empty));
			}
			LinkedHashMap<String, String> data = languages.get(language);
			if (data == null)
				return completedError("Language " + language + " does not exist. Add it first with ADD_LANGUAGE.");
			Map<String, String> missing = untranslated(english, data, filter);
			Map<String, String> limited = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : missing.entrySet()) {
				if (limited.size() >= limit)
					break;
				limited.put(entry.getKey(), entry.getValue());
			}
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("language", language);
			response.put("untranslatedCount", missing.size());
			response.put("entriesToTranslate", limited);
			response.put("note", "Values are en_us texts. Translate them and store with SET for language " + language
					+ ". Keep format codes (§, %s, %1$s) intact.");
			return completedObject(response);
		}
		}
		return completedError("Unknown action");
	}

	private static Map<String, String> untranslated(Map<String, String> english, Map<String, String> other,
			@Nullable Pattern filter) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : english.entrySet()) {
			String value = entry.getValue();
			if (value == null || value.isBlank())
				continue;
			String translated = other.get(entry.getKey());
			boolean needsTranslation = translated == null || translated.isBlank() || (translated.equals(value)
					&& value.chars().anyMatch(Character::isLetter));
			if (needsTranslation && matches(filter, entry.getKey(), value))
				result.put(entry.getKey(), value);
		}
		return result;
	}

	private static boolean matches(@Nullable Pattern filter, String key, @Nullable String value) {
		return filter == null || filter.matcher(key).find() || (value != null && filter.matcher(value).find());
	}

	private static String normalizeLanguage(@Nullable String language) {
		if (language == null || language.isBlank())
			return DEFAULT_LANGUAGE;
		return language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
	}

	private static void regenerate(MCreator mcreator) {
		LocalizationUtils.generateLanguageFiles(mcreator.getGenerator(), mcreator.getWorkspace(),
				mcreator.getGeneratorConfiguration().getLanguageFileSpecification());
		UiRefresh.resourcesChanged(mcreator);
	}

}
