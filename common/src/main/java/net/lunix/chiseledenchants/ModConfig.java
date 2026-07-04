package net.lunix.chiseledenchants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // ── Scan geometry (§2) — how much of the surrounding shelves the mod reads for targeting.
    //    Vanilla POWER is untouched (its own fixed ring, capped at 15); this only widens book capacity. ──
    /** Vertical layers scanned, from table level up. 2 = vanilla; 3 lets players stack shelves 3 high. */
    public int scanLayers = 2;
    /** Horizontal reach of the shelf scan (clamped to a minimum of 2 — the air gap owns distance 1). */
    public int scanRadius = 2;
    /**
     * Base chance per landed enchant that its lowest-level source book is eaten (§7). Default 1.0 =
     * a book is ALWAYS consumed unless lapis protection reduces it: effective = this × (1 − protection),
     * where protection scales to 1.0 at a full stack ({@link #lapisForFullProtection}). So "100% unless
     * you feed a stack of lapis." Lowest-level book of the enchant is eaten first.
     */
    public double bookConsumeChance = 1.0;

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
    /**
     * Total lapis BLOCKS the table CONSUMES for 100% book protection — a full stack by default. The per-enchant
     * cost counts toward this; every block beyond the enchant cost (up to this) buys protection and is eaten too,
     * so full protection costs a stack of lapis blocks (a real end-game sink). Excess past this is left behind.
     */
    public int lapisForFullProtection = 64;

    // ── Treasure enchants (§5) — guarantee-only; never in the vanilla random roll ──
    /** Master switch: treasure enchants may be guaranteed. Set false for fully-vanilla treasure (off). */
    public boolean allowTreasureEnchants = true;
    /**
     * Which treasure enchants may be guaranteed when the switch above is on.
     * ["*"] = all treasure enchants (default, incl. modded); [] = none;
     * or list specific ids, e.g. "minecraft:mending", to restrict.
     */
    public List<String> treasureEnchantWhitelist = List.of("*");

    // ── Curses (§5) ──
    public boolean allowCurses = false;

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
