# Quality checklist before releasing a mod

1. Metadata: name, description, author, version, license, logo (workspace_settings_edit).
2. No broken references and no compile errors (quality_audit, build BUILD_AND_WAIT).
3. Every item/block/tool/armor: display name, texture, creative tab, obtainable in survival.
4. Balanced stats compared to vanilla (minecraft_reference tool_tiers, armor, food, mobs, blocks).
5. Recipes for everything craftable, no conflicting recipes, smelting/blasting for ores.
6. Blocks: correct tool and tier, drops (or loot table), sounds, hardness; ores generate at sensible heights.
7. Entities: model, texture, sounds, loot table, spawn rules or spawn egg, sensible AI.
8. Tags for compatibility (c:ingots/..., mineable tags, needs_*_tool tags).
9. Translations: at least en_us complete; ideally more languages (localization).
10. Sounds with subtitles; textures consistent in style (texture_edit PREVIEW, screenshot).
11. Test in game (run_client), check logs for errors (run_dir_files, read_console).
12. Export with workspace_actions EXPORT_MOD and keep a workspace backup (EXPORT_WORKSPACE).
