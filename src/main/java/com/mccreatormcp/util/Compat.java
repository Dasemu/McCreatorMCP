package com.mccreatormcp.util;

import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.references.ReferencesFinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * MCreator internal APIs used by the plugin changed between 2026.3 builds (for example ReferencesFinder methods
 * taking a single value vs. a collection). This class resolves the available variant at runtime, so one plugin
 * build works on all supported MCreator versions.
 */
public final class Compat {

	private static final Logger LOG = LogManager.getLogger("MCP Plus");

	private Compat() {}

	public static Set<ModElement> soundUsages(Workspace workspace, SoundElement sound) {
		return usages("searchSoundUsages", workspace, SoundElement.class, sound);
	}

	public static Set<ModElement> structureUsages(Workspace workspace, String structure) {
		return usages("searchStructureUsages", workspace, String.class, structure);
	}

	public static Set<ModElement> localizationKeyUsages(Workspace workspace, Collection<String> keys) {
		Set<ModElement> result = new LinkedHashSet<>();
		for (String key : keys)
			result.addAll(usages("searchLocalizationKeyUsages", workspace, String.class, key));
		return result;
	}

	/**
	 * @return broken references per element, or null if this MCreator version can not detect them
	 */
	@SuppressWarnings("unchecked") @Nullable
	public static Map<ModElement, Set<String>> brokenReferences(Workspace workspace) {
		try {
			Method method = ReferencesFinder.class.getMethod("searchBrokenReferences", Workspace.class);
			return (Map<ModElement, Set<String>>) method.invoke(null, workspace);
		} catch (NoSuchMethodException e) {
			return null;
		} catch (ReflectiveOperationException e) {
			LOG.warn("Failed to search broken references", e);
			return null;
		}
	}

	/**
	 * Invalidates cached model data (texture mappings) if this MCreator version caches it
	 *
	 * @param modelFile model file that changed, or null to invalidate all models
	 */
	public static void invalidateModelCache(Workspace workspace, @Nullable File modelFile) {
		try {
			Object modelManager = Workspace.class.getMethod("getModelManager").invoke(workspace);
			if (modelFile != null)
				modelManager.getClass().getMethod("invalidateCache", File.class).invoke(modelManager, modelFile);
			else
				modelManager.getClass().getMethod("invalidateCache").invoke(modelManager);
		} catch (NoSuchMethodException ignored) {
			// older builds do not cache models
		} catch (ReflectiveOperationException e) {
			LOG.warn("Failed to invalidate model cache", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Set<ModElement> usages(String name, Workspace workspace, Class<T> type, T value) {
		try {
			// newer builds: (Workspace, Collection<T>)
			Method method = ReferencesFinder.class.getMethod(name, Workspace.class, Collection.class);
			return (Set<ModElement>) method.invoke(null, workspace, List.of(value));
		} catch (NoSuchMethodException e) {
			try {
				// older builds: (Workspace, T)
				Method method = ReferencesFinder.class.getMethod(name, Workspace.class, type);
				return (Set<ModElement>) method.invoke(null, workspace, value);
			} catch (ReflectiveOperationException e2) {
				LOG.warn("Failed to search usages with {}", name, e2);
			}
		} catch (ReflectiveOperationException e) {
			LOG.warn("Failed to search usages with {}", name, e);
		}
		return Set.of();
	}

}
