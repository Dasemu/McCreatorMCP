# Vanilla food values (Java Edition)

`nutritionalValue` = hunger points restored (each drumstick = 2). `saturation` in MCreator is the
saturation *modifier*; the saturation gained is nutrition x modifier x 2.

| Food            | Nutrition | Saturation modifier |
|-----------------|----------:|--------------------:|
| Apple           | 4  | 0.3 |
| Baked potato    | 5  | 0.6 |
| Bread           | 5  | 0.6 |
| Carrot          | 3  | 0.6 |
| Cookie          | 2  | 0.1 |
| Melon slice     | 2  | 0.3 |
| Sweet berries   | 2  | 0.1 |
| Raw beef / pork | 3  | 0.3 |
| Cooked chicken  | 6  | 0.6 |
| Cooked cod      | 5  | 0.6 |
| Cooked salmon   | 6  | 0.8 |
| Steak / cooked porkchop | 8 | 0.8 |
| Mushroom stew   | 6  | 0.6 |
| Rabbit stew     | 10 | 0.6 |
| Pumpkin pie     | 8  | 0.3 |
| Golden carrot   | 6  | 1.2 |
| Golden apple    | 4  | 1.2 |

## Advice

- Raw foods: low saturation (0.1-0.3); cooked foods: 0.6-0.8; magic foods: 1.2 plus potion effects.
- Stews/bowls should not stack (stackSize 1) and return a bowl.
- Default eating time is 32 ticks (`useDuration`); fast snacks 16.
