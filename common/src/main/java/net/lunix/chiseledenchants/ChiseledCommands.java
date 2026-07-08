package net.lunix.chiseledenchants;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /chiseledenchants} (alias {@code /cench}):
 *   • about              — mod info: version, how to get the guide book, and clickable links
 *   • (no args)          — summarize the chiseled setup of the enchanting table you're looking at
 *   • preview            — what the table will apply to the item in your MAIN HAND, with cost (chat readout
 *                          in place of the one-enchant-per-slot vanilla tooltip)
 *   • table              — what the SHELVES are configured to apply (item-agnostic), with cost
 *   • find &lt;enchant&gt;  — trace colored particle threads from the table to each shelf holding it
 *   • reload             — reload the config from disk (op / gamemaster permission)
 */
public final class ChiseledCommands {

    private ChiseledCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
                Commands.literal("chiseledenchants")
                        .executes(ChiseledCommands::summary)
                        .then(Commands.literal("about")
                                .executes(ChiseledCommands::about))
                        .then(Commands.literal("preview")
                                .executes(ChiseledCommands::preview))
                        .then(Commands.literal("table")
                                .executes(ChiseledCommands::previewTable))
                        .then(Commands.literal("find")
                                .then(Commands.argument("enchant", StringArgumentType.greedyString())
                                        .executes(ctx -> find(ctx, StringArgumentType.getString(ctx, "enchant")))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(ChiseledCommands::reload))
                        .then(Commands.literal("reset")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(ChiseledCommands::reset)));
        dispatcher.register(Commands.literal("cench").redirect(root));
    }

    /** Mod info: version, how to obtain the guide book, and clickable community/repo links (all config-driven). */
    private static int about(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ModConfig cfg = ModConfig.get();
        src.sendSuccess(() -> Component.literal("── chiseledEnchants v" + ChiseledEnchantsCommon.VERSION + " ──")
                .withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("Targeted enchanting via chiseled bookshelves.")
                .withStyle(ChatFormatting.GRAY), false);
        if (cfg.requireSpecialTable && cfg.specialTableName != null && !cfg.specialTableName.isBlank()) {
            String how = cfg.craftOnlyTable ? "crafted" : "crafted or anvil-renamed";
            src.sendSuccess(() -> Component.literal("Enchant at the \"")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(cfg.specialTableName.trim()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\" table (" + how + ") with chiseled shelves.")
                            .withStyle(ChatFormatting.WHITE)), false);
        }
        if (cfg.guideEnabled && cfg.guideKeyword != null && !cfg.guideKeyword.isBlank()) {
            src.sendSuccess(() -> Component.literal("Guide book: rename a book & quill to \"")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(cfg.guideKeyword.trim()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\" in an anvil.").withStyle(ChatFormatting.WHITE)), false);
        }
        src.sendSuccess(() -> Component.literal("Links:").withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> linkLine("GitHub", cfg.linkGithub), false);
        src.sendSuccess(() -> linkLine("Modrinth", cfg.linkModrinth), false);
        src.sendSuccess(() -> linkLine("CurseForge", cfg.linkCurseforge), false);
        src.sendSuccess(() -> linkLine("Discord", cfg.linkDiscord), false);
        return 1;
    }

    /** One "Name: url" line — url is a clickable OPEN_URL link, or a gray "coming soon" when blank/invalid. */
    private static Component linkLine(String name, String url) {
        MutableComponent line = Component.literal("  " + name + ": ").withStyle(ChatFormatting.GRAY);
        if (url == null || url.isBlank()) {
            return line.append(Component.literal("coming soon").withStyle(ChatFormatting.DARK_GRAY));
        }
        String u = url.trim();
        try {
            URI uri = URI.create(u);
            return line.append(Component.literal(u).withStyle(s -> s
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(uri))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open " + u)))));
        } catch (IllegalArgumentException e) {
            return line.append(Component.literal(u).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Raycast the enchanting table the player is looking at (6-block reach), or null. */
    private static BlockPos lookedAtTable(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(6.0));
        BlockHitResult hit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        return level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE) ? pos : null;
    }

    private static int summary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        BlockPos tablePos = lookedAtTable(player);
        if (tablePos == null) {
            src.sendFailure(Component.literal("Look directly at an enchanting table to inspect its setup."));
            return 0;
        }
        ServerLevel level = player.level();
        ModConfig cfg = ModConfig.get();
        int chanceDenom = ChiseledEnchanting.chanceDenom(cfg);
        Map<Holder<Enchantment>, List<ChiseledEnchanting.Book>> byEnchant = ChiseledEnchanting.scan(level, tablePos);

        src.sendSuccess(() -> Component.literal("── Chiseled Enchanting ──").withStyle(ChatFormatting.GOLD), false);
        if (byEnchant.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No single-enchant books in the chiseled shelves around this table.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        int totalBooks = byEnchant.values().stream().mapToInt(List::size).sum();
        String rule = cfg.smallBooksChanceBoost
                ? "max books needed to unlock; lower books boost the chance; " + chanceDenom + " = 100% land"
                : "only MAX-level books count; " + chanceDenom + " = 100% land";
        src.sendSuccess(() -> Component.literal(byEnchant.size() + " enchant(s), " + totalBooks
                + " book(s)  ·  " + rule).withStyle(ChatFormatting.GRAY), false);

        List<Map.Entry<Holder<Enchantment>, List<ChiseledEnchanting.Book>>> entries = new ArrayList<>(byEnchant.entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().getRegisteredName()));
        for (Map.Entry<Holder<Enchantment>, List<ChiseledEnchanting.Book>> e : entries) {
            src.sendSuccess(() -> line(e.getKey(), e.getValue(), chanceDenom, cfg), false);
        }
        return 1;
    }

    /** Chat readout of what the table will apply to the item in the player's MAIN HAND, with cost. */
    private static int preview(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        BlockPos tablePos = lookedAtTable(player);
        if (tablePos == null) {
            src.sendFailure(Component.literal("Look directly at an enchanting table to preview it."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        ChiseledEnchanting.TablePreview p = ChiseledEnchanting.preview(player.level(), tablePos, held);
        boolean flagged = ChiseledEnchanting.hasFlaggedBooks(
                ChiseledEnchanting.scan(player.level(), tablePos), ModConfig.get());
        renderPreview(src, held.getHoverName().getString(), "None of the stocked enchants apply to that item.", p, flagged);
        return 1;
    }

    /** Chat readout of what the SHELVES are configured to apply (item-agnostic), with cost. */
    private static int previewTable(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        BlockPos tablePos = lookedAtTable(player);
        if (tablePos == null) {
            src.sendFailure(Component.literal("Look directly at an enchanting table to inspect its shelves."));
            return 0;
        }
        ChiseledEnchanting.TablePreview p = ChiseledEnchanting.previewShelves(player.level(), tablePos);
        boolean flagged = ChiseledEnchanting.hasFlaggedBooks(
                ChiseledEnchanting.scan(player.level(), tablePos), ModConfig.get());
        renderPreview(src, "These shelves", "No eligible enchants are stocked in the shelves.", p, flagged);
        return 1;
    }

    /** Shared formatter for both preview commands. {@code subject} heads the OK list; {@code noneMsg} covers NONE. */
    private static void renderPreview(CommandSourceStack src, String subject, String noneMsg,
                                      ChiseledEnchanting.TablePreview p, boolean flagged) {
        src.sendSuccess(() -> Component.literal("── Table Preview ──").withStyle(ChatFormatting.GOLD), false);
        switch (p.kind()) {
            case VANILLA -> {
                ModConfig vcfg = ModConfig.get();
                String hint = vcfg.requireSpecialTable
                        ? "Normal enchanting here. Targeted enchanting needs the \"" + vcfg.specialTableName
                          + "\" table with chiseled shelves."
                        : "Normal enchanting here. Add chiseled shelves for targeted enchanting.";
                src.sendSuccess(() -> Component.literal(hint).withStyle(ChatFormatting.GRAY), false);
            }
            case MIXED -> src.sendSuccess(() -> Component.literal(
                    "⚠ Mixed shelves (chiseled + regular). The table is blanked — remove the regular bookshelves.")
                    .withStyle(ChatFormatting.RED), false);
            case CONFLICT -> {
                String cmsg = ModConfig.get().resolveConflicts
                        ? "⚠ Conflicting enchants are tied on book count (e.g. 6 Sharpness + 6 Smite). Add a book to one to break the tie."
                        : "⚠ Conflicting enchants stocked (e.g. Sharpness + Smite). The table is blanked — remove the conflicts.";
                src.sendSuccess(() -> Component.literal(cmsg).withStyle(ChatFormatting.RED), false);
            }
            case BOOK_DISABLED -> src.sendSuccess(() -> Component.literal(
                    "You're holding a book, but book enchanting is disabled (allowBookEnchanting).")
                    .withStyle(ChatFormatting.YELLOW), false);
            case EMPTY -> src.sendSuccess(() -> Component.literal(
                    "Hold the item you want to enchant in your main hand, then run this again.")
                    .withStyle(ChatFormatting.YELLOW), false);
            case NOT_ENCHANTABLE -> src.sendSuccess(() -> Component.literal(
                    "That item can't be enchanted.").withStyle(ChatFormatting.YELLOW), false);
            case NONE_APPLICABLE -> src.sendSuccess(() -> Component.literal(noneMsg).withStyle(ChatFormatting.GRAY), false);
            case OK -> {
                src.sendSuccess(() -> Component.literal(subject + " — applies at max level:")
                        .withStyle(ChatFormatting.GRAY), false);
                for (ChiseledEnchanting.PreviewEnchant pe : p.enchants()) {
                    src.sendSuccess(() -> {
                        int pct = (int) Math.round(pe.landChance() * 100);
                        return Component.literal("  • ")
                                .append(pe.enchant().value().description().copy().withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(" " + roman(pe.level())).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("  — " + pct + "% land")
                                        .withStyle(pct >= 100 ? ChatFormatting.GREEN : ChatFormatting.AQUA));
                    }, false);
                }
                String lnoun = ChiseledEnchanting.lapisNoun(ModConfig.get());
                src.sendSuccess(() -> Component.literal("Cost: " + p.xpLevels() + " XP level"
                        + (p.xpLevels() == 1 ? "" : "s") + " + " + p.lapisCost() + " lapis " + lnoun)
                        .withStyle(ChatFormatting.GOLD), false);
                if (ModConfig.get().bookProtectionEnabled) {
                    src.sendSuccess(() -> Component.literal("Extra lapis " + lnoun + " beyond the cost buy book protection.")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                }
            }
        }
        if (flagged) {
            src.sendSuccess(() -> Component.literal("⚠ Some stocked books are below max level — they won't count.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** Trace colored TRAIL particle threads from the table to each shelf holding a matching enchant. */
    private static int find(CommandContext<CommandSourceStack> ctx, String query) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        BlockPos tablePos = lookedAtTable(player);
        if (tablePos == null) {
            src.sendFailure(Component.literal("Look directly at an enchanting table to find its books."));
            return 0;
        }
        ServerLevel level = player.level();
        Map<Holder<Enchantment>, List<ChiseledEnchanting.Book>> byEnchant = ChiseledEnchanting.scan(level, tablePos);
        String q = query.trim().toLowerCase(Locale.ROOT);
        Vec3 origin = Vec3.atCenterOf(tablePos);   // pulses trace FROM the table OUT to each shelf

        Set<Holder<Enchantment>> matched = new HashSet<>();
        List<ParticleScheduler.Beam> beams = new ArrayList<>();
        for (Map.Entry<Holder<Enchantment>, List<ChiseledEnchanting.Book>> e : byEnchant.entrySet()) {
            Holder<Enchantment> ench = e.getKey();
            String id = ench.getRegisteredName().toLowerCase(Locale.ROOT);
            String name = ench.value().description().getString().toLowerCase(Locale.ROOT);
            if (!id.contains(q) && !name.contains(q)) continue;
            matched.add(ench);
            int color = 0xFF000000 | colorFor(ench);
            Set<BlockPos> shelves = new HashSet<>();
            for (ChiseledEnchanting.Book b : e.getValue()) shelves.add(b.pos());
            for (BlockPos shelf : shelves) beams.add(new ParticleScheduler.Beam(Vec3.atCenterOf(shelf), color));
        }
        int shelvesLit = beams.size();
        ModConfig cfg = ModConfig.get();
        ParticleScheduler.schedule(level, origin, beams, cfg.particleRepeats, cfg.particleDurationTicks,
                cfg.particleFromTable);
        if (matched.isEmpty()) {
            src.sendFailure(Component.literal("No shelves here hold an enchant matching \"" + query + "\"."));
            return 0;
        }
        final int lit = shelvesLit;
        src.sendSuccess(() -> Component.literal("Traced " + lit + " shelf/shelves for " + matched.size()
                + " matching enchant(s).").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ModConfig.load();
        ctx.getSource().sendSuccess(
                () -> Component.literal("chiseledEnchants config reloaded.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Write a fresh default config (old one backed up to .bak); the admin then applies it with /cench reload. */
    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ModConfig.writeFreshDefaults(ctx.getSource().getServer().registryAccess());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Wrote a fresh default chiseledEnchants config (old one saved as chiseledenchants.json.bak). "
                + "Run /cench reload to apply.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Stable bright color per enchant (hashed hue). */
    private static int colorFor(Holder<Enchantment> ench) {
        float hue = Math.floorMod(ench.getRegisteredName().hashCode(), 360) / 360f;
        return Mth.hsvToRgb(hue, 0.85f, 1.0f);
    }

    private static Component line(Holder<Enchantment> ench, List<ChiseledEnchanting.Book> books,
                                  int chanceDenom, ModConfig cfg) {
        int maxLevel = Math.max(1, ench.value().getMaxLevel());

        int[] exact = new int[maxLevel + 1];
        int maxCount = 0;
        for (ChiseledEnchanting.Book b : books) {
            if (b.level() >= maxLevel) maxCount++;                       // at (or above) the enchant's max = counts
            if (b.level() >= 1 && b.level() <= maxLevel) exact[b.level()]++;
        }
        int belowCount = books.size() - maxCount;
        StringBuilder bd = new StringBuilder();
        for (int l = maxLevel; l >= 1; l--) {
            if (exact[l] == 0) continue;
            if (bd.length() > 0) bd.append(", ");
            bd.append(exact[l]).append("× ").append(roman(l));
        }

        MutableComponent out = Component.empty()
                .append(ench.value().description().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" — " + bd).withStyle(ChatFormatting.GRAY));

        if (!ChiseledEnchanting.eligible(ench, cfg)) {
            return out.append(Component.literal("  → disabled by config").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (maxCount == 0) {                                             // no max book → nothing counts
            String why = cfg.smallBooksChanceBoost
                    ? "no " + roman(maxLevel) + " book — below-max are ignored without one"
                    : "no " + roman(maxLevel) + " books — below-max don't count";
            out.append(Component.literal("  → 0% (" + why + ")").withStyle(ChatFormatting.YELLOW));
            return out;
        }

        List<ChiseledEnchanting.Book> counting = ChiseledEnchanting.countingBooks(ench, books, cfg);
        int chancePct = (int) Math.round(100.0 * ChiseledEnchanting.landChance(ench, counting, cfg));
        out.append(Component.literal("  → " + chancePct + "% land, " + roman(maxLevel))
                .withStyle(chancePct >= 100 ? ChatFormatting.GREEN : ChatFormatting.AQUA));
        if (belowCount > 0) {
            out.append(cfg.smallBooksChanceBoost
                    ? Component.literal("  (+" + belowCount + " below-max boosting)").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.literal("  (⚠ " + belowCount + " below-max won't count)").withStyle(ChatFormatting.YELLOW));
        }
        int excess = Math.max(0, maxCount - chanceDenom);
        if (!cfg.smallBooksChanceBoost && excess > 0) {
            out.append(Component.literal("  (" + excess + " excess)").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private static String roman(int n) {
        return (n >= 1 && n < ROMAN.length) ? ROMAN[n] : Integer.toString(n);
    }
}
