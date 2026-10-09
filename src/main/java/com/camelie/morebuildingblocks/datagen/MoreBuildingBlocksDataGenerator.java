package com.camelie.morebuildingblocks.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.loader.api.FabricLoader;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.registry.MBBlocks;
import com.camelie.morebuildingblocks.registry.MBItems;

/**
 * Entry point for {@code ./gradlew runDatagen}.
 *
 * <p>Every model, blockstate, recipe and loot table this mod ships is produced
 * here; no JSON is written by hand.
 *
 * <p>Recipes and loot tables are registered directly. Models are not: the model
 * datagen API lives in the client half of Minecraft, which this source set cannot
 * see at compile time. That provider is therefore registered through the separate
 * {@value #MODEL_ENTRYPOINT} entrypoint, contributed by the client source set and
 * loaded by name at runtime. A normal game launch never reaches this code at all.
 */
public class MoreBuildingBlocksDataGenerator implements DataGeneratorEntrypoint {
	/** The extra entrypoint key the client source set registers models under. */
	public static final String MODEL_ENTRYPOINT = "morebuildingblocks-datagen";

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		// Blocks and their items must exist before any provider runs: the recipes
		// and loot tables reference them, and the model provider reads item ids.
		MBBlocks.bootstrap();

		// The creative tab is normally registered by MoreBuildingBlocks#onInitialize,
		// which the data generator does not run. Nothing here needs the tab, so this
		// is best effort only: on a normal run it is already registered (or the
		// registry is still open) and on a run where it is not, the frozen registry
		// is caught rather than failing data generation.
		try {
			MBItems.register();
		} catch (IllegalStateException e) {
			MoreBuildingBlocks.LOGGER.debug("Creative tab not registered during data generation: {}", e.getMessage());
		}

		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

		pack.addProvider(MBRecipeProvider::new);
		pack.addProvider(MBLootTableProvider::new);

		// Eager: an unloaded client class must fail the run with a clear message
		// rather than silently produce a resource pack with no models in it.
		FabricLoader.getInstance()
				.getEntrypointContainers(MODEL_ENTRYPOINT, ModelProviderFactory.class)
				.forEach(container -> container.getEntrypoint().addModelProvider(pack));
	}
}
