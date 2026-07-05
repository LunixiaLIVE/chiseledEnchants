package net.lunix.chiseledenchants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Config for chiseledEnchants. See DESIGN.md §8 for the full schema and semantics.
 * Plain JSON via Gson for now (a JSONC/commented loader is a later nicety).
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Guarantee strength (§4) ──
    /** Books of an enchant needed for a 100% chance to LAND (level-independent). Default 6. */
    public int booksForFullChance = 6;
    /**
     * Slots the guaranteed LEVEL is averaged over — empty slots count as 0, so gaps and low books
     * drag the level down. A full set of max-level books across these slots = the enchant's max level.
     * Default 6 (one full chiseled shelf).
     */
    public int slotsForLevelAverage = 6;

    // ── Special enchanting table (the crafted gate) ──
    /**
     * Require the modded system to run on a specially-named enchanting table (the crafted gate). When true,
     * a normal enchanting table is pure vanilla even with chiseled shelves; only the named table + chiseled
     * shelves does targeted enchanting. false = any enchanting table with chiseled shelves works. Default true.
     */
    public boolean requireSpecialTable = true;
    /**
     * Custom name that marks the special table (matches the crafted item's name; case-insensitive, style-agnostic).
     * Obtain the table via the recipe, or by anvil-renaming any enchanting table to this exact text.
     */
    public String specialTableName = "Chiseled Enchanter";
    /**
     * Craft-only: require the table's name to carry the RARE (aqua) color the recipe stamps — a vanilla anvil
     * can't add color to a rename, so it can't forge the table. false = any table named specialTableName works
     * (anvil-rename allowed). Default true.
     */
    public boolean craftOnlyTable = true;
    /**
     * Recipe ingredients — the recipe is the vanilla enchanting-table shape ( _B_ / DoD / ooo ) with each
     * vanilla ingredient swapped for the item below (reads as "what replaces what"). Edit and run /reload to
     * apply. Unknown ids fall back to the default; the bundled datapack recipe is the fallback if it fails.
     */
    public String recipeReplacesBook = "minecraft:dragon_head";        // the BOOK slot (top center, x1)
    public String recipeReplacesDiamond = "minecraft:netherite_ingot"; // the 2 DIAMOND slots (x2)
    public String recipeReplacesObsidian = "minecraft:obsidian";       // the 4 OBSIDIAN slots (x4)

    /** Actionbar notice shown when a player opens the modded table. Blank = no notice. */
    public String tableOpenNotice = "This table runs on lapis blocks";

    // ── Scan geometry (§2) — how much of the surrounding shelves the mod reads for targeting.
    //    Vanilla POWER is untouched (its own fixed ring, capped at 15); this only widens book capacity. ──
    /** Vertical layers scanned, from table level up. 2 = vanilla; 3 lets players stack shelves 3 high. */
    public int scanLayers = 2;
    /** Horizontal reach of the shelf scan (clamped to a minimum of 2 — the air gap owns distance 1). */
    public int scanRadius = 2;
    /**
     * Base chance per landed enchant that its lowest-level source book is eaten (§7). Default 0.5 =
     * a 50% chance per book, unless lapis protection reduces it: effective = this × (1 − protection), where
     * protection comes from lapis blocks beyond the option's cost ({@link #protectionPerBlock}).
     * Lowest-level book of the enchant is eaten first.
     */
    public double bookConsumeChance = 0.5;

    // ── /cench find particle trace ──
    /** How many times the trace pulse repeats. Default 10. */
    public int particleRepeats = 10;
    /** Length of each trace pulse in ticks (20 = 1 second). Also sets the travel speed. Default 20. */
    public int particleDurationTicks = 20;
    /** Flow direction: true = pulses go table → shelves, false = shelves → table. Default true. */
    public boolean particleFromTable = true;

    // ── Guide book (anvil-rename trigger) ──
    /** Master switch for the anvil-rename guide book. */
    public boolean guideEnabled = true;
    /** Rename a book &amp; quill to this (case-insensitive) in an anvil to receive the guide. Default "chiseledEnchants". */
    public String guideKeyword = "chiseledEnchants";
    /** Anvil XP-level cost to produce the guide. Default 1. */
    public int guideAnvilCost = 1;

    // ── /cench about links — fill in and /cench reload; blank = shown as "coming soon" ──
    public String linkGithub = "https://github.com/LunixiaLIVE/chiseledEnchants";
    public String linkModrinth = "";
    public String linkCurseforge = "";
    public String linkDiscord = "";

    // ── Modded-table economy (§5/§6) ──
    /** XP levels a MAXED enchant costs (any type). Per enchant: ceil(this × level / maxLevel). No cap. Default 10. */
    public int costOfMaxEnchant = 10;
    /**
     * How the XP cost is taken. true = "front end": a flat points cost from the BOTTOM of the curve
     * (charges the first-N-levels worth of points, so deep-XP players aren't gouged — cheap). false =
     * "back end", the regular way: remove that many LEVELS off the top (vanilla-style, costs more the
     * higher your level). Default true.
     */
    public boolean xpFromFirstLevels = true;
    // ── Lapis BLOCKS — a fixed per-OPTION cost unlocks that level; blocks beyond it buy book protection. ──
    /** Lapis blocks required to use the FIRST (cheapest) option. Default 4. */
    public int lapisLow = 4;
    /** Lapis blocks required to use the MIDDLE option. Default 4. */
    public int lapisMid = 4;
    /** Lapis blocks required to use the LAST (max-level) option. Default 6. */
    public int lapisHigh = 6;
    /**
     * Whether extra lapis blocks can buy book protection at all. false = the table consumes ONLY the option's
     * required blocks (no protection, no surplus taken); books are eaten at {@link #bookConsumeChance}. Default true.
     */
    public boolean bookProtectionEnabled = true;
    /**
     * Book protection added per lapis block placed BEYOND the option's required cost (only when protection is
     * enabled). Those protection blocks are consumed too. Default 2.0 → 50 extra blocks reaches the cap below.
     */
    public double protectionPerBlock = 2.0;
    /**
     * Hard ceiling on book protection (%). Set BELOW 100 so players can never fully protect their books —
     * there's always at least a (100 − this)% chance to lose one, no matter how much lapis they feed. Default
     * 100 (full protection reachable). Blocks past what's needed to reach this cap aren't consumed.
     */
    public double maxProtectionPercent = 100.0;

    // ── Treasure enchants (§5) — guarantee-only; never in the vanilla random roll ──
    /** Master switch: treasure enchants may be guaranteed. Set false for fully-vanilla treasure (off). */
    public boolean allowTreasureEnchants = true;

    // ── Curses (§5) ──
    public boolean allowCurses = false;

    // ── Per-enchant whitelist — auto-filled from the live enchant registry on server start (every enchant,
    //    modded included, defaults to true). Flip an entry to false to bar that enchant from the table. ──
    public Map<String, Boolean> enchantWhitelist = new TreeMap<>();

    // ── Books (§8) ──
    /** Whether the modded table may enchant a BOOK item (off by default — the table is for gear). */
    public boolean allowBookEnchanting = false;
    /** When book-enchanting is on, allow mutually-conflicting enchants on a single book (books can hold them). */
    public boolean allowConflictingOnBook = false;

    // ── Cheaper enchant-table slots — the guaranteed LEVEL is capped per slot (top = the enchant's max).
    //    Keyed by the enchant's MAX level. mid/low = [min, max] rolled random-inclusive each use; 0 = none.
    //    Vanilla enchants max at 1-5 (the entries below). For MODDED enchants with a higher max, add keys
    //    (e.g. "6", "10") AT YOUR OWN RISK; any max not listed falls back to extrapolating the max-5 shape. ──
    public String __slotTiersNote =
            "slotTiers keyed by enchant max level; the top (30-level) slot always allows up to that max. "
            + "mid/low are [min,max] random-inclusive level caps (0 = no guarantee on that slot). "
            + "Vanilla maxes are 1-5; add higher keys for modded enchants at your own risk (unlisted maxes extrapolate).";
    public Map<String, SlotTier> slotTiers = defaultSlotTiers();

    private static ModConfig instance = new ModConfig();

    /** Per-slot random level-cap ranges for one enchant max level. */
    public static class SlotTier {
        public int[] mid;   // [min, max] cap for the middle slot
        public int[] low;   // [min, max] cap for the lower slot
        public SlotTier() {}
        public SlotTier(int[] mid, int[] low) { this.mid = mid; this.low = low; }
    }

    private static Map<String, SlotTier> defaultSlotTiers() {
        Map<String, SlotTier> m = new LinkedHashMap<>();
        m.put("1", new SlotTier(new int[]{0, 0}, new int[]{0, 0}));   // max 1 → top only
        m.put("2", new SlotTier(new int[]{1, 1}, new int[]{0, 0}));   // 2 → mid 1, low none
        m.put("3", new SlotTier(new int[]{2, 2}, new int[]{1, 1}));   // 3 → mid 2, low 1
        m.put("4", new SlotTier(new int[]{2, 3}, new int[]{1, 1}));   // 4 → mid 2-3, low 1
        m.put("5", new SlotTier(new int[]{3, 4}, new int[]{1, 2}));   // 5 → mid 3-4, low 1-2
        return m;
    }

    public static ModConfig get() {
        return instance;
    }

    /** Add any enchant missing from {@link #enchantWhitelist} as allowed (true), then save if it changed. */
    public static void syncEnchantWhitelist(RegistryAccess access) {
        Registry<Enchantment> reg = access.lookupOrThrow(Registries.ENCHANTMENT);
        boolean changed = false;
        for (Identifier id : reg.keySet()) {
            if (instance.enchantWhitelist.putIfAbsent(id.toString(), Boolean.TRUE) == null) changed = true;
        }
        if (changed) save();
    }

    private static Path configPath() {
        return ChiseledEnchantsCommon.CONFIG_DIR.resolve("chiseledenchants.json");
    }

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) instance = loaded;
            } catch (IOException e) {
                ChiseledEnchantsCommon.LOGGER.warn("[chiseledEnchants] Failed to load config: {}", e.getMessage());
                instance = new ModConfig();
            }
        }
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(configPath())) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            ChiseledEnchantsCommon.LOGGER.warn("[chiseledEnchants] Failed to save config: {}", e.getMessage());
        }
    }
}
