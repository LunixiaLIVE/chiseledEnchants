package net.lunix.chiseledenchants.mixin;

import net.lunix.chiseledenchants.ChiseledEnchantsCommon;
import net.lunix.chiseledenchants.ChiseledRecipe;
import net.lunix.chiseledenchants.ModConfig;
import net.lunix.chiseledenchants.RecipeRebuilder;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Build the "Chiseled Enchanter" recipe from config and inject it into the loaded recipe set (a datapack recipe
 * can't read config).
 *
 * <p>At datapack load/reload we swap the recipe into the incoming {@link RecipeMap} <em>before</em> vanilla
 * {@code apply()} processes it (via {@link ModifyVariable} on the map argument), so vanilla itself recomputes every
 * derived cache — crafting, recipe-book property sets and displays — from the config recipe. A single {@code /reload}
 * therefore fully applies edited ingredients, book included.
 *
 * <p>{@code /cench admin reload} calls {@link #chiseledEnchants$rebuildRecipe(FeatureFlagSet)} directly so recipe
 * edits apply live without a datapack reload: it swaps the map and re-runs {@code finalizeRecipeLoading} to refresh
 * the book caches; the command then resyncs clients. Wrapped in try/catch so a failure leaves the bundled datapack
 * recipe (same id) in place.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements RecipeRebuilder {

    @Shadow private RecipeMap recipes;

    @Shadow public abstract void finalizeRecipeLoading(FeatureFlagSet features);

    /**
     * Replace the recipe map heading into {@code apply()} with one carrying the config-driven recipe, so vanilla's
     * own finalize pass computes all derived caches (including the recipe-book display) from it.
     */
    @ModifyVariable(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private RecipeMap chiseledEnchants_swapRecipe(RecipeMap map) {
        ModConfig.load();   // re-read config so a single /reload applies edited ingredients
        return chiseledEnchants$rebuiltMap(map);
    }

    /**
     * Rebuild the config-driven recipe and swap it into the live recipe map, then recompute the recipe-book caches
     * so a subsequent client resync shows the edited recipe. The caller loads config first and resyncs clients after.
     */
    @Override
    public void chiseledEnchants$rebuildRecipe(FeatureFlagSet features) {
        this.recipes = chiseledEnchants$rebuiltMap(this.recipes);
        finalizeRecipeLoading(features);   // refresh propertySets / displays used by the recipe book
    }

    /** Return a copy of {@code base} with the bundled datapack copy of our recipe replaced by the config-built one. */
    @Unique
    private RecipeMap chiseledEnchants$rebuiltMap(RecipeMap base) {
        try {
            RecipeHolder<?> holder = ChiseledRecipe.build();
            List<RecipeHolder<?>> all = new ArrayList<>(base.values());
            all.removeIf(h -> h.id().equals(ChiseledRecipe.KEY));   // drop the datapack copy, use the config one
            all.add(holder);
            ModConfig cfg = ModConfig.get();
            ChiseledEnchantsCommon.LOGGER.info(
                    "[chiseledEnchants] Injected Chiseled Enchanter recipe (book={}, diamond={}, obsidian={})",
                    cfg.recipeReplacesBook, cfg.recipeReplacesDiamond, cfg.recipeReplacesObsidian);
            return RecipeMap.create(all);
        } catch (Exception e) {
            ChiseledEnchantsCommon.LOGGER.error("[chiseledEnchants] Failed to inject the Chiseled Enchanter recipe "
                    + "(bundled datapack recipe stays active)", e);
            return base;
        }
    }
}
