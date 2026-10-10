package com.camelie.morebuildingblocks.block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The twelve pieces a pane is made of, and the shapes they add up to.
 *
 * <p>A pane keeps its post - the two pixel column through the middle of the block - and gains up
 * to twelve pieces around it: eight upright halves, two per horizontal direction (the lower half
 * is the vanilla connection boolean, the upper one is this mod's {@code *_up} flag), and four
 * horizontal corners at mid height, one per quadrant. A piece is two pixels thick, so the outline
 * and the collision box are the same shape.
 *
 * <p>With none of the eight flags set the pieces are exactly the vanilla post and arms - the
 * lower half stands for the arm itself - so an untouched pane keeps the shape it always had.
 */
public final class MBPaneShapes {
	/** The post: a two pixel column through the middle of the block. */
	private static final VoxelShape POST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
	private static final VoxelShape NORTH_LOWER = Block.box(7.0, 0.0, 0.0, 9.0, 8.0, 8.0);
	private static final VoxelShape NORTH_UPPER = Block.box(7.0, 8.0, 0.0, 9.0, 16.0, 8.0);
	private static final VoxelShape EAST_LOWER = Block.box(8.0, 0.0, 7.0, 16.0, 8.0, 9.0);
	private static final VoxelShape EAST_UPPER = Block.box(8.0, 8.0, 7.0, 16.0, 16.0, 9.0);
	private static final VoxelShape SOUTH_LOWER = Block.box(7.0, 0.0, 8.0, 9.0, 8.0, 16.0);
	private static final VoxelShape SOUTH_UPPER = Block.box(7.0, 8.0, 8.0, 9.0, 16.0, 16.0);
	private static final VoxelShape WEST_LOWER = Block.box(0.0, 0.0, 7.0, 8.0, 8.0, 9.0);
	private static final VoxelShape WEST_UPPER = Block.box(0.0, 8.0, 7.0, 8.0, 16.0, 9.0);
	private static final VoxelShape CORNER_NORTH_EAST = Block.box(8.0, 7.0, 0.0, 16.0, 9.0, 8.0);
	private static final VoxelShape CORNER_SOUTH_EAST = Block.box(8.0, 7.0, 8.0, 16.0, 9.0, 16.0);
	private static final VoxelShape CORNER_SOUTH_WEST = Block.box(0.0, 7.0, 8.0, 8.0, 9.0, 16.0);
	private static final VoxelShape CORNER_NORTH_WEST = Block.box(0.0, 7.0, 0.0, 8.0, 9.0, 8.0);

	private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

	private MBPaneShapes() {
	}

	/** Whether this mod's pieces are in play for this state. */
	public static boolean isPane(final BlockState state) {
		return state.getBlock() instanceof IronBarsBlock && state.hasProperty(MBPaneProperties.NORTH_UP);
	}

	/** The shape of the post and every piece the state turns on. */
	public static VoxelShape shape(final BlockState state) {
		return SHAPES.computeIfAbsent(state, MBPaneShapes::build);
	}

	private static VoxelShape build(final BlockState state) {
		VoxelShape shape = POST;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (lower(state, direction)) {
				shape = Shapes.or(shape, lowerShape(direction));
			}

			if (upper(state, direction)) {
				shape = Shapes.or(shape, upperShape(direction));
			}
		}

		for (Direction[] pair : CORNERS) {
			if (corner(state, pair[0], pair[1])) {
				shape = Shapes.or(shape, cornerShape(pair[0], pair[1]));
			}
		}

		return shape;
	}

	/**
	 * Whether the neighbour hides the state's face on this side.
	 *
	 * <p>Vanilla hides a whole face between two panes as soon as both sides have an arm, which
	 * blanks pieces that stand alone: the upper half of one pane faces the air when the pane above
	 * carries no arm there. This asks instead whether the pieces on either side of the shared plane
	 * line up, and only then hides the face.
	 */
	public static boolean hides(final BlockState state, final BlockState neighbour, final Direction direction) {
		if (direction.getAxis().isVertical()) {
			boolean upwards = direction == Direction.UP;

			for (Direction arm : Direction.Plane.HORIZONTAL) {
				boolean here = upwards ? upper(state, arm) : lower(state, arm);
				boolean there = upwards ? lower(neighbour, arm) : upper(neighbour, arm);

				if (here != there) {
					return false;
				}
			}

			return true;
		}

		Direction opposite = direction.getOpposite();
		Direction left = direction.getCounterClockWise();
		Direction right = direction.getClockWise();

		return lower(state, direction) == lower(neighbour, opposite)
			&& upper(state, direction) == upper(neighbour, opposite)
			&& corner(state, left, direction) == corner(neighbour, right, opposite)
			&& corner(state, right, direction) == corner(neighbour, left, opposite);
	}

	/** The vanilla connection boolean of this arm, which is also its lower half. */
	public static boolean lower(final BlockState state, final Direction direction) {
		return state.getValue(MBPaneProperties.lower(direction));
	}

	/** The upper half of this arm. */
	public static boolean upper(final BlockState state, final Direction direction) {
		return state.getValue(MBPaneProperties.upper(direction));
	}

	/** The mid height piece filling the quadrant between two perpendicular directions. */
	public static boolean corner(final BlockState state, final Direction first, final Direction second) {
		Direction alongX = first.getAxis() == Direction.Axis.X ? first : second;
		Direction alongZ = alongX == first ? second : first;
		boolean east = alongX == Direction.EAST;
		boolean south = alongZ == Direction.SOUTH;

		if (east) {
			return state.getValue(south ? MBPaneProperties.CORNER_SOUTH_EAST : MBPaneProperties.CORNER_NORTH_EAST);
		}

		return state.getValue(south ? MBPaneProperties.CORNER_SOUTH_WEST : MBPaneProperties.CORNER_NORTH_WEST);
	}

	/** The four quadrants, each named by the two directions it lies between. */
	private static final Direction[][] CORNERS = {
		{Direction.NORTH, Direction.EAST},
		{Direction.SOUTH, Direction.EAST},
		{Direction.SOUTH, Direction.WEST},
		{Direction.NORTH, Direction.WEST}
	};

	private static VoxelShape lowerShape(final Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH_LOWER;
			case EAST -> EAST_LOWER;
			case SOUTH -> SOUTH_LOWER;
			case WEST -> WEST_LOWER;
			default -> throw new IllegalArgumentException("A pane arm points horizontally, not " + direction);
		};
	}

	private static VoxelShape upperShape(final Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH_UPPER;
			case EAST -> EAST_UPPER;
			case SOUTH -> SOUTH_UPPER;
			case WEST -> WEST_UPPER;
			default -> throw new IllegalArgumentException("A pane arm points horizontally, not " + direction);
		};
	}

	private static VoxelShape cornerShape(final Direction first, final Direction second) {
		Direction alongX = first.getAxis() == Direction.Axis.X ? first : second;
		Direction alongZ = alongX == first ? second : first;
		boolean east = alongX == Direction.EAST;
		boolean south = alongZ == Direction.SOUTH;

		if (east) {
			return south ? CORNER_SOUTH_EAST : CORNER_NORTH_EAST;
		}

		return south ? CORNER_SOUTH_WEST : CORNER_NORTH_WEST;
	}
}
