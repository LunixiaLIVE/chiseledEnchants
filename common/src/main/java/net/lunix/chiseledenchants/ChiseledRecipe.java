package net.lunix.chiseledenchants;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.Map;

/**
 * Builds the "Chiseled Enchanter" crafting recipe from config so its ingredients are configurable (a datapack
 * recipe can't read config). Injected into the loaded recipe set by {@link net.lunix.chiseledenchants.mixin.RecipeManagerMixin};
 * the bundled datapack recipe of the same id stays as a fallback if this ever fails to build/inject.
 */
public final class ChiseledRecipe {

    private ChiseledRecipe() {}

    /** Same id as the bundled datapack recipe, so injecting this replaces it. */
    public static final ResourceKey<Recipe<?>> KEY = ResourceKey.create(
            Registries.RECIPE, Identifier.fromNamespaceAndPath(ChiseledEnchantsCommon.MOD_ID, "chiseled_enchanter"));

    /** Build the recipe from the current config (vanilla enchanting-table shape with the configured swaps). */
    public static RecipeHolder<?> build() {
        ModConfig cfg = ModConfig.get();
        Item book = item(cfg.recipeReplacesBook, Items.NETHER_STAR);
        Item diamond = item(cfg.recipeReplacesDiamond, Items.NETHERITE_INGOT);
        Item obsidian = item(cfg.recipeReplacesObsidian, Items.OBSIDIAN);

        ShapedRecipePattern pattern = ShapedRecipePattern.of(
                Map.of('b', Ingredient.of(book), 'd', Ingredient.of(diamond), '#', Ingredient.of(obsidian)),
                " b ", "d#d", "###");

        String tableName = (cfg.specialTableName == null || cfg.specialTableName.isBlank())
                ? "Chiseled Enchanter" : cfg.specialTableName.trim();
        DataComponentPatch comps = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal(tableName)
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(false)))
                .set(DataComponents.RARITY, Rarity.RARE)
                .build();
        ItemStackTemplate result = new ItemStackTemplate(Items.ENCHANTING_TABLE, comps);

        ShapedRecipe recipe = new ShapedRecipe(
                new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.EQUIPMENT, ""),
                pattern, result);
        return new RecipeHolder<>(KEY, recipe);
    }

    private static Item item(String id, Item fallback) {
        if (id == null || id.isBlank()) return fallback;
        Identifier loc = Identifier.tryParse(id.trim());
        if (loc == null) { warn(id); return fallback; }
        return BuiltInRegistries.ITEM.getOptional(loc).orElseGet(() -> { warn(id); return fallback; });
    }

    private static void warn(String id) {
        ChiseledEnchantsCommon.LOGGER.warn("[chiseledEnchants] Unknown recipe item '{}' — using default.", id);
    }
}
