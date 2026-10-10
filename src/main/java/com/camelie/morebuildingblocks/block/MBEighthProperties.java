package com.camelie.morebuildingblocks.block;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block state vocabulary of an eighth block, and the position arithmetic of the eight pieces it
 * is cut into.
 *
 * <p>An eighth block owns its whole cell and hides one of the eight cubes that fill it - an eighth
 * of a block on a side - in each of eight switches. The switches run {@code true} first, so
 * {@code defaultBlockState()} is the plain full block the item is drawn as; a state that turns one
 * of them off simply leaves that cube out, and the outline and collision shape are the union of the
 * pieces that are still there.
 *
 * <p>The pieces are numbered {@code 4*east + 2*upper + south}, which is also the order of
 * {@link #BITS}: index {@code 0} is the lower north western cube, index {@code 7} the upper south
 * eastern one.
 *
 * <p>Every eighth block placed in one cell turns its texture the same way - see {@link #FACING},
 * which is written once, by the piece a cell starts with, and then left alone. Only a material
 * whose own texture is laid out around a direction - the glazed terracotta - is turned at all; a
 * plank or copper eighth keeps the grain of the block it was cut from, as vanilla keeps it.
 */
public final class MBEighthProperties {
	/** The number of quarter turns a texture can be turned by; the values of {@link #FACING}. */
	public static final int TURNS = 4;

	/** The lower north western eighth, at index {@code 0}. */
	public static final BooleanProperty LOWER_NORTH_WEST = BooleanProperty.create("lower_north_west");

	/** The lower south western eighth, at index {@code 1}. */
	public static final BooleanProperty LOWER_SOUTH_WEST = BooleanProperty.create("lower_south_west");

	/** The upper north western eighth, at index {@code 2}. */
	public static final BooleanProperty UPPER_NORTH_WEST = BooleanProperty.create("upper_north_west");

	/** The upper south western eighth, at index {@code 3}. */
	public static final BooleanProperty UPPER_SOUTH_WEST = BooleanProperty.create("upper_south_west");

	/** The lower north eastern eighth, at index {@code 4}. */
	public static final BooleanProperty LOWER_NORTH_EAST = BooleanProperty.create("lower_north_east");

	/** The lower south eastern eighth, at index {@code 5}. */
	public static final BooleanProperty LOWER_SOUTH_EAST = BooleanProperty.create("lower_south_east");

	/** The upper north eastern eighth, at index {@code 6}. */
	public static final BooleanProperty UPPER_NORTH_EAST = BooleanProperty.create("upper_north_east");

	/** The upper south eastern eighth, at index {@code 7}. */
	public static final BooleanProperty UPPER_SOUTH_EAST = BooleanProperty.create("upper_south_east");

	/** The eight switches in index order; {@code BITS[3]} is also {@link #UPPER_SOUTH_WEST}. */
	public static final BooleanProperty[] BITS = {
		LOWER_NORTH_WEST,
		LOWER_SOUTH_WEST,
		UPPER_NORTH_WEST,
		UPPER_SOUTH_WEST,
		LOWER_NORTH_EAST,
		LOWER_SOUTH_EAST,
		UPPER_NORTH_EAST,
		UPPER_SOUTH_EAST,
	};

	/**
	 * The quarter turn a patterned texture in one cell is turned by.
	 *
	 * <p>A cell's first eighth fixes it - the direction the player who placed it was looking - and
	 * the pieces added afterwards leave it as they found it, so that a wall of eighths built into one
	 * cell keeps one grain instead of turning at every piece. The numbers are the ones {@code
	 * MBModelProvider} turns the models by, and the same ones {@link
	 * MBBlockStateProperties#GLAZED_FACING} uses, so that a glazed eighth block reads its texture the
	 * way a glazed stair or slab does.
	 *
	 * <p>Only a material the game itself turns - the glazed terracotta, whose pattern is laid out
	 * around a facing - has models for the four values. Every other eighth block keeps its own
	 * grain and draws the same model for all four.
	 */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** The shape of each single piece, in index order. */
	private static final VoxelShape[] PIECES = pieces();

	/** The shapes of every one of the 256 states, in index-bitmask order; see {@link #mask}. */
	private static final VoxelShape[] SHAPES = shapes();

	private MBEighthProperties() {
	}

	/** @return the index of the piece filling the given halves of the cell. */
	public static int index(final boolean east, final boolean upper, final boolean south) {
		return (east ? 4 : 0) | (upper ? 2 : 0) | (south ? 1 : 0);
	}

	/** @return {@code true} when {@link #index} is to fill the eastern half of the cell. */
	public static boolean isEast(final int index) {
		return (index & 4) != 0;
	}

	/** @return {@code true} when {@link #index} is to fill the upper half of the cell. */
	public static boolean isUpper(final int index) {
		return (index & 2) != 0;
	}

	/** @return {@code true} when {@link #index} is to fill the southern half of the cell. */
	public static boolean isSouth(final int index) {
		return (index & 1) != 0;
	}

	/** @return the switch of the piece at {@code index}. */
	public static BooleanProperty bit(final int index) {
		return BITS[index];
	}

	/**
	 * @return the index {@code index} takes after {@code quarters} quarter turns clockwise seen from
	 *         above, which is the direction a block state's {@code y} rotation turns a model.
	 */
	public static int turned(final int index, final int quarters) {
		boolean east = isEast(index);
		boolean south = isSouth(index);

		for (int turn = 0; turn < Mth.positiveModulo(quarters, TURNS); turn++) {
			boolean turnedEast = !south;
			south = east;
			east = turnedEast;
		}

		return index(east, isUpper(index), south);
	}

	/** @return the quarter turn {@code facing} names, counted the way {@link #turned} counts them. */
	public static int turns(final Direction facing) {
		return MBBlockStateProperties.glazedTurns(facing);
	}

	/** @return the direction {@link #turns} maps to {@code quarters}; the inverse of {@code turns}. */
	public static Direction facing(final int quarters) {
		return switch (Mth.positiveModulo(quarters, TURNS)) {
			case 0 -> Direction.SOUTH;
			case 1 -> Direction.WEST;
			case 2 -> Direction.NORTH;
			default -> Direction.EAST;
		};
	}

	/** @return the switches of {@code state} as one bitmask, bit {@code i} being {@code BITS[i]}. */
	public static int mask(final BlockState state) {
		int mask = 0;

		for (int index = 0; index < BITS.length; index++) {
			if (state.getValue(BITS[index])) {
				mask |= 1 << index;
			}
		}

		return mask;
	}

	/** @return {@code true} for the state that hides nothing, i.e. the plain full block. */
	public static boolean isFull(final BlockState state) {
		return mask(state) == 0xFF;
	}

	/** @return the outline shape of {@code state}: the union of the pieces it still has. */
	public static VoxelShape shape(final BlockState state) {
		return SHAPES[mask(state)];
	}

	/** @return the shape of the single piece at {@code index}. */
	public static VoxelShape piece(final int index) {
		return PIECES[index];
	}

	/**
	 * @return {@code state} with every piece moved {@code quarters} quarter turns clockwise seen from
	 *         above, and its texture turned with them, so that the state still describes the same
	 *         build.
	 */
	public static BlockState turned(final BlockState state, final int quarters) {
		BlockState turned = state;

		for (int index = 0; index < BITS.length; index++) {
			turned = turned.setValue(BITS[turned(index, quarters)], state.getValue(BITS[index]));
		}

		return turned.setValue(FACING, facing(turns(state.getValue(FACING)) + quarters));
	}

	private static VoxelShape[] pieces() {
		VoxelShape[] pieces = new VoxelShape[BITS.length];

		for (int index = 0; index < pieces.length; index++) {
			double x = isEast(index) ? 8.0 : 0.0;
			double y = isUpper(index) ? 8.0 : 0.0;
			double z = isSouth(index) ? 8.0 : 0.0;
			pieces[index] = Block.box(x, y, z, x + 8.0, y + 8.0, z + 8.0);
		}

		return pieces;
	}

	private static VoxelShape[] shapes() {
		VoxelShape[] shapes = new VoxelShape[1 << BITS.length];

		for (int mask = 0; mask < shapes.length; mask++) {
			VoxelShape shape = Shapes.empty();

			for (int index = 0; index < BITS.length; index++) {
				if ((mask & (1 << index)) != 0) {
					shape = Shapes.or(shape, PIECES[index]);
				}
			}

			shapes[mask] = shape;
		}

		return shapes;
	}
}
