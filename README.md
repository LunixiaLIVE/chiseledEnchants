<div align="center">

# 📖 chiseledEnchants

### Guarantee the enchants you want — stock chiseled bookshelves and target the enchanting table.

![](https://img.shields.io/badge/Fabric-DBA463?style=for-the-badge&logoColor=white)&nbsp;![](https://img.shields.io/badge/NeoForge-F16436?style=for-the-badge&logoColor=white)&nbsp;

![](https://img.shields.io/badge/Minecraft-26.x-62B47A?style=flat-square) ![](https://img.shields.io/badge/Side-Client_%26_Server-8E44AD?style=flat-square) ![](https://img.shields.io/badge/Architectury-not_required-2ECC71?style=flat-square) ![](https://img.shields.io/badge/Fabric_API-required_on_Fabric-4A90D9?style=flat-square) ![](https://img.shields.io/badge/License-MIT-blue?style=flat-square) ![](https://img.shields.io/badge/Status-in_development-orange?style=flat-square)

</div>

---

> [!WARNING]
> **In active development.** The project scaffolds and builds cleanly on both loaders, but the targeted-enchanting mechanics are not yet implemented — the enchanting table still behaves vanilla. Watch this space.

## ✨ What it does

Vanilla enchanting is a slot machine. chiseledEnchants turns the bookshelves around your enchanting table into a **targeting system**:

- **Regular bookshelves** work exactly like vanilla (power + random enchants).
- **Chiseled bookshelves** stocked with **single-enchant books** let you **guarantee** specific enchantments on the item — 4 max-level books of an enchant = a 100% guarantee (compatible enchants only).
- Books are sourced the intended way — **villager librarian trades**.
- **Treasure enchants** (Mending, Soul Speed, …) are guarantee-only and admin-gated by a whitelist.
- Spend **lapis blocks** instead of gems to protect your books from being consumed, with a **live "book consume chance"** readout right in the enchant screen.

## 📦 Versions &amp; downloads

> [!NOTE]
> This repo uses a **branch-per-version** layout. This `main` branch is **documentation only** — the code for each Minecraft version lives on its own branch, each with an independent history and its own `CHANGELOG.md`.

| Branch | Minecraft | Loaders | Dependencies | Log |
|:------:|:---------:|:-------:|:------------:|:---:|
| [`multi_26.2`](https://github.com/LunixiaLIVE/chiseledEnchants/tree/multi_26.2) | 26.2.x | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/chiseledEnchants/blob/multi_26.2/CHANGELOG.md) |
| [`multi_26.1`](https://github.com/LunixiaLIVE/chiseledEnchants/tree/multi_26.1) | 26.1, 26.1.1, 26.1.2 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/chiseledEnchants/blob/multi_26.1/CHANGELOG.md) |

> [!TIP]
> Every `multi_*` branch builds **one universal jar** that runs on **both** Fabric and NeoForge (per-loader `-fabric` / `-neoforge` jars are produced too). All of them are fully standalone — **no Architectury API at runtime**.

<details>
<summary>🛠️ <b>Building from source</b></summary>

Each code branch is a self-contained Gradle project. Grab the branch for your Minecraft version:

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
