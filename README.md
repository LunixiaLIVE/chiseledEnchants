# chiseledEnchants

**Targeted enchanting for Minecraft.** Stock chiseled bookshelves with single-enchant books and *choose* exactly what your gear gets — no more rerolling the enchanting table and praying.

- **Loaders:** Fabric + NeoForge (one jar, standalone — no Architectury API at runtime)
- **Minecraft:** 26.1.x · 26.2.x
- **Side:** server-side only. Vanilla clients connect and play with **no client mod and no resource pack** — there are no new registered items, so nothing desyncs.
- **License:** MIT

---

## How it works

1. **Craft the "Chiseled Enchanter."** It's a special enchanting table — same recipe shape as vanilla, but with dragon head + netherite + obsidian. Its name carries a rarity color a vanilla anvil can't reproduce, so it can only be obtained by crafting (it's in your recipe book). A normal enchanting table stays 100% vanilla.
2. **Ring it with chiseled bookshelves** in the usual power-provider spots (one-block air gap, just like vanilla enchanting power).
3. **Stock the shelves with single-enchant books** for whatever you want to guarantee.
4. **Enchant.** The table applies the enchants *you* stocked instead of a random roll.

### Only max-level books count

This is the core rule: a book only counts if it's at that enchant's **maximum** level — Sharpness **V**, Unbreaking **III**, Mending **I**, Protection **IV**, and so on. Below-max books are **flagged and ignored**, never averaged in. That means the table can only ever *replicate* an enchant you already own at max — it can't launder cheap low-level books up into a tier you never earned.

### Chance to land

Per enchant: `chance = max-level books ÷ booksForFullChance` (default **6 books = 100%**). Every enchant that lands does so at its **max level**. Stock 6 Sharpness V books → guaranteed Sharpness V; stock 3 → roughly a coin-flip.

### One option, flat cost

The table offers a single enchant option (always max level). It unlocks once the lapis slot holds the required **lapis blocks** — `lapisCost`, default **14** (blocks, *not* gems). Lapis blocks beyond the cost buy **book protection**: each adds `protectionPerBlock`% (default 2%) up to `maxProtectionPercent` (default 100%). So a full 64-stack = 14 to cast + 50 → 100% protection.

- **XP:** about `costOfMaxEnchant` levels (default 10) per maxed enchant that lands.
- **Books:** each landed enchant has a `bookConsumeChance` (default 50%) to eat one of its source books, reduced by protection. Turn the whole protection mechanic off with `bookProtectionEnabled: false`.

### Conflicts

With `resolveConflicts` on (default), stocking conflicting enchants (e.g. Sharpness + Smite) resolves to **whichever has more books** — the loser is dropped. An **equal-book tie blanks the table** on purpose, so you can inspect it (`/cench table`) and add a book to break the tie rather than get handed the wrong one. Turn it off to make *any* conflict blank the options instead.

### Live status bar

A boss bar above the table tells you the setup's state at a glance:

- 🟢 **Green** — ready.
- 🔴 **Red** — a problem, with the reason: mixed chiseled + regular shelves, a conflicting-enchant tie, or a below-max book that won't count.

---

## Commands

`/chiseledenchants` (alias `/cench`) — look at an enchanting table, then:

| Command | What it does |
| --- | --- |
| `/cench` | Summarize the chiseled setup of the table you're looking at |
| `/cench preview` | What the table will apply to the item in your main hand, with cost |
| `/cench table` | What the shelves are configured to apply (item-agnostic) |
| `/cench find <enchant>` | Trace colored particle threads from the table to each shelf holding it |
| `/cench about` | Mod info + links |
| `/cench reload` | Reload the config from disk (op / gamemaster) |

## The guide book

Rename a **book & quill** to `chiseledEnchants` in an anvil to receive an in-game guide written for this server's exact settings (it reads the live config). Fully server-authored, so it renders on a vanilla client.

---

## Configuration

Config lives at `config/chiseledenchants.json` and hot-reloads with `/cench reload`. Highlights:

| Key | Default | Meaning |
| --- | --- | --- |
| `booksForFullChance` | 6 | Max-level books for a 100% land chance |
| `lapisCost` | 14 | Lapis blocks to unlock the option; surplus buys protection |
| `costOfMaxEnchant` | 10 | XP levels per maxed enchant |
| `bookConsumeChance` | 0.5 | Base chance a landed enchant eats a source book |
| `bookProtectionEnabled` | true | Whether surplus lapis can protect books |
| `protectionPerBlock` / `maxProtectionPercent` | 2.0 / 100 | Protection per surplus block, and its ceiling |
| `resolveConflicts` | true | More-books-wins (tie blanks) vs. any-conflict-blanks |
| `requireSpecialTable` / `craftOnlyTable` | true / true | Gate the system to the crafted Chiseled Enchanter |
| `allowCurses` / `allowTreasureEnchants` | false / true | Master switches |
| `allowBookEnchanting` | false | Let the table enchant a book item |
| `enchantWhitelist` | *(all true)* | Per-enchant on/off, auto-filled from the live registry |
| `scanLayers` / `scanRadius` | 2 / 2 | How much of the surrounding shelves the mod reads |

Recipe ingredients, guide keyword, particle trace, and community links are configurable too.

---

## Building

```bash
./gradlew universalJar
```

Produces a single jar in `build/libs/` that loads on both Fabric and NeoForge.

## Links

- **GitHub:** https://github.com/LunixiaLIVE/chiseledEnchants

Built by [LunixiaLIVE](https://github.com/LunixiaLIVE).
