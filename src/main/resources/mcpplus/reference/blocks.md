# Vanilla block hardness / blast resistance

| Block                  | Hardness | Resistance | Tool / tier            |
|------------------------|---------:|-----------:|------------------------|
| Leaves                 | 0.2  | 0.2     | hoe/shears             |
| Glass                  | 0.3  | 0.3     | any (drops nothing)    |
| Netherrack             | 0.4  | 0.4     | pickaxe wood           |
| Dirt / sand            | 0.5  | 0.5     | shovel                 |
| Gravel                 | 0.6  | 0.6     | shovel                 |
| Wool                   | 0.8  | 0.8     | shears                 |
| Stone                  | 1.5  | 6       | pickaxe wood           |
| Cobblestone            | 2    | 6       | pickaxe wood           |
| Planks / logs          | 2    | 3 / 2   | axe                    |
| Ore (stone variant)    | 3    | 3       | pickaxe by ore tier    |
| Deepslate ore          | 4.5  | 3       | pickaxe by ore tier    |
| End stone              | 3    | 9       | pickaxe wood           |
| Iron / diamond block   | 5    | 6       | pickaxe stone / iron   |
| Ancient debris         | 30   | 1200    | pickaxe diamond        |
| Obsidian               | 50   | 1200    | pickaxe diamond        |
| Bedrock                | -1 (unbreakable) | 3600000 | -              |

## Mapping to the MCreator `block` element

- `hardness`, `resistance`, `destroyTool` (pickaxe, axe, shovel, hoe), `breakHarvestLevel`/tier
  and `requiresCorrectTool` control how the block is mined; storage blocks of a material need the same
  tier as its ore.
- Ores: use `generateFeature` with `blocksToReplace` (stone and deepslate), drop the raw material via
  `customDrop` or a loot table, give XP (`xpAmountMin/Max`, e.g. 0-2 like coal, 3-7 like diamond) and add a
  deepslate variant for quality.
- Storage block of 9 ingots needs two recipes (9 ingots -> block, block -> 9 ingots).
