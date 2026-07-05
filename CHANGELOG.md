# Changelog — chiseledEnchants (Paper) (`paper`)

Paper plugin port of the [chiseledEnchants](https://github.com/LunixiaLIVE/chiseledEnchants) mod.
Minecraft **26.1.x + 26.2.x** · single jar (Bukkit API is cross-version) · server-side.

## [0.1.0] — scaffold
### Added
- Project scaffold against Paper API `26.1.2.build.72-stable` (Java 25), building a
  `chiseledenchants-<version>_MC-<mc>-paper.jar`.
- Plugin entrypoint (`ChiseledEnchantsPaper`), `config.yml` mirroring the mod's options,
  and `/chiseledenchants` (alias `/cench`) with `about` / `reload` / `guide`.
- Working **BossBar status** manager (green ready / red problem) and a **guide-book** stub.
- **Recipe** registration for the crafted "Chiseled Enchanter" (named enchanting table).
- `EnchantListener` (`PrepareItemEnchantEvent` + `EnchantItemEvent`) and `ShelfScanner`
  wired in as stubs — the targeting logic is the next phase.

> ⚠️ Scaffold only — the enchant takeover, shelf scan, and lapis/XP economy are stubbed.
