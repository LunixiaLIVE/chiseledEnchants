<div align="center">

# 📖 chiseledEnchants

### Guarantee the enchants you want — stock chiseled bookshelves and target the enchanting table.

![](https://img.shields.io/badge/Fabric-DBA463?style=for-the-badge&logoColor=white)&nbsp;![](https://img.shields.io/badge/NeoForge-F16436?style=for-the-badge&logoColor=white)&nbsp;

![](https://img.shields.io/badge/Minecraft-26.x-62B47A?style=flat-square) ![](https://img.shields.io/badge/Server--side-vanilla_clients_OK-8E44AD?style=flat-square) ![](https://img.shields.io/badge/Architectury-not_required-2ECC71?style=flat-square) ![](https://img.shields.io/badge/Fabric_API-required_on_Fabric-4A90D9?style=flat-square) ![](https://img.shields.io/badge/License-MIT-blue?style=flat-square) ![](https://img.shields.io/badge/Status-in_development-orange?style=flat-square)

</div>

---

> [!WARNING]
> **In active development.** The guarantee mechanics, tiered slots, scan geometry, config, and `/cench`
> commands are implemented; the full **modded-table takeover** (separate XP/lapis-block economy,
> suppressing the vanilla roll, blank-on-error) is the next phase. The complete spec lives in
> [`DESIGN.md`](https://github.com/LunixiaLIVE/chiseledEnchants/blob/multi_26.1/DESIGN.md) on the code branch.

## ✨ What it does

Vanilla enchanting is a slot machine. chiseledEnchants adds a **separate, self-contained** enchanting
path driven by the bookshelves around your table:

- **Regular bookshelves → a vanilla table.** Untouched — power and random enchants, exactly like vanilla.
- **Chiseled bookshelves → a "modded table".** Stock them with **single-enchant books** and the table
  becomes a **targeting system**: you get *only* the enchants you aimed for — no random roll.
- **A full shelf (6 books) of an enchant = a 100% guarantee** of it. The landed **level is the average**
  of your books, and the three enchant slots offer **max / mid / lower** tiers (the cheaper slots roll a
  capped level).
- **Costs are real:** the enchant charges **XP that scales with what you guarantee** (a maxed enchant ≈
  10 levels, no cap), plus **lapis blocks** — and a **full stack of lapis blocks protects your books**
  from being consumed.
- **Treasure enchants** (Mending, Soul Speed, …) are **guarantee-only** — never from a random roll —
  on by default and admin-gated by a whitelist.
- **`/cench`** summarizes a table's setup, **finds** your books (colored particle threads to each shelf),
  and **reloads** config live.
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
