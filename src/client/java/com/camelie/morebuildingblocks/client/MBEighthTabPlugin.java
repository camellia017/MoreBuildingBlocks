package com.camelie.morebuildingblocks.client;

import java.util.ArrayList;
import java.util.EnumSet;
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
import net.minecraft.world.level.block.ColorCollection;

import com.camelie.morebuildingblocks.registry.MBEighths;

/**
 * Puts the eighth blocks into vanilla's own tabs as well, so that a builder who finds a block finds
 * its smaller form in the same place.
 *
 * <p>Most materials are anchored on the block an eighth is cut from: the game's own slab where the
 * game has one, and the base block otherwise. The families the game lists as a run - the copper
 * weatherings, the dyed wool, the dyed concrete and the terracotta - are not, because an eighth
 * there would cut that run in half. They wait for the whole run instead: the eighths of a material
 * follow the last stair or slab of that material, in the order the game's own tab lists them.
 *
 * <p>The terracotta is the one family in the coloured tab the game cut no stairs and no slabs from:
 * this mod built them, so behind the game's run of dyed terracotta go the stairs, the slabs and the
 * eighths of the family - the plain block first, then the sixteen the game dyed.
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
	 * Building blocks: the four eighths of one copper family behind the whole run of that family's
	 * stairs and slabs - the plain weathering and then the waxed one - and every other eighth behind
	 * the block it is cut from.
	 */
	private static void insertInBuildingBlocks(final FabricCreativeModeTabOutput output) {
		Set<Item> tab = itemsOf(output);

		insertCopper(output, false);
		insertCopper(output, true);

		for (MBEighths.Material material : MBEighths.materials()) {
			if (!isCopper(material.base())) {
				insertBehindOwnBlock(output, tab, material);
			}
		}
	}

	/** One copper family, the plain weathering or the waxed one, behind its own stairs and slabs. */
	private static void insertCopper(final FabricCreativeModeTabOutput output, final boolean waxed) {
		List<MBEighths.Material> copper =
			materialsIn(material -> isCopper(material.base()) && isWaxed(material.base()) == waxed);

		insertBehind(output, copper, gameFormsOf(copper));
	}

	/**
	 * Coloured blocks: the game lists the blocks it dyed a colour first and then the shapes it cut
	 * from them, so the eighths of a material go behind the whole run of that material rather than
	 * inside it. Every run follows the order the game's own tab lists the colours in, which is read
	 * back from that tab: it is not the order of {@code DyeColor.values()}.
	 */
	private static void insertInColoredBlocks(final FabricCreativeModeTabOutput output) {
		Set<Item> tab = itemsOf(output);
		List<DyeColor> colours = colourOrderOf(output);

		insertBehind(output, materialsOf(colours, Blocks.WOOL), slabsOf(colours, Blocks.WOOL_SLAB));
		insertBehind(output, materialsOf(colours, Blocks.CONCRETE), slabsOf(colours, Blocks.CONCRETE_SLAB));
		insertTerracotta(output, colours, false);
		insertTerracotta(output, colours, true);

		// Whatever the game did not dye keeps the anchor it had: a material whose block happens to
		// be in this tab as well must not lose its eighth just because it has no colour.
		for (MBEighths.Material material : MBEighths.materials()) {
			if (colourOf(material.base()) == null && !isTerracotta(material.basePath())) {
				insertBehindOwnBlock(output, tab, material);
			}
		}
	}

	/**
	 * The terracotta families of one kind - the plain terracotta and the sixteen the game dyed, or
	 * only the sixteen glazed ones - behind the game's own run of those blocks: first the stairs this
	 * mod built, then its slabs, then its eighths, each form starting with the plain family where
	 * there is one and then following the colour order.
	 */
	private static void insertTerracotta(final FabricCreativeModeTabOutput output, final List<DyeColor> colours, final boolean glazed) {
		List<MBEighths.Material> families = new ArrayList<>();

		if (!glazed) {
			MBEighths.Material plain = MBEighths.byBase(Blocks.TERRACOTTA);

			if (plain != null) {
				families.add(plain);
			}
		}

		families.addAll(materialsOf(colours, glazed ? Blocks.GLAZED_TERRACOTTA : Blocks.DYED_TERRACOTTA));

		Set<Item> anchors = new HashSet<>();

		for (DyeColor colour : colours) {
			add(anchors, (glazed ? Blocks.GLAZED_TERRACOTTA : Blocks.DYED_TERRACOTTA).pick(colour));
		}

		List<ItemStack> forms = new ArrayList<>();

		for (MBEighths.Material family : families) {
			addForm(forms, family.stairs());
		}

		for (MBEighths.Material family : families) {
			addForm(forms, family.slab());
		}

		for (MBEighths.Material family : families) {
			addForm(forms, family.eighth());
		}

		if (!forms.isEmpty() && hasAnchor(output, anchors)) {
			output.insertAfter(isIn(anchors), forms, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		}
	}

	/** The eighths of those materials behind the items their own run of the game's tab ends on. */
	private static void insertBehind(final FabricCreativeModeTabOutput output, final List<MBEighths.Material> materials, final Set<Item> anchors) {
		List<ItemStack> eighths = eighthsOf(materials);

		if (!eighths.isEmpty() && hasAnchor(output, anchors)) {
			output.insertAfter(isIn(anchors), eighths, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
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

	/** Whether the game waxed a cut copper block, which stops it from weathering any further. */
	private static boolean isWaxed(final Block base) {
		return BuiltInRegistries.BLOCK.getKey(base).getPath().startsWith("waxed_");
	}

	/** Whether a material is one of the terracotta families, plain, dyed or glazed. */
	private static boolean isTerracotta(final String basePath) {
		return basePath.equals("terracotta") || basePath.endsWith("_terracotta");
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

	/**
	 * The order the game lists the colours in, read back from its own run of wool: {@code DyeColor}
	 * keeps its constants in the order the dyes were added, not in the order the tab shows them.
	 */
	private static List<DyeColor> colourOrderOf(final FabricCreativeModeTabOutput output) {
		List<DyeColor> colours = new ArrayList<>();
		Set<DyeColor> seen = EnumSet.noneOf(DyeColor.class);

		for (ItemStack stack : output.getDisplayStacks()) {
			for (DyeColor colour : DyeColor.values()) {
				if (!seen.contains(colour) && stack.getItem() == Item.BY_BLOCK.get(Blocks.WOOL.pick(colour))) {
					seen.add(colour);
					colours.add(colour);
					break;
				}
			}
		}

		return colours;
	}

	/** The materials cut from one dyed block - wool, concrete or terracotta - in colour order. */
	private static List<MBEighths.Material> materialsOf(final List<DyeColor> colours, final ColorCollection<Block> blocks) {
		List<MBEighths.Material> materials = new ArrayList<>();

		for (DyeColor colour : colours) {
			MBEighths.Material material = MBEighths.byBase(blocks.pick(colour));

			if (material != null) {
				materials.add(material);
			}
		}

		return materials;
	}

	/** The slab items of one dyed block, i.e. the run of the game's tab a family of eighths ends on. */
	private static Set<Item> slabsOf(final List<DyeColor> colours, final ColorCollection<Block> slabs) {
		Set<Item> items = new HashSet<>();

		for (DyeColor colour : colours) {
			add(items, slabs.pick(colour));
		}

		return items;
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

	private static void addForm(final List<ItemStack> forms, final Block block) {
		Item item = block == null ? null : Item.BY_BLOCK.get(block);

		if (item != null) {
			forms.add(new ItemStack(item));
		}
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
