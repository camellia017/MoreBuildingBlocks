package com.camelie.morebuildingblocks.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.camelie.morebuildingblocks.registry.MBEighths;

/**
 * Puts the eighth blocks into vanilla's own tabs as well, so that a builder who finds a block finds
 * its smaller form in the same place.
 *
 * <p>Most materials are anchored on the block an eighth is cut from: the game's own slab where the
 * game has one, and the base block otherwise. The coloured materials and the copper ones are not,
 * because the game's own tabs walk those families block by block, and an eighth there would cut the
 * stairs in half. They wait instead until every stair of their colour, or of their weathering, has
 * been listed, and only then join the tab one after another.
 *
 * <p>Which tab a material lands in is decided by asking each tab for its items and keeping only the
 * materials whose anchor is really there: inserting behind a missing anchor appends to the end of
 * the tab instead of doing nothing.
 */
public final class MBEighthTabPlugin {
	private MBEighthTabPlugin() {
	}

	public static void onInitializeClient() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(MBEighthTabPlugin::insertInBuildingBlocks);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COLORED_BLOCKS).register(MBEighthTabPlugin::insertInColoredBlocks);
	}

	/**
	 * Building blocks: the copper eighths behind the whole run of copper stairs and slabs, from the
	 * least to the most weathered, and every other eighth behind the block it is cut from.
	 */
	private static void insertInBuildingBlocks(final FabricCreativeModeTabOutput output) {
		Set<Item> tab = itemsOf(output);
		List<MBEighths.Material> copper = materialsIn(material -> isCopper(material.base()));
		List<ItemStack> eighths = eighthsOf(copper);
		Set<Item> anchors = gameFormsOf(copper);

		if (!eighths.isEmpty() && hasAnchor(output, anchors)) {
			output.insertAfter(isIn(anchors), eighths, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		}

		for (MBEighths.Material material : MBEighths.materials()) {
			if (!isCopper(material.base())) {
				insertBehindOwnBlock(output, tab, material);
			}
		}
	}

	/**
	 * Coloured blocks: colour by colour, the eighths of that colour behind the whole run of stairs
	 * and slabs the game dyed that colour.
	 */
	private static void insertInColoredBlocks(final FabricCreativeModeTabOutput output) {
		Set<Item> tab = itemsOf(output);

		for (DyeColor colour : DyeColor.values()) {
			List<MBEighths.Material> materials = materialsIn(material -> colourOf(material.base()) == colour);
			List<ItemStack> eighths = eighthsOf(materials);
			Set<Item> anchors = gameFormsOf(materials);

			if (!eighths.isEmpty() && hasAnchor(output, anchors)) {
				output.insertAfter(isIn(anchors), eighths, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			}
		}

		// Whatever the game did not dye keeps the anchor it had: a material whose block happens to
		// be in this tab as well must not lose its eighth just because it has no colour.
		for (MBEighths.Material material : MBEighths.materials()) {
			if (colourOf(material.base()) == null) {
				insertBehindOwnBlock(output, tab, material);
			}
		}
	}

	private static void insertBehindOwnBlock(final FabricCreativeModeTabOutput output, final Set<Item> tab, final MBEighths.Material material) {
		Item eighth = Item.BY_BLOCK.get(material.eighth());
		Item anchor = anchor(material);

		if (eighth != null && anchor != null && tab.contains(anchor)) {
			output.insertAfter(anchor, eighth);
		}
	}

	/** The item an eighth block belongs behind, or {@code null} when the material has no vanilla item. */
	private static Item anchor(final MBEighths.Material material) {
		// A family of this mod is anchored on its base block: the game has no stairs or slab of it
		// for the eighth to join, whatever the mod itself adds.
		Block anchor = material.modForms() ? material.base() : material.slab();

		return Item.BY_BLOCK.get(anchor != null ? anchor : material.base());
	}

	/** Whether an eighth is cut from one of the game's cut copper blocks, which weather as a family. */
	private static boolean isCopper(final Block base) {
		String path = BuiltInRegistries.BLOCK.getKey(base).getPath();

		return path.equals("cut_copper") || path.endsWith("_cut_copper");
	}

	/** The colour the game dyed {@code base}, or {@code null} when the game did not dye it at all. */
	private static DyeColor colourOf(final Block base) {
		for (DyeColor colour : DyeColor.values()) {
			if (base == Blocks.WOOL.pick(colour) || base == Blocks.CONCRETE.pick(colour)
					|| base == Blocks.DYED_TERRACOTTA.pick(colour) || base == Blocks.GLAZED_TERRACOTTA.pick(colour)) {
				return colour;
			}
		}

		return null;
	}

	/** Those materials of the mod that pass {@code filter}, in the order the mod registered them. */
	private static List<MBEighths.Material> materialsIn(final Predicate<MBEighths.Material> filter) {
		List<MBEighths.Material> materials = new ArrayList<>();

		for (MBEighths.Material material : MBEighths.materials()) {
			if (filter.test(material)) {
				materials.add(material);
			}
		}

		return materials;
	}

	/** The items of those eighth blocks among {@code materials} that the game has an item for, in order. */
	private static List<ItemStack> eighthsOf(final List<MBEighths.Material> materials) {
		List<ItemStack> stacks = new ArrayList<>();

		for (MBEighths.Material material : materials) {
			Item eighth = Item.BY_BLOCK.get(material.eighth());

			if (eighth != null) {
				stacks.add(new ItemStack(eighth));
			}
		}

		return stacks;
	}

	/**
	 * The items of the game's own stairs and slabs among {@code materials}: the ones a run of them
	 * ends on in a vanilla tab. The forms this mod built for a family are not listed there.
	 */
	private static Set<Item> gameFormsOf(final List<MBEighths.Material> materials) {
		Set<Item> items = new HashSet<>();

		for (MBEighths.Material material : materials) {
			if (!material.modForms()) {
				add(items, material.stairs());
				add(items, material.slab());
			}
		}

		return items;
	}

	private static void add(final Set<Item> items, final Block block) {
		Item item = block == null ? null : Item.BY_BLOCK.get(block);

		if (item != null) {
			items.add(item);
		}
	}

	private static Predicate<ItemStack> isIn(final Set<Item> items) {
		return stack -> items.contains(stack.getItem());
	}

	private static boolean hasAnchor(final FabricCreativeModeTabOutput output, final Set<Item> items) {
		return !items.isEmpty() && output.getDisplayStacks().stream().anyMatch(isIn(items));
	}

	private static Set<Item> itemsOf(final FabricCreativeModeTabOutput output) {
		return output.getDisplayStacks().stream().map(ItemStack::getItem).collect(Collectors.toSet());
	}
}
