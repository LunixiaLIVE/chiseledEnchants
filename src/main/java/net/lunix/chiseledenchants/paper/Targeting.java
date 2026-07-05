package net.lunix.chiseledenchants.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.EnchantingTable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The targeting brain — Paper port of the mod's ChiseledEnchanting logic. Detection, eligibility, the
 * max-level-books-only rule, conflict resolution (more books wins; equal-book tie blanks), the land roll,
 * and the XP cost. Pure logic over the scanned shelves; the events call into this.
 */
public final class Targeting {

    private Targeting() {}

    /** A resolved enchant: what to grant (always at max level) and its source books (for consumption). */
    public record Landed(Enchantment ench, int level, List<ShelfScanner.Book> books) {}

    // ── Detection ─────────────────────────────────────────────────────────────
    public static boolean isModdedTable(Block table) {
        if (table.getType() != Material.ENCHANTING_TABLE) return false;
        if (PluginConfig.requireSpecialTable && !isSpecialTable(table)) return false;
        return ShelfScanner.shelfCensus(table)[0] > 0;   // at least one chiseled shelf
    }

    private static boolean isSpecialTable(Block table) {
        if (!(table.getState() instanceof EnchantingTable et)) return false;
        Component name = et.customName();
        if (name == null) return false;
        String plain = PlainTextComponentSerializer.plainText().serialize(name).trim();
        String want = PluginConfig.specialTableName == null ? "" : PluginConfig.specialTableName.trim();
        if (want.isEmpty() || !plain.equalsIgnoreCase(want)) return false;
        if (!PluginConfig.craftOnlyTable) return true;                 // any-colour name accepted
        TextColor color = name.color();                               // recipe stamps LIGHT_PURPLE; anvil can't colour
        return NamedTextColor.LIGHT_PURPLE.equals(color);
    }

    public static boolean hasMixedShelves(Block table) {
        int[] c = ShelfScanner.shelfCensus(table);
        return c[0] > 0 && c[1] > 0;
    }

    // ── Eligibility ───────────────────────────────────────────────────────────
    public static boolean eligible(Enchantment e) {
        if (e.isCursed() && !PluginConfig.allowCurses) return false;
        if (e.isTreasure() && !PluginConfig.allowTreasureEnchants) return false;
        Boolean w = PluginConfig.enchantWhitelist.get(e.getKey().toString().toLowerCase());
        return w == null || w;                                         // absent = allowed
    }

    // ── Max-level books (the anti-launder rule) ───────────────────────────────
    static List<ShelfScanner.Book> maxBooks(Enchantment e, List<ShelfScanner.Book> all) {
        int max = Math.max(1, e.getMaxLevel());
        List<ShelfScanner.Book> out = new ArrayList<>();
        for (ShelfScanner.Book b : all) if (b.level() >= max) out.add(b);
        return out;
    }

    public static boolean hasFlaggedBooks(Map<Enchantment, List<ShelfScanner.Book>> byEnchant) {
        for (Map.Entry<Enchantment, List<ShelfScanner.Book>> e : byEnchant.entrySet()) {
            if (!eligible(e.getKey())) continue;
            int max = Math.max(1, e.getKey().getMaxLevel());
            for (ShelfScanner.Book b : e.getValue()) if (b.level() < max) return true;
        }
        return false;
    }

    private static boolean conflicts(Enchantment a, Enchantment b) {
        return a.conflictsWith(b) || b.conflictsWith(a);
    }

    /** True if any conflict exists among counting (max-book) enchants (item-agnostic if {@code item} null). */
    private static boolean anyConflict(Map<Enchantment, List<ShelfScanner.Book>> byEnchant, ItemStack item) {
        List<Enchantment> elig = new ArrayList<>();
        for (Map.Entry<Enchantment, List<ShelfScanner.Book>> e : byEnchant.entrySet())
            if (eligible(e.getKey()) && !maxBooks(e.getKey(), e.getValue()).isEmpty()
                    && (item == null || e.getKey().canEnchantItem(item))) elig.add(e.getKey());
        for (int i = 0; i < elig.size(); i++)
            for (int j = i + 1; j < elig.size(); j++)
                if (conflicts(elig.get(i), elig.get(j))) return true;
        return false;
    }

    /** Two conflicting max-book enchants TIED on count = ambiguous → blank (item-agnostic if {@code item} null). */
    public static boolean tieConflict(Map<Enchantment, List<ShelfScanner.Book>> byEnchant, ItemStack item) {
        List<Map.Entry<Enchantment, Integer>> contenders = new ArrayList<>();
        for (Map.Entry<Enchantment, List<ShelfScanner.Book>> e : byEnchant.entrySet()) {
            int count = maxBooks(e.getKey(), e.getValue()).size();
            if (count == 0 || !eligible(e.getKey())) continue;
            if (item != null && !e.getKey().canEnchantItem(item)) continue;
            contenders.add(Map.entry(e.getKey(), count));
        }
        contenders.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Map.Entry<Enchantment, Integer>> kept = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> g : contenders) {
            boolean dropped = false;
            for (Map.Entry<Enchantment, Integer> k : kept) {
                if (conflicts(k.getKey(), g.getKey())) {
                    if (k.getValue().intValue() == g.getValue().intValue()) return true;   // ambiguous tie
                    dropped = true;
                    break;
                }
            }
            if (!dropped) kept.add(g);
        }
        return false;
    }

    // ── Status boss bar (item-agnostic) ───────────────────────────────────────
    /** Null = green/ready; else a RED problem reason. */
    public static String statusReason(Block table, Map<Enchantment, List<ShelfScanner.Book>> byEnchant) {
        if (hasMixedShelves(table)) return "Mixed shelves — remove the regular bookshelves";
        boolean conflict = PluginConfig.resolveConflicts ? tieConflict(byEnchant, null) : anyConflict(byEnchant, null);
        if (conflict) return PluginConfig.resolveConflicts
                ? "Conflicting enchants tied on books — add a book to break the tie"
                : "Conflicting enchants stocked — remove the conflicts";
        if (hasFlaggedBooks(byEnchant)) return "Some books aren't max-level — they won't count";
        return null;
    }

    // ── Resolve ───────────────────────────────────────────────────────────────
    /** The enchants this table grants for {@code item} — each at max level. Deterministic per setup. */
    public static List<Landed> resolve(Player player, Block table, ItemStack item,
                                       Map<Enchantment, List<ShelfScanner.Book>> byEnchant) {
        boolean isBook = item != null && item.getType() == Material.BOOK;
        boolean allowConflict = isBook && PluginConfig.allowConflictingOnBook;
        if (!allowConflict) {
            boolean blank = PluginConfig.resolveConflicts ? tieConflict(byEnchant, item) : anyConflict(byEnchant, item);
            if (blank) return List.of();
        }

        int chanceDenom = Math.max(1, PluginConfig.booksForFullChance);
        record G(Enchantment e, double chance, int level, List<ShelfScanner.Book> books, int count) {}
        List<G> guarantees = new ArrayList<>();
        for (Map.Entry<Enchantment, List<ShelfScanner.Book>> entry : byEnchant.entrySet()) {
            Enchantment e = entry.getKey();
            if (!eligible(e)) continue;
            if (item != null && !isBook && !e.canEnchantItem(item)) continue;
            List<ShelfScanner.Book> max = maxBooks(e, entry.getValue());
            if (max.isEmpty()) continue;
            double chance = Math.min(1.0, (double) max.size() / chanceDenom);
            guarantees.add(new G(e, chance, Math.max(1, e.getMaxLevel()), max, max.size()));
        }
        // most books first, alphabetical tiebreak (deterministic)
        guarantees.sort((a, b) -> {
            int c = Integer.compare(b.count(), a.count());
            return c != 0 ? c : a.e().getKey().toString().compareTo(b.e().getKey().toString());
        });
        // resolve conflicts before the roll (winner already ordered first)
        List<G> kept = new ArrayList<>();
        for (G g : guarantees) {
            boolean conflict = false;
            if (!allowConflict) for (G acc : kept) if (conflicts(acc.e(), g.e())) { conflict = true; break; }
            if (!conflict) kept.add(g);
        }
        // deterministic land roll — winners land at max level
        Random rng = new Random(setupSeed(player, table, byEnchant));
        List<Landed> landed = new ArrayList<>();
        for (G g : kept) {
            if (rng.nextDouble() >= g.chance()) continue;
            landed.add(new Landed(g.e(), g.level(), g.books()));
        }
        return landed;
    }

    /** Stable across prepare↔click for the same setup; changes when a book is consumed (counts change). */
    private static long setupSeed(Player player, Block table, Map<Enchantment, List<ShelfScanner.Book>> byEnchant) {
        long seed = player.getUniqueId().getMostSignificantBits();
        seed = seed * 31 + table.getX();
        seed = seed * 31 + table.getY();
        seed = seed * 31 + table.getZ();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<Enchantment, List<ShelfScanner.Book>> e : byEnchant.entrySet())
            keys.add(e.getKey().getKey() + ":" + maxBooks(e.getKey(), e.getValue()).size());
        Collections.sort(keys);
        for (String k : keys) seed = seed * 31 + k.hashCode();
        return seed;
    }

    /** XP level cost = Σ ceil(costOfMaxEnchant × level / maxLevel). */
    public static int xpCost(List<Landed> landed) {
        int total = 0;
        for (Landed l : landed) {
            int max = Math.max(1, l.ench().getMaxLevel());
            total += (int) Math.ceil((double) PluginConfig.costOfMaxEnchant * l.level() / max);
        }
        return total;
    }
}
