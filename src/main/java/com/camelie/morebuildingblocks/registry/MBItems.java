package com.camelie.morebuildingblocks.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;

/**
 * Item level registration: block items for every generated block, plus the
 * "MoreBuildingBlocks" creative mode tab.
 *
 * <p>Every block item is created by {@link MBRegistry}, which also fills
 * {@link Item#BY_BLOCK}; this class only collects them in a stable, display
 * friendly order.
 *
 * <p>The tab itself is registered from {@link #register()} rather than from a static initialiser.
 * {@code BuiltInRegistries.CREATIVE_MODE_TAB} is frozen once the game has bootstrapped, so the only
 * safe moment is the mod initialiser - a static initialiser reached later (for example from the data
 * generator entrypoint) fails with {@code IllegalStateException: Registry is already frozen}.
 */
public final class MBItems {
	/** Id of the mod's creative tab. */
	public static final ResourceKey<CreativeModeTab> MAIN_TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "main"));

	private static final List<Block> DISPLAY_ORDER = new ArrayList<>();

	private static CreativeModeTab mainTab;

	static {
		// Base blocks first (terracotta, then dyed, then glazed), so the tab shows
		// the source block right before the stairs and slab made from it.
		DISPLAY_ORDER.add(MBBlocks.TERRACOTTA);
		MBBlocks.DYED_TERRACOTTA.forEach(DISPLAY_ORDER::add);
		MBBlocks.GLAZED_TERRACOTTA.forEach(DISPLAY_ORDER::add);

		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			DISPLAY_ORDER.add(family.stairs());
			DISPLAY_ORDER.add(family.slab());
		}
	}

	private MBItems() {
	}

	/** Registers the creative tab. Call once from the mod initialiser, before the registries freeze. */
	public static CreativeModeTab register() {
		if (mainTab == null) {
			mainTab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN_TAB_KEY,
					FabricCreativeModeTab.builder()
							.title(Component.literal("MoreBuildingBlocks"))
							.icon(() -> new ItemStack(MBBlocks.TERRACOTTA))
							.displayItems((parameters, output) -> {
								for (Block block : DISPLAY_ORDER) {
									Item item = Item.BY_BLOCK.get(block);

									if (item != null) {
										output.accept(item);
									}
								}
							})
							.build());
		}

		return mainTab;
	}

	/** The mod's creative tab, or {@code null} before {@link #register()} has run. */
	public static CreativeModeTab mainTab() {
		return mainTab;
	}

	/** Every block that should appear in the creative tab, in display order. */
	public static List<Block> displayOrder() {
		return List.copyOf(DISPLAY_ORDER);
	}

	/** All items registered by the mod, in display order. */
	public static List<ItemLike> items() {
		List<ItemLike> items = new ArrayList<>(DISPLAY_ORDER.size());

		for (Block block : DISPLAY_ORDER) {
			Item item = Item.BY_BLOCK.get(block);

			if (item != null) {
				items.add(item);
			}
		}

		return items;
	}
}
