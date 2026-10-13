package com.camelie.morebuildingblocks.block;

import java.util.concurrent.atomic.AtomicReferenceArray;
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
	 * The twelve flags of a pane in one number, and one shape per combination of them, built on
	 * first use.
	 *
	 * <p>A shape depends on the flags alone: which boxes make it up are static, and the block, the
	 * position and the waterlogged flag never reach them. Vanilla also gives every pane the same
	 * post and arm sizes, so panes and iron bars alike read the same boxes. Caching by state built
	 * a shape for each of a pane's 8192 states, half of them waterlogged twins of the other half,
	 * while the flags have 4096 combinations in all — and one table of those serves every pane.
	 *
	 * <p>The bits are the lower plates in 0..3, the upper plates in 4..7 and the corners in 8..11,
	 * each group named north, east, south, west, the corners in the order of {@link #CORNERS}.
	 * Shapes are immutable, so two threads racing to build the same one only waste a shape, and
	 * either answer is the same.
	 */
	private static final int ARMS = 4;
	private static final int UPPER_BIT = ARMS;
	private static final int CORNER_BIT = 2 * ARMS;

	/** One slot per combination of the flags: the lower plates, the upper plates and the corners. */
	private static final AtomicReferenceArray<VoxelShape> PIECE_SHAPES =
		new AtomicReferenceArray<>(1 << (CORNER_BIT + 4));

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
		if (!isPane(state)) {
			// Another mod's pane, or a fence: nothing of this mod's is in play
			return currentShape;
		}

		final int mask = mask(state);

		if (mask == 0) {
			// Every flag is off, so the pane is the one vanilla drew and keeps the shape it had
			return currentShape;
		}

		VoxelShape pieces = PIECE_SHAPES.get(mask);

		if (pieces == null) {
			pieces = build(mask);
			PIECE_SHAPES.set(mask, pieces);
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
	 * whether the pieces this state draws on the shared plane meet their own piece on the other
	 * side, and hides the face only then, so a piece the neighbour carries nothing behind stays
	 * visible however much the neighbour adds beside it.
	 *
	 * <p>Across an upright plane a corner answers on its own, against the corner of the neighbour
	 * that stands behind it: the middle pane of a slab leans on the block below and carries its four
	 * corners with no arm at all, and the panes beside it still line up with it, corner piece for
	 * corner piece. Where this state draws no corner on the plane, the arms answer as they always
	 * did.
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
		boolean nearCorner = corner(state, left, direction);
		boolean farCorner = corner(state, right, direction);

		// A corner of this state decides on its own across an upright plane, against the corner of
		// the neighbour that stands behind it. The middle pane of a slab leans on the block below
		// and keeps its four corners with no arm at all, and the panes beside it still line up with
		// it, corner piece for corner piece; an arm that meets nothing on the other side does not
		// keep those faces. Only where this state draws no corner on the plane do the arms answer,
		// as they always did, so a piece the neighbour carries nothing behind keeps its face.
		if (nearCorner || farCorner) {
			return (!nearCorner || corner(neighbour, left, opposite))
				&& (!farCorner || corner(neighbour, right, opposite));
		}

		return (!lower(state, direction) || lower(neighbour, opposite))
			&& (!upper(state, direction) || upper(neighbour, opposite));
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

	/** The corner box of each quadrant, in the order {@link #CORNERS} names them. */
	private static final VoxelShape[] CORNER_SHAPES = {
		CORNER_NORTH_EAST, CORNER_SOUTH_EAST, CORNER_SOUTH_WEST, CORNER_NORTH_WEST
	};

	/** The upright plates of each arm, in the order north, east, south, west. */
	private static final VoxelShape[] LOWER_SHAPES = {NORTH_LOWER, EAST_LOWER, SOUTH_LOWER, WEST_LOWER};
	private static final VoxelShape[] UPPER_SHAPES = {NORTH_UPPER, EAST_UPPER, SOUTH_UPPER, WEST_UPPER};

	/** The twelve flags of this state in one mask, the bits laid out as {@link #PIECE_SHAPES}. */
	private static int mask(final BlockState state) {
		int mask = 0;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			final int arm = arm(direction);

			if (lower(state, direction)) {
				mask |= 1 << arm;
			}

			if (upper(state, direction)) {
				mask |= 1 << (UPPER_BIT + arm);
			}
		}

		for (int quadrant = 0; quadrant < CORNERS.length; quadrant++) {
			if (corner(state, CORNERS[quadrant][0], CORNERS[quadrant][1])) {
				mask |= 1 << (CORNER_BIT + quadrant);
			}
		}

		return mask;
	}

	/** Where an arm sits among the four: north, east, south, west. */
	private static int arm(final Direction direction) {
		return switch (direction) {
			case NORTH -> 0;
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> throw new IllegalArgumentException("A pane arm points horizontally, not " + direction);
		};
	}

	/** Every piece the mask turns on. It is never asked for a mask of zero, which draws nothing. */
	private static VoxelShape build(final int mask) {
		VoxelShape shape = Shapes.empty();

		for (int arm = 0; arm < ARMS; arm++) {
			if (on(mask, arm)) {
				shape = Shapes.or(shape, LOWER_SHAPES[arm]);
			}

			if (on(mask, UPPER_BIT + arm)) {
				shape = Shapes.or(shape, UPPER_SHAPES[arm]);
			}
		}

		boolean northEast = on(mask, CORNER_BIT);
		boolean southEast = on(mask, CORNER_BIT + 1);
		boolean southWest = on(mask, CORNER_BIT + 2);
		boolean northWest = on(mask, CORNER_BIT + 3);
		// A band runs along its side, so every corner of that side turns it on: the two quadrants
		// beside it in the plate, and the upright plate of the arm it leads into
		boolean north = northEast || northWest || plated(mask, 0);
		boolean south = southEast || southWest || plated(mask, 2);
		boolean east = northEast || southEast || plated(mask, 1);
		boolean west = northWest || southWest || plated(mask, 3);
		// The column is left open between the plates of the two halves, so it follows them alone
		boolean column = anyOn(mask, 0, ARMS);
		boolean cap = anyOn(mask, UPPER_BIT, ARMS);

		shape = Shapes.or(shape,
				northEast ? CORNER_SHAPES[0] : Shapes.empty(),
				southEast ? CORNER_SHAPES[1] : Shapes.empty(),
				southWest ? CORNER_SHAPES[2] : Shapes.empty(),
				northWest ? CORNER_SHAPES[3] : Shapes.empty(),
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
	private static boolean plated(final int mask, final int arm) {
		return on(mask, arm) || on(mask, UPPER_BIT + arm);
	}

	/** Whether the flag in this bit is set. */
	private static boolean on(final int mask, final int bit) {
		return (mask & 1 << bit) != 0;
	}

	/** Whether any of the flags from this bit on, this many of them, is set. */
	private static boolean anyOn(final int mask, final int from, final int count) {
		final int bits = (1 << count) - 1;

		return (mask & bits << from) != 0;
	}
}
