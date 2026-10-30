# Recommended workflow for creating content with MCP

1. Orient: workspace_overview, then quality_audit to see the current state.
2. Plan the feature set and progression (minecraft_reference progression).
3. Resources first: textures (create_texture, texture_import, texture_edit), sounds (sound), models
   (model_import), structures (structure).
4. Elements: get_mod_element_schema for the type, then mod_element_definition ADD. For families of elements
   use pack_maker or mod_element_manage DUPLICATE + mod_element_definition PATCH.
5. Logic: procedures with blockly_triggers / blockly_blocks / blockly_templates.
6. Make it obtainable: recipes, loot tables, tags (tag), world generation.
7. Polish: names, creative tab, translations (localization), metadata (workspace_settings_edit),
   folders (mod_element_manage).
8. Verify: quality_audit, build BUILD_AND_WAIT, run_client, screenshot / PREVIEW for visuals.
9. Ship: workspace_actions EXPORT_MOD. Use workspace_history CHECKPOINT before risky bulk changes and
   REVERT if something goes wrong.
