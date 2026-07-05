package net.lunix.chiseledenchants.mixin;

import net.lunix.chiseledenchants.ChiseledEnchantsCommon;
import net.lunix.chiseledenchants.ChiseledRecipe;
import net.lunix.chiseledenchants.ModConfig;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Build the "Chiseled Enchanter" recipe from config and inject it into the loaded recipe set (a datapack recipe
 * can't read config). Runs server-side at datapack load/reload; vanilla then handles crafting, consumption and
 * client sync. Config is re-read here first, so editing the config and running a single {@code /reload} applies
 * the new ingredients. Wrapped in try/catch so a failure leaves the bundled datapack recipe (same id) in place.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow private RecipeMap recipes;

    @Inject(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"))
    private void chiseledEnchants_addRecipe(RecipeMap map, ResourceManager rm, ProfilerFiller pf, CallbackInfo ci) {
        try {
            ModConfig.load();   // re-read config so a single /reload applies edited ingredients
            RecipeHolder<?> holder = ChiseledRecipe.build();
            List<RecipeHolder<?>> all = new ArrayList<>(this.recipes.values());
            all.removeIf(h -> h.id().equals(ChiseledRecipe.KEY));   // drop the datapack copy, use the config one
            all.add(holder);
            this.recipes = RecipeMap.create(all);
            ModConfig cfg = ModConfig.get();
            ChiseledEnchantsCommon.LOGGER.info(
                    "[chiseledEnchants] Injected Chiseled Enchanter recipe (book={}, diamond={}, obsidian={})",
                    cfg.recipeReplacesBook, cfg.recipeReplacesDiamond, cfg.recipeReplacesObsidian);
        } catch (Exception e) {
            ChiseledEnchantsCommon.LOGGER.error("[chiseledEnchants] Failed to inject the Chiseled Enchanter recipe "
                    + "(bundled datapack recipe stays active)", e);
        }
    }
}
