<div align="center">

# 📖 chiseledEnchants

### Guarantee the enchants you want — stock chiseled bookshelves and target the enchanting table.

![](https://img.shields.io/badge/Fabric-DBA463?style=for-the-badge&logoColor=white)&nbsp;![](https://img.shields.io/badge/NeoForge-F16436?style=for-the-badge&logoColor=white)&nbsp;

![](https://img.shields.io/badge/Minecraft-26.x-62B47A?style=flat-square) ![](https://img.shields.io/badge/Server--side-vanilla_clients_OK-8E44AD?style=flat-square) ![](https://img.shields.io/badge/Architectury-not_required-2ECC71?style=flat-square) ![](https://img.shields.io/badge/Fabric_API-required_on_Fabric-4A90D9?style=flat-square) ![](https://img.shields.io/badge/License-MIT-blue?style=flat-square) ![](https://img.shields.io/badge/Status-feature--complete-brightgreen?style=flat-square)

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
  particle threads to each shelf), and **reloads** config live. An in-game **guide book** (anvil-rename a
  book & quill to `chiseledEnchants`) explains everything, generated from your server's live settings.
- **Server-side** — a **vanilla client** can join a modded server and it just works. Runs in single-player too.

Books are sourced the intended way — **villager librarian trades**. No new blocks or items; it reuses
vanilla chiseled bookshelves and lapis blocks.

## 📦 Versions &amp; downloads

> [!NOTE]
> This repo uses a **branch-per-version** layout. This `main` branch is **documentation only** — the code
> for each Minecraft version lives on its own branch, each with an independent history.

| Branch | Minecraft | Loaders | Dependencies | Design |
|:------:|:---------:|:-------:|:------------:|:---:|
| [`multi_26.2`](https://github.com/LunixiaLIVE/chiseledEnchants/tree/multi_26.2) | 26.2.x | Fabric · NeoForge | Fabric API *(Fabric only)* | — |
| [`multi_26.1`](https://github.com/LunixiaLIVE/chiseledEnchants/tree/multi_26.1) | 26.1, 26.1.1, 26.1.2 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/chiseledEnchants/blob/multi_26.1/DESIGN.md) |

> [!TIP]
> Every `multi_*` branch builds **one universal jar** that runs on **both** Fabric and NeoForge (per-loader
> `-fabric` / `-neoforge` jars are produced too). Fully standalone — **no Architectury API at runtime**.

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
