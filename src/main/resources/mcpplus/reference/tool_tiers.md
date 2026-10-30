# Vanilla tool tiers (Java Edition 1.21+)

| Tier      | Durability | Mining speed | Enchantability | Breaks blocks needing (blockDropsTier) |
|-----------|-----------:|-------------:|---------------:|----------------------------------------|
| Wood      | 59         | 2            | 15             | WOOD (stone, coal ore)                  |
| Stone     | 131        | 4            | 5              | STONE (iron, copper, lapis ore)         |
| Iron      | 250        | 6            | 14             | IRON (gold, diamond, redstone, emerald) |
| Diamond   | 1561       | 8            | 10             | DIAMOND (obsidian, ancient debris)      |
| Gold      | 32         | 12           | 22             | GOLD (same as wood)                     |
| Netherite | 2031       | 9            | 15             | NETHERITE (everything)                  |

## In-game attack damage / attack speed

| Tool    | Wood     | Stone    | Iron     | Diamond  | Gold     | Netherite |
|---------|----------|----------|----------|----------|----------|-----------|
| Sword   | 4 / 1.6  | 5 / 1.6  | 6 / 1.6  | 7 / 1.6  | 4 / 1.6  | 8 / 1.6   |
| Axe     | 7 / 0.8  | 9 / 0.8  | 9 / 0.9  | 9 / 1.0  | 7 / 1.0  | 10 / 1.0  |
| Pickaxe | 2 / 1.2  | 3 / 1.2  | 4 / 1.2  | 5 / 1.2  | 2 / 1.2  | 6 / 1.2   |
| Shovel  | 2.5 / 1.0| 3.5 / 1.0| 4.5 / 1.0| 5.5 / 1.0| 2.5 / 1.0| 6.5 / 1.0 |
| Hoe     | 1 / 1.0  | 1 / 2.0  | 1 / 3.0  | 1 / 4.0  | 1 / 1.0  | 1 / 4.0   |

## Mapping to the MCreator `tool` element

- `usageCount` = durability, `efficiency` = mining speed, `enchantability` = enchantability.
- `damageVsEntity` = attack damage shown in game (the template subtracts the player's base 1 damage).
- `attackSpeed` = attack speed shown in game (the template subtracts the base 4).
- `blockDropsTier` = which blocks drop when mined (see table).
- `repairItems` = items usable in an anvil to repair (use the tier material, e.g. the ingot).

## Balancing advice

- Place a new material between two vanilla tiers and keep every stat in that band, e.g. a material between
  iron and diamond: durability 500-1000, speed 6.5-7.5, sword damage 6.5, enchantability 10-14.
- Trade-offs make materials interesting: high speed + low durability (like gold), or high durability + low
  enchantability.
- Going above netherite should require end-game resources and a costly recipe (e.g. smithing upgrade).
