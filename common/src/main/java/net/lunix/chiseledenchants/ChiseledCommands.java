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
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /chiseledenchants} (alias {@code /cench}):
 *   • (no args)          — summarize the chiseled setup of the enchanting table you're looking at
 *   • find &lt;enchant&gt;  — trace colored particle threads from the table to each shelf holding it
 *   • reload             — reload the config from disk (op / gamemaster permission)
 */
public final class ChiseledCommands {

    private ChiseledCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
                Commands.literal("chiseledenchants")
                        .executes(ChiseledCommands::summary)
                        .then(Commands.literal("find")
                                .then(Commands.argument("enchant", StringArgumentType.greedyString())
                                        .executes(ctx -> find(ctx, StringArgumentType.getString(ctx, "enchant")))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(ChiseledCommands::reload)));
        dispatcher.register(Commands.literal("cench").redirect(root));
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
        int levelDenom = ChiseledEnchanting.levelDenom(cfg);
        Map<Holder<Enchantment>, List<ChiseledEnchanting.Book>> byEnchant = ChiseledEnchanting.scan(level, tablePos);

        src.sendSuccess(() -> Component.literal("── Chiseled Enchanting ──").withStyle(ChatFormatting.GOLD), false);
        if (byEnchant.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No single-enchant books in the chiseled shelves around this table.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        int totalBooks = byEnchant.values().stream().mapToInt(List::size).sum();
        src.sendSuccess(() -> Component.literal(byEnchant.size() + " enchant(s), " + totalBooks
                + " book(s)  ·  " + chanceDenom + " books = 100% land, level averaged over " + levelDenom)
                .withStyle(ChatFormatting.GRAY), false);

        List<Map.Entry<Holder<Enchantment>, List<ChiseledEnchanting.Book>>> entries = new ArrayList<>(byEnchant.entrySet());
        entries.sort(Comparator.comparing(e -> e.getKey().getRegisteredName()));
        for (Map.Entry<Holder<Enchantment>, List<ChiseledEnchanting.Book>> e : entries) {
            src.sendSuccess(() -> line(e.getKey(), e.getValue(), chanceDenom, levelDenom, cfg), false);
        }
        return 1;
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
        Vec3 target = Vec3.atCenterOf(tablePos);

        Set<Holder<Enchantment>> matched = new HashSet<>();
        int shelvesLit = 0;
        for (Map.Entry<Holder<Enchantment>, List<ChiseledEnchanting.Book>> e : byEnchant.entrySet()) {
            Holder<Enchantment> ench = e.getKey();
            String id = ench.getRegisteredName().toLowerCase(Locale.ROOT);
            String name = ench.value().description().getString().toLowerCase(Locale.ROOT);
            if (!id.contains(q) && !name.contains(q)) continue;
            matched.add(ench);
            int color = 0xFF000000 | colorFor(ench);
            Set<BlockPos> shelves = new HashSet<>();
            for (ChiseledEnchanting.Book b : e.getValue()) shelves.add(b.pos());
            for (BlockPos shelf : shelves) {
                Vec3 from = Vec3.atCenterOf(shelf);
                level.sendParticles(new TrailParticleOption(target, color, 30),
                        from.x, from.y, from.z, 14, 0.16, 0.2, 0.16, 0.0);
                shelvesLit++;
            }
        }
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

    /** Stable bright color per enchant (hashed hue). */
    private static int colorFor(Holder<Enchantment> ench) {
        float hue = Math.floorMod(ench.getRegisteredName().hashCode(), 360) / 360f;
        return Mth.hsvToRgb(hue, 0.85f, 1.0f);
    }

    private static Component line(Holder<Enchantment> ench, List<ChiseledEnchanting.Book> books,
                                  int chanceDenom, int levelDenom, ModConfig cfg) {
        int maxLevel = Math.max(1, ench.value().getMaxLevel());
        int n = books.size();

        int[] exact = new int[maxLevel + 1];
        for (ChiseledEnchanting.Book b : books) {
            if (b.level() >= 1 && b.level() <= maxLevel) exact[b.level()]++;
        }
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

        int chancePct = (int) Math.round(100.0 * Math.min(1.0, (double) n / chanceDenom));
        int[] levels = books.stream().mapToInt(ChiseledEnchanting.Book::level).boxed()
                .sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
        int sumTop = 0;
        for (int i = 0; i < Math.min(levelDenom, levels.length); i++) sumTop += levels[i];
        int level = Math.max(1, Math.min(maxLevel, (int) Math.round((double) sumTop / levelDenom)));

        out.append(Component.literal("  → " + chancePct + "% land, " + roman(level))
                .withStyle(chancePct >= 100 ? ChatFormatting.GREEN : ChatFormatting.AQUA));
        int excess = Math.max(0, n - Math.max(chanceDenom, levelDenom));
        if (excess > 0) {
            out.append(Component.literal("  (⚠ " + excess + " excess book" + (excess == 1 ? "" : "s") + ")")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return out;
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private static String roman(int n) {
        return (n >= 1 && n < ROMAN.length) ? ROMAN[n] : Integer.toString(n);
    }
}
