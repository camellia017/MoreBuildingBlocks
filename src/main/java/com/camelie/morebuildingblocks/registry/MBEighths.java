package com.camelie.morebuildingblocks.registry;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBEighthBlock;
import com.camelie.morebuildingblocks.block.MBWeatheringEighthBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every eighth block of the mod: one for each material that has something to cut it from, which is
 * every block of the game that is cut into slabs - 100 of them - plus the 33 terracotta families this
 * mod builds its stairs and slabs on, since vanilla cuts no slab or stair out of a terracotta.
 *
 * <p>An eighth block is one cell cut into eight cubes an eighth of a block on a side; which of them
 * are there is kept in the block's own eight switches, so a cell can hold between one and seven
 * pieces or, with all eight, the plain full block again. See {@link MBEighthBlock}.
 *
 * <p>Each material also carries the slab and the stairs of the same block, which is what an eighth
 * block refuses to share a cell with, and what the game is asked for a stonecutter recipe: one base
 * block cuts into eight eighth blocks, and nothing crafts them at a crafting table.
 *
 * <p>The material decides what its eighth is: where the game weathers the block - copper - the
 * eighth is a block the game weathers too, which {@link MBEighthWeathering} hands to the game's own
 * weathering maps, and every other eighth is a plain one.
 *
 * <p>The vanilla half of the table is generated from the blockstates of the game itself, so the rows
 * below are written with the ids those blockstates gave. Reading the rows back resolves every id,
 * and a name the game does not know stops the game from starting instead of quietly leaving an
 * eighth block out.
 */
public final class MBEighths {
	/** The prefix of every eighth block id, e.g. {@code eighth_red_terracotta}. */
	public static final String PREFIX = "eighth_";

	private static final List<Material> MATERIALS = new ArrayList<>();
	private static final Map<Block, Material> BY_BASE = new HashMap<>();
	private static final Map<Block, Material> BY_EIGHTH = new HashMap<>();

	static {
		// The 33 terracotta families of the mod: their stairs and slabs are the forms each eighth
		// block of the family has to refuse a cell to.
		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			register(family.base(), family.slab(), family.stairs());
		}

		// The 100 vanilla blocks that are cut into slabs, in id order. Terracotta is not among them -
		// vanilla cuts no slab from it - which is exactly why the mod builds its own families above.
		// Planks: the overworld woods, then the nether ones.
		vanilla("oak_planks", "oak_slab", "oak_stairs");
		vanilla("spruce_planks", "spruce_slab", "spruce_stairs");
		vanilla("birch_planks", "birch_slab", "birch_stairs");
		vanilla("jungle_planks", "jungle_slab", "jungle_stairs");
		vanilla("acacia_planks", "acacia_slab", "acacia_stairs");
		vanilla("dark_oak_planks", "dark_oak_slab", "dark_oak_stairs");
		vanilla("mangrove_planks", "mangrove_slab", "mangrove_stairs");
		vanilla("cherry_planks", "cherry_slab", "cherry_stairs");
		vanilla("pale_oak_planks", "pale_oak_slab", "pale_oak_stairs");
		vanilla("poplar_planks", "poplar_slab", "poplar_stairs");
		vanilla("bamboo_planks", "bamboo_slab", "bamboo_stairs");
		vanilla("bamboo_mosaic", "bamboo_mosaic_slab", "bamboo_mosaic_stairs");
		vanilla("crimson_planks", "crimson_slab", "crimson_stairs");
		vanilla("warped_planks", "warped_slab", "warped_stairs");
		// Wool and concrete, in the order of the dye colours.
		vanilla("white_wool", "white_wool_slab", "white_wool_stairs");
		vanilla("orange_wool", "orange_wool_slab", "orange_wool_stairs");
		vanilla("magenta_wool", "magenta_wool_slab", "magenta_wool_stairs");
		vanilla("light_blue_wool", "light_blue_wool_slab", "light_blue_wool_stairs");
		vanilla("yellow_wool", "yellow_wool_slab", "yellow_wool_stairs");
		vanilla("lime_wool", "lime_wool_slab", "lime_wool_stairs");
		vanilla("pink_wool", "pink_wool_slab", "pink_wool_stairs");
		vanilla("gray_wool", "gray_wool_slab", "gray_wool_stairs");
		vanilla("light_gray_wool", "light_gray_wool_slab", "light_gray_wool_stairs");
		vanilla("cyan_wool", "cyan_wool_slab", "cyan_wool_stairs");
		vanilla("purple_wool", "purple_wool_slab", "purple_wool_stairs");
		vanilla("blue_wool", "blue_wool_slab", "blue_wool_stairs");
		vanilla("brown_wool", "brown_wool_slab", "brown_wool_stairs");
		vanilla("green_wool", "green_wool_slab", "green_wool_stairs");
		vanilla("red_wool", "red_wool_slab", "red_wool_stairs");
		vanilla("black_wool", "black_wool_slab", "black_wool_stairs");
		vanilla("white_concrete", "white_concrete_slab", "white_concrete_stairs");
		vanilla("orange_concrete", "orange_concrete_slab", "orange_concrete_stairs");
		vanilla("magenta_concrete", "magenta_concrete_slab", "magenta_concrete_stairs");
		vanilla("light_blue_concrete", "light_blue_concrete_slab", "light_blue_concrete_stairs");
		vanilla("yellow_concrete", "yellow_concrete_slab", "yellow_concrete_stairs");
		vanilla("lime_concrete", "lime_concrete_slab", "lime_concrete_stairs");
		vanilla("pink_concrete", "pink_concrete_slab", "pink_concrete_stairs");
		vanilla("gray_concrete", "gray_concrete_slab", "gray_concrete_stairs");
		vanilla("light_gray_concrete", "light_gray_concrete_slab", "light_gray_concrete_stairs");
		vanilla("cyan_concrete", "cyan_concrete_slab", "cyan_concrete_stairs");
		vanilla("purple_concrete", "purple_concrete_slab", "purple_concrete_stairs");
		vanilla("blue_concrete", "blue_concrete_slab", "blue_concrete_stairs");
		vanilla("brown_concrete", "brown_concrete_slab", "brown_concrete_stairs");
		vanilla("green_concrete", "green_concrete_slab", "green_concrete_stairs");
		vanilla("red_concrete", "red_concrete_slab", "red_concrete_stairs");
		vanilla("black_concrete", "black_concrete_slab", "black_concrete_stairs");
		// Stone and its bricks.
		vanilla("stone", "stone_slab", "stone_stairs");
		vanilla("stone_bricks", "stone_brick_slab", "stone_brick_stairs");
		vanilla("mossy_stone_bricks", "mossy_stone_brick_slab", "mossy_stone_brick_stairs");
		vanilla("smooth_stone", "smooth_stone_slab", null);
		vanilla("cobblestone", "cobblestone_slab", "cobblestone_stairs");
		vanilla("mossy_cobblestone", "mossy_cobblestone_slab", "mossy_cobblestone_stairs");
		// Andesite, diorite and granite, each with its polished form.
		vanilla("andesite", "andesite_slab", "andesite_stairs");
		vanilla("polished_andesite", "polished_andesite_slab", "polished_andesite_stairs");
		vanilla("diorite", "diorite_slab", "diorite_stairs");
		vanilla("polished_diorite", "polished_diorite_slab", "polished_diorite_stairs");
		vanilla("granite", "granite_slab", "granite_stairs");
		vanilla("polished_granite", "polished_granite_slab", "polished_granite_stairs");
		// Deepslate.
		vanilla("cobbled_deepslate", "cobbled_deepslate_slab", "cobbled_deepslate_stairs");
		vanilla("polished_deepslate", "polished_deepslate_slab", "polished_deepslate_stairs");
		vanilla("deepslate_bricks", "deepslate_brick_slab", "deepslate_brick_stairs");
		vanilla("deepslate_tiles", "deepslate_tile_slab", "deepslate_tile_stairs");
		// Tuff.
		vanilla("tuff", "tuff_slab", "tuff_stairs");
		vanilla("polished_tuff", "polished_tuff_slab", "polished_tuff_stairs");
		vanilla("tuff_bricks", "tuff_brick_slab", "tuff_brick_stairs");
		// Blackstone.
		vanilla("blackstone", "blackstone_slab", null);
		vanilla("polished_blackstone", "polished_blackstone_slab", "polished_blackstone_stairs");
		vanilla("polished_blackstone_bricks", "polished_blackstone_brick_slab", "polished_blackstone_brick_stairs");
		// Nether bricks.
		vanilla("nether_bricks", "nether_brick_slab", "nether_brick_stairs");
		vanilla("red_nether_bricks", "red_nether_brick_slab", "red_nether_brick_stairs");
		// Bricks, and the other brick families.
		vanilla("bricks", "brick_slab", "brick_stairs");
		vanilla("end_stone_bricks", "end_stone_brick_slab", "end_stone_brick_stairs");
		vanilla("mud_bricks", "mud_brick_slab", "mud_brick_stairs");
		vanilla("resin_bricks", "resin_brick_slab", "resin_brick_stairs");
		// Sandstone.
		vanilla("sandstone", "sandstone_slab", null);
		vanilla("cut_sandstone", "cut_sandstone_slab", null);
		vanilla("smooth_sandstone", "smooth_sandstone_slab", null);
		vanilla("red_sandstone", "red_sandstone_slab", null);
		vanilla("cut_red_sandstone", "cut_red_sandstone_slab", null);
		vanilla("smooth_red_sandstone", "smooth_red_sandstone_slab", null);
		// Quartz.
		vanilla("quartz_block", "quartz_slab", null);
		vanilla("smooth_quartz", "smooth_quartz_slab", null);
		// Prismarine.
		vanilla("prismarine", "prismarine_slab", "prismarine_stairs");
		vanilla("prismarine_bricks", "prismarine_brick_slab", "prismarine_brick_stairs");
		vanilla("dark_prismarine", "dark_prismarine_slab", "dark_prismarine_stairs");
		// Purpur.
		vanilla("purpur_block", "purpur_slab", "purpur_stairs");
		// Copper, from the least to the most weathered, and then the same four waxed: wax stops the
		// block weathering, so both families are cut the same way and both are worth an eighth.
		vanilla("cut_copper", "cut_copper_slab", "cut_copper_stairs");
		vanilla("exposed_cut_copper", "exposed_cut_copper_slab", "exposed_cut_copper_stairs");
		vanilla("weathered_cut_copper", "weathered_cut_copper_slab", "weathered_cut_copper_stairs");
		vanilla("oxidized_cut_copper", "oxidized_cut_copper_slab", "oxidized_cut_copper_stairs");
		vanilla("waxed_cut_copper", "waxed_cut_copper_slab", "waxed_cut_copper_stairs");
		vanilla("waxed_exposed_cut_copper", "waxed_exposed_cut_copper_slab", "waxed_exposed_cut_copper_stairs");
		vanilla("waxed_weathered_cut_copper", "waxed_weathered_cut_copper_slab", "waxed_weathered_cut_copper_stairs");
		vanilla("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_slab", "waxed_oxidized_cut_copper_stairs");
		// Cinnabar and sulfur.
		vanilla("cinnabar", "cinnabar_slab", "cinnabar_stairs");
		vanilla("polished_cinnabar", "polished_cinnabar_slab", "polished_cinnabar_stairs");
		vanilla("cinnabar_bricks", "cinnabar_brick_slab", "cinnabar_brick_stairs");
		vanilla("sulfur", "sulfur_slab", "sulfur_stairs");
		vanilla("polished_sulfur", "polished_sulfur_slab", "polished_sulfur_stairs");
		vanilla("sulfur_bricks", "sulfur_brick_slab", "sulfur_brick_stairs");
	}

	private MBEighths() {
	}

	/**
	 * One material an eighth block is cut from.
	 *
	 * @param basePath the path of the base block id, e.g. {@code red_terracotta}
	 * @param base the block the eighth block is cut from, and the block whose textures and
	 *             properties it takes
	 * @param slab the slab of the same material, or {@code null} when the game has none - every one
	 *             of the mod's terracotta families is such a material, and no vanilla material is
	 * @param stairs the stairs of the same material, or {@code null} when the game has none, which
	 *               is the case for sandstone, quartz and blackstone slabs and the smooth stone they
	 *               are cut from
	 * @param eighth the eighth block itself
	 */
	public record Material(String basePath, Block base, @Nullable Block slab, @Nullable Block stairs, Block eighth) {

		/** @return the path of the eighth block id, e.g. {@code eighth_red_terracotta}. */
		public String eighthPath() {
			return PREFIX + this.basePath;
		}

		/**
		 * @return {@code true} when the stairs and the slab are this mod's own blocks. The 33
		 *         terracotta families are cut from vanilla base blocks, but the forms the mod built for
		 *         them are the mod's; the 100 vanilla materials keep the game's own, which is what
		 *         decides where an eighth block belongs in the creative inventory - next to the game's
		 *         own slab and stairs, or next to the ones this mod built.
		 */
		public boolean modForms() {
			return this.slab != null
				&& BuiltInRegistries.BLOCK.getKey(this.slab).getNamespace().equals(MoreBuildingBlocks.MOD_ID);
		}
	}

	/**
	 * Registers the eighth block of a base block and records what it was cut from.
	 *
	 * @param base the block the eighth block is cut from; its id supplies the eighth block's name
	 * @param slab the slab of the same material, or {@code null} when the game has none
	 * @param stairs the stairs of the same material, or {@code null} when the game has none
	 */
	@SuppressWarnings("deprecation")
	static void register(final Block base, final @Nullable Block slab, final @Nullable Block stairs) {
		String basePath = BuiltInRegistries.BLOCK.getKey(base).getPath();

		if (BY_BASE.containsKey(base)) {
			throw new IllegalStateException("Two eighth blocks for the base block " + basePath);
		}

		BlockBehaviour.Properties properties =
			BlockBehaviour.Properties.ofLegacyCopy(base).isViewBlocking(MBBlockFamilies.NEAR_PLANE_INTERSECTS_OUTLINE);
		Block eighth = MBRegistry.register(
			PREFIX + basePath,
			blockProperties -> eighth(base, slab, stairs, blockProperties),
			properties
		);
		Material material = new Material(basePath, base, slab, stairs, eighth);
		MATERIALS.add(material);
		BY_BASE.put(base, material);
		BY_EIGHTH.put(eighth, material);
	}

	/**
	 * Builds the eighth block of a material, in the shape the material calls for: an eighth of a
	 * block the game weathers is one more copper block to the game, so it is registered as the
	 * weathering eighth - at the stage of the block it is cut from, which the base block itself is
	 * asked for - and everything else is a plain eighth.
	 *
	 * <p>The pairs a weathering eighth turns through are not built here; {@link MBEighthWeathering}
	 * puts it into the game's own weathering maps once the whole table exists.
	 */
	private static MBEighthBlock eighth(final Block base, final @Nullable Block slab, final @Nullable Block stairs, final BlockBehaviour.Properties properties) {
		if (base instanceof WeatheringCopper copper) {
			return new MBWeatheringEighthBlock(copper.getAge(), slab, stairs, properties);
		}

		return new MBEighthBlock(slab, stairs, properties);
	}

	/**
	 * Registers the eighth block of a vanilla material named by id, which is how the generated half of
	 * the table is written: the ids come from the game's own blockstates rather than from hand-written
	 * names.
	 *
	 * @param basePath the path of the vanilla base block, e.g. {@code red_terracotta}
	 * @param slabPath the path of its slab, or {@code null} when it has none
	 * @param stairsPath the path of its stairs, or {@code null} when it has none
	 */
	static void vanilla(final String basePath, final @Nullable String slabPath, final @Nullable String stairsPath) {
		register(
			block(basePath),
			slabPath == null ? null : block(slabPath),
			stairsPath == null ? null : block(stairsPath)
		);
	}

	/** @throws IllegalStateException when the game has no block with that id. */
	private static Block block(final String path) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path));

		if (block == null) {
			throw new IllegalStateException("No block named " + path + " to cut an eighth block from");
		}

		return block;
	}

	/** Every material, the mod's own families first and then the vanilla ones in id order. */
	public static List<Material> materials() {
		return Collections.unmodifiableList(MATERIALS);
	}

	/** @return the material of {@code eighth}, or {@code null} when it is not an eighth block. */
	public static @Nullable Material byEighth(final Block eighth) {
		return BY_EIGHTH.get(eighth);
	}

	/** @return the material of {@code base}, or {@code null} when no eighth block is cut from it. */
	public static @Nullable Material byBase(final Block base) {
		return BY_BASE.get(base);
	}

	/**
	 * Registers every eighth block. Only here so that {@link MBBlocks} can name the class without
	 * depending on the order the compiler happens to initialize it in: the whole table is built by
	 * the static initializer above, which runs the first time this class is touched.
	 */
	public static void bootstrap() {
	}
}
