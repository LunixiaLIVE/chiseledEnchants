package net.lunix.chiseledenchants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
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

    /** Landed guarantee: an enchant + the level it lands at + its source books (for consumption). */
    private record Landed(Holder<Enchantment> ench, int level, List<Book> books) {}

    /** One line of a table preview: an enchant, the level it lands at (top slot), and its land chance. */
    public record PreviewEnchant(Holder<Enchantment> enchant, int level, double landChance) {}

    /** Result of {@link #preview}: the table's state + (if OK) the enchants the top slot applies and the cost. */
    public record TablePreview(Kind kind, List<PreviewEnchant> enchants, int xpLevels, int lapisCost) {
        public enum Kind { VANILLA, MIXED, CONFLICT, BOOK_DISABLED, EMPTY, NOT_ENCHANTABLE, NONE_APPLICABLE, OK }
    }

    /**
     * Modded-table takeover (called from the clickMenuButton mixin). Runs the whole modded enchant:
     * resolve guarantees at the chosen slot tier, charge the "first-levels" XP + lapis blocks, apply the
     * enchants, and roll book consumption. Returns true if an enchant happened (so the mixin cancels vanilla).
     */
    public static Boolean moddedEnchant(Container enchantSlots, ContainerLevelAccess access, int slotId, long seed, Player player) {
        Boolean[] result = {null};   // null = vanilla table (leave alone); TRUE = enchanted; FALSE = modded but no enchant
        access.execute((level, tablePos) -> {
            if (level.isClientSide() || !isModdedTable(level, tablePos)) return;   // vanilla table
            result[0] = Boolean.FALSE;                                            // modded table → vanilla suppressed
            if (hasMixedShelves(level, tablePos)) return;                         // blank/error — no enchant
            ItemStack item = enchantSlots.getItem(0);
            if (item.isEmpty()) return;
            ModConfig cfg = ModConfig.get();
            boolean isBook = item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK);
            if (isBook && !cfg.allowBookEnchanting) return;

            RandomSource rng = level.getRandom();                       // truly-random book consumption
            Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);
            if (hasConflict(byEnchant, item, isBook, cfg)) return;       // conflicting library — table is blank
            List<Landed> landed = resolve(byEnchant, item, isBook, slotId, seed, cfg);   // same seed as the displayed slot
            if (landed.isEmpty()) return;

            int xpLevels = xpCost(landed, cfg);                         // §5/§6 — 1 lapis block per enchant below
            int lapisCost = landed.size();
            ItemStack lapis = enchantSlots.getItem(1);
            boolean creative = player.hasInfiniteMaterials();
            if (!creative && (lapis.getCount() < lapisCost || player.experienceLevel < xpLevels)) return;

            // apply
            for (Landed l : landed) {
                int lvl = l.level();
                EnchantmentHelper.updateEnchantments(item, m -> {
                    if (m.getLevel(l.ench()) < lvl) m.set(l.ench(), lvl);
                });
            }
            enchantSlots.setItem(0, item);

            // XP is charged only in survival — creative keeps free levels (vanilla-consistent), and this also
            // means the affordability gate above never locks a 0-XP creative player out of the table.
            if (!creative) {
                if (cfg.xpFromFirstLevels) player.giveExperiencePoints(-totalXpForLevel(xpLevels)); // flat "first levels"
                else player.giveExperienceLevels(-xpLevels);                                        // regular, off the top
            }

            // Lapis (and books) are consumed in BOTH game modes so the table's real cost shows even in creative.
            // The table EATS lapis to BUY book protection (§7): a full stack (lapisForFullProtection, which
            // includes the per-enchant cost) = 100% protection and is fully consumed; every lapis beyond the
            // enchant cost reduces book loss and is eaten too; anything past a full stack is left in the slot.
            int fullProt = Math.max(lapisCost, cfg.lapisForFullProtection);
            int lapisSpent = Math.min(lapis.getCount(), fullProt);                        // eat up to a full stack
            double protection = Math.max(0.0, Math.min(1.0,
                    (double) (lapisSpent - lapisCost) / Math.max(1, fullProt - lapisCost)));
            lapis.shrink(lapisSpent);
            double consume = cfg.bookConsumeChance * (1.0 - protection);
            for (Landed l : landed) {
                if (rng.nextFloat() >= consume) continue;
                l.books().stream().min(Comparator.comparingInt(Book::level)).ifPresent(b -> consumeBook(level, b));
            }

            player.onEnchantmentPerformed(item, lapisCost);
            enchantSlots.setChanged();
            result[0] = Boolean.TRUE;
        });
        return result[0];
    }

    /**
     * Remove one book from a chiseled-shelf slot AND clear that slot's OCCUPIED blockstate, so the book
     * model actually disappears (the block entity's removeItem alone leaves the visual occupancy set — that's
     * normally the block's job on interaction). setBlock flag 3 = update + notify clients.
     */
    private static void consumeBook(Level level, Book b) {
        b.be().removeItem(b.slot(), 1);
        b.be().setChanged();
        BlockState st = level.getBlockState(b.pos());
        if (st.getBlock() instanceof ChiseledBookShelfBlock
                && b.slot() < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size()) {
            level.setBlock(b.pos(), st.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(b.slot()), false), 3);
        } else {
            level.sendBlockUpdated(b.pos(), st, st, 3);
        }
    }

    /**
     * slotsChanged override for MODDED tables: make the 3 slots clickable when the setup is valid, or
     * blank them (cost 0 → empty, unusable) on an error — mixed shelves, conflicting library, book when
     * disabled, or nothing landable. Vanilla tables are left with vanilla's own values.
     * (Placeholder cost {@code i+1} for now; the real per-slot XP cost is charged at click.)
     */
    public static void moddedSlots(Container enchantSlots, ContainerLevelAccess access, long seed,
                                   int[] costs, int[] enchantClue, int[] levelClue) {
        access.execute((level, tablePos) -> {
            if (level.isClientSide() || !isModdedTable(level, tablePos)) return;
            ItemStack item = enchantSlots.getItem(0);
            ModConfig cfg = ModConfig.get();
            boolean isBook = item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK);
            Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);
            boolean err = item.isEmpty() || !item.isEnchantable()
                    || (isBook && !cfg.allowBookEnchanting)
                    || hasMixedShelves(level, tablePos)
                    || hasConflict(byEnchant, item, isBook, cfg);
            for (int i = 0; i < 3; i++) {
                // same seed as the click, so the previewed cost/level is exactly what gets applied/charged
                List<Landed> preview = err ? List.of() : resolve(byEnchant, item, isBook, i, seed, cfg);
                if (preview.isEmpty()) {
                    costs[i] = 0;
                    enchantClue[i] = -1;
                    levelClue[i] = -1;
                } else {
                    costs[i] = Math.max(1, xpCost(preview, cfg));   // real XP cost == what's charged
                    // A vanilla client shows only ONE enchant per slot, so spread the set across the 3 slots:
                    // the top slot (id 2) names the strongest enchant and each lower slot reveals another, so a
                    // glance across all three previews up to 3 of the enchants that will ALL be applied on click
                    // (the full list is always available via /cench).
                    List<Landed> ranked = new ArrayList<>(preview);
                    ranked.sort(Comparator.comparingInt(Landed::level).reversed());
                    Landed pick = ranked.get((2 - i) % ranked.size());
                    enchantClue[i] = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getId(pick.ench().value());
                    levelClue[i] = pick.level();
                }
            }
        });
    }

    /** Resolve which enchants land + at what level for the chosen slot tier (no cost/consumption here). */
    private static List<Landed> resolve(Map<Holder<Enchantment>, List<Book>> byEnchant, ItemStack item,
                                        boolean isBook, int slotId, long seed, ModConfig cfg) {
        RandomSource rng = RandomSource.create(seed + slotId);   // deterministic per slot → displayed cost == charged
        int chanceDenom = chanceDenom(cfg);
        int levelDenom = levelDenom(cfg);
        List<Guarantee> guarantees = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            if (!eligible(e.getKey(), cfg)) continue;                        // §5 curse/treasure gating
            List<Book> bks = e.getValue();
            double landChance = Math.min(1.0, (double) bks.size() / chanceDenom);
            int[] levels = bks.stream().mapToInt(Book::level).boxed()
                    .sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
            int sumTop = 0;
            for (int i = 0; i < Math.min(levelDenom, levels.length); i++) sumTop += levels[i];
            int maxLevel = Math.max(1, e.getKey().value().getMaxLevel());
            int avgLevel = Math.max(1, Math.min(maxLevel, (int) Math.round((double) sumTop / levelDenom)));
            guarantees.add(new Guarantee(e.getKey(), landChance, avgLevel, bks));
        }
        guarantees.sort(Comparator.comparingDouble(Guarantee::landChance).reversed());

        List<Holder<Enchantment>> accepted = new ArrayList<>(EnchantmentHelper.getEnchantmentsForCrafting(item).keySet());
        boolean allowConflict = isBook && cfg.allowConflictingOnBook;
        List<Landed> landed = new ArrayList<>();
        for (Guarantee g : guarantees) {
            if (rng.nextFloat() >= g.landChance()) continue;                  // didn't land
            int maxLevel = Math.max(1, g.enchant().value().getMaxLevel());
            int cap = slotCap(slotId, maxLevel, rng, cfg);                    // per-slot tier ceiling
            if (cap <= 0) continue;
            if (!isBook && !g.enchant().value().canEnchant(item)) continue;   // item-type compatibility
            if (!allowConflict) {
                boolean conflict = false;
                for (Holder<Enchantment> acc : accepted)
                    if (!acc.equals(g.enchant()) && !Enchantment.areCompatible(acc, g.enchant())) { conflict = true; break; }
                if (conflict) continue;
            }
            accepted.add(g.enchant());
            landed.add(new Landed(g.enchant(), Math.min(g.level(), cap), g.books()));
        }
        return landed;
    }

    /** Any two eligible, item-applicable stocked enchants that conflict → blank the table (§7). */
    public static boolean hasConflict(Map<Holder<Enchantment>, List<Book>> byEnchant, ItemStack item, boolean isBook, ModConfig cfg) {
        if (isBook && cfg.allowConflictingOnBook) return false;
        List<Holder<Enchantment>> elig = new ArrayList<>();
        for (Holder<Enchantment> e : byEnchant.keySet())
            if (eligible(e, cfg) && (isBook || e.value().canEnchant(item))) elig.add(e);
        for (int i = 0; i < elig.size(); i++)
            for (int j = i + 1; j < elig.size(); j++)
                if (!Enchantment.areCompatible(elig.get(i), elig.get(j))) return true;
        return false;
    }

    /**
     * Item-specific readout of what this table will do to {@code item} (for the /cench preview command —
     * players get the info from chat instead of the one-enchant-per-slot vanilla tooltip). Deterministic:
     * reports every eligible, item-applicable stocked enchant at its TOP-slot level + land chance, plus the
     * top-slot XP/lapis cost. The two cheaper slots apply reduced levels per slotTiers. Mirrors the exact
     * gating of {@link #moddedEnchant}/{@link #moddedSlots}, so an error here is the same blank you'd see.
     */
    public static TablePreview preview(Level level, BlockPos tablePos, ItemStack item) {
        if (!isModdedTable(level, tablePos)) return new TablePreview(TablePreview.Kind.VANILLA, List.of(), 0, 0);
        if (hasMixedShelves(level, tablePos)) return new TablePreview(TablePreview.Kind.MIXED, List.of(), 0, 0);
        ModConfig cfg = ModConfig.get();
        if (item.isEmpty()) return new TablePreview(TablePreview.Kind.EMPTY, List.of(), 0, 0);
        boolean isBook = item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK);
        if (isBook && !cfg.allowBookEnchanting) return new TablePreview(TablePreview.Kind.BOOK_DISABLED, List.of(), 0, 0);
        if (!isBook && !item.isEnchantable()) return new TablePreview(TablePreview.Kind.NOT_ENCHANTABLE, List.of(), 0, 0);
        Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);
        if (hasConflict(byEnchant, item, isBook, cfg)) return new TablePreview(TablePreview.Kind.CONFLICT, List.of(), 0, 0);

        int chanceDenom = chanceDenom(cfg), levelDenom = levelDenom(cfg);
        List<PreviewEnchant> lines = new ArrayList<>();
        List<Landed> forCost = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            Holder<Enchantment> ench = e.getKey();
            if (!eligible(ench, cfg)) continue;
            if (!isBook && !ench.value().canEnchant(item)) continue;      // silently skip incompatible
            List<Book> bks = e.getValue();
            double chance = Math.min(1.0, (double) bks.size() / chanceDenom);
            int[] levels = bks.stream().mapToInt(Book::level).boxed()
                    .sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
            int sumTop = 0;
            for (int i = 0; i < Math.min(levelDenom, levels.length); i++) sumTop += levels[i];
            int maxLevel = Math.max(1, ench.value().getMaxLevel());
            int topLevel = Math.max(1, Math.min(maxLevel, (int) Math.round((double) sumTop / levelDenom)));
            lines.add(new PreviewEnchant(ench, topLevel, chance));
            forCost.add(new Landed(ench, topLevel, bks));
        }
        if (lines.isEmpty()) return new TablePreview(TablePreview.Kind.NONE_APPLICABLE, List.of(), 0, 0);
        lines.sort(Comparator.comparingDouble(PreviewEnchant::landChance).reversed());
        return new TablePreview(TablePreview.Kind.OK, lines, xpCost(forCost, cfg), forCost.size());
    }

    /**
     * Item-agnostic sibling of {@link #preview}: what the shelves are configured to apply on ANY compatible
     * item (for /cench table). No held item, so no item-type filter — every eligible stocked enchant is listed
     * at its top-slot level + land chance, with the cost assuming all land. Same table-state gating otherwise.
     */
    public static TablePreview previewShelves(Level level, BlockPos tablePos) {
        if (!isModdedTable(level, tablePos)) return new TablePreview(TablePreview.Kind.VANILLA, List.of(), 0, 0);
        if (hasMixedShelves(level, tablePos)) return new TablePreview(TablePreview.Kind.MIXED, List.of(), 0, 0);
        ModConfig cfg = ModConfig.get();
        Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);

        // Item-agnostic conflict: any two eligible stocked enchants that conflict would blank a real table.
        List<Holder<Enchantment>> elig = new ArrayList<>();
        for (Holder<Enchantment> e : byEnchant.keySet()) if (eligible(e, cfg)) elig.add(e);
        for (int i = 0; i < elig.size(); i++)
            for (int j = i + 1; j < elig.size(); j++)
                if (!Enchantment.areCompatible(elig.get(i), elig.get(j)))
                    return new TablePreview(TablePreview.Kind.CONFLICT, List.of(), 0, 0);

        int chanceDenom = chanceDenom(cfg), levelDenom = levelDenom(cfg);
        List<PreviewEnchant> lines = new ArrayList<>();
        List<Landed> forCost = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            Holder<Enchantment> ench = e.getKey();
            if (!eligible(ench, cfg)) continue;
            List<Book> bks = e.getValue();
            double chance = Math.min(1.0, (double) bks.size() / chanceDenom);
            int[] levels = bks.stream().mapToInt(Book::level).boxed()
                    .sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
            int sumTop = 0;
            for (int i = 0; i < Math.min(levelDenom, levels.length); i++) sumTop += levels[i];
            int maxLevel = Math.max(1, ench.value().getMaxLevel());
            int topLevel = Math.max(1, Math.min(maxLevel, (int) Math.round((double) sumTop / levelDenom)));
            lines.add(new PreviewEnchant(ench, topLevel, chance));
            forCost.add(new Landed(ench, topLevel, bks));
        }
        if (lines.isEmpty()) return new TablePreview(TablePreview.Kind.NONE_APPLICABLE, List.of(), 0, 0);
        lines.sort(Comparator.comparingDouble(PreviewEnchant::landChance).reversed());
        return new TablePreview(TablePreview.Kind.OK, lines, xpCost(forCost, cfg), forCost.size());
    }

    /** Total XP levels the modded enchant costs = Σ ceil(costOfMaxEnchant × level / maxLevel), no cap. */
    private static int xpCost(List<Landed> landed, ModConfig cfg) {
        int total = 0;
        for (Landed l : landed) {
            int maxLevel = Math.max(1, l.ench().value().getMaxLevel());
            total += (int) Math.ceil((double) cfg.costOfMaxEnchant * l.level() / maxLevel);
        }
        return total;
    }

    /** Total XP points to reach {@code level} from 0 (vanilla curve) — for the flat "first-N-levels" charge. */
    public static int totalXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360.0);
        return (int) (4.5 * level * level - 162.5 * level + 2220.0);
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
                    BlockPos shelfPos = tablePos.offset(dx, h, dz);
                    if (!(level.getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity be)) continue;
                    if (!airGapClear(level, tablePos, dx, h, dz)) continue;   // our own gate (chiseled aren't power providers)
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

    /** Vanilla-style air gap: the distance-1 block toward the table is a power transmitter (air / non-occluding). */
    private static boolean airGapClear(Level level, BlockPos tablePos, int dx, int h, int dz) {
        return level.getBlockState(tablePos.offset(dx / 2, h, dz / 2)).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
    }

    /** Census of air-gapped shelves in the scan volume: [chiseled, regular] — drives modded/vanilla/mixed detection. */
    public static int[] shelfCensus(Level level, BlockPos tablePos) {
        ModConfig cfg = ModConfig.get();
        int layers = Math.max(1, cfg.scanLayers);
        int radius = Math.max(2, cfg.scanRadius);
        int chiseled = 0, regular = 0;
        for (int h = 0; h < layers; h++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;
                    if (!airGapClear(level, tablePos, dx, h, dz)) continue;
                    BlockPos shelfPos = tablePos.offset(dx, h, dz);
                    if (level.getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity) chiseled++;
                    else if (level.getBlockState(shelfPos).is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) regular++;
                }
            }
        }
        return new int[]{chiseled, regular};
    }

    /** A modded table = any chiseled shelf in range (even empty). */
    public static boolean isModdedTable(Level level, BlockPos tablePos) {
        return shelfCensus(level, tablePos)[0] > 0;
    }

    /** Mixed = chiseled AND regular shelves both present → error (blank the table). */
    public static boolean hasMixedShelves(Level level, BlockPos tablePos) {
        int[] c = shelfCensus(level, tablePos);
        return c[0] > 0 && c[1] > 0;
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
