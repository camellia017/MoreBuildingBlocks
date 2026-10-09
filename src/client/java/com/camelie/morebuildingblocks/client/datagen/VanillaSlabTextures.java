package com.camelie.morebuildingblocks.client.datagen;

import java.util.Map;

/**
 * The three textures every vanilla slab model uses.
 *
 * <p>A vertical slab model has no parent, so unlike a vanilla slab model it cannot
 * leave {@code #bottom}, {@code #top} and {@code #side} for a parent to fill in: a
 * model with no parent has to name the sprites it draws. This table is the
 * machine-verified mapping from all 101 vanilla {@code *_slab} block ids to the
 * {@code bottom}/{@code top}/{@code side} sprite each of their own models declares,
 * read out of {@code assets/minecraft/models/block/*_slab.json} in 26.3.
 *
 * <p>Most slabs use one texture for all three slots, and for many of them that
 * texture is simply the block's own - but the exceptions are not guessable. Bricks,
 * planks and the various brick families use a plural or material name
 * ({@code bricks}, {@code oak_planks}, {@code deepslate_tiles}); sandstone has a
 * separate bottom and top; {@code smooth_stone_slab} has a dedicated side texture;
 * and the four waxed copper slabs have no model of their own at all and reuse the
 * unwaxed texture. So the mapping is written out rather than derived.
 *
 * <p>Keys are the slab block's path in the {@code minecraft} namespace; values are
 * texture paths inside that same namespace.
 */
final class VanillaSlabTextures {

	/** One slab's texture triple. */
	record Textures(String bottom, String top, String side) {
	}

	private static final Map<String, Textures> BY_SLAB = Map.ofEntries(
			slab("acacia_slab", "block/acacia_planks"),
			slab("andesite_slab", "block/andesite"),
			slab("bamboo_mosaic_slab", "block/bamboo_mosaic"),
			slab("bamboo_slab", "block/bamboo_planks"),
			slab("birch_slab", "block/birch_planks"),
			slab("black_concrete_slab", "block/black_concrete"),
			slab("blackstone_slab", "block/blackstone_top", "block/blackstone_top", "block/blackstone"),
			slab("black_wool_slab", "block/black_wool"),
			slab("blue_concrete_slab", "block/blue_concrete"),
			slab("blue_wool_slab", "block/blue_wool"),
			slab("brick_slab", "block/bricks"),
			slab("brown_concrete_slab", "block/brown_concrete"),
			slab("brown_wool_slab", "block/brown_wool"),
			slab("cherry_slab", "block/cherry_planks"),
			slab("cinnabar_slab", "block/cinnabar"),
			slab("polished_cinnabar_slab", "block/polished_cinnabar"),
			slab("polished_deepslate_slab", "block/polished_deepslate"),
			slab("polished_sulfur_slab", "block/polished_sulfur"),
			slab("sulfur_slab", "block/sulfur"),
						slab("cinnabar_brick_slab", "block/cinnabar_bricks"),
			slab("cobbled_deepslate_slab", "block/cobbled_deepslate"),
			slab("cobblestone_slab", "block/cobblestone"),
			slab("crimson_slab", "block/crimson_planks"),
			slab("cut_copper_slab", "block/cut_copper"),
			slab("cut_red_sandstone_slab", "block/red_sandstone_top", "block/red_sandstone_top",
					"block/cut_red_sandstone"),
			slab("cut_sandstone_slab", "block/sandstone_top", "block/sandstone_top", "block/cut_sandstone"),
			slab("cyan_concrete_slab", "block/cyan_concrete"),
			slab("cyan_wool_slab", "block/cyan_wool"),
			slab("dark_oak_slab", "block/dark_oak_planks"),
			slab("dark_prismarine_slab", "block/dark_prismarine"),
			slab("deepslate_brick_slab", "block/deepslate_bricks"),
			slab("deepslate_tile_slab", "block/deepslate_tiles"),
			slab("diorite_slab", "block/diorite"),
			slab("end_stone_brick_slab", "block/end_stone_bricks"),
			slab("exposed_cut_copper_slab", "block/exposed_cut_copper"),
			slab("granite_slab", "block/granite"),
			slab("gray_concrete_slab", "block/gray_concrete"),
			slab("gray_wool_slab", "block/gray_wool"),
			slab("green_concrete_slab", "block/green_concrete"),
			slab("green_wool_slab", "block/green_wool"),
			slab("jungle_slab", "block/jungle_planks"),
			slab("light_blue_concrete_slab", "block/light_blue_concrete"),
			slab("light_blue_wool_slab", "block/light_blue_wool"),
			slab("light_gray_concrete_slab", "block/light_gray_concrete"),
			slab("light_gray_wool_slab", "block/light_gray_wool"),
			slab("lime_concrete_slab", "block/lime_concrete"),
			slab("lime_wool_slab", "block/lime_wool"),
			slab("magenta_concrete_slab", "block/magenta_concrete"),
			slab("magenta_wool_slab", "block/magenta_wool"),
			slab("mangrove_slab", "block/mangrove_planks"),
			slab("mossy_cobblestone_slab", "block/mossy_cobblestone"),
			slab("mossy_stone_brick_slab", "block/mossy_stone_bricks"),
			slab("mud_brick_slab", "block/mud_bricks"),
			slab("nether_brick_slab", "block/nether_bricks"),
			slab("oak_slab", "block/oak_planks"),
			slab("orange_concrete_slab", "block/orange_concrete"),
			slab("orange_wool_slab", "block/orange_wool"),
			slab("oxidized_cut_copper_slab", "block/oxidized_cut_copper"),
			slab("pale_oak_slab", "block/pale_oak_planks"),
			slab("petrified_oak_slab", "block/oak_planks"),
			slab("pink_concrete_slab", "block/pink_concrete"),
			slab("pink_wool_slab", "block/pink_wool"),
			slab("polished_andesite_slab", "block/polished_andesite"),
			slab("polished_blackstone_brick_slab", "block/polished_blackstone_bricks"),
			slab("polished_blackstone_slab", "block/polished_blackstone"),
			slab("polished_diorite_slab", "block/polished_diorite"),
			slab("polished_granite_slab", "block/polished_granite"),
			slab("polished_tuff_slab", "block/polished_tuff"),
			slab("poplar_slab", "block/poplar_planks"),
			slab("prismarine_brick_slab", "block/prismarine_bricks"),
			slab("prismarine_slab", "block/prismarine"),
			slab("purple_concrete_slab", "block/purple_concrete"),
			slab("purple_wool_slab", "block/purple_wool"),
			slab("purpur_slab", "block/purpur_block"),
			slab("quartz_slab", "block/quartz_block_top", "block/quartz_block_top", "block/quartz_block_side"),
			slab("red_concrete_slab", "block/red_concrete"),
			slab("red_nether_brick_slab", "block/red_nether_bricks"),
			slab("red_sandstone_slab", "block/red_sandstone_bottom", "block/red_sandstone_top",
					"block/red_sandstone"),
			slab("red_wool_slab", "block/red_wool"),
			slab("resin_brick_slab", "block/resin_bricks"),
			slab("sandstone_slab", "block/sandstone_bottom", "block/sandstone_top", "block/sandstone"),
			slab("smooth_quartz_slab", "block/quartz_block_bottom"),
			slab("smooth_red_sandstone_slab", "block/red_sandstone_top"),
			slab("smooth_sandstone_slab", "block/sandstone_top"),
			slab("smooth_stone_slab", "block/smooth_stone", "block/smooth_stone", "block/smooth_stone_slab_side"),
			slab("spruce_slab", "block/spruce_planks"),
			slab("stone_brick_slab", "block/stone_bricks"),
			slab("stone_slab", "block/stone"),
			slab("sulfur_brick_slab", "block/sulfur_bricks"),
			slab("tuff_brick_slab", "block/tuff_bricks"),
			slab("tuff_slab", "block/tuff"),
			slab("warped_slab", "block/warped_planks"),
			slab("waxed_cut_copper_slab", "block/cut_copper"),
			slab("waxed_exposed_cut_copper_slab", "block/exposed_cut_copper"),
			slab("waxed_oxidized_cut_copper_slab", "block/oxidized_cut_copper"),
			slab("waxed_weathered_cut_copper_slab", "block/weathered_cut_copper"),
			slab("weathered_cut_copper_slab", "block/weathered_cut_copper"),
			slab("white_concrete_slab", "block/white_concrete"),
			slab("white_wool_slab", "block/white_wool"),
			slab("yellow_concrete_slab", "block/yellow_concrete"),
			slab("yellow_wool_slab", "block/yellow_wool"));

	private VanillaSlabTextures() {
	}

	/** The trio a slab draws with, or {@code null} if the id is not one of the 101. */
	static Textures bySlab(String slabPath) {
		return BY_SLAB.get(slabPath);
	}

	private static Map.Entry<String, Textures> slab(String path, String all) {
		return Map.entry(path, new Textures(all, all, all));
	}

	private static Map.Entry<String, Textures> slab(String path, String bottom, String top, String side) {
		return Map.entry(path, new Textures(bottom, top, side));
	}
}
