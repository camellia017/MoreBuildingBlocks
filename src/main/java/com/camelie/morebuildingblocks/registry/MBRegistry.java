package com.camelie.morebuildingblocks.registry;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

/**
 * Low level registration plumbing shared by every block the mod adds.
 *
 * <p>Vanilla keeps its own copy of these helpers private in {@code Blocks} / {@code Items}, so the
 * mod re-implements the same two-step protocol here: register the block, then register a
 * {@link BlockItem} that is wired into {@link Item#BY_BLOCK}.</p>
 */
public final class MBRegistry {

	private MBRegistry() {
	}

	/** Namespaced id inside the {@code morebuildingblocks} namespace. */
	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, path);
	}

	/** Block resource key for a path inside the {@code morebuildingblocks} namespace. */
	public static ResourceKey<Block> blockKey(final String path) {
		return ResourceKey.create(Registries.BLOCK, id(path));
	}

	/** Item resource key for a path inside the {@code morebuildingblocks} namespace. */
	public static ResourceKey<Item> itemKey(final String path) {
		return ResourceKey.create(Registries.ITEM, id(path));
	}

	/** Registers a block together with the {@link BlockItem} that places it. */
	public static Block register(final String name, final Function<BlockBehaviour.Properties, Block> factory, final BlockBehaviour.Properties properties) {
		Block block = registerBlock(name, factory, properties);
		registerBlockItem(name, block);
		return block;
	}

	/** Registers a plain {@link Block} together with its item. */
	public static Block register(final String name, final BlockBehaviour.Properties properties) {
		return register(name, Block::new, properties);
	}

	/**
	 * Registers only the block half of the pair. Useful when the item form has to be registered
	 * elsewhere, or when a block is intentionally item-less.
	 */
	public static Block registerBlock(final String name, final Function<BlockBehaviour.Properties, Block> factory, final BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = blockKey(name);
		Block block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	/** Registers the {@link BlockItem} for an already registered block. */
	public static Item registerBlockItem(final String name, final Block block) {
		return registerItem(name, properties -> new BlockItem(block, properties));
	}

	/** Registers an item built from vanilla's stock {@link Item.Properties}. */
	public static Item registerItem(final String name, final Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = itemKey(name);
		Item item = factory.apply(new Item.Properties().setId(key).useBlockDescriptionPrefix());
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
