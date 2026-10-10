package com.camelie.morebuildingblocks.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The six rules that decide which of a pane's twelve pieces are on, and the three ways the mod keeps
 * them up to date.
 *
 * <p>The twelve pieces are the four vanilla connection booleans, which here mean the lower half of
 * each arm, the mod's four {@code *_up} flags for the upper halves, and the four corner flags, see
 * {@link MBPaneProperties}. A rule turns one or more pieces on or off; the rules in the order they
 * are applied, each one seeing what the previous ones left behind:
 *
 * <ol>
 * <li>Two adjacent directions hold a block, and the space between them does not: the corner is off.
 * This has the last word, so a corner the next two rules lit is put out again.</li>
 * <li>Two adjacent directions hold a block that is not a pane, and so does the space between them:
 * that piece is on.</li>
 * <li>Two adjacent directions are panes, and the diagonal space between them is a pane too: that
 * piece is on.</li>
 * <li>No diagonal pane anywhere, and some horizontal direction holds a block: that direction's arm
 * is at full height, both halves on.</li>
 * <li>Exactly one of the two vertical neighbours holds a block that is not a pane: the arms are
 * copied from that side to the other, upper half from lower half or the other way round - this is a
 * copy, so an arm half can go out again here. Corners stay as they are.</li>
 * <li>Nothing lit anything, and both the space above and the space below are air: all twelve pieces
 * are on, which is what makes a lone pane hanging in the air a full window instead of a post.</li>
 * </ol>
 *
 * <p>Every rule reads the world - the block at each neighbour position, or whether that block is a
 * pane - and never the pieces a neighbour computed. A pane's twelve flags are therefore a pure
 * function of its surroundings: the same surroundings always give the same flags, whatever order
 * blocks were placed in, and one pass is enough to settle a pane. That is what
 * {@code docs/refit-pane-spec.md} promised, and why the queue in {@link #refreshChunk} is only
 * insurance rather than a fixpoint iteration.
 *
 * <p>Nothing outside {@code minecraft}'s own panes is touched: {@link MBPaneShapes#isPane} turns
 * away every other block, and the callers of {@link #update} leave those blocks as they are.
 */
public final class MBPaneConnection {
	/**
	 * Whether a chunk post process call is running right now. {@code LevelChunkPostProcessMixin}
	 * flips this around the call.
	 */
	private static boolean inPostProcess;

	/**
	 * Whether that post process call met a pane of ours. Loading a chunk from disk runs the same
	 * post process as generating one, so "the call happened" says nothing about whether there is
	 * anything to settle; this flag records whether the work is worth doing at all.
	 */
	private static boolean touchedPane;

	private MBPaneConnection() {
	}

	/**
	 * The offsets that can affect a pane: the six orthogonal neighbours plus the twelve diagonal
	 * spaces between two of them - four in the same horizontal plane, eight above and below an arm.
	 * Rules 2 to 5 read nothing outside this table, so a change to the rules that reaches further
	 * has to extend it.
	 */
	private static final int[][] NEIGHBOUR_OFFSETS = {
		{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
		{1, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {-1, 0, -1},
		{0, 1, -1}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0},
		{0, -1, -1}, {1, -1, 0}, {0, -1, 1}, {-1, -1, 0}
	};

	/** The twelve diagonal offsets of {@link #NEIGHBOUR_OFFSETS}, on their own so they can be walked. */
	private static final int[][] DIAGONAL_OFFSETS = {
		{1, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {-1, 0, -1},
		{0, 1, -1}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0},
		{0, -1, -1}, {1, -1, 0}, {0, -1, 1}, {-1, -1, 0}
	};

	/** A chunk post process call is starting; called by {@code LevelChunkPostProcessMixin}. */
	public static void beginPostProcess() {
		inPostProcess = true;
		touchedPane = false;
	}

	/**
	 * That call is over; returns whether it met a pane of ours, so the caller knows whether the
	 * chunk needs {@link #refreshChunk}. Called by {@code LevelChunkPostProcessMixin}.
	 */
	public static boolean endPostProcess() {
		boolean touched = touchedPane;
		inPostProcess = false;
		touchedPane = false;
		return touched;
	}

	/**
	 * The twelve flags this pane should have, given the world around it. Anything that is not one of
	 * our panes comes back unchanged.
	 */
	public static BlockState update(final BlockState state, final BlockGetter level, final BlockPos pos) {
		if (!MBPaneShapes.isPane(state)
				|| !state.hasProperty(MBPaneProperties.CORNER_NORTH_EAST)
				|| !(state.getBlock() instanceof IronBarsBlock pane)) {
			// Someone else's pane: not even worth a post process pass, so no touchedPane either
			return state;
		}

		if (inPostProcess) {
			touchedPane = true;
		}

		// ===== The six directions: is it a block a vanilla pane connects to, and is it a pane =====
		boolean north = connectsTo(pane, level, pos, Direction.NORTH);
		boolean east = connectsTo(pane, level, pos, Direction.EAST);
		boolean south = connectsTo(pane, level, pos, Direction.SOUTH);
		boolean west = connectsTo(pane, level, pos, Direction.WEST);
		boolean up = connectsTo(pane, level, pos, Direction.UP);
		boolean down = connectsTo(pane, level, pos, Direction.DOWN);
		boolean paneNorth = isPaneBlock(level, pos.north());
		boolean paneEast = isPaneBlock(level, pos.east());
		boolean paneSouth = isPaneBlock(level, pos.south());
		boolean paneWest = isPaneBlock(level, pos.west());
		BlockState aboveState = level.getBlockState(pos.above());
		BlockState belowState = level.getBlockState(pos.below());
		boolean paneUp = aboveState.getBlock() instanceof IronBarsBlock;
		boolean paneDown = belowState.getBlock() instanceof IronBarsBlock;
		// Rule 6 wants "no block at all" above and below, which is air and not e.g. a fence
		boolean airAbove = aboveState.isAir();
		boolean airBelow = belowState.isAir();
		// A connectable block that is not a pane: a full block, a wall, a slab pressed against us
		boolean solidNorth = north && !paneNorth;
		boolean solidEast = east && !paneEast;
		boolean solidSouth = south && !paneSouth;
		boolean solidWest = west && !paneWest;
		boolean solidUp = up && !paneUp;
		boolean solidDown = down && !paneDown;

		// ===== The four horizontal diagonals: a diagonal has no single face, so either side counts =====
		boolean diagonalNorthEast = connectsToDiagonal(pane, level, pos, 1, -1);
		boolean diagonalSouthEast = connectsToDiagonal(pane, level, pos, 1, 1);
		boolean diagonalSouthWest = connectsToDiagonal(pane, level, pos, -1, 1);
		boolean diagonalNorthWest = connectsToDiagonal(pane, level, pos, -1, -1);

		// ===== Rule 2, twelve times: both directions hold a block that is not a pane =====
		boolean solidPairNorthEast = solidNorth && solidEast;
		boolean solidPairSouthEast = solidSouth && solidEast;
		boolean solidPairSouthWest = solidSouth && solidWest;
		boolean solidPairNorthWest = solidNorth && solidWest;
		boolean solidPairDownNorth = solidDown && solidNorth;
		boolean solidPairDownEast = solidDown && solidEast;
		boolean solidPairDownSouth = solidDown && solidSouth;
		boolean solidPairDownWest = solidDown && solidWest;
		boolean solidPairUpNorth = solidUp && solidNorth;
		boolean solidPairUpEast = solidUp && solidEast;
		boolean solidPairUpSouth = solidUp && solidSouth;
		boolean solidPairUpWest = solidUp && solidWest;
		boolean rule2Hit = solidPairNorthEast || solidPairSouthEast || solidPairSouthWest || solidPairNorthWest
				|| solidPairDownNorth || solidPairDownEast || solidPairDownSouth || solidPairDownWest
				|| solidPairUpNorth || solidPairUpEast || solidPairUpSouth || solidPairUpWest;

		// ===== Rule 3, twelve times: both directions are panes, and so is the space between them =====
		// Two horizontal arms meet in the corner of the same plane; an arm above or below meets a
		// horizontal one in the diagonal that is one step up or down
		boolean panePairNorthEast = paneNorth && paneEast && isPaneAt(level, pos, 1, 0, -1);
		boolean panePairSouthEast = paneSouth && paneEast && isPaneAt(level, pos, 1, 0, 1);
		boolean panePairSouthWest = paneSouth && paneWest && isPaneAt(level, pos, -1, 0, 1);
		boolean panePairNorthWest = paneNorth && paneWest && isPaneAt(level, pos, -1, 0, -1);
		boolean panePairDownNorth = paneDown && paneNorth && isPaneAt(level, pos, 0, -1, -1);
		boolean panePairDownEast = paneDown && paneEast && isPaneAt(level, pos, 1, -1, 0);
		boolean panePairDownSouth = paneDown && paneSouth && isPaneAt(level, pos, 0, -1, 1);
		boolean panePairDownWest = paneDown && paneWest && isPaneAt(level, pos, -1, -1, 0);
		boolean panePairUpNorth = paneUp && paneNorth && isPaneAt(level, pos, 0, 1, -1);
		boolean panePairUpEast = paneUp && paneEast && isPaneAt(level, pos, 1, 1, 0);
		boolean panePairUpSouth = paneUp && paneSouth && isPaneAt(level, pos, 0, 1, 1);
		boolean panePairUpWest = paneUp && paneWest && isPaneAt(level, pos, -1, 1, 0);
		boolean rule3Hit = panePairNorthEast || panePairSouthEast || panePairSouthWest || panePairNorthWest
				|| panePairDownNorth || panePairDownEast || panePairDownSouth || panePairDownWest
				|| panePairUpNorth || panePairUpEast || panePairUpSouth || panePairUpWest;

		// ----- Rules 2 and 3 both only turn pieces on, so a piece either of them lit stays lit -----
		boolean northEast = solidPairNorthEast || panePairNorthEast;
		boolean southEast = solidPairSouthEast || panePairSouthEast;
		boolean southWest = solidPairSouthWest || panePairSouthWest;
		boolean northWest = solidPairNorthWest || panePairNorthWest;
		boolean northLower = solidPairDownNorth || panePairDownNorth;
		boolean eastLower = solidPairDownEast || panePairDownEast;
		boolean southLower = solidPairDownSouth || panePairDownSouth;
		boolean westLower = solidPairDownWest || panePairDownWest;
		boolean northUpper = solidPairUpNorth || panePairUpNorth;
		boolean eastUpper = solidPairUpEast || panePairUpEast;
		boolean southUpper = solidPairUpSouth || panePairUpSouth;
		boolean westUpper = solidPairUpWest || panePairUpWest;

		// ----- Rule 4: no diagonal pane anywhere, and this direction holds a block, so the arm is
		// full height. Runs after rules 2 and 3 and only turns pieces on, so their corners stay -----
		boolean rule4Hit = !rule3Hit && (north || east || south || west);
		if (!rule3Hit) {
			if (north) {
				northLower = true;
				northUpper = true;
			}
			if (east) {
				eastLower = true;
				eastUpper = true;
			}
			if (south) {
				southLower = true;
				southUpper = true;
			}
			if (west) {
				westLower = true;
				westUpper = true;
			}
		}

		// ----- Rule 1, first say and last word: two adjacent directions hold a block and the space
		// between them does not, so the corner goes out again even if rule 2 or 3 just lit it -----
		boolean rule1Hit = (north && east && !diagonalNorthEast)
				|| (south && east && !diagonalSouthEast)
				|| (south && west && !diagonalSouthWest)
				|| (north && west && !diagonalNorthWest);
		if (north && east && !diagonalNorthEast) {
			northEast = false;
		}
		if (south && east && !diagonalSouthEast) {
			southEast = false;
		}
		if (south && west && !diagonalSouthWest) {
			southWest = false;
		}
		if (north && west && !diagonalNorthWest) {
			northWest = false;
		}

		// ----- Rule 5: exactly one of the two vertical neighbours holds a block that is not a pane,
		// so the arms copy that side's halves - a copy, so a half can go out again. Corners stay -----
		boolean nonPaneBlockAbove = !airAbove && !paneUp;
		boolean nonPaneBlockBelow = !airBelow && !paneDown;
		boolean rule5Hit = nonPaneBlockAbove ^ nonPaneBlockBelow;
		if (nonPaneBlockAbove && !nonPaneBlockBelow) {
			northUpper = northLower;
			eastUpper = eastLower;
			southUpper = southLower;
			westUpper = westLower;
		} else if (nonPaneBlockBelow && !nonPaneBlockAbove) {
			northLower = northUpper;
			eastLower = eastUpper;
			southLower = southUpper;
			westLower = westUpper;
		}

		// ----- Rule 6, the fallback: air above and below and no other rule had anything to say, so
		// a pane hanging in the air is the full window vanilla would draw, not the bare post -----
		if (airAbove && airBelow && !(rule1Hit || rule2Hit || rule3Hit || rule4Hit || rule5Hit)) {
			northLower = true;
			eastLower = true;
			southLower = true;
			westLower = true;
			northUpper = true;
			eastUpper = true;
			southUpper = true;
			westUpper = true;
			northEast = true;
			southEast = true;
			southWest = true;
			northWest = true;
		}

		return state
				.setValue(MBPaneProperties.lower(Direction.NORTH), northLower)
				.setValue(MBPaneProperties.lower(Direction.EAST), eastLower)
				.setValue(MBPaneProperties.lower(Direction.SOUTH), southLower)
				.setValue(MBPaneProperties.lower(Direction.WEST), westLower)
				.setValue(MBPaneProperties.upper(Direction.NORTH), northUpper)
				.setValue(MBPaneProperties.upper(Direction.EAST), eastUpper)
				.setValue(MBPaneProperties.upper(Direction.SOUTH), southUpper)
				.setValue(MBPaneProperties.upper(Direction.WEST), westUpper)
				.setValue(corner(1, -1), northEast)
				.setValue(corner(1, 1), southEast)
				.setValue(corner(-1, 1), southWest)
				.setValue(corner(-1, -1), northWest);
	}

	/** The same connection test vanilla uses: panes, walls, or a full face turned towards us. */
	private static boolean connectsTo(final IronBarsBlock pane, final BlockGetter level, final BlockPos pos,
			final Direction direction) {
		return attachesTo(pane, level, pos.relative(direction), direction.getOpposite());
	}

	/**
	 * Whether the block at {@code neighborPos} connects to us, judged exactly like vanilla's
	 * {@code IronBarsBlock#attachsTo}: another pane, iron bars or a wall counts whatever its shape
	 * is, anything else has to be a full face turned towards us.
	 *
	 * @param faceTowardUs the neighbour's face that looks at us
	 */
	private static boolean attachesTo(final IronBarsBlock pane, final BlockGetter level, final BlockPos neighborPos,
			final Direction faceTowardUs) {
		BlockState neighbor = level.getBlockState(neighborPos);
		return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, faceTowardUs));
	}

	/**
	 * Whether the diagonal space at a horizontal offset counts as holding a block. A diagonal has no
	 * single face, so either of the two sides that look at us is enough. A full block, a pane and a
	 * wall all count; a stair, which is only half a face tall, does not.
	 */
	private static boolean connectsToDiagonal(final IronBarsBlock pane, final BlockGetter level, final BlockPos pos,
			final int dx, final int dz) {
		BlockPos diagonal = pos.offset(dx, 0, dz);
		Direction backX = dx > 0 ? Direction.WEST : Direction.EAST;
		Direction backZ = dz > 0 ? Direction.NORTH : Direction.SOUTH;
		return attachesTo(pane, level, diagonal, backX) || attachesTo(pane, level, diagonal, backZ);
	}

	/** Whether that position holds a pane or iron bars, the family of blocks we cut into pieces. */
	private static boolean isPaneBlock(final BlockGetter level, final BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof IronBarsBlock;
	}

	/** The same question for an offset position; rule 3 asks it about twelve diagonals. */
	private static boolean isPaneAt(final BlockGetter level, final BlockPos pos, final int dx, final int dy, final int dz) {
		return isPaneBlock(level, pos.offset(dx, dy, dz));
	}

	/** The corner flag of the quadrant between two horizontal offsets, named by their signs. */
	private static Property<Boolean> corner(final int dx, final int dz) {
		if (dx > 0) {
			return dz > 0 ? MBPaneProperties.CORNER_SOUTH_EAST : MBPaneProperties.CORNER_NORTH_EAST;
		}

		return dz > 0 ? MBPaneProperties.CORNER_SOUTH_WEST : MBPaneProperties.CORNER_NORTH_WEST;
	}

	/**
	 * The direction from a diagonal position back to the pane it belongs to. Vanilla's shape update
	 * wants a direction, and this is the one that points at the block that changed; the rules read
	 * the world themselves, so nothing depends on it beyond being a legal neighbour.
	 */
	private static Direction directionBackToOrigin(final int dx, final int dy, final int dz) {
		if (dx != 0) {
			return dx > 0 ? Direction.WEST : Direction.EAST;
		}
		if (dy != 0) {
			return dy > 0 ? Direction.DOWN : Direction.UP;
		}
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	/**
	 * Settles every pane of ours in a chunk that has just been generated.
	 *
	 * <p>Vanilla's {@code LevelChunk#postProcessGeneration} calls {@code updateFromNeighbourShapes}
	 * once per marked position, in no particular order. A rule that read a neighbour's own flags
	 * would leave the panes that were computed first on a stale answer - that is how some iron bars
	 * used to stay unconnected until a block was placed beside them. These rules read the world
	 * instead, so one pass is already right, and this queue is insurance: {@link #update} is
	 * idempotent, so walking the neighbourhood cannot settle on a different answer. If the rules
	 * ever stop reading the world, this and {@code LevelChunkPostProcessMixin} become load bearing.
	 */
	public static void refreshChunk(final LevelChunk chunk, final Level level) {
		List<BlockPos> seeds = collectPanesInChunk(chunk);

		if (seeds.isEmpty()) {
			return;
		}

		// inQueue only keeps one copy of a position in the queue; a position leaves it when it is
		// taken out, so a pane that a later change touches again is computed again
		Set<BlockPos> inQueue = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		for (BlockPos pos : seeds) {
			if (inQueue.add(pos)) {
				queue.add(pos);
			}
		}

		// A ceiling, so that a pathological arrangement cannot bounce around forever
		int budget = seeds.size() * 8 + 64;

		while (!queue.isEmpty() && budget-- > 0) {
			BlockPos pos = queue.poll();
			inQueue.remove(pos);
			BlockState state = level.getBlockState(pos);

			if (!MBPaneShapes.isPane(state)) {
				continue;
			}

			BlockState next = update(state, level, pos);

			if (next == state) {
				continue;
			}

			// 16 = UPDATE_KNOWN_SHAPE: skip vanilla's shape cascade, the queue here settles instead
			level.setBlock(pos, next, 2 | 16);

			// The four horizontal diagonals have to be queued too: vanilla's cascade never reaches
			// them, and the rules read whether a diagonal holds a block
			for (int[] offset : NEIGHBOUR_OFFSETS) {
				BlockPos neighbor = pos.offset(offset[0], offset[1], offset[2]);

				if (level.getBlockState(neighbor).getBlock() instanceof IronBarsBlock && inQueue.add(neighbor)) {
					queue.add(neighbor);
				}
			}
		}
	}

	/**
	 * Tells the panes in the twelve diagonal spaces around a change that their surroundings moved.
	 *
	 * <p>Rules 2, 3 and 5 read diagonals, and {@code BlockStateBase#updateNeighbourShapes} only
	 * wakes the six orthogonal neighbours, so a block placed or broken diagonally to a pane would
	 * leave that pane alone - which shows up as a pane that only updates once a block is placed next
	 * to it. Vanilla's redstone wire has the same problem and solves it in its indirect neighbour
	 * step, which is what this repeats: the twelve diagonals each get a shape update, and a diagonal
	 * that really does change cascades on as usual, so a chain of them settles.
	 *
	 * @param state the block state that changed, the new one when placing and the old one when breaking
	 * @param pos   where it changed
	 */
	public static void refreshDiagonals(final LevelAccessor level, final BlockState state, final BlockPos pos,
			final int flags, final int recursionLeft) {
		for (int[] offset : DIAGONAL_OFFSETS) {
			BlockPos diagonal = pos.offset(offset[0], offset[1], offset[2]);

			if (!MBPaneShapes.isPane(level.getBlockState(diagonal))) {
				continue;
			}

			// A diagonal is not on any axis, so this direction is only here to give vanilla's step a
			// legal neighbour to name; our rules read the world and ignore it
			level.neighborShapeChanged(directionBackToOrigin(offset[0], offset[1], offset[2]), diagonal, pos, state,
					flags, recursionLeft);
		}
	}

	/** Every pane of ours in a chunk, with the section palettes asked first to skip empty ground. */
	private static List<BlockPos> collectPanesInChunk(final LevelChunk chunk) {
		List<BlockPos> result = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = chunk.getMinSectionY();

		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];

			if (section == null || section.hasOnlyAir()
					|| !section.getStates().maybeHas(state -> state.getBlock() instanceof IronBarsBlock)) {
				continue;
			}

			int baseY = SectionPos.sectionToBlockCoord(minSectionY + index);

			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);

						if (MBPaneShapes.isPane(state)) {
							result.add(chunk.getPos().getBlockAt(x, baseY + y, z));
						}
					}
				}
			}
		}

		return result;
	}
}
