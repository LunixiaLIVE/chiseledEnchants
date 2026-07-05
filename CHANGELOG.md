# Changelog — chiseledEnchants (`multi_26.1`)

Minecraft **26.1, 26.1.1, 26.1.2** · Fabric + NeoForge · standalone (no Architectury API at runtime).

## [0.9.0] — pre-release
### Added
- **Targeted enchanting.** Ring an enchanting table with chiseled bookshelves and stock them with
  single-enchant books to choose exactly what lands — the vanilla random roll is suppressed.
- **The crafted "Chiseled Enchanter"** — a specially-named enchanting table (dragon head + netherite +
  obsidian) whose rarity-colored name a vanilla anvil can't reproduce, so it's craft-only. Normal tables
  stay fully vanilla (`requireSpecialTable`, `craftOnlyTable`).
- **Modded-table takeover:** its own XP + lapis-block economy, applies only the stocked enchants, and
  blanks the options on any setup error.
- **Max-level books only** count toward a guarantee (Sharpness V, Unbreaking III, Mending I). Below-max
  books are flagged and ignored — no level averaging, so the table can't launder low books into a higher
  tier you never earned.
- Land chance = max-level books ÷ `booksForFullChance` (default 6 = 100%); every landed enchant applies at
  its max level.
- **Single enchant option** unlocked by a flat lapis-block cost (`lapisCost`, default 14); lapis beyond the
  cost buys book protection (`protectionPerBlock` up to `maxProtectionPercent`; toggle
  `bookProtectionEnabled`).
- XP cost scaling (`costOfMaxEnchant`, front- or back-loaded via `xpFromFirstLevels`) and per-enchant book
  consumption (`bookConsumeChance`).
- **Conflict resolution** (`resolveConflicts`): more books wins; an equal-book tie blanks the table so it
  can be inspected and fixed. Optional conflicting enchants on books (`allowConflictingOnBook`).
- **Live status boss bar** above the table: green = ready, red = a problem (mixed shelves, a tie, or a
  below-max book), with the reason.
- Per-enchant whitelist auto-filled from the live enchant registry; curse and treasure master switches;
  optional book enchanting (`allowBookEnchanting`).
- In-game **guide book** — anvil-rename a book & quill to `chiseledEnchants` — generated from the live
  config so it always reflects the server's settings.
- **`/chiseledenchants`** (`/cench`): setup summary, held-item preview, shelf preview, particle `find`,
  `about`, and op-only `reload`.
- Lapis slot accepts only lapis **blocks** at the modded table, with shift-click routing.
- Config `config/chiseledenchants.json` with the full schema; hot-reloads with `/cench reload`.
- Single **universal jar** for Fabric + NeoForge (per-loader jars produced too); standalone at runtime.
