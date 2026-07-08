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

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the "Chiseled Enchanter" crafting recipe from config so its ingredients are configurable (a datapack
 * recipe can't read config). Injected into the loaded recipe set by {@link net.lunix.chiseledenchants.mixin.RecipeManagerMixin}
 * — same id as the bundled datapack recipe, so it replaces it and the recipe advancement still unlocks it in the
 * book. The bundled datapack recipe (also light-purple) stays as a fallback if this fails to build/inject.
 */
public final class ChiseledRecipe {

    private ChiseledRecipe() {}

    public static final ResourceKey<Recipe<?>> KEY = ResourceKey.create(
            Registries.RECIPE, Identifier.fromNamespaceAndPath(ChiseledEnchantsCommon.MOD_ID, "chiseled_enchanter"));

    /** Build the recipe from the current config (vanilla enchanting-table shape with the configured swaps). */
    public static RecipeHolder<?> build() {
        ModConfig cfg = ModConfig.get();
        Item book = item(cfg.recipeReplacesBook, Items.DRAGON_HEAD);
        Item diamond = item(cfg.recipeReplacesDiamond, Items.NETHERITE_INGOT);
        Item obsidian = item(cfg.recipeReplacesObsidian, Items.OBSIDIAN);
        Item corners = itemOrNull(cfg.recipeReplacesEmptySlots);   // null = leave the 2 corner slots empty

        Map<Character, Ingredient> keys = new HashMap<>();
        keys.put('b', Ingredient.of(book));
        keys.put('d', Ingredient.of(diamond));
        keys.put('#', Ingredient.of(obsidian));
        String topRow = " b ";
        if (corners != null) {
            keys.put('e', Ingredient.of(corners));
            topRow = "ebe";                                        // fill the two corners to make it pricier
        }
        ShapedRecipePattern pattern = ShapedRecipePattern.of(keys, topRow, "d#d", "###");

        String tableName = (cfg.specialTableName == null || cfg.specialTableName.isBlank())
                ? "Chiseled Enchanter" : cfg.specialTableName.trim();
        DataComponentPatch comps = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal(tableName)
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)))
                .set(DataComponents.RARITY, Rarity.EPIC)
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

    /** Like {@link #item} but blank/unknown → null (used for the optional empty-corner filler). */
    private static Item itemOrNull(String id) {
        if (id == null || id.isBlank()) return null;
        Identifier loc = Identifier.tryParse(id.trim());
        if (loc == null) { warn(id); return null; }
        return BuiltInRegistries.ITEM.getOptional(loc).orElseGet(() -> { warn(id); return null; });
    }

    private static void warn(String id) {
        ChiseledEnchantsCommon.LOGGER.warn("[chiseledEnchants] Unknown recipe item '{}' — using default.", id);
    }
}
