# chiseledEnchants — Design (finalized)

Targeted enchanting. Vanilla enchanting is a slot machine; chiseledEnchants adds a **separate,
self-contained** enchanting path that lets you **guarantee** the enchants you want by stocking
chiseled bookshelves with single-enchant books.

This document is the agreed spec. Implementation is in progress (see **Status** at the bottom).

---

## 1. Two kinds of table (decided by the shelves around it)

- **Regular bookshelves → a vanilla table.** 100% untouched — power, level, lapis, XP, and the
  random roll all behave exactly like vanilla. chiseledEnchants does nothing here.
- **Chiseled bookshelves → a "modded table."** This is our system end-to-end: **no vanilla random
  roll**, only the enchants you targeted, on our own cost/economy.
- **Both types on one table → error.** The three enchant options blank out (see §7); `/cench`
  explains why. You build one kind of table or the other, never a mix.

Chiseled bookshelves **do not provide vanilla enchanting power** (the mod removes them from that
role). The two systems are fully separate to avoid player confusion.

## 2. Scanning shelves (config geometry)

The mod reads chiseled shelves in a configurable volume around the table:
- `scanLayers` (default 2) — vertical layers, table level up.
- `scanRadius` (default 2) — horizontal reach (min 2; the block adjacent to the table is the air gap).

Books pool **across all reachable shelves** — 6 Sharpness V books anywhere in range count together.
No "power" gate: even a single chiseled shelf enables the modded table.

## 3. The guarantee (per stocked enchant)

- **Chance to land** = `min(1, books ÷ booksForFullChance)` (default 6). Levels don't matter for the
  chance — 6 books of an enchant = 100% it lands. A full shelf (6 slots) = one guaranteed enchant.
- **Level** = `round( average of your best `slotsForLevelAverage` books, empty slots counting as 0 )`,
  clamped to the enchant's max. Gaps and low books drag it down.

## 4. The three slots (level tiers, per enchant max)

The top (30-level-style) slot allows up to the enchant's max; the two cheaper slots cap the level to
a **random-inclusive range** from `slotTiers`, keyed by the enchant's max level:

| Enchant max | Top | Middle | Lower |
|:---:|:---:|:---:|:---:|
| 5 | 5 | 3–4 | 1–2 |
| 4 | 4 | 2–3 | 1 |
| 3 | 3 | 2 | 1 |
| 2 | 2 | 1 | — |
| 1 | 1 | — | — |

`guaranteedLevel = min( round(bookAverage), rolledSlotCap )`. Modded enchants (max > 5) not in the
table extrapolate the max-5 shape (admin-expandable at their own risk).

## 5. XP cost (scales with value, no cap)

```
cost(enchant) = ceil( costOfMaxEnchant × level ÷ maxLevel )   // rounded up
totalCost     = Σ cost over everything that lands             // no cap
```
A maxed enchant always costs `costOfMaxEnchant` (default **10**) levels, regardless of type
(maxed Mending = maxed Sharpness V = 10). Seven maxed enchants = 70 levels.

Charged from the **"first levels"**: you need level ≥ totalCost, and the XP deducted is the flat
bottom-of-the-curve amount — so deep-XP players pay a sane chunk instead of being gouged by the
exponential top of their bar.

## 6. Lapis blocks (cost + book insurance)

The modded table's lapis slot accepts **lapis blocks** (widened via mixin; our domain).
- **1 lapis block per enchant** applied — **0 is never allowed**, it always costs lapis.
- **Book protection** = `min(1, lapisBlocks ÷ lapisForFullProtection)` (default 64). A full stack of
  lapis blocks = books never consumed.
- **Book consumption** = `bookConsumeChance × (1 − protection)` (base `bookConsumeChance` = 0.5), rolled
  once per landed enchant; the **lowest-level book** of that enchant is eaten first.

## 7. Blank-on-error (server-driven, vanilla clients see it)

The enchant table's three options are server-computed and synced, so we can **blank them** (empty,
unusable) as an in-GUI "something's wrong" signal — no client mod. Blanks when:
- **Mixed shelves** (regular + chiseled around the table), or
- **Conflicting enchants** among the scanned books (Sharpness + Smite, Protection + Fire Protection).

Otherwise the table **never rejects** — it applies every eligible stocked enchant. `/cench` explains
the specific reason.

## 8. Eligibility & books

- Only **single-enchant** books count (steers toward villager trades).
- **Item-compatible** enchants only; incompatible ones are simply ignored (not an error).
- **Treasure enchants** (`#minecraft:in_enchanting_table` complement) are **guarantee-only** — never
  in any random roll. On by default (`allowTreasureEnchants`, whitelist `["*"]`).
- **Curses** off by default (`allowCurses`).
- **Enchanting a book** is **disabled by default** (`allowBookEnchanting` = false) — the modded table
  is for gear. If enabled, `allowConflictingOnBook` (default false) lets conflicting enchants land on a
  single book (books can hold them), bypassing the conflict-blank for books only.

## 9. Commands (`/chiseledenchants`, alias `/cench`)

- `/cench` — summarize the table you're looking at (books, land %, level, excess, and any error).
- `/cench find <enchant>` — trace colored `TRAIL` particle threads from the table to each shelf holding it.
- `/cench reload` — reload config from disk (op / gamemaster).

## 10. Config summary

`booksForFullChance` · `slotsForLevelAverage` · `scanLayers` · `scanRadius` · `slotTiers` ·
`costOfMaxEnchant` · `lapisForFullProtection` · `bookConsumeChance` · `allowTreasureEnchants` ·
`treasureEnchantWhitelist` · `allowCurses` · `allowBookEnchanting` · `allowConflictingOnBook`.

## 11. Loaders / distribution

Architectury multi-loader (Fabric + NeoForge), fully **standalone** (no Architectury API at runtime).
**Server-side only required** — a vanilla client can join a modded server and everything works; runs in
single-player too. No custom blocks/items — reuses vanilla chiseled bookshelves and lapis blocks.

---

## Status

- ✅ Design finalized (this document).
- 🔨 Currently implemented: the guarantee math, slot tiers, scan geometry, config, and the
  `/cench` commands — as a **post-enchant additive layer** on the vanilla roll.
- ⏭️ Next: the **modded-table takeover** — decouple chiseled shelves from vanilla power, suppress the
  vanilla roll on modded tables, the XP/lapis-block economy, blank-on-error, and the books toggle
  (§1, §5, §6, §7, §8). This replaces the additive layer with the self-contained system above.
