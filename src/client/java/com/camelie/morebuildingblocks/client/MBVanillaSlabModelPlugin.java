package com.camelie.morebuildingblocks.client;

import java.util.Map;

import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.BlockStateResolver;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.camelie.morebuildingblocks.client.datagen.VanillaSlabTextures;

/**
 * Gives every vanilla slab a model for the upright states the slab mixin adds.
 *
 * <p>Vanilla's own blockstate files only describe a flat slab, because when they
 * were written there was no {@code vertical} or {@code axis} property to describe.
 * Rather than restating 101 of vanilla's files in this mod's data pack - and taking
 * responsibility for every model they name, including quartz, smooth stone and
 * petrified oak, whose double forms are not their base block's model - this resolver
 * supplies the states vanilla's files cannot and leaves the rest exactly as vanilla
 * shipped them.
 *
 * <p>A resolver replaces every state of its block, so the flat states are repeated
 * here from the same three names vanilla's files use.
 */
public class MBVanillaSlabModelPlugin implements ClientModInitializer {
	/**
	 * Where the datagen provider writes the upright model of a vanilla slab, keyed by
	 * that slab's own path.
	 *
	 * <p>Each one is a complete model with its own {@code bottom}/{@code top}/{@code side}
	 * textures, because a model reached from a vanilla block's blockstate has no child
	 * file above it to inherit those slots from.
	 */
	private static final String VERTICAL_DIRECTORY = "vertical_slab/";

	/**
	 * The five vanilla slabs whose doubled form is not drawn with their {@code #side} sprite,
	 * mapped to the model vanilla's own blockstate names. Every other slab doubles into its
	 * {@code #side} sprite's model (see {@link #doubleModel}).
	 *
	 * <p>These were read back from the 101 {@code blockstates/*_slab.json} files in the 26.3
	 * client jar; the four sandstone and quartz entries name a texture rather than their own
	 * block because their smooth and cut faces are not the side of the full block.
	 */
	private static final Map<String, String> DOUBLE_MODEL_OVERRIDES = Map.of(
		"smooth_stone_slab", "smooth_stone_slab_double",
		"quartz_slab", "quartz_block",
		"smooth_quartz_slab", "smooth_quartz",
		"smooth_sandstone_slab", "smooth_sandstone",
		"smooth_red_sandstone_slab", "smooth_red_sandstone");

	@Override
	public void onInitializeClient() {
		ModelLoadingPlugin.register(context -> {
			for (Block block : BuiltInRegistries.BLOCK) {
				if (block instanceof SlabBlock && !MoreBuildingBlocks.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
					context.registerBlockStateResolver(block, MBVanillaSlabModelPlugin::resolve);
				}
			}
		});
	}

	private static void resolve(BlockStateResolver.Context context) {
		Block block = context.block();
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);

		Variant bottom = new Variant(flatModel(id, ""));
		Variant top = new Variant(flatModel(id, "_top"));
		Variant full = new Variant(doubleModel(id));
		Variant verticalNegativeX = new Variant(verticalModel(id, "neg_x"));
		Variant verticalPositiveX = new Variant(verticalModel(id, "pos_x"));
		Variant verticalNegativeZ = new Variant(verticalModel(id, "neg_z"));
		Variant verticalPositiveZ = new Variant(verticalModel(id, "pos_z"));

		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			boolean vertical = state.getValue(MBBlockStateProperties.VERTICAL);
			Direction.Axis axis = state.getValue(MBBlockStateProperties.AXIS);
			SlabType type = state.getValue(SlabBlock.TYPE);

			if (vertical && axis != Direction.Axis.Y && type != SlabType.DOUBLE) {
				// SlabType names a side of the block, not of the axis: upright, "top" is the half
				// whose visible face points towards the negative end, so it is the negative half.
				boolean positive = type == VerticalSlab.Fill.positive(axis).type();
				Variant upright = axis == Direction.Axis.X
					? (positive ? verticalPositiveX : verticalNegativeX)
					: (positive ? verticalPositiveZ : verticalNegativeZ);

				context.setModel(state, new SingleVariant.Unbaked(upright).asRoot());
				continue;
			}

			context.setModel(state, new SingleVariant.Unbaked(switch (type) {
				case TOP -> top;
				case DOUBLE -> full;
				case BOTTOM -> bottom;
			}).asRoot());
		}
	}

	/**
	 * Vanilla's {@code block/<id>_slab} / {@code block/<id>_slab_top} naming, which all but the
	 * four waxed copper slabs follow.
	 */
	private static Identifier flatModel(Identifier slabId, String suffix) {
		String path = slabId.getPath();

		if (path.endsWith("_cut_copper_slab")) {
			// The waxed copper slabs have no models of their own: their blockstates draw the
			// unwaxed ones, which are what the UVs and the cutting pattern belong to.
			path = path.replace("waxed_", "");
		}

		String model = path;
		return slabId.withPath(ignored -> "block/" + model + suffix);
	}

	/**
	 * {@code morebuildingblocks:vertical_slab/<slab path>/<fill>}, written by datagen: one box per
	 * half the slab can fill, so each of the four upright directions reads the right half of the
	 * block instead of two of them sharing a rotated copy of the same box.
	 */
	private static Identifier verticalModel(Identifier slabId, String fill) {
		return Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID,
			VERTICAL_DIRECTORY + slabId.getPath() + "/" + fill);
	}

	/**
	 * Vanilla's double-slab model, which is the full block's own model and <em>not</em>
	 * derivable from the slab's id: {@code acacia_slab} draws {@code block/acacia_planks}
	 * rather than the non-existent {@code block/acacia}, and {@code brick_slab} draws
	 * {@code block/bricks}.
	 *
	 * <p>The full block uses its {@code #side} sprite, so the texture table - which already
	 * holds the real 26.3 values for all 101 vanilla slabs - names the model directly. Five
	 * slabs differ, and each one is listed in {@link #DOUBLE_MODEL_OVERRIDES} with the model
	 * vanilla's own blockstate names.
	 */
	private static Identifier doubleModel(Identifier slabId) {
		String path = slabId.getPath();
		String override = DOUBLE_MODEL_OVERRIDES.get(path);

		if (override != null) {
			return slabId.withPath(ignored -> "block/" + override);
		}

		VanillaSlabTextures.Textures textures = VanillaSlabTextures.bySlab(path);

		if (textures == null) {
			return slabId.withPath(ignored -> "block/" + path.replaceFirst("_slab$", ""));
		}

		return slabId.withPath(ignored -> textures.side());
	}
}
