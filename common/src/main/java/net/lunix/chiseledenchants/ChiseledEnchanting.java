package net.lunix.chiseledenchants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core chiseledEnchants logic (DESIGN.md §2/§4/§5/§6/§7). Runs server-side after a successful
 * vanilla enchant: scans nearby chiseled bookshelves for single-enchant books, rolls per-enchant
 * guarantees, forces the winners onto the freshly-enchanted item, then consumes books.
 */
public final class ChiseledEnchanting {

    private ChiseledEnchanting() {}

    record Book(BlockPos pos, ChiseledBookShelfBlockEntity be, int slot, int level) {}
    private record Guarantee(Holder<Enchantment> enchant, double landChance, int level, List<Book> books) {}

    /** Entry point from the EnchantmentMenu mixin, after a successful enchant on {@code slotId}. */
    public static void applyGuarantees(Container enchantSlots, ContainerLevelAccess access, int slotId, int[] costs) {
        access.execute((level, tablePos) -> run(level, tablePos, enchantSlots, slotId));
    }

    private static void run(Level level, BlockPos tablePos, Container enchantSlots, int slotId) {
        if (level.isClientSide()) return;
        ItemStack item = enchantSlots.getItem(0);
        if (item.isEmpty()) return;

        ModConfig cfg = ModConfig.get();
        boolean isBook = item.is(Items.ENCHANTED_BOOK) || item.is(Items.BOOK);

        // ── §2 scan (shared with the summary command) ──
        Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);
        if (byEnchant.isEmpty()) return;

        // ── §4 aggregate (averaging model): N books of an enchant = 100% chance to LAND (levels don't
        //    affect the chance). The LEVEL is the average of the N slots, empty slots counting as 0, so
        //    gaps and low books drag it down. round(avg), clamped to the enchant's max. This is the
        //    top-cost ("30-level") slot — the maximum achievable level. ──
        int chanceDenom = chanceDenom(cfg);
        int levelDenom = levelDenom(cfg);
        List<Guarantee> guarantees = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            if (!eligible(e.getKey(), cfg)) continue;                   // §5 curse/treasure gating
            List<Book> bks = e.getValue();
            double landChance = Math.min(1.0, (double) bks.size() / chanceDenom);
            int[] levels = bks.stream().mapToInt(Book::level).boxed()
                    .sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
            int sumTop = 0;                                             // sum of the top level-slots (rest are 0)
            for (int i = 0; i < Math.min(levelDenom, levels.length); i++) sumTop += levels[i];
            int maxLevel = Math.max(1, e.getKey().value().getMaxLevel());
            int avgLevel = Math.max(1, Math.min(maxLevel, (int) Math.round((double) sumTop / levelDenom)));
            guarantees.add(new Guarantee(e.getKey(), landChance, avgLevel, bks));
        }
        guarantees.sort(Comparator.comparingDouble(Guarantee::landChance).reversed());

        // ── §6 resolve: roll each, force compatible/non-conflicting winners onto the item ──
        RandomSource rng = level.getRandom();
        List<Holder<Enchantment>> accepted =
                new ArrayList<>(EnchantmentHelper.getEnchantmentsForCrafting(item).keySet());
        List<Guarantee> landed = new ArrayList<>();
        for (Guarantee g : guarantees) {
            if (rng.nextFloat() >= g.landChance()) continue;                 // didn't land
            int maxLevel = Math.max(1, g.enchant().value().getMaxLevel());
            int cap = slotCap(slotId, maxLevel, rng, cfg);                   // per-slot tier ceiling (random on cheaper slots)
            if (cap <= 0) continue;                                          // no guarantee on this slot for this enchant
            if (!isBook && !g.enchant().value().canEnchant(item)) continue;  // item-type compatibility
            boolean conflict = false;
            for (Holder<Enchantment> acc : accepted) {
                if (!acc.equals(g.enchant()) && !Enchantment.areCompatible(acc, g.enchant())) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;
            final int applyLevel = Math.min(g.level(), cap);
            EnchantmentHelper.updateEnchantments(item, m -> {
                if (m.getLevel(g.enchant()) < applyLevel) m.set(g.enchant(), applyLevel);
            });
            accepted.add(g.enchant());
            landed.add(g);
        }
        if (landed.isEmpty()) return;
        enchantSlots.setItem(0, item);

        // ── §7 consumption: 0-or-1 book per landed enchant, lowest-level first ──
        for (Guarantee g : landed) {
            if (rng.nextFloat() >= cfg.bookConsumeChance) continue;
            g.books().stream().min(Comparator.comparingInt(Book::level)).ifPresent(b -> {
                b.be().removeItem(b.slot(), 1);
                b.be().setChanged();
                BlockState st = level.getBlockState(b.pos());
                level.sendBlockUpdated(b.pos(), st, st, 3);
            });
        }
    }

    /**
     * §2 scan — reuse vanilla's EXACT power-scan positions + air-gap check, so a shelf that powers
     * the table is the same shelf that can target (chiseled shelves are power providers via our data
     * tag). Returns every single-enchant book grouped by enchant, aggregated across all shelves.
     * Shared by the apply path and the /chiseledenchants summary command.
     */
    public static Map<Holder<Enchantment>, List<Book>> scan(Level level, BlockPos tablePos) {
        ModConfig cfg = ModConfig.get();
        int layers = Math.max(1, cfg.scanLayers);
        int radius = Math.max(2, cfg.scanRadius);     // distance 1 is reserved for the air gap
        Map<Holder<Enchantment>, List<Book>> byEnchant = new HashMap<>();
        for (int h = 0; h < layers; h++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;   // table column + air-gap ring
                    BlockPos offset = new BlockPos(dx, h, dz);
                    // vanilla's own gate: shelf is a power provider (chiseled qualifies via our tag) + air gap clear
                    if (!EnchantingTableBlock.isValidBookShelf(level, tablePos, offset)) continue;
                    BlockPos shelfPos = tablePos.offset(offset);
                    if (!(level.getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity be)) continue;
                    NonNullList<ItemStack> books = be.getItems();
                    for (int s = 0; s < books.size(); s++) {
                        ItemStack book = books.get(s);
                        if (book.isEmpty()) continue;
                        ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
                        if (stored == null || stored.size() != 1) continue;      // single-enchant only
                        Holder<Enchantment> ench = stored.keySet().iterator().next();
                        byEnchant.computeIfAbsent(ench, k -> new ArrayList<>())
                                .add(new Book(shelfPos, be, s, stored.getLevel(ench)));
                    }
                }
            }
        }
        return byEnchant;
    }

    /** Denominator for the land chance — books of an enchant for a 100% chance to land. */
    public static int chanceDenom(ModConfig cfg) {
        return Math.max(1, cfg.booksForFullChance);
    }

    /** Denominator for the level average — slots the guaranteed level is averaged over. */
    public static int levelDenom(ModConfig cfg) {
        return Math.max(1, cfg.slotsForLevelAverage);
    }

    /**
     * Per-slot level ceiling. Top slot (id 2) allows up to the enchant's max; the two cheaper slots
     * roll a random cap from the config tier for this enchant's max level (unlisted maxes extrapolate
     * the max-5 shape). Returns 0 = no guarantee on this slot for this enchant.
     */
    private static int slotCap(int slotId, int maxLevel, RandomSource rng, ModConfig cfg) {
        if (slotId >= 2) return maxLevel;
        int[] range;
        ModConfig.SlotTier tier = cfg.slotTiers == null ? null : cfg.slotTiers.get(String.valueOf(maxLevel));
        if (tier != null) {
            range = (slotId == 1) ? tier.mid : tier.low;
        } else {                                                 // modded max not in the table → extrapolate max-5 shape
            range = (slotId == 1)
                    ? new int[]{Math.round(0.6f * maxLevel), Math.round(0.8f * maxLevel)}
                    : new int[]{Math.round(0.2f * maxLevel), Math.round(0.4f * maxLevel)};
        }
        if (range == null || range.length < 2) return 0;
        int hi = Math.max(range[0], range[1]);
        if (hi <= 0) return 0;                                   // "none" on this slot
        int lo = Math.max(1, Math.min(range[0], range[1]));
        if (lo > hi) return 0;
        return lo + rng.nextInt(hi - lo + 1);                    // random-inclusive [lo, hi]
    }

    /** §5 eligibility: curses + treasure gating (treasure = not in #minecraft:in_enchanting_table). */
    public static boolean eligible(Holder<Enchantment> ench, ModConfig cfg) {
        if (ench.is(EnchantmentTags.CURSE) && !cfg.allowCurses) return false;
        boolean treasure = !ench.is(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (treasure) {
            if (!cfg.allowTreasureEnchants) return false;
            String id = ench.getRegisteredName();   // "namespace:path", e.g. "minecraft:mending"
            if (!(cfg.treasureEnchantWhitelist.contains("*")
                    || cfg.treasureEnchantWhitelist.contains(id))) return false;
        }
        return true;
    }
}
