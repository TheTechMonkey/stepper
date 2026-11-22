# Stepper

A Fabric mod for Minecraft 26.3 that adds a **small stair** for every full solid block in the game, from vanilla and from every other mod you have installed.

A small stair is two steps, each 1/3 of a block tall, so the whole piece is 2/3 of a block tall. It places, rotates, forms corners and flips upside down exactly like vanilla stairs, and it can be waterlogged.

## How it works

- **Every block, automatically.** At startup Stepper creates a small stair for each plain, solid, full-cube block: stone, planks, wool, ores, terracotta, modded bricks and so on. It skips chests and machines, see-through blocks (glass, leaves), odd shapes and unbreakable blocks.
- **Looks like the base block.** The model is cut from the base block's own model while the world renders, so textures, resource packs, random texture variants, overlays and biome tints (like grass) all carry over.
- **Behaves like the base block.** Same sound, map color, hardness, blast resistance and light level. It mines at the base block's speed and only drops when you use a tool that could harvest the base block.
- **Names** come from the base block: "Small Stone Stairs", "Small Oak Planks Stairs".

## Recipes

- **Crafting:** 3 base blocks in an L shape make 6 small stairs.
  ```
   #
  ##
  ```
- **Stonecutter:** 1 base block makes 2 small stairs.

Recipes are generated as a datapack in the world's `datapacks/stepper_generated` folder when the world or server starts, and it's enabled automatically. It's only rewritten when your list of block mods changes.

All small stairs are in their own creative tab, "Stepper: Small Stairs".

## Multiplayer

Install it on the server and every client. Because the list of small stairs depends on which block mods are installed, **the server and all players need the same block mods**, or players can't join. A shared modpack (PackWiz, Modrinth, CurseForge) takes care of that.

## Known limitations (1.0.0)

- In inventories and in your hand, a small stair item shows its base block's full-block icon. Placed small stairs show the real shape.
- Small stairs from flammable blocks (planks, wool) don't burn yet.

## Building

```
gradlew build
```
The jar is in `build/libs`.

## Credits

The shape matches [Small Stairs](https://modrinth.com/mod/small-stairs119) by pitan76 (MIT), which is based on Sayo's original SmallStairs mod. Stepper is a separate implementation that generates small stairs for every block instead of a fixed list.

## License

MIT
