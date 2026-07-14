package net.lunix.chiseledenchants;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
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

    /** The one enabled enchant option: the top (max-level) slot. The two cheaper slots are disabled. */
    public static final int TOP_SLOT = 2;

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
            if (slotId != TOP_SLOT) return;                                       // only the top option is enabled
            if (hasMixedShelves(level, tablePos)) return;                         // blank/error — no enchant
            ItemStack item = enchantSlots.getItem(0);
            if (item.isEmpty()) return;
            ModConfig cfg = ModConfig.get();
            boolean isBook = item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK);
            if (isBook && !cfg.allowBookEnchanting) return;

            RandomSource rng = level.getRandom();                       // truly-random book consumption
            Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);
            if (cfg.resolveConflicts ? tieConflict(byEnchant, item, isBook, cfg)
                    : hasConflict(byEnchant, item, isBook, cfg)) return;   // conflicting library — table is blank
            List<Landed> landed = resolve(byEnchant, item, isBook, seed, cfg);   // same seed as the displayed slot
            if (landed.isEmpty()) return;

            int xpLevels = xpCost(landed, cfg);                         // §5/§6 — XP is per landed enchant
            int lapisRequired = lapisCost(cfg);                         // §6 — the flat lapis cost that unlocks the option
            ItemStack lapis = enchantSlots.getItem(1);
            int lapisAvail = lapis.is(lapisItem(cfg)) ? lapis.getCount() : 0;   // the table requires the configured lapis type
            boolean creative = player.hasInfiniteMaterials();
            if (!creative && lapisAvail < lapisRequired) {
                player.sendOverlayMessage(Component.literal("The " + cfg.specialTableName + " needs "
                        + lapisRequired + " lapis " + lapisNoun(cfg) + ".").withStyle(ChatFormatting.RED));
                return;
            }
            if (!creative && player.experienceLevel < xpLevels) return;

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

            // Lapis BLOCKS (and books) are consumed in BOTH game modes so the table's real cost shows even in
            // creative. The option's required blocks unlock the level. When book protection is ON, every block
            // BEYOND that adds protectionPerBlock protection (0–1) up to the maxProtection ceiling and is eaten
            // too (blocks past the ceiling aren't consumed); when OFF, only the required blocks are taken.
            int protBlocks = 0;
            double protection = 0.0;
            if (cfg.bookProtectionEnabled) {
                double perBlock = Math.max(0.0, cfg.protectionPerBlock);
                double maxProt = Math.max(0.0, Math.min(1.0, cfg.maxProtection));
                int blocksForCap = perBlock > 0.0 ? (int) Math.ceil(maxProt / perBlock) : 0;
                protBlocks = Math.min(Math.max(0, lapisAvail - lapisRequired), blocksForCap);
                protection = Math.min(maxProt, protBlocks * perBlock);
            }
            int lapisSpent = lapisRequired + protBlocks;
            lapis.shrink(lapisSpent);
            double consume = cfg.bookConsumeChance * (1.0 - protection);
            for (Landed l : landed) {
                if (rng.nextFloat() >= consume) continue;
                l.books().stream().min(Comparator.comparingInt(Book::level)).ifPresent(b -> consumeBook(level, b));
            }

            player.onEnchantmentPerformed(item, lapisSpent);
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
     * slotsChanged override for MODDED tables: only the TOP option is enabled — it's clickable when the setup
     * is valid and the required lapis is present, else blank (cost 0). The two cheaper slots are always blank
     * (the mod grants max-level enchants only, so there are no reduced tiers). Vanilla tables are untouched.
     */
    public static void moddedSlots(Container enchantSlots, ContainerLevelAccess access, long seed,
                                   int[] costs, int[] enchantClue, int[] levelClue) {
        access.execute((level, tablePos) -> {
            if (level.isClientSide() || !isModdedTable(level, tablePos)) return;
            for (int i = 0; i < 3; i++) { costs[i] = 0; enchantClue[i] = -1; levelClue[i] = -1; }   // all blank by default
            ItemStack item = enchantSlots.getItem(0);
            ModConfig cfg = ModConfig.get();
            boolean isBook = item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK);
            Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);
            boolean err = item.isEmpty() || !item.isEnchantable()
                    || (isBook && !cfg.allowBookEnchanting)
                    || hasMixedShelves(level, tablePos)
                    || (cfg.resolveConflicts ? tieConflict(byEnchant, item, isBook, cfg)
                            : hasConflict(byEnchant, item, isBook, cfg));
            if (err) return;
            ItemStack lapis = enchantSlots.getItem(1);
            int lapisAvail = lapis.is(lapisItem(cfg)) ? lapis.getCount() : 0;   // the option unlocks once its lapis is in
            if (lapisAvail < lapisCost(cfg)) return;
            List<Landed> preview = resolve(byEnchant, item, isBook, seed, cfg);    // same seed as the click
            if (preview.isEmpty()) return;
            costs[TOP_SLOT] = Math.max(1, xpCost(preview, cfg));                   // real XP cost == what's charged
            // A vanilla client shows only ONE enchant per slot, so surface the strongest as the clue for the
            // enabled slot; the full set that will ALL be applied on click is available via /cench.
            List<Landed> ranked = new ArrayList<>(preview);
            ranked.sort(Comparator.comparingInt(Landed::level).reversed());
            Landed pick = ranked.get(0);
            enchantClue[TOP_SLOT] = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getId(pick.ench().value());
            levelClue[TOP_SLOT] = pick.level();
        });
    }

    /**
     * Resolve which enchants land (no cost/consumption here). Only the enchant's MAX-level books count — this
     * is what blocks book laundering (you can't average low books up into a level you never earned), so every
     * landed enchant is granted at its max. Conflicts are resolved by max-book count before the land roll.
     */
    private static List<Landed> resolve(Map<Holder<Enchantment>, List<Book>> byEnchant, ItemStack item,
                                        boolean isBook, long seed, ModConfig cfg) {
        RandomSource rng = RandomSource.create(seed);   // shared seed → displayed cost == charged
        List<Guarantee> guarantees = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            if (!eligible(e.getKey(), cfg)) continue;                        // §5 curse/treasure gating
            List<Book> counting = countingBooks(e.getKey(), e.getValue(), cfg);   // max-only, or all (boost)
            if (counting.isEmpty()) continue;                               // no max book → ignored
            double landChance = landChance(e.getKey(), counting, cfg);
            int level = Math.max(1, e.getKey().value().getMaxLevel());
            guarantees.add(new Guarantee(e.getKey(), landChance, level, counting));
        }
        // Order for conflict resolution: MOST MAX-LEVEL books first (the unlocking books — boost doesn't change
        // who wins a conflict), ties broken alphabetically by id (deterministic).
        guarantees.sort(Comparator.comparingInt((Guarantee g) -> maxBooks(g.enchant(), g.books()).size()).reversed()
                .thenComparing(g -> g.enchant().getRegisteredName()));

        List<Holder<Enchantment>> accepted = new ArrayList<>(EnchantmentHelper.getEnchantmentsForCrafting(item).keySet());
        boolean allowConflict = isBook && cfg.allowConflictingOnBook;

        // Resolve conflicts BEFORE the land roll, so the higher-book-count enchant always wins its group and the
        // loser is dropped outright (never granted) — regardless of whether the winner rolls to land.
        List<Guarantee> kept = new ArrayList<>();
        for (Guarantee g : guarantees) {
            if (!isBook && !g.enchant().value().canEnchant(item)) continue;   // item-type compatibility
            if (!allowConflict) {
                boolean conflict = false;
                for (Holder<Enchantment> acc : accepted)
                    if (!acc.equals(g.enchant()) && !Enchantment.areCompatible(acc, g.enchant())) { conflict = true; break; }
                if (conflict) continue;                                        // loses the conflict (fewer books)
            }
            accepted.add(g.enchant());
            kept.add(g);
        }

        // Land roll on the survivors — each lands at its MAX level (no per-slot cap: there's only one tier).
        List<Landed> landed = new ArrayList<>();
        for (Guarantee g : kept) {
            if (rng.nextFloat() >= g.landChance()) continue;                  // didn't land
            landed.add(new Landed(g.enchant(), g.level(), g.books()));
        }
        return landed;
    }

    /** Only the enchant's MAX-level books count toward a guarantee — below-max books are flagged, never used. */
    private static List<Book> maxBooks(Holder<Enchantment> ench, List<Book> all) {
        int max = Math.max(1, ench.value().getMaxLevel());
        List<Book> out = new ArrayList<>();
        for (Book b : all) if (b.level() >= max) out.add(b);
        return out;
    }

    /**
     * The books that COUNT for an enchant: only its max-level books, or — when {@code smallBooksChanceBoost} is
     * on AND a max book is present — ALL its books (below-max ones help the chance). Empty when no max-level book
     * of that enchant is stocked (below-max-only enchants are ignored, preserving the anti-launder rule).
     */
    static List<Book> countingBooks(Holder<Enchantment> ench, List<Book> all, ModConfig cfg) {
        List<Book> max = maxBooks(ench, all);
        if (max.isEmpty()) return List.of();
        return cfg.smallBooksChanceBoost ? all : max;
    }

    /**
     * Land chance from the counting books. Without the boost: min(1, maxBooks / booksForFullChance). With it:
     * min(1, Σ (1/booksForFullChance) × (bookLevel / maxLevel)) — each book adds a level-weighted share, a max
     * book adding a full 1/booksForFullChance.
     */
    static double landChance(Holder<Enchantment> ench, List<Book> counting, ModConfig cfg) {
        if (counting.isEmpty()) return 0.0;
        double denom = chanceDenom(cfg);
        if (!cfg.smallBooksChanceBoost) return Math.min(1.0, counting.size() / denom);
        int max = Math.max(1, ench.value().getMaxLevel());
        double sum = 0.0;
        for (Book b : counting) sum += (1.0 / denom) * (Math.min(b.level(), max) / (double) max);
        return Math.min(1.0, sum);
    }

    /** The lapis item the modded table accepts/consumes (config): lapis blocks or gems. */
    public static Item lapisItem(ModConfig cfg) {
        return cfg.useBlocks ? Items.LAPIS_BLOCK : Items.LAPIS_LAZULI;
    }

    /** "blocks" / "gems" — for player-facing text. */
    public static String lapisNoun(ModConfig cfg) {
        return cfg.useBlocks ? "blocks" : "gems";
    }

    /**
     * True if any ELIGIBLE stocked enchant has a below-max book that WON'T count (status bar goes red). Without
     * the boost, every below-max book is dead weight. With the boost, a below-max book only fails to count when
     * its enchant has NO max-level book present (so those books are ignored).
     */
    public static boolean hasFlaggedBooks(Map<Holder<Enchantment>, List<Book>> byEnchant, ModConfig cfg) {
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            if (!eligible(e.getKey(), cfg)) continue;
            int max = Math.max(1, e.getKey().value().getMaxLevel());
            boolean hasMax = !maxBooks(e.getKey(), e.getValue()).isEmpty();
            for (Book b : e.getValue()) {
                if (b.level() >= max) continue;                              // a max book — fine
                if (!cfg.smallBooksChanceBoost || !hasMax) return true;      // this below-max book can't count
            }
        }
        return false;
    }

    /** Any two eligible, item-applicable stocked enchants that conflict → blank the table (§7). */
    public static boolean hasConflict(Map<Holder<Enchantment>, List<Book>> byEnchant, ItemStack item, boolean isBook, ModConfig cfg) {
        if (isBook && cfg.allowConflictingOnBook) return false;
        List<Holder<Enchantment>> elig = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet())
            if (eligible(e.getKey(), cfg) && !maxBooks(e.getKey(), e.getValue()).isEmpty()
                    && (isBook || e.getKey().value().canEnchant(item))) elig.add(e.getKey());   // only counting enchants
        for (int i = 0; i < elig.size(); i++)
            for (int j = i + 1; j < elig.size(); j++)
                if (!Enchantment.areCompatible(elig.get(i), elig.get(j))) return true;
        return false;
    }

    /** Item-agnostic conflict among the shelves' COUNTING (max-book) enchants — for /cench table + the status bar. */
    private static boolean anyConflictShelves(Map<Holder<Enchantment>, List<Book>> byEnchant, ModConfig cfg) {
        List<Holder<Enchantment>> elig = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet())
            if (eligible(e.getKey(), cfg) && !maxBooks(e.getKey(), e.getValue()).isEmpty()) elig.add(e.getKey());
        for (int i = 0; i < elig.size(); i++)
            for (int j = i + 1; j < elig.size(); j++)
                if (!Enchantment.areCompatible(elig.get(i), elig.get(j))) return true;
        return false;
    }

    /**
     * With resolveConflicts on, two conflicting contenders TIED on book count is ambiguous → blank the table so
     * the player can see it (/cench table) and add a book to break the tie, instead of a coin-flip enchant. A
     * clear winner (more books) does not error. {@code item} may be null for an item-agnostic check.
     */
    public static boolean tieConflict(Map<Holder<Enchantment>, List<Book>> byEnchant, ItemStack item, boolean isBook, ModConfig cfg) {
        if (isBook && cfg.allowConflictingOnBook) return false;
        List<Map.Entry<Holder<Enchantment>, Integer>> contenders = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            int count = maxBooks(e.getKey(), e.getValue()).size();   // only max-level books count toward the tie
            if (count == 0) continue;
            boolean applicable = isBook || item == null || e.getKey().value().canEnchant(item);
            if (eligible(e.getKey(), cfg) && applicable) contenders.add(Map.entry(e.getKey(), count));
        }
        contenders.sort(Comparator.comparingInt(
                (Map.Entry<Holder<Enchantment>, Integer> e) -> e.getValue()).reversed());
        List<Map.Entry<Holder<Enchantment>, Integer>> kept = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, Integer> g : contenders) {
            boolean dropped = false;
            for (Map.Entry<Holder<Enchantment>, Integer> k : kept) {
                if (!Enchantment.areCompatible(k.getKey(), g.getKey())) {
                    if (k.getValue().intValue() == g.getValue().intValue()) return true;   // equal books + conflict = ambiguous
                    dropped = true;                                                        // g loses to a higher-count enchant
                    break;
                }
            }
            if (!dropped) kept.add(g);
        }
        return false;
    }

    /**
     * Item-specific readout of what this table will do to {@code item} (for the /cench preview command —
     * players get the info from chat instead of the one-enchant-per-slot vanilla tooltip). Deterministic:
     * reports every eligible, item-applicable COUNTING (max-book) enchant at its max level + land chance, plus
     * the XP/lapis cost. Mirrors the exact gating of {@link #moddedEnchant}/{@link #moddedSlots}, so an error
     * here is the same blank you'd see (below-max books are excluded — they're flagged, not applied).
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
        if (cfg.resolveConflicts ? tieConflict(byEnchant, item, isBook, cfg)
                : hasConflict(byEnchant, item, isBook, cfg))
            return new TablePreview(TablePreview.Kind.CONFLICT, List.of(), 0, 0);

        List<PreviewEnchant> lines = new ArrayList<>();
        List<Landed> forCost = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            Holder<Enchantment> ench = e.getKey();
            if (!eligible(ench, cfg)) continue;
            if (!isBook && !ench.value().canEnchant(item)) continue;      // silently skip incompatible
            List<Book> counting = countingBooks(ench, e.getValue(), cfg);
            if (counting.isEmpty()) continue;                            // no max book → doesn't apply
            double chance = landChance(ench, counting, cfg);
            int lvl = Math.max(1, ench.value().getMaxLevel());
            lines.add(new PreviewEnchant(ench, lvl, chance));
            forCost.add(new Landed(ench, lvl, counting));
        }
        if (lines.isEmpty()) return new TablePreview(TablePreview.Kind.NONE_APPLICABLE, List.of(), 0, 0);
        lines.sort(Comparator.comparingDouble(PreviewEnchant::landChance).reversed());
        return new TablePreview(TablePreview.Kind.OK, lines, xpCost(forCost, cfg), lapisCost(cfg));
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

        // Item-agnostic conflict: resolveConflicts on → only an equal-book tie blanks; off → any conflict blanks.
        boolean conflict = cfg.resolveConflicts ? tieConflict(byEnchant, null, false, cfg)
                : anyConflictShelves(byEnchant, cfg);
        if (conflict) return new TablePreview(TablePreview.Kind.CONFLICT, List.of(), 0, 0);

        List<PreviewEnchant> lines = new ArrayList<>();
        List<Landed> forCost = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<Book>> e : byEnchant.entrySet()) {
            Holder<Enchantment> ench = e.getKey();
            if (!eligible(ench, cfg)) continue;
            List<Book> counting = countingBooks(ench, e.getValue(), cfg);
            if (counting.isEmpty()) continue;                           // no max book → doesn't apply
            double chance = landChance(ench, counting, cfg);
            int lvl = Math.max(1, ench.value().getMaxLevel());
            lines.add(new PreviewEnchant(ench, lvl, chance));
            forCost.add(new Landed(ench, lvl, counting));
        }
        if (lines.isEmpty()) return new TablePreview(TablePreview.Kind.NONE_APPLICABLE, List.of(), 0, 0);
        lines.sort(Comparator.comparingDouble(PreviewEnchant::landChance).reversed());
        return new TablePreview(TablePreview.Kind.OK, lines, xpCost(forCost, cfg), lapisCost(cfg));
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

    /** Flat lapis blocks required to use the table's single option; blocks beyond it buy book protection. */
    public static int lapisCost(ModConfig cfg) {
        return Math.max(0, cfg.lapisCost);
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

    /**
     * A modded table = the special-named enchanting table (the crafted gate, when enabled) that also has at
     * least one chiseled shelf in range. With the gate off, any table with a chiseled shelf qualifies.
     */
    public static boolean isModdedTable(Level level, BlockPos tablePos) {
        ModConfig cfg = ModConfig.get();
        if (cfg.requireSpecialTable && !isSpecialTable(level, tablePos, cfg)) return false;
        return shelfCensus(level, tablePos)[0] > 0;
    }

    /**
     * The crafted gate: an enchanting table whose custom name matches {@code specialTableName}. When
     * {@code craftOnlyTable}, the name must also carry the EPIC light-purple color the recipe stamps — a vanilla
     * anvil can't color a rename, so only the crafted table qualifies.
     */
    private static boolean isSpecialTable(Level level, BlockPos tablePos, ModConfig cfg) {
        if (!(level.getBlockEntity(tablePos) instanceof EnchantingTableBlockEntity be)) return false;
        Component name = be.getCustomName();
        if (name == null) return false;
        String want = cfg.specialTableName == null ? "" : cfg.specialTableName.trim();
        if (want.isEmpty() || !name.getString().trim().equalsIgnoreCase(want)) return false;
        if (!cfg.craftOnlyTable) return true;                              // any-color name accepted
        TextColor color = name.getStyle().getColor();                     // recipe stamps EPIC light-purple; anvil can't color
        return color != null && color.equals(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE));
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

    /**
     * Refresh the open table's status boss bar (called on open + whenever the slots change): RED with a reason
     * when the setup has a problem — mixed shelves, a conflict/tie, or a flagged below-max book — and GREEN
     * ("ready") otherwise. Item-agnostic: it reflects the SHELF setup, not whatever item is in the slot.
     */
    public static void updateNotice(ServerPlayer player, Container enchantSlots, ContainerLevelAccess access) {
        access.execute((level, tablePos) -> {
            if (level.isClientSide() || !isModdedTable(level, tablePos)) return;
            ModConfig cfg = ModConfig.get();
            Map<Holder<Enchantment>, List<Book>> byEnchant = scan(level, tablePos);
            String problem = null;
            if (hasMixedShelves(level, tablePos)) {
                problem = "Mixed shelves — remove the regular bookshelves";
            } else if (cfg.resolveConflicts ? tieConflict(byEnchant, null, false, cfg)
                    : anyConflictShelves(byEnchant, cfg)) {
                problem = cfg.resolveConflicts
                        ? "Conflicting enchants tied on books — add a book to break the tie"
                        : "Conflicting enchants stocked — remove the conflicts";
            } else if (hasFlaggedBooks(byEnchant, cfg)) {
                problem = "Some books aren't max-level — they won't count";
            }
            if (problem != null) {
                TableNotice.setBar(player, Component.literal("⚠ " + problem).withStyle(ChatFormatting.RED),
                        BossEvent.BossBarColor.RED);
            } else {
                String base = (cfg.tableOpenNotice == null || cfg.tableOpenNotice.isBlank())
                        ? (cfg.specialTableName == null ? "Chiseled Enchanter" : cfg.specialTableName)
                        : cfg.tableOpenNotice.trim();
                base = base.replace("{lapis}", lapisNoun(cfg));   // "blocks"/"gems" from the in-memory config
                TableNotice.setBar(player, Component.literal(base).withStyle(ChatFormatting.GREEN),
                        BossEvent.BossBarColor.GREEN);
            }
        });
    }

    /** §5 eligibility: curses + treasure gating (treasure = not in #minecraft:in_enchanting_table). */
    public static boolean eligible(Holder<Enchantment> ench, ModConfig cfg) {
        if (ench.is(EnchantmentTags.CURSE) && !cfg.allowCurses) return false;                    // curse master switch
        if (!ench.is(EnchantmentTags.IN_ENCHANTING_TABLE) && !cfg.allowTreasureEnchants) return false; // treasure master switch
        String id = ench.getRegisteredName();               // "namespace:path", e.g. "minecraft:mending"
        return cfg.enchantWhitelist.getOrDefault(id, true);  // per-enchant toggle (absent = allowed)
    }
}
