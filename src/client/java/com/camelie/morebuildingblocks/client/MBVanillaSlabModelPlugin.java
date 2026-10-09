package com.camelie.morebuildingblocks.client;

import com.mojang.math.Quadrant;

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

		Variant bottom = variant(flatModel(id, ""), null);
		Variant top = variant(flatModel(id, "_top"), null);
		Variant full = variant(doubleModel(id), null);
		Variant verticalNegativeX = variant(verticalModel(id, "neg_x"), null);
		Variant verticalPositiveX = variant(verticalModel(id, "pos_x"), null);
		Variant verticalNegativeZ = variant(verticalModel(id, "neg_z"), null);
		Variant verticalPositiveZ = variant(verticalModel(id, "pos_z"), null);

		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			boolean vertical = state.getValue(MBBlockStateProperties.VERTICAL);
			Direction.Axis axis = state.getValue(MBBlockStateProperties.AXIS);
			SlabType type = state.getValue(SlabBlock.TYPE);

			if (vertical && axis != Direction.Axis.Y && type != SlabType.DOUBLE) {
				boolean positive = type == SlabType.TOP;
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

	/** Vanilla's {@code block/<id>_slab} / {@code block/<id>_slab_top} naming. */
	private static Identifier flatModel(Identifier slabId, String suffix) {
		return slabId.withPath(path -> "block/" + path + suffix);
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
	 * Vanilla's double-slab model, which is only usually the base block's model.
	 * Every other slab resolves to {@code block/<id without _slab>}.
	 */
	private static Identifier doubleModel(Identifier slabId) {
		String path = switch (slabId.getPath()) {
			case "smooth_stone_slab" -> "smooth_stone_slab_double";
			case "quartz_slab" -> "quartz_block";
			case "petrified_oak_slab" -> "oak_planks";
			default -> slabId.getPath().replaceFirst("_slab$", "");
		};

		return slabId.withPath(ignored -> "block/" + path);
	}

	private static Variant variant(Identifier model, Quadrant yRotation) {
		if (yRotation == null) {
			return new Variant(model);
		}

		return new Variant(model, new Variant.SimpleModelState(Quadrant.R0, yRotation, Quadrant.R0, false));
	}
}
