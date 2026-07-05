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
import java.util.Map;
import java.util.TreeMap;


/**
 * Config for chiseledEnchants. See DESIGN.md §8 for the full schema and semantics.
 * Plain JSON via Gson for now (a JSONC/commented loader is a later nicety).
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Guarantee strength (§4) ──
    /**
     * MAX-level books of an enchant needed for a 100% chance to LAND. Only max-level books count at all
     * (Sharpness V, Mending I, Unbreaking III…) — below-max books are flagged and ignored, so the table can
     * only ever replicate an enchant you already own at max, never manufacture a higher tier. Default 6.
     */
    public int booksForFullChance = 6;

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

    /**
     * Text shown on the GREEN "ready" status boss bar above the modded table (it turns RED with a reason when
     * the setup has a problem — mixed shelves, a conflict/tie, or a below-max book). Blank = the table's name.
     */
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
    public String linkModrinth = "https://modrinth.com/project/chiseledenchants";
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
    // ── Lapis BLOCKS — a flat cost unlocks the (single) option; blocks beyond it buy book protection. ──
    /**
     * Lapis blocks required to use the table's option. Blocks BEYOND this (up to a full 64 stack) each add
     * {@link #protectionPerBlock} book protection, so the default 14 leaves 50 in a stack → full protection at
     * 0.02 (2%) per block. Default 14.
     */
    public int lapisCost = 14;
    /**
     * Whether extra lapis blocks can buy book protection at all. false = the table consumes ONLY the option's
     * required blocks (no protection, no surplus taken); books are eaten at {@link #bookConsumeChance}. Default true.
     */
    public boolean bookProtectionEnabled = true;
    /**
     * Book protection added per lapis block placed BEYOND the option's required cost (only when protection is
     * enabled), as a 0–1 fraction to match {@link #bookConsumeChance}. Those protection blocks are consumed too.
     * Default 0.02 (2% per block) → 50 extra blocks reaches the cap below.
     */
    public double protectionPerBlock = 0.02;
    /**
     * Hard ceiling on book protection, as a 0–1 fraction. Set BELOW 1 so players can never fully protect their
     * books — there's always at least a (1 − this) chance to lose one, no matter how much lapis they feed.
     * Default 1.0 (full protection reachable). Blocks past what's needed to reach this cap aren't consumed.
     */
    public double maxProtection = 1.0;

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

    // ── Conflicts (§7) ──
    /**
     * When conflicting enchants are stocked (e.g. Sharpness + Smite), resolve by book count — the one with
     * MORE books wins and is granted; the loser is dropped (ties break alphabetically by id). false = the old
     * behavior: any conflicting stock blanks the table's options. Default true.
     */
    public boolean resolveConflicts = true;

    private static ModConfig instance = new ModConfig();

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
