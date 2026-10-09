package com.camelie.morebuildingblocks.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Extra block state properties the mod attaches to slabs and stairs.
 *
 * <p>{@link #VERTICAL} and {@link #AXIS} go on every slab in the game, because every slab can be
 * placed upright: {@link com.camelie.morebuildingblocks.mixin.SlabBlockMixin} declares them and
 * vanilla's {@code type=top|bottom} keeps describing which half of the block is filled, on either
 * the Y axis (horizontal slab) or the {@link #AXIS} axis (vertical slab).
 *
 * <p>{@link #GLAZED_FACING} is different: only a glazed pattern is directional, so it is declared
 * only by the blocks marked {@link GlazedPattern}.
 */
public final class MBBlockStateProperties {
	/** {@code true} for a vertical slab, {@code false} for a vanilla horizontal slab. */
	public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");

	/**
	 * The horizontal axis a vertical slab is thin along. This is vanilla's own {@code axis} property,
	 * reused so that no new serialised state name is introduced for it.
	 */
	public static final EnumProperty<Direction.Axis> AXIS =
		net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;

	/**
	 * The direction the player who placed this block was looking, which a glazed pattern is turned
	 * by. The mod sets it exactly the way {@code GlazedTerracottaBlock.getStateForPlacement} does:
	 * {@code context.getHorizontalDirection().getOpposite()}.
	 *
	 * <p>A glazed terracotta pattern is directional, so the stairs and the slab cut out of one have
	 * to be turned to match the block the player would have got by placing the glazed terracotta
	 * itself. Vanilla does that by giving the model one {@code y} rotation per {@code facing} value;
	 * the mod's slab and stair models have to bake it into their geometry instead, because a
	 * blockstate {@code y} would turn the cut corner with the texture.
	 *
	 * <p>This is deliberately <em>not</em> vanilla's {@code HORIZONTAL_FACING}, even though it holds
	 * the same four directions in the same order. That property is called {@code facing}, and {@code
	 * StairBlock} already declares one; a {@link
	 * net.minecraft.world.level.block.state.StateDefinition} keys its properties by name, so a
	 * second property of the same name on the same block collides, and the block's whole blockstate
	 * file comes out unreadable with {@code facing} twice in every variant key.
	 */
	public static final EnumProperty<Direction> GLAZED_FACING = EnumProperty.create(
		"glazed_facing", Direction.class,
		Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

	/**
	 * The number of quarter turns a glazed model needs so that it shows the pattern a glazed
	 * terracotta block placed by the same player shows.
	 *
	 * <p>Vanilla's {@code minecraft:block/white_glazed_terracotta} blockstate rotates the model by
	 * {@code 0} at {@code facing=south}, {@code 90} at {@code west}, {@code 180} at {@code north}
	 * and {@code 270} at {@code east}. Turning a model by a quarter turn once is therefore the same
	 * as having placed it one step round that order, which is exactly what the returned count is.
	 */
	public static int glazedTurns(final Direction facing) {
		return switch (facing) {
			case SOUTH -> 0;
			case WEST -> 1;
			case NORTH -> 2;
			case EAST -> 3;
			default -> 0;
		};
	}

	/** Vertical slab against the west face: full height (Y), half depth (X 0..8), full depth (Z). */
	private static final VoxelShape VERTICAL_WEST = Shapes.box(0.0, 0.0, 0.0, 0.5, 1.0, 1.0);
	/** Vertical slab against the east face: full height (Y), half depth (X 8..16), full depth (Z). */
	private static final VoxelShape VERTICAL_EAST = Shapes.box(0.5, 0.0, 0.0, 1.0, 1.0, 1.0);
	/** Vertical slab against the north face: half depth (Z 0..8), full width (X), full height (Y). */
	private static final VoxelShape VERTICAL_NORTH = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.5);
	/** Vertical slab against the south face: half depth (Z 8..16), full width (X), full height (Y). */
	private static final VoxelShape VERTICAL_SOUTH = Shapes.box(0.0, 0.0, 0.5, 1.0, 1.0, 1.0);

	private MBBlockStateProperties() {
	}

	/**
	 * Builds the vertical slab collision/outline shape: full block height, half depth along
	 * {@code axis}.
	 *
	 * @param axis     {@link Direction.Axis#X} or {@link Direction.Axis#Z}
	 * @param positive whether the filled half is the positive half of {@code axis}
	 */
	public static VoxelShape verticalSlabShape(final Direction.Axis axis, final boolean positive) {
		return switch (axis) {
			case X -> positive ? VERTICAL_EAST : VERTICAL_WEST;
			case Z -> positive ? VERTICAL_SOUTH : VERTICAL_NORTH;
			case Y -> Shapes.block();
		};
	}
}
