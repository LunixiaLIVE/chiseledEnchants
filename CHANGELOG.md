# Changelog — chiseledEnchants (Paper) (`paper`)

Paper plugin port of the [chiseledEnchants](https://github.com/LunixiaLIVE/chiseledEnchants) mod.
Minecraft **26.1.x + 26.2.x** · single jar (Bukkit API is cross-version) · server-side.

## [0.1.0] — pre-release
### Added
- Project against Paper API `26.1.2.build.72-stable` (Java 25) → `chiseledenchants-<version>_MC-<mc>-paper.jar`;
  one jar for 26.1.x + 26.2.x. Verified loading on a real Paper 26.1.2 server.
- Plugin entrypoint, `config.yml` mirroring the mod's options, and `/chiseledenchants` (`/cench`) with
  `about` / `reload` / `guide`.
- **Targeted enchanting core** (`Targeting` + `ShelfScanner`), ported from the mod:
  - Modded-table detection (special-named enchanting table + LIGHT_PURPLE colour when `craftOnlyTable`, with
    chiseled shelves in range) and the max-level-books-only rule.
  - Chance-to-land (`booksForFullChance`), conflict resolution (more books wins; equal-book tie blanks),
    curse/treasure gating, per-enchant whitelist, and the deterministic land roll.
- **`EnchantListener`**: `PrepareItemEnchantEvent` sets the top offer + drives the green/red status boss bar;
  `EnchantItemEvent` applies the resolved max-level enchants and charges the **lapis-GEM** + XP economy with
  surplus-gem book protection, then rolls book consumption. Honours `allowBookEnchanting`.
- **Recipe** registration for the crafted "Chiseled Enchanter".

> ⚠️ First cut — compiles and loads clean, but the in-game enchant flow (offer clickability with no vanilla
> bookshelf power, gem/XP charging, inventory sync) still needs live testing. The scan uses a box (the mod's
> exact air-gap geometry and the full guide pages are later refinements).
