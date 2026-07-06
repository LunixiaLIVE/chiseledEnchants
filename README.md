<div align="center">

# 📖 chiseledEnchants

### Guarantee the enchants you want — stock chiseled bookshelves and target the enchanting table.

![](https://img.shields.io/badge/Fabric-DBA463?style=for-the-badge&logoColor=white)&nbsp;![](https://img.shields.io/badge/NeoForge-F16436?style=for-the-badge&logoColor=white)&nbsp;

[![](https://img.shields.io/badge/Download_on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/project/chiseledenchants)&nbsp;<sub>CurseForge — coming soon</sub>

![](https://img.shields.io/badge/Minecraft-26.x-62B47A?style=flat-square) ![](https://img.shields.io/badge/Server--side-vanilla_clients_OK-8E44AD?style=flat-square) ![](https://img.shields.io/badge/Fabric_API-required_on_Fabric-4A90D9?style=flat-square) ![](https://img.shields.io/badge/License-MIT-blue?style=flat-square) ![](https://img.shields.io/badge/Status-feature--complete-brightgreen?style=flat-square)

</div>

---

> [!NOTE]
> **Feature-complete, pre-release (1.0.0).** The full modded-table takeover is in — separate XP/lapis-block
> economy, the vanilla roll suppressed, blank-on-error, live status bar. In testing ahead of a public
> release. Per-version code and changelog live on the [`multi_*`](#-versions--downloads) branches.

## ✨ What it does

Vanilla enchanting is a slot machine. chiseledEnchants adds a **separate, self-contained** enchanting
path driven by the bookshelves around your table:

- **Regular bookshelves → a vanilla table.** Untouched — power and random enchants, exactly like vanilla.
- **The crafted "Chiseled Enchanter" + chiseled bookshelves → targeting.** Craft the special table
  (dragon head + netherite + obsidian — its rarity-colored name can't be forged in an anvil, so it's
  craft-only), ring it with chiseled bookshelves, and stock them with **single-enchant books**. You get
  *only* the enchants you aimed for — the vanilla roll is suppressed.
- **Only max-level books count.** A book counts toward a guarantee only at that enchant's **max** level
  (Sharpness V, Unbreaking III, Mending I). Below-max books are flagged and ignored — nothing is averaged,
  so the table can only replicate a tier you already own, never launder cheap books into a higher one.
- **6 max-level books of an enchant = a 100% guarantee** of it; fewer = a proportional chance to land, and
  everything that lands applies at its **max level**.
- **One option, real costs.** A single enchant option unlocks for a flat **lapis-block cost** (14 blocks,
  not gems); XP scales with what you guarantee (a maxed enchant ≈ 10 levels). **Surplus lapis blocks
  protect your books** from being consumed — a full stack reaches the cap.
- **Conflicts resolve by book count.** Sharpness vs. Smite → whoever has more books wins; an equal tie
  blanks the table on purpose so you can inspect it and add a book to break it.
- **A live status bar** above the table reads **🟢 green = ready** / **🔴 red = a problem** (mixed shelves,
  a tie, or a below-max book) — with the reason.
- **Treasure enchants** (Mending, Soul Speed, …) are **guarantee-only** — never from a random roll — with a
  per-enchant whitelist and curse/treasure master switches.
- **`/cench`** summarizes a table's setup, **previews** what you'll get, **finds** your books (colored
  particle threads to each shelf), and **reloads** config live.
- **Server-side** — a **vanilla client** can join a modded server and it just works. Runs in single-player too.

Books are sourced the intended way — **villager librarian trades**. No new blocks or items; it reuses
vanilla chiseled bookshelves and lapis blocks.

## 📖 In-game guide book

Players don't need a wiki. **Rename a book &amp; quill to `chiseledEnchants` in an anvil** to receive a
detailed in-game guide — how the table works, the max-books rule, costs, the status bar, and the commands —
written from **your server's live config**, so every number matches your actual settings. It's a normal
signed book, so it renders on a **vanilla client** with no resource pack.

## ⚙️ For server admins

Everything is tunable, and it's meant to be. The config lives at **`config/chiseledenchants.json`** and
**hot-reloads with `/cench reload`** — no restart needed. All chance and protection values are **0–1
fractions** (e.g. `0.5` = 50%). Here's the full file with defaults (grouped and annotated for readability;
the real file is plain JSON):

```jsonc
{
  // ── Guarantee strength ──
  "booksForFullChance": 6,          // max-level books of an enchant for a 100% chance to land

  // ── The special "Chiseled Enchanter" table ──
  "requireSpecialTable": true,      // gate targeting to the crafted table (false = any table + chiseled shelves)
  "specialTableName": "Chiseled Enchanter",
  "craftOnlyTable": true,           // require the recipe's rarity-colored name (an anvil can't forge it)
  "recipeReplacesBook": "minecraft:dragon_head",        // the recipe's book slot
  "recipeReplacesDiamond": "minecraft:netherite_ingot", // the 2 diamond slots
  "recipeReplacesObsidian": "minecraft:obsidian",       // the 4 obsidian slots
  "tableOpenNotice": "This table runs on lapis blocks", // green status-bar text (blank = the table's name)

  // ── Scan geometry (book capacity only; vanilla enchanting power is untouched) ──
  "scanLayers": 2,                  // vertical layers scanned, from table level up
  "scanRadius": 2,                  // horizontal reach of the shelf scan

  // ── Economy ──
  "costOfMaxEnchant": 10,           // XP levels a maxed enchant costs (scales by level)
  "xpFromFirstLevels": true,        // charge cheap "first levels" points vs. levels off the top
  "lapisCost": 14,                  // lapis BLOCKS to unlock the option; surplus buys book protection
  "bookConsumeChance": 0.5,         // chance a landed enchant eats one of its source books (0–1)
  "bookProtectionEnabled": true,    // whether surplus lapis can protect books at all
  "protectionPerBlock": 0.02,       // protection per surplus lapis block, 0–1 (0.02 = 2%)
  "maxProtection": 1.0,             // ceiling on protection, 0–1 (set below 1.0 to never fully protect)

  // ── Rules ──
  "resolveConflicts": true,         // more-books-wins, tie blanks (false = any conflict blanks)
  "allowTreasureEnchants": true,    // treasure enchants (Mending, Soul Speed…) may be guaranteed
  "allowCurses": false,             // allow curses to be applied
  "allowBookEnchanting": false,     // let the table enchant a book item
  "allowConflictingOnBook": false,  // permit mutually-conflicting enchants on a book
  "enchantWhitelist": {},           // auto-filled from the live registry on first start; flip any to false

  // ── In-game guide book ──
  "guideEnabled": true,
  "guideKeyword": "chiseledEnchants", // anvil-rename a book & quill to this to receive the guide
  "guideAnvilCost": 1,              // anvil XP-level cost to make the guide

  // ── /cench find particle trace ──
  "particleRepeats": 10,
  "particleDurationTicks": 20,
  "particleFromTable": true,        // pulses flow table → shelves (false = shelves → table)

  // ── Community links (blank = shown as "coming soon") ──
  "linkGithub": "https://github.com/LunixiaLIVE/chiseledEnchants",
  "linkModrinth": "https://modrinth.com/project/chiseledenchants",
  "linkCurseforge": "",             // still in the works
  "linkDiscord": ""
}
```

> [!TIP]
> `enchantWhitelist` fills itself in on first launch with **every** enchant (modded included) set to `true`
> — flip any to `false` to bar it. Edit the file and run `/cench reload`; the next guide book made reflects
> the new numbers automatically.

### 🔓 What counts as a targeting table — `requireSpecialTable` + `craftOnlyTable`

These two options decide which enchanting tables do targeted enchanting, from a strict crafted gate to
wide open. They're the part admins most often ask about, so here's the whole picture:

| `requireSpecialTable` | `craftOnlyTable` | What works as a targeting table | Good for |
|:---:|:---:|---|---|
| `true` | `true` *(default)* | **Only the crafted "Chiseled Enchanter."** The recipe stamps a rarity-colored name a vanilla anvil can't reproduce, so nothing else qualifies. | Keeping targeted enchanting behind a real crafting cost — a deliberate build and a progression goal. |
| `true` | `false` | Any enchanting table **anvil-renamed** to the special name (`specialTableName`); the color is ignored. | Convenience — let players upgrade tables they already have, no recipe grind. Just an anvil + a level or two. |
| `false` | *(ignored)* | **Any** enchanting table that has chiseled bookshelves around it. | Fully open — build- or minigame-focused servers where you don't want a gate at all. |

> [!NOTE]
> `craftOnlyTable` **only matters when `requireSpecialTable` is `true`** — it's the knob for *how you obtain*
> the special table (craft it vs. rename any table). With `requireSpecialTable: false` there is no special
> table at all, so `craftOnlyTable` is ignored entirely.

## 📦 Versions &amp; downloads

> [!NOTE]
> This repo uses a **branch-per-version** layout. This `main` branch is **documentation only** — the code
> for each Minecraft version lives on its own branch, each with an independent history.

| Branch | Minecraft | Loaders | Dependencies | Design |
|:------:|:---------:|:-------:|:------------:|:---:|
| [`multi_26.2`](https://github.com/LunixiaLIVE/chiseledEnchants/tree/multi_26.2) | 26.2.x | Fabric · NeoForge | Fabric API *(Fabric only)* | — |
| [`multi_26.1`](https://github.com/LunixiaLIVE/chiseledEnchants/tree/multi_26.1) | 26.1, 26.1.1, 26.1.2 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/chiseledEnchants/blob/multi_26.1/DESIGN.md) |
| [`plugin`](https://github.com/LunixiaLIVE/chiseledEnchants/tree/plugin) | 26.1.x · 26.2.x | Paper 🚧 | — | — |

> [!TIP]
> Every `multi_*` branch builds **one universal jar** that runs on **both** Fabric and NeoForge (per-loader
> `-fabric` / `-neoforge` jars are produced too). Fully self-contained — **no extra library mods to install**.

> [!WARNING]
> 🚧 The **Paper plugin** (`plugin` branch) is **in development** — not yet released. It's a separate
> Bukkit/Paper build (one jar for 26.1.x + 26.2.x); the mod (Fabric/NeoForge) above is the released target.

<details>
<summary>🛠️ <b>Building from source</b></summary>

Each code branch is a self-contained Gradle project:

```bash
git clone -b multi_26.1 https://github.com/LunixiaLIVE/chiseledEnchants.git
cd chiseledEnchants
./gradlew build
```

The universal jar lands in `build/libs/` — drop it into your `mods/` folder on either loader.
</details>

## 📄 License

Released under the **MIT License**.

<div align="center"><sub>⛏️ Part of the <a href="https://github.com/LunixiaLIVE">LunixiaLIVE</a> quality-of-life mod suite.</sub></div>
