package net.lunix.chiseledenchants;

import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Duck interface implemented by {@code RecipeManagerMixin} so the {@code /cench admin reload} command can rebuild
 * the config-driven "Chiseled Enchanter" recipe at runtime — without a full datapack {@code /reload}. Swapping the
 * recipe map applies edited ingredients to live crafting, and recomputing the derived recipe-book caches (via the
 * passed feature set) lets the caller resync clients so the recipe book display updates too.
 */
public interface RecipeRebuilder {
    void chiseledEnchants$rebuildRecipe(FeatureFlagSet features);
}
