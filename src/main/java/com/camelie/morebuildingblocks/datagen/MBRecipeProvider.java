package com.camelie.morebuildingblocks.datagen;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;

import com.camelie.morebuildingblocks.registry.MBBlockFamilies;
import com.camelie.morebuildingblocks.registry.MBEighths;

/**
 * Crafting table and stonecutter recipes for every terracotta stairs and slab, and the stonecutter
 * recipe of every eighth block.
 *
 * <p>A stair and a slab can be assembled at a crafting table; an eighth block cannot. It is one
 * eighth of a block, so the only way to get one is to cut a block into eight of them.
 *
 * <p>Recipe ids are rewritten to the {@code morebuildingblocks} namespace by
 * Fabric's datagen mixins, so a builder saved through {@code save(this.output)}
 * lands in {@code data/morebuildingblocks/recipe/&lt;product&gt;.json}.
 *
 * <p>Fabric's {@link FabricRecipeProvider} hands the provider its own recipe and
 * advancement {@link BootstrapContext}s. The advancement context is kept as a
 * plain recording buffer: recipe unlock advancements are cosmetic, so they are
 * collected and then dropped rather than written into the datapack.
 */
public class MBRecipeProvider extends FabricRecipeProvider {
	public MBRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registryLookup,
			BootstrapContext<Recipe<?>> recipeOutput, BootstrapContext<Advancement> advancementOutput) {
		return new FamilyRecipeProvider(recipeOutput, new RecordingBootstrapContext<>(advancementOutput));
	}

	@Override
	public String getName() {
		return "MoreBuildingBlocks Recipes";
	}

	/** Emits one crafting recipe and two stonecutter recipes per block family. */
	private static final class FamilyRecipeProvider extends RecipeProvider {
		private FamilyRecipeProvider(BootstrapContext<Recipe<?>> recipeOutput,
				BootstrapContext<Advancement> advancementOutput) {
			super(recipeOutput, advancementOutput);
		}

		@Override
		public void buildRecipes() {
			for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
				Block base = family.base();
				Block stairs = family.stairs();
				Block slab = family.slab();
				Ingredient baseIngredient = Ingredient.of(base);
				String criterionName = getHasName(base);

				// Crafting table: 6 blocks -> 4 stairs.
				this.stairBuilder(stairs, baseIngredient)
						.unlockedBy(criterionName, this.has(base))
						.group("stairs")
						.save(this.output);

				// Crafting table: 3 blocks -> 6 slabs.
				this.slabBuilder(RecipeCategory.BUILDING_BLOCKS, slab, baseIngredient)
						.unlockedBy(criterionName, this.has(base))
						.group("slabs")
						.save(this.output);

				// Stonecutter: 1 block -> 1 stair.
				this.stonecutterResultFromBase(RecipeCategory.BUILDING_BLOCKS, stairs, base);

				// Stonecutter: 1 block -> 2 slabs, matching the vanilla yield.
				this.stonecutterResultFromBase(RecipeCategory.BUILDING_BLOCKS, slab, base, 2);
			}

			for (MBEighths.Material material : MBEighths.materials()) {
				// Stonecutter: 1 block -> 8 eighth blocks. One cut, eight pieces, and no crafting
				// table recipe: a block is cut into eighths, never assembled out of them.
				this.stonecutterResultFromBase(RecipeCategory.BUILDING_BLOCKS, material.eighth(), material.base(), 8);
			}
		}
	}

	/**
	 * A {@link BootstrapContext} that records whatever is registered into it.
	 *
	 * <p>Lookups are delegated to the wrapped context so that recipe builders can
	 * still resolve vanilla items and tags.
	 */
	private record RecordingBootstrapContext<T>(BootstrapContext<T> delegate) implements BootstrapContext<T> {
		@Override
		public Holder.Reference<T> register(ResourceKey<T> key, T value) {
			return this.delegate.register(key, value);
		}

		@Override
		public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> registryKey) {
			return this.delegate.lookup(registryKey);
		}

		@Override
		public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> registryKey) {
			return this.delegate.listContextElements(registryKey);
		}
	}
}
