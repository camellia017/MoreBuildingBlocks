package com.camelie.morebuildingblocks.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

import com.camelie.morebuildingblocks.datagen.ModelProviderFactory;

/**
 * Registers the client half of this mod's data generation.
 *
 * <p>Listed in {@code fabric.mod.json} under the
 * {@value com.camelie.morebuildingblocks.datagen.MoreBuildingBlocksDataGenerator#MODEL_ENTRYPOINT}
 * entrypoint key, because the model provider needs the client-only model datagen
 * API and so cannot live in the main source set.
 */
public class MBModelProviderFactory implements ModelProviderFactory {
	@Override
	public void addModelProvider(FabricDataGenerator.Pack pack) {
		// The cast is required: the pack also inherits vanilla's addProvider, so a
		// bare method reference is ambiguous between the two factory types.
		pack.addProvider((FabricDataGenerator.Pack.Factory<MBModelProvider>) MBModelProvider::new);
	}
}
