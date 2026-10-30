# Vanilla armor (Java Edition 1.21+)

| Material  | Durability multiplier (`maxDamage`) | Helmet | Chest | Legs | Boots | Total | Toughness | Knockback res. | Enchantability |
|-----------|------:|---:|---:|---:|---:|---:|---:|---:|---:|
| Leather   | 5     | 1  | 3  | 2  | 1  | 7  | 0  | 0   | 15 |
| Chainmail | 15    | 2  | 5  | 4  | 1  | 12 | 0  | 0   | 12 |
| Iron      | 15    | 2  | 6  | 5  | 2  | 15 | 0  | 0   | 9  |
| Gold      | 7     | 2  | 5  | 3  | 1  | 11 | 0  | 0   | 25 |
| Diamond   | 33    | 3  | 8  | 6  | 3  | 20 | 2  | 0   | 10 |
| Turtle    | 25    | 2  | -  | -  | -  | -  | 0  | 0   | 9  |
| Netherite | 37    | 3  | 8  | 6  | 3  | 20 | 3  | 0.1 | 15 |

Durability of each piece = base x multiplier, with base helmet 11, chestplate 16, leggings 15, boots 13
(e.g. iron chestplate = 16 x 15 = 240).

## Mapping to the MCreator `armor` element

- `maxDamage` = durability multiplier, `damageValueHelmet/Body/Leggings/Boots` = armor points per piece,
  `toughness`, `knockbackResistance` (0.1 per piece for netherite), `enchantability`.
- `armorTextureFile` = armor layer texture base name (`<name>_layer_1.png` and `<name>_layer_2.png`,
  64x32), import with texture_import textureType armor or create with create_armor_texture.
- Item textures: `textureHelmet`, `textureBody`, `textureLeggings`, `textureBoots`.

## Balancing advice

- Player armor points cap at 30 and toughness reduces damage from strong hits; keep totals at or below 20
  unless the set is end-game.
- Give special sets a trade-off or a set bonus via procedures instead of raw stats.
