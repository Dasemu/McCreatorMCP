# MCreator MCP Plus

Plugin para [MCreator](https://mcreator.net) que amplía su servidor **MCP (Model Context Protocol)** para que un
asistente de IA (Claude, Cursor, VS Code Copilot…) pueda crear mods completos y de calidad: recursos, traducciones,
metadatos, organización, auditoría de calidad, historial, exportación y referencia de balance vanilla.

MCreator 2026.3 trae un servidor MCP propio con 22-23 tools. Este plugin **añade 14 tools nuevas** a ese mismo
servidor, así que el cliente ve un único servidor con unas 37 tools en total. Si el servidor oficial está
desactivado, el plugin arranca su propio servidor (tools oficiales + tools del plugin) en otro puerto.

## Requisitos

- **MCreator 2026.3** (build `2026.3.41718` o posterior). MCreator 2026.2 y anteriores **no** tienen servidor MCP
  ni la API para plugins que este proyecto necesita.
- Para compilar: JDK 25 (MCreator 2026.3 trae uno en su carpeta `jdk/`).

## Instalación

1. Descarga o compila `mcreator-mcp-plus-1.0.0.zip` (ver [Compilar](#compilar)).
2. En MCreator: **File → Preferences → Plugins → Load plugin** y selecciona el `.zip`, o cópialo a la carpeta de
   plugins del usuario (`~/.mcreator/plugins` en Linux/macOS, `C:\Users\<usuario>\.mcreator\plugins` en Windows).
3. Reinicia MCreator.

> MCreator solo carga plugins de versiones declaradas en `supportedversions` (`plugin.json`). Si usas una snapshot
> distinta de `2026.3.41718`, añade su número de versión ahí o arranca MCreator con la variable de entorno
> `MCREATOR_PLUGINS_DEV=1`.

## Activar el servidor MCP

Hay dos modos, y el plugin elige uno solo:

| Modo | Cómo se activa | URL |
|------|----------------|-----|
| **Integrado (recomendado)** | *Preferences → Integrations → Enable MCP server* | `http://localhost:3025/mcp` (puerto configurable en la misma pantalla) |
| **Independiente** | Automático si el servidor integrado está desactivado | `http://localhost:3026/mcp` |

- En modo integrado, el botón **MCP** de la barra de estado de cada ventana elige el workspace sobre el que
  trabaja la IA.
- En modo independiente, la IA trabaja sobre la ventana de MCreator activa. El puerto se cambia con la variable
  de entorno `MCREATOR_MCP_PLUS_PORT`, y este modo se desactiva con `MCREATOR_MCP_PLUS_STANDALONE=false`.
- Ambos servidores solo escuchan en `localhost`.

### Conectar un cliente

Claude Code:

```bash
claude mcp add --transport http mcreator http://localhost:3025/mcp
```

Clientes con soporte HTTP (Cursor, VS Code…), en su configuración MCP:

```json
{ "mcpServers": { "mcreator": { "url": "http://localhost:3025/mcp" } } }
```

Claude Desktop (a través del puente `mcp-remote`):

```json
{ "mcpServers": { "mcreator": { "command": "npx", "args": ["-y", "mcp-remote", "http://localhost:3025/mcp"] } } }
```

## Tools que añade el plugin

| Tool | Para qué sirve |
|------|----------------|
| `workspace_overview` | Resumen del workspace en una llamada: metadatos, generador y versión de Minecraft, elementos por tipo, elementos bloqueados o que no compilan, recursos, idiomas y carpetas. |
| `workspace_settings_edit` | Cambiar nombre, versión, descripción, autor, licencia, web, créditos, logo/icono (incluso importando la imagen) y dependencias del mod. |
| `localization` | Idiomas y traducciones: listar, añadir/quitar idiomas, crear/editar/borrar claves y detectar textos sin traducir para traducirlos. |
| `texture_import` | Importar PNG/JPG desde base64, URL o ruta local para cualquier tipo de textura (armaduras con 2 capas incluidas), con redimensionado pixel-art y texturas animadas (`.mcmeta`). |
| `texture_edit` | Editar texturas: vista previa ampliada, recolorear (hue shift, colorize, tint, replace color), brillo/contraste/saturación, redimensionar, recortar, voltear, rotar, contorno, superposición, copiar, borrar, ver usos y crear animaciones a partir de frames. |
| `sound` | Importar sonidos OGG (con variaciones), categoría y subtítulo; listar, actualizar y eliminar. |
| `model_import` | Importar modelos JSON de Java Edition (Blockbench), OBJ+MTL (con mapeo de texturas), modelos Java de entidades y geometría Bedrock; listar y eliminar. |
| `structure` | Importar estructuras `.nbt` (también desde los mundos de prueba del cliente), listar y eliminar. |
| `mod_element_manage` | Listado detallado, duplicar elementos (muy útil para crear variantes), ver usos, carpetas (crear, mover, borrar), abrir un elemento en el editor y ver su icono. |
| `quality_audit` | Auditoría del mod con puntuación 0-100 e incidencias priorizadas con su solución (detalle abajo). |
| `workspace_history` | Puntos de restauración: listar, crear uno antes de cambios arriesgados y revertir el workspace. |
| `workspace_actions` | Guardar, regenerar todo el código, exportar el `.jar` distribuible y exportar el workspace a `.zip`. |
| `screenshot` | Captura de la ventana o de la pestaña actual para que la IA vea lo que ve el usuario. |
| `minecraft_reference` | Referencia de valores vanilla mapeados a los campos de MCreator (herramientas, armaduras, comida, bloques, menas, mobs, encantamientos) y guías de texturas, sonidos, nombres, progresión, checklist de calidad y flujo de trabajo. |

`quality_audit` revisa:

- metadatos del mod y logo;
- referencias rotas y elementos que no compilan;
- nombre visible, textura y pestaña creativa de cada elemento;
- items y bloques que no se pueden conseguir en survival;
- bloques que no sueltan nada al romperse;
- estadísticas desequilibradas frente a vanilla;
- recetas de crafteo en conflicto;
- procedimientos que nunca se ejecutan;
- texturas y sonidos sin usar;
- sonidos sin subtítulo;
- textos sin traducir.

### Tools oficiales de MCreator (siguen disponibles)

`query_workspace`, `mod_element_definition`, `get_mod_element_schema`, `pack_maker`, `create_texture`,
`create_armor_texture`, `tag`, `variable`, `datalist_entries`, `blockly_blocks`, `blockly_triggers`,
`blockly_templates`, `help_tips`, `project_files`, `project_file_edit`, `run_dir_files`, `read_class_source`,
`build`, `run_client`, `read_console`, `get_current_tab`, `find_references` y `find_broken_references` (esta
última solo en versiones recientes).

Con ellas la IA ya puede, por ejemplo, crear una espada: `mod_element_definition` con la acción `ADD`, el tipo
`tool` y `toolType: "Sword"`, o un set completo con `pack_maker`.

## Flujo de trabajo recomendado

1. Orientarse: `workspace_overview` y `quality_audit`.
2. Planificar la progresión: `minecraft_reference` (`progression`, `tool_tiers`, `armor`…).
3. Crear los recursos: `create_texture`, `texture_import`, `texture_edit`, `sound`, `model_import` y `structure`.
4. Crear los elementos: `get_mod_element_schema` y luego `mod_element_definition ADD`. Para familias de
   elementos, `pack_maker` o `mod_element_manage DUPLICATE` seguido de `PATCH`.
5. Añadir lógica con procedimientos: `blockly_*`.
6. Hacer el contenido obtenible: recetas, loot tables, tags y generación en el mundo.
7. Pulir: traducciones (`localization`), metadatos (`workspace_settings_edit`) y carpetas.
8. Verificar: `quality_audit`, `build`, `run_client`, `screenshot` y `texture_edit PREVIEW`.
9. Publicar: `workspace_actions EXPORT_MOD`. Antes de cambios masivos, `workspace_history CHECKPOINT`.

## Compilar

El plugin se compila contra las librerías de una instalación de MCreator 2026.3 (la carpeta que contiene
`lib/mcreator.jar`):

```bash
./gradlew build -PmcreatorPath=/ruta/a/MCreator
```

El resultado queda en `build/dist/mcreator-mcp-plus-<versión>.zip`. Para copiarlo directamente a
`~/.mcreator/plugins`:

```bash
./gradlew installPlugin -PmcreatorPath=/ruta/a/MCreator
```

También se puede definir `MCREATOR_PATH` como variable de entorno. Gradle debe ejecutarse con un JDK 25, por
ejemplo con `JAVA_HOME` apuntando a la carpeta `jdk` de MCreator.

## Estructura

```
src/main/java/com/mccreatormcp/
  McpPlusPlugin.java   punto de entrada: registra las tools o arranca el servidor independiente
  tools/               una clase por tool MCP
  util/                imágenes, entradas binarias, nombres, refresco de la UI y compatibilidad entre builds
src/main/resources/
  plugin.json          descriptor del plugin de MCreator
  mcpplus/reference/   contenido de minecraft_reference
```

`util/Compat.java` e `util/Io.java` existen porque algunas APIs internas de MCreator cambiaron de firma entre
builds de 2026.3. El plugin las resuelve en tiempo de ejecución, así que un mismo `.zip` funciona en todas las
versiones soportadas.

## Cómo se ha probado

- Compilación contra MCreator `master` y contra la build publicada `2026.3.41718`. Las referencias binarias a
  MCreator son idénticas en ambos casos.
- Carga del `.zip` con el `PluginLoader` real de MCreator y registro en el servidor MCP oficial:
  - 37 tools en `master`;
  - 36 tools en `2026.3.41718`.
- Peticiones MCP reales por HTTP (`initialize`, `tools/list`, `tools/call`) para validar los esquemas JSON.
- Ejecución de las tools sobre un workspace real (generador NeoForge 26.3 con elementos de ejemplo de los tests de
  MCreator), en ambas versiones, sin interfaz gráfica.
- **Sin probar:** las acciones que dependen de la interfaz o de Gradle (`screenshot`, abrir en el editor,
  `REGENERATE_CODE`, `EXPORT_MOD`, revertir el historial). Usan las mismas APIs que las acciones de MCreator, pero
  conviene probarlas en tu instalación.

## Licencia

MCreator se distribuye bajo GPL-3.0. Como este plugin enlaza con sus clases, lo más coherente es publicarlo también
bajo GPL-3.0.
