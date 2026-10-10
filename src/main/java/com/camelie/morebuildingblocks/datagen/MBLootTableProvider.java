package com.camelie.morebuildingblocks.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;

import com.camelie.morebuildingblocks.registry.MBBlockFamilies;

/**
 * Loot tables so every generated block drops itself.
 *
 * <p>Fabric's provider only strict-validates blocks from this mod's namespace,
 * which is why the vanilla provider's all-blocks loop must not be reused.
 */
public class MBLootTableProvider extends FabricBlockLootSubProvider {
	public MBLootTableProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	@Override
	public void generate() {
		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			Block stairs = family.stairs();
			Block slab = family.slab();

			this.dropSelf(stairs);
			this.dropSelf(slab);
		}
	}
}
