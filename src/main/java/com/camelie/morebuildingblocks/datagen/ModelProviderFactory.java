package com.camelie.morebuildingblocks.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Contributed by the client source set under the
 * {@link MoreBuildingBlocksDataGenerator#MODEL_ENTRYPOINT} entrypoint key.
 *
 * <p>It exists so that the data generator entrypoint, which lives in the main
 * source set, can hand the model provider's registration over without naming any
 * client-only Minecraft class.
 */
public interface ModelProviderFactory {
	/** Registers every client-side provider this mod ships. */
	void addModelProvider(FabricDataGenerator.Pack pack);
}
