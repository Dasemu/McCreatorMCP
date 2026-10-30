# Vanilla mob stats (Java Edition, normal difficulty)

| Mob           | Health | Attack | Armor | Speed (movement attr.) | Notes                   |
|---------------|-------:|-------:|------:|-----------------------:|-------------------------|
| Chicken       | 4   | -   | 0 | 0.25 | passive                    |
| Pig / Cow     | 10  | -   | 0 | 0.25 / 0.2 | passive              |
| Sheep         | 8   | -   | 0 | 0.23 | passive                    |
| Wolf (tamed)  | 40  | 4   | 0 | 0.3  | neutral                    |
| Zombie        | 20  | 3   | 2 | 0.23 | hostile, burns in daylight |
| Skeleton      | 20  | 2-4 (bow) | 0 | 0.25 | ranged               |
| Creeper       | 20  | explosion | 0 | 0.25 |                       |
| Spider        | 16  | 2   | 0 | 0.3  | climbs walls               |
| Enderman      | 40  | 7   | 0 | 0.3  | neutral, teleports         |
| Blaze         | 20  | 6 (fireballs 5) | 0 | 0.23 | flying          |
| Iron golem    | 100 | 7.5-21.5 | 0 | 0.25 | knockback resistance 1 |
| Ravager       | 100 | 12  | 0 | 0.3  | mini boss                  |
| Wither        | 300 | 8-15 | 4 | 0.6 (flying) | boss                |
| Warden        | 500 | 30  | 0 | 0.3  | boss-like                  |
| Ender dragon  | 200 | 10  | 0 | -    | boss                       |
| Player        | 20  | 1   | 0 | 0.1  |                            |

## Advice for `livingentity` elements

- Match a vanilla mob of similar role: early hostile mobs 15-25 health, 2-4 damage.
- Bosses should have a boss bar, high knockback resistance and drop a unique reward.
- Always add a spawn egg, natural spawning (biomes, weight, group size) or a summoning method,
  a loot table, sounds (ambient/hurt/death) and a good model and texture.
