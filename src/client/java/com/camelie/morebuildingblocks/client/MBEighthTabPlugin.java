package com.camelie.morebuildingblocks.client;

import java.util.Set;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.camelie.morebuildingblocks.registry.MBEighths;

/**
 * Puts the eighth blocks into vanilla's own tabs as well, each one right behind the block it is cut
 * from, so that a builder who finds a block finds its smaller form in the same place.
 *
 * <p>The anchor decides which tab a material belongs to: the game's own slab where the game has
 * one, and the base block otherwise. Asking both tabs and keeping only the materials whose anchor
 * is really there is what splits the coloured materials from the rest, and it has to be done
 * explicitly, because inserting behind a missing anchor appends to the end of the tab instead.
 */
public final class MBEighthTabPlugin {
	private MBEighthTabPlugin() {
	}

	public static void onInitializeClient() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(MBEighthTabPlugin::insert);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COLORED_BLOCKS).register(MBEighthTabPlugin::insert);
	}

	private static void insert(final FabricCreativeModeTabOutput output) {
		Set<Item> tab = output.getDisplayStacks().stream().map(ItemStack::getItem).collect(Collectors.toSet());

		for (MBEighths.Material material : MBEighths.materials()) {
			Item eighth = Item.BY_BLOCK.get(material.eighth());
			Item anchor = anchor(material);

			if (eighth != null && anchor != null && tab.contains(anchor)) {
				output.insertAfter(anchor, eighth);
			}
		}
	}

	/** The item an eighth block belongs behind, or {@code null} when the material has no vanilla item. */
	private static Item anchor(final MBEighths.Material material) {
		// A family of this mod is anchored on its base block: the game has no stairs or slab of it
		// for the eighth to join, whatever the mod itself adds.
		Block anchor = material.modForms() ? material.base() : material.slab();

		return Item.BY_BLOCK.get(anchor != null ? anchor : material.base());
	}
}
