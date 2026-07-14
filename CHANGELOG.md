# Changelog — chiseledEnchants (`multi_26.1`)

Minecraft **26.1, 26.1.1, 26.1.2** · Fabric + NeoForge · standalone (no extra library mods).

## [1.0.0]
### Added
- **In-game admin console** under `/cench admin`: `reload`, `reset`, `get <setting>`, `set <setting> [value]`,
  and `whitelist <enchant> <true|false>` — all op/gamemaster-gated, with tab completion for settings, values,
  enchant ids, and (for the recipe item settings) every registered item id. Edits the config file live; `set`
  with no value clears an item setting to an empty slot.
- **Recipe hot-reload.** Recipe edits apply on `/reload` *and* `/cench admin reload` — the crafting **recipe
  book** included — with no relog and no datapack reload.
- **`useBlocks`** — the modded table can take lapis **blocks** (default) or **gems**; costs and the status bar
  adapt to the chosen unit.
- **`smallBooksChanceBoost`** — below-max books can add to the land *chance* (never the level) when a max book
  is present.
- **`recipeReplacesEmptySlots`** — fill the two empty recipe corners to make the crafted table pricier.
- **Mod list info** for Mod Menu (Fabric) and the NeoForge mods screen: icon, description, license, credits,
  a Website button (mod-suite hub) and Issues button, plus Discord / Modrinth / CurseForge links.

### Changed
- Status boss bar now names the required currency dynamically — "lapis blocks" or "lapis gems" per
  `useBlocks` — via a `{lapis}` token in `tableOpenNotice` (existing configs migrate automatically).
- `/cench about` trimmed to GitHub + Discord for cleaner output.

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
