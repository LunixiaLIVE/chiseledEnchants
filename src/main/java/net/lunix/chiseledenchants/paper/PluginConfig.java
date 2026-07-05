package net.lunix.chiseledenchants.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory config, loaded from {@code config.yml} and refreshed by {@code /cench reload}. Mirrors the mod's
 * schema (see the mod's ModConfig). All chance/protection values are 0–1 fractions.
 */
public final class PluginConfig {

    private PluginConfig() {}

    // ── Guarantee strength ──
    public static int booksForFullChance = 6;

    // ── Special table ──
    public static boolean requireSpecialTable = true;
    public static String specialTableName = "Chiseled Enchanter";
    public static boolean craftOnlyTable = true;

    // ── Economy ──
    public static int lapisCost = 14;
    public static int costOfMaxEnchant = 10;
    public static double bookConsumeChance = 0.5;
    public static boolean bookProtectionEnabled = true;
    public static double protectionPerBlock = 0.02;
    public static double maxProtection = 1.0;

    // ── Rules ──
    public static boolean resolveConflicts = true;
    public static boolean allowCurses = false;
    public static boolean allowTreasureEnchants = true;
    /** Whether the table may enchant a plain BOOK item (off by default — the table is for gear). */
    public static boolean allowBookEnchanting = false;
    /** When book enchanting is on, allow mutually-conflicting enchants on one book. */
    public static boolean allowConflictingOnBook = false;

    /** Per-enchant whitelist ("namespace:key" → allowed). Absent = allowed. */
    public static final Map<String, Boolean> enchantWhitelist = new HashMap<>();

    // ── Scan geometry ──
    public static int scanRadius = 2;
    public static int scanLayers = 2;

    // ── Guide + links ──
    public static boolean guideEnabled = true;
    public static String guideKeyword = "chiseledEnchants";
    public static String linkGithub = "";
    public static String linkModrinth = "";
    public static String linkCurseforge = "";
    public static String linkDiscord = "";

    public static void load(Plugin plugin) {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        booksForFullChance = c.getInt("booksForFullChance", booksForFullChance);

        requireSpecialTable = c.getBoolean("requireSpecialTable", requireSpecialTable);
        specialTableName = c.getString("specialTableName", specialTableName);
        craftOnlyTable = c.getBoolean("craftOnlyTable", craftOnlyTable);

        lapisCost = c.getInt("lapisCost", lapisCost);
        costOfMaxEnchant = c.getInt("costOfMaxEnchant", costOfMaxEnchant);
        bookConsumeChance = c.getDouble("bookConsumeChance", bookConsumeChance);
        bookProtectionEnabled = c.getBoolean("bookProtectionEnabled", bookProtectionEnabled);
        protectionPerBlock = c.getDouble("protectionPerBlock", protectionPerBlock);
        maxProtection = c.getDouble("maxProtection", maxProtection);

        resolveConflicts = c.getBoolean("resolveConflicts", resolveConflicts);
        allowCurses = c.getBoolean("allowCurses", allowCurses);
        allowTreasureEnchants = c.getBoolean("allowTreasureEnchants", allowTreasureEnchants);
        allowBookEnchanting = c.getBoolean("allowBookEnchanting", allowBookEnchanting);
        allowConflictingOnBook = c.getBoolean("allowConflictingOnBook", allowConflictingOnBook);

        enchantWhitelist.clear();
        ConfigurationSection wl = c.getConfigurationSection("enchantWhitelist");
        if (wl != null) for (String key : wl.getKeys(false)) enchantWhitelist.put(key.toLowerCase(), wl.getBoolean(key));

        scanRadius = Math.max(2, c.getInt("scanRadius", scanRadius));
        scanLayers = Math.max(1, c.getInt("scanLayers", scanLayers));

        guideEnabled = c.getBoolean("guideEnabled", guideEnabled);
        guideKeyword = c.getString("guideKeyword", guideKeyword);
        linkGithub = c.getString("linkGithub", linkGithub);
        linkModrinth = c.getString("linkModrinth", linkModrinth);
        linkCurseforge = c.getString("linkCurseforge", linkCurseforge);
        linkDiscord = c.getString("linkDiscord", linkDiscord);
    }
}
