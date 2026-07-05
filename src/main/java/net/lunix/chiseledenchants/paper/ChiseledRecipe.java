package net.lunix.chiseledenchants.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Registers the crafted "Chiseled Enchanter" — a specially-named enchanting table. The result's name carries
 * the LIGHT_PURPLE colour so a vanilla anvil can't reproduce it (craft-only, when {@code craftOnlyTable}).
 * Vanilla enchanting-table shape with the ingredients swapped. Paper equivalent of the mod's ChiseledRecipe.
 * TODO: read the ingredients from config ({@code recipeReplaces*}) instead of hard-coding them.
 */
public final class ChiseledRecipe {

    private ChiseledRecipe() {}

    public static void register(Plugin plugin) {
        ItemStack result = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(PluginConfig.specialTableName, NamedTextColor.LIGHT_PURPLE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        result.setItemMeta(meta);

        NamespacedKey key = new NamespacedKey(plugin, "chiseled_enchanter");
        plugin.getServer().removeRecipe(key);   // idempotent across /reload
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(" B ", "DOD", "OOO");
        recipe.setIngredient('B', Material.DRAGON_HEAD);
        recipe.setIngredient('D', Material.NETHERITE_INGOT);
        recipe.setIngredient('O', Material.OBSIDIAN);
        plugin.getServer().addRecipe(recipe);
    }
}
