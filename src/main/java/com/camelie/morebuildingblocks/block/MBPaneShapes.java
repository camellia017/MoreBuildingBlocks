package com.camelie.morebuildingblocks.block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The twelve corner plates a pane is made of, and the shapes they add up to.
 *
 * <p>A pane gains up to twelve pieces: eight upright plates, two per horizontal direction (the
 * lower one is the vanilla connection boolean, the upper one is this mod's {@code *_up} flag), and
 * four horizontal plates at mid height, one per quadrant. Every piece is a corner of the pane:
 * seven pixels of the quadrant its two directions share, two pixels thick, so the outline and the
 * collision box are the same shape. No piece reaches past the eight pixel line into the quadrant
 * beside it - the two pixels of band left open there, and the two pixel column through the middle,
 * are the shared boxes below.
 *
 * <p>The stick - the two pixel column through the middle plus the post faces - stands for a pane
 * that connects to nothing, and the models only draw it while all twelve flags are false. The
 * shapes follow: with none of them set the vanilla shape stays untouched, and as soon as one is
 * set the shape is the pieces alone, which is what the models then draw.
 *
 * <p>A corner is cut the way vanilla's own pane is laid out, seven pixels of corner, two of edge,
 * seven of corner: the quadrant itself is seven, and thin boxes fill the two pixels of the plate
 * and of the arm that lead to it. Those boxes are shared - the northern half of the plate belongs
 * to the north eastern and the north western corner both, the eastern arm to the north eastern and
 * the south eastern one - so each is added once for whichever of the pair, or of the two upright
 * plates on that side, is on. The two pixels where the plate and the arm cross at mid height are
 * filled by one more box of their own, and the two pixel column through the middle by one box per
 * half, for each half that carries an upright plate. Without them a cornered pane would keep a hole
 * between its quadrants, which is what a plate that stops at the eight pixel line leaves on its
 * own.
 *
 * <p>Vanilla asks {@code skipRendering} whether a whole face may be skipped, not whether the
 * pieces on either side of it meet. Two questions have to be answered here: a pane's middle
 * height corners stay visible between stacked panes even though vanilla hides that face outright,
 * and a piece facing air has to be drawn even when the neighbour carries an arm.
 */
public final class MBPaneShapes {
	/**
	 * The upright plates of an arm: the corner of the quadrant it points into, seven pixels along
	 * the arm, seven pixels tall and two pixels thick, so it stops at the eight pixel line.
	 */
	private static final VoxelShape NORTH_LOWER = Block.box(7.0, 0.0, 0.0, 9.0, 7.0, 7.0);
	private static final VoxelShape NORTH_UPPER = Block.box(7.0, 9.0, 0.0, 9.0, 16.0, 7.0);
	private static final VoxelShape EAST_LOWER = Block.box(9.0, 0.0, 7.0, 16.0, 7.0, 9.0);
	private static final VoxelShape EAST_UPPER = Block.box(9.0, 9.0, 7.0, 16.0, 16.0, 9.0);
	private static final VoxelShape SOUTH_LOWER = Block.box(7.0, 0.0, 9.0, 9.0, 7.0, 16.0);
	private static final VoxelShape SOUTH_UPPER = Block.box(7.0, 9.0, 9.0, 9.0, 16.0, 16.0);
	private static final VoxelShape WEST_LOWER = Block.box(0.0, 0.0, 7.0, 7.0, 7.0, 9.0);
	private static final VoxelShape WEST_UPPER = Block.box(0.0, 9.0, 7.0, 7.0, 16.0, 9.0);
	/** The two pixel column through the middle, in the two halves the upright plates leave open. */
	private static final VoxelShape COLUMN_LOWER = Block.box(7.0, 0.0, 7.0, 9.0, 7.0, 9.0);
	private static final VoxelShape COLUMN_UPPER = Block.box(7.0, 9.0, 7.0, 9.0, 16.0, 9.0);
	/** The seven pixel quadrant of a corner, its outer corner at the block's own corner. */
	private static final VoxelShape CORNER_NORTH_EAST = Block.box(9.0, 7.0, 0.0, 16.0, 9.0, 7.0);
	private static final VoxelShape CORNER_SOUTH_EAST = Block.box(9.0, 7.0, 9.0, 16.0, 9.0, 16.0);
	private static final VoxelShape CORNER_SOUTH_WEST = Block.box(0.0, 7.0, 9.0, 7.0, 9.0, 16.0);
	private static final VoxelShape CORNER_NORTH_WEST = Block.box(0.0, 7.0, 0.0, 7.0, 9.0, 7.0);

	/**
	 * The two pixels of band that lead from a plate to the middle of the block.
	 *
	 * <p>Each is shared by the corners on its side: the northern band by the north eastern and the
	 * north western corner and by the two upright plates of the north arm, the eastern one by the
	 * corners east of the middle and by the plates of the east arm. It is added once for whichever
	 * of them is on.
	 */
	private static final VoxelShape EDGE_NORTH = Block.box(7.0, 7.0, 0.0, 9.0, 9.0, 7.0);
	private static final VoxelShape EDGE_SOUTH = Block.box(7.0, 7.0, 9.0, 9.0, 9.0, 16.0);
	private static final VoxelShape EDGE_EAST = Block.box(9.0, 7.0, 7.0, 16.0, 9.0, 9.0);
	private static final VoxelShape EDGE_WEST = Block.box(0.0, 7.0, 7.0, 7.0, 9.0, 9.0);

	/** Where the plate and the arm cross at mid height, two pixels either way. */
	private static final VoxelShape MIDDLE = Block.box(7.0, 7.0, 7.0, 9.0, 9.0, 9.0);

	/**
	 * The piece shapes depend on the state alone, so they are cached. An empty shape means the
	 * state has no piece turned on and keeps the shape it already had.
	 */
	private static final Map<BlockState, VoxelShape> PIECE_SHAPES = new ConcurrentHashMap<>();

	private MBPaneShapes() {
	}

	/**
	 * Whether this mod's pieces are in play for this state.
	 *
	 * <p>Panes and iron bars share {@link IronBarsBlock} and the same post and arm sizes, so they
	 * share the twelve pieces, the shapes and the connection rules. Panes from other mods are left
	 * alone: their textures may not follow vanilla's naming, and only the {@code minecraft}
	 * blockstates are overridden, so pieces there would be collision boxes without models.
	 */
	public static boolean isPane(final BlockState state) {
		return state.getBlock() instanceof IronBarsBlock
			&& state.hasProperty(MBPaneProperties.NORTH_UP)
			&& BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft");
	}

	/** Whether any of the four corners is on. */
	public static boolean hasCorner(final BlockState state) {
		if (!isPane(state)) {
			return false;
		}

		for (Direction[] pair : CORNERS) {
			if (corner(state, pair[0], pair[1])) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The outline or the collision box of a pane.
	 *
	 * <p>With every flag false the shape stays as it was, so an untouched pane keeps the shape it
	 * always had. With a piece turned on the shape becomes the pieces alone, which is what the
	 * models draw from then on.
	 *
	 * @param currentShape the shape vanilla came up with, possibly already widened by other code
	 * @param vanillaShape the same shape without those additions, used to tell them apart
	 */
	public static VoxelShape adjustShape(final VoxelShape currentShape, final VoxelShape vanillaShape,
			final BlockState state) {
		VoxelShape pieces = PIECE_SHAPES.computeIfAbsent(state, MBPaneShapes::build);

		if (pieces.isEmpty()) {
			return currentShape;
		}

		if (currentShape != vanillaShape) {
			// Something else widened the shape - keep that part, or the crosshair skips it
			pieces = Shapes.or(pieces, Shapes.join(currentShape, vanillaShape, BooleanOp.ONLY_FIRST));
		}

		return pieces;
	}

	/**
	 * Whether the neighbour hides the state's face on this side.
	 *
	 * <p>Vanilla hides a whole face between two panes as soon as both sides have an arm, and hides
	 * every face between two stacked panes outright. Both were written for a pane that is a post
	 * with full height arms. They blank pieces that stand alone - the upper half of one pane faces
	 * the air when the pane above carries no arm there - and they blank the up and down faces of a
	 * corner, which sits at mid height and stays visible between stacked panes. This asks instead
	 * whether every piece this state draws on the shared plane meets its own piece on the other
	 * side, and hides the face only then, so a piece the neighbour carries nothing behind stays
	 * visible however much the neighbour adds beside it.
	 */
	public static boolean hides(final BlockState state, final BlockState neighbour, final Direction direction) {
		if (direction.getAxis().isVertical()) {
			// A corner's up and down faces are exposed whatever the pane above or below carries
			if (hasCorner(state)) {
				return false;
			}

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

		// Every piece this state draws on the shared plane has to meet its own piece on the other
		// side. A piece the neighbour carries nothing behind keeps its face, whichever pieces the
		// neighbour adds beside it.
		return (!lower(state, direction) || lower(neighbour, opposite))
			&& (!upper(state, direction) || upper(neighbour, opposite))
			&& (!corner(state, left, direction) || corner(neighbour, left, opposite))
			&& (!corner(state, right, direction) || corner(neighbour, right, opposite));
	}

	/** The vanilla connection boolean of this arm, which is also its lower plate. */
	public static boolean lower(final BlockState state, final Direction direction) {
		return state.getValue(MBPaneProperties.lower(direction));
	}

	/** This mod's own flag for the upper plate of this arm. */
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

	/** Every piece the state turns on, or an empty shape when it turns on none. */
	private static VoxelShape build(final BlockState state) {
		if (!isPane(state)) {
			return Shapes.empty();
		}

		VoxelShape shape = Shapes.empty();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (lower(state, direction)) {
				shape = Shapes.or(shape, lowerShape(direction));
			}

			if (upper(state, direction)) {
				shape = Shapes.or(shape, upperShape(direction));
			}
		}

		boolean northEast = corner(state, Direction.NORTH, Direction.EAST);
		boolean southEast = corner(state, Direction.SOUTH, Direction.EAST);
		boolean southWest = corner(state, Direction.SOUTH, Direction.WEST);
		boolean northWest = corner(state, Direction.NORTH, Direction.WEST);
		// A band runs along its side, so every corner of that side turns it on: the two quadrants
		// beside it in the plate, and the upright plate of the arm it leads into
		boolean north = northEast || northWest || plated(state, Direction.NORTH);
		boolean south = southEast || southWest || plated(state, Direction.SOUTH);
		boolean east = northEast || southEast || plated(state, Direction.EAST);
		boolean west = northWest || southWest || plated(state, Direction.WEST);
		// The column is left open between the plates of the two halves, so it follows them alone
		boolean column = lower(state, Direction.NORTH) || lower(state, Direction.EAST)
				|| lower(state, Direction.SOUTH) || lower(state, Direction.WEST);
		boolean cap = upper(state, Direction.NORTH) || upper(state, Direction.EAST)
				|| upper(state, Direction.SOUTH) || upper(state, Direction.WEST);

		shape = Shapes.or(shape,
				northEast ? CORNER_NORTH_EAST : Shapes.empty(),
				southEast ? CORNER_SOUTH_EAST : Shapes.empty(),
				southWest ? CORNER_SOUTH_WEST : Shapes.empty(),
				northWest ? CORNER_NORTH_WEST : Shapes.empty(),
				north ? EDGE_NORTH : Shapes.empty(),
				south ? EDGE_SOUTH : Shapes.empty(),
				east ? EDGE_EAST : Shapes.empty(),
				west ? EDGE_WEST : Shapes.empty(),
				column ? COLUMN_LOWER : Shapes.empty(),
				cap ? COLUMN_UPPER : Shapes.empty(),
				north || south || east || west ? MIDDLE : Shapes.empty());

		return shape;
	}

	/** Whether either plate of this arm is on, the lower one or the upper one. */
	private static boolean plated(final BlockState state, final Direction direction) {
		return lower(state, direction) || upper(state, direction);
	}

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
}
