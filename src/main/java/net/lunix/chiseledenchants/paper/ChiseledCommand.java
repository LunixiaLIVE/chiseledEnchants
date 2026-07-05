package net.lunix.chiseledenchants.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /chiseledenchants} (alias {@code /cench}). Paper equivalent of the mod's ChiseledCommands.
 * {@code about} / {@code reload} / {@code guide} work; {@code preview} / {@code table} / {@code find}
 * are stubbed until the shelf-scan + targeting logic lands.
 */
public final class ChiseledCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("about", "reload", "guide", "preview", "table", "find");

    private final ChiseledEnchantsPaper plugin;

    public ChiseledCommand(ChiseledEnchantsPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "about" -> {
                sender.sendMessage("§6chiseledEnchants§r (Paper) v" + plugin.getPluginMeta().getVersion());
                sender.sendMessage("§7Targeted enchanting via chiseled bookshelves.");
                if (!PluginConfig.linkGithub.isBlank()) sender.sendMessage("§7GitHub: §b" + PluginConfig.linkGithub);
                if (!PluginConfig.linkModrinth.isBlank()) sender.sendMessage("§7Modrinth: §b" + PluginConfig.linkModrinth);
            }
            case "reload" -> {
                if (!sender.hasPermission("chiseledenchants.reload")) {
                    sender.sendMessage("§cYou don't have permission to reload the config.");
                    return true;
                }
                PluginConfig.load(plugin);
                sender.sendMessage("§achiseledEnchants config reloaded.");
            }
            case "guide" -> {
                if (sender instanceof Player player) {
                    player.getInventory().addItem(GuideBook.create());
                    sender.sendMessage("§aHere's the chiseledEnchants guide.");
                } else {
                    sender.sendMessage("§cOnly players can receive the guide book.");
                }
            }
            case "preview", "table", "find" ->
                    sender.sendMessage("§7/cench " + sub + " — coming soon (targeting logic not implemented yet).");
            default ->
                    sender.sendMessage("§6chiseledEnchants§r — /cench about | reload | guide | preview | table | find");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return SUBS.stream().filter(s -> s.startsWith(p)).toList();
        }
        return List.of();
    }
}
