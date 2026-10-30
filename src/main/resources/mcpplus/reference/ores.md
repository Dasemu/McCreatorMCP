# Vanilla ore generation (Java Edition 1.18+)

| Ore      | Height range (Y)      | Most common at | Vein size | Notes                               |
|----------|-----------------------|----------------|----------:|-------------------------------------|
| Coal     | 0 to 320              | 96             | 17        | rarer below Y 0                      |
| Copper   | -16 to 112            | 48             | 10 (20 in dripstone caves) |                     |
| Iron     | -64 to 72 and 80 to 384 | 16 and 232   | 9         | also small veins everywhere          |
| Lapis    | -64 to 64             | 0              | 7         |                                      |
| Gold     | -64 to 32             | -16            | 9         | extra in badlands                    |
| Redstone | -64 to 15             | -64 (more deep)| 8         |                                      |
| Diamond  | -64 to 16             | -64 (more deep)| 4-12      | reduced air exposure                 |
| Emerald  | -16 to 320            | 232            | 1-3       | mountains only                       |
| Ancient debris | 8 to 119 (Nether) | 15          | 1-3       | blast resistant                      |

## Mapping to the MCreator `block` element

- `generateFeature` = true, `blocksToReplace` = stone (and deepslate for a deepslate ore variant),
  `frequencyPerChunks` = veins per chunk (iron ~10-20, diamond ~4-7), `frequencyOnChunk` = vein size,
  `minGenerateHeight` / `maxGenerateHeight` = Y range, restrict dimensions and biomes as needed.
- Rarer and deeper should mean more valuable. Avoid generating more often than iron unless it is a common
  building material.
