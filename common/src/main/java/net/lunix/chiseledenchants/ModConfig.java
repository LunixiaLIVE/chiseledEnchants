package net.lunix.chiseledenchants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Config for chiseledEnchants. See DESIGN.md §8 for the full schema and semantics.
 * Plain JSON via Gson for now (a JSONC/commented loader is a later nicety).
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Guarantee strength (§4) ──
    /** Chance each MAX-level book contributes; 0.25 => 4 max-level books = 100%. */
    public double chancePerMaxBook = 0.25;
    /** Single 0-or-1 roll per landed enchant; lowest-level book eaten first (§7). */
    public double bookConsumeChance = 0.5;

    // ── Lapis-block protection (§7.1) — same lapis slot: gems = none, blocks = protection ──
    public boolean allowLapisBlockProtection = true;
    /** Blocks spent for 0% consume; each block = 1/N protection. */
    public int blocksForFullProtection = 4;

    // ── Treasure enchants (§5) — guarantee-only; never in the vanilla random roll ──
    public boolean allowTreasureEnchants = true;
    /** Whitelist when the switch above is on. ["*"] = all, [] = none. */
    public List<String> treasureEnchantWhitelist = List.of(
            "minecraft:mending",
            "minecraft:frost_walker",
            "minecraft:soul_speed",
            "minecraft:swift_sneak"
    );

    // ── Curses (§5) ──
    public boolean allowCurses = false;

    // ── Balance knobs (§8) ──
    public int extraLevelCostPerGuarantee = 0;
    public boolean guaranteeRequiresSlotLevel = false;

    private static ModConfig instance = new ModConfig();

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
