package com.camelie.morebuildingblocks.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Which of a pane's twelve pieces are on, decided from the six edge connections and the twelve
 * corner connections it has with the world around it.
 *
 * <p>The framework first, the pieces after. A pane has six faces, and each face either holds an
 * edge connection or does not: it holds one when the block next to it is another pane - panes always
 * connect to each other - or when that block turns a full 1x1 face towards the pane. A half slab's
 * 1x0.5 side is not a full face, and neither is a step or one of the mod's own eighth blocks, which
 * is the point of the distinction: a pane leans on full faces only.
 *
 * <p>The twelve pieces are the corner connections. Each of the three planes through a pane - the one
 * spanned by east/west and north/south, the one by east/west and up/down, the one by north/south and
 * up/down - holds four of them, and each joins two of the pane's faces. A corner connection is there
 * when both of its faces are satisfied, and a face is satisfied when the block next to it turns a
 * full face towards the pane, or when that block is a pane which connects towards the corner's
 * diagonal space itself. Two full faces meet in a corner whether or not that space holds anything; a
 * pair of panes only meets there when something connectable lies between them.
 *
 * <p>Four rules, in order:
 *
 * <ol>
 * <li>Edge connections: panes and full faces, as above.</li>
 * <li>Corner connections: as above, one per pair of faces.</li>
 * <li>Pruning: a corner connection that hangs on a full face is dropped when the pane is true in the
 * one plane that does not contain that face, and the far side of the plane the corner lies in holds
 * nothing at all - the pane leans on that face instead of turning round it. A face that loses every
 * corner it lit goes with them and draws no arm; a face that never lit one keeps its edge.</li>
 * <li>Completing: a pane left true in exactly one plane fills that plane with all four of its
 * corners - both halves of both arms a vertical plane carries, even towards air, and all four corner
 * pieces of the horizontal one. A pane left true in exactly two planes turns in both of them, and
 * the two meet along the third axis: along that axis each of the four sides carries exactly two
 * corners, and a side with one of them on gets the other one too, whether or not the world beside it
 * offers anything. A pane left true in all three planes keeps only the corners the rules above gave
 * it. Only planes that are already true are read, so this rule adds corners and never takes any
 * away.</li>
 * </ol>
 *
 * <p>What is drawn: the twelve pieces are the twelve corner connections, so a pane with corners is
 * drawn from those corners alone - the corners it turns, and the ones the rule above fills in. The
 * two halves of an arm are the pieces of the corners below and above that edge, and an edge that no
 * corner hangs on draws nothing at all - an arm is not a thing of its own. A pane the rules leave
 * without a single corner connection - not one left after the pruning - is vanilla's own instead:
 * it falls back to vanilla's four connections and to the full arms vanilla draws for them, and
 * shows no corner piece anywhere, so a pane next to a wall, a fence or another shape vanilla would
 * bend around still looks the way vanilla draws it. A pane with even one corner left is never asked
 * about vanilla's way: no wall and no fence lights an arm there, since only the pane's own planes
 * fill arms and only panes and full faces ever make a plane true. Nothing reaches up or down, so
 * the two vertical faces never carry an arm of their own; they only decide, through their corners,
 * which halves of the horizontal arms are on.
 *
 * <p>Every rule reads the world and never the pieces a neighbour computed, so a pane's twelve flags
 * are a pure function of its surroundings: the same surroundings always give the same flags, whatever
 * order blocks were placed in, and one pass settles a pane. That is why the queue in
 * {@link #refreshChunk} is only insurance rather than a fixpoint iteration.
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
	 * The six faces of a pane, in a fixed order so that the pruning pass visits them the same way
	 * every time.
	 */
	private static final Direction[] FACES = {
		Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
	};

	/** The four faces an arm can point at; a pane has no piece that reaches up or down. */
	private static final Direction[] ARMS = {
		Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
	};

	/** What one of a pane's six faces finds next to it. */
	private enum Edge {
		/** Air, or a shape that does not offer a full 1x1 face, such as a slab's thin side. */
		NONE,
		/** Another pane: the two always connect, whatever either of them draws. */
		PANE,
		/** A block that turns a full 1x1 face towards the pane. */
		FACE
	}

	/**
	 * One of the twelve corner connections, named by the two faces it joins, and the piece it draws.
	 * A corner between two horizontal faces is a corner piece; a corner between a horizontal face and
	 * the vertical one above or below it is the upper or the lower half of that arm.
	 */
	private enum Crossing {
		EAST_UP(Direction.EAST, Direction.UP),
		EAST_DOWN(Direction.EAST, Direction.DOWN),
		WEST_UP(Direction.WEST, Direction.UP),
		WEST_DOWN(Direction.WEST, Direction.DOWN),
		EAST_SOUTH(Direction.EAST, Direction.SOUTH),
		EAST_NORTH(Direction.EAST, Direction.NORTH),
		WEST_SOUTH(Direction.WEST, Direction.SOUTH),
		WEST_NORTH(Direction.WEST, Direction.NORTH),
		UP_SOUTH(Direction.UP, Direction.SOUTH),
		UP_NORTH(Direction.UP, Direction.NORTH),
		DOWN_SOUTH(Direction.DOWN, Direction.SOUTH),
		DOWN_NORTH(Direction.DOWN, Direction.NORTH);

		private final Direction first;
		private final Direction second;
		private final Direction.Axis plane;

		Crossing(final Direction first, final Direction second) {
			this.first = first;
			this.second = second;
			this.plane = missing(first, second);
		}

		/** The axis neither face uses, which is what names the plane the two of them span. */
		private static Direction.Axis missing(final Direction first, final Direction second) {
			for (Direction.Axis axis : Direction.Axis.values()) {
				if (axis != first.getAxis() && axis != second.getAxis()) {
					return axis;
				}
			}

			throw new IllegalStateException(first + " and " + second + " do not span a plane");
		}

		/** The plane this corner lies in, named by the axis it does not span. */
		Direction.Axis plane() {
			return this.plane;
		}

		/** Whether this corner hangs on that face of the pane. */
		boolean hangs(final Direction face) {
			return this.first == face || this.second == face;
		}

		/** Whether one of the two faces of this corner lies on that axis. */
		boolean uses(final Direction.Axis axis) {
			return this.first.getAxis() == axis || this.second.getAxis() == axis;
		}

		/**
		 * The face of this corner that is not on that axis, which names the side of that axis the
		 * corner sits on. Only meaningful for a corner that uses the axis.
		 */
		Direction beside(final Direction.Axis axis) {
			return this.first.getAxis() == axis ? this.second : this.first;
		}

		/** The piece this corner draws. */
		Property<Boolean> piece() {
			return switch (this) {
				case EAST_UP -> MBPaneProperties.upper(Direction.EAST);
				case EAST_DOWN -> MBPaneProperties.lower(Direction.EAST);
				case WEST_UP -> MBPaneProperties.upper(Direction.WEST);
				case WEST_DOWN -> MBPaneProperties.lower(Direction.WEST);
				case EAST_SOUTH -> MBPaneProperties.CORNER_SOUTH_EAST;
				case EAST_NORTH -> MBPaneProperties.CORNER_NORTH_EAST;
				case WEST_SOUTH -> MBPaneProperties.CORNER_SOUTH_WEST;
				case WEST_NORTH -> MBPaneProperties.CORNER_NORTH_WEST;
				case UP_SOUTH -> MBPaneProperties.upper(Direction.SOUTH);
				case UP_NORTH -> MBPaneProperties.upper(Direction.NORTH);
				case DOWN_SOUTH -> MBPaneProperties.lower(Direction.SOUTH);
				case DOWN_NORTH -> MBPaneProperties.lower(Direction.NORTH);
			};
		}

		/** Whether both faces of this corner are satisfied, see {@link #side}. */
		boolean on(final BlockGetter level, final BlockPos pos, final Map<Direction, Edge> edges) {
			return side(level, pos, edges, this.first, this.second)
					&& side(level, pos, edges, this.second, this.first);
		}
	}

	/**
	 * The offsets that can affect a pane: the six orthogonal neighbours plus the twelve diagonal
	 * spaces between two of them - four in the same horizontal plane, eight above and below an arm.
	 * The corner connections read nothing outside this table, so a rule that reaches further has to
	 * extend it.
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
	public static BlockState update(BlockState state, final BlockGetter level, final BlockPos pos) {
		if (!MBPaneShapes.isPane(state)
				|| !state.hasProperty(MBPaneProperties.CORNER_NORTH_EAST)
				|| !(state.getBlock() instanceof IronBarsBlock pane)) {
			// Someone else's pane: not even worth a post process pass, so no touchedPane either
			return state;
		}

		if (inPostProcess) {
			touchedPane = true;
		}

		// ===== The six edge connections: another pane, a full face, or nothing =====
		Map<Direction, Edge> edges = new EnumMap<>(Direction.class);

		for (Direction face : FACES) {
			edges.put(face, edge(level, pos, face));
		}

		// ===== The twelve corner connections: both of the faces they join have to be satisfied =====
		EnumSet<Crossing> crossings = EnumSet.noneOf(Crossing.class);

		for (Crossing crossing : Crossing.values()) {
			if (crossing.on(level, pos, edges)) {
				crossings.add(crossing);
			}
		}

		// ===== Rule 3: a full face that loses every corner it lit loses its edge as well =====
		prune(edges, crossings);

		// ===== Rule 4: a plane that still holds a corner is filled with all four of its corners =====
		complete(crossings);

		// ===== What is drawn: the piece of every corner left, and vanilla's arms for a pane the
		// rules leave without one =====
		for (Crossing crossing : Crossing.values()) {
			state = state.setValue(crossing.piece(), crossings.contains(crossing));
		}

		// A pane with a corner is drawn from its corners alone: the twelve pieces are the twelve
		// corners, so an edge that no corner hangs on draws nothing, and the two halves of an arm
		// are the pieces of the corners below and above it. A pane with no corner left at all is
		// vanilla's own, and falls back to the full arms vanilla draws for its four connections.
		if (crossings.isEmpty()) {
			for (Direction face : ARMS) {
				if (connectsTo(pane, level, pos, face)) {
					state = state.setValue(MBPaneProperties.lower(face), true)
							.setValue(MBPaneProperties.upper(face), true);
				}
			}
		}

		return state;
	}

	/** What one face of a pane finds next to it. */
	private static Edge edge(final BlockGetter level, final BlockPos pos, final Direction face) {
		BlockPos neighborPos = pos.relative(face);
		BlockState neighbor = level.getBlockState(neighborPos);

		if (neighbor.getBlock() instanceof IronBarsBlock) {
			return Edge.PANE;
		}

		return neighbor.isFaceSturdy(level, neighborPos, face.getOpposite()) ? Edge.FACE : Edge.NONE;
	}

	/**
	 * Whether one face of a corner connection is satisfied: the block next to it turns a full face
	 * towards the pane, or it is a pane that connects towards the corner's diagonal space itself -
	 * which it only does when something connectable lies in that space.
	 *
	 * @param face     the pane's own face, which the corner hangs on
	 * @param diagonal the direction from that neighbour towards the corner's diagonal space
	 */
	private static boolean side(final BlockGetter level, final BlockPos pos, final Map<Direction, Edge> edges,
			final Direction face, final Direction diagonal) {
		return switch (edges.get(face)) {
			case FACE -> true;
			case PANE -> edge(level, pos.relative(face), diagonal) != Edge.NONE;
			case NONE -> false;
		};
	}

	/**
	 * The pruning rule.
	 *
	 * <p>A corner connection that hangs on a full face is only real while the pane turns across that
	 * face. It does not when the pane turns within the plane the face itself lies in - it has
	 * structure of its own there - and when the far side of the plane the corner lies in holds
	 * nothing; then that face is a wall the pane leans on, and the corner goes. A face that loses
	 * every corner it lit loses those corners and nothing else, and draws nothing for them; a face
	 * that never lit one is left standing, as is a pane.
	 *
	 * <p>The pane also has to be closed in the plane it turns in, which is to say every edge
	 * connection lying in that plane has to be there: with one of the four missing, the corners the
	 * full face lit are what holds that side of the pane, and taking them away would leave it
	 * leaning on a face it does not turn across.
	 *
	 * <p>One pass over the six faces, and every decision reads the corners as the rule before this
	 * one left them, so the order the faces are visited in cannot change the answer.
	 */
	private static void prune(final Map<Direction, Edge> edges, final EnumSet<Crossing> crossings) {
		EnumSet<Crossing> turning = EnumSet.copyOf(crossings);
		EnumSet<Crossing> cut = EnumSet.noneOf(Crossing.class);

		for (Direction face : FACES) {
			// Only a full face can be leaned on, and only while the pane turns in the plane that
			// face lies in and every edge connection of that plane is there
			if (edges.get(face) != Edge.FACE || !plane(turning, face.getAxis())
					|| !closed(edges, face.getAxis())) {
				continue;
			}

			EnumSet<Crossing> attached = EnumSet.noneOf(Crossing.class);

			for (Crossing crossing : turning) {
				if (crossing.hangs(face)) {
					attached.add(crossing);
				}
			}

			// Nothing hangs on this face, so this rule has no corner of its to take away
			if (attached.isEmpty()) {
				continue;
			}

			// The two planes that do contain this face; the third one is the one asked about above
			for (Direction.Axis axis : Direction.Axis.values()) {
				if (axis == face.getAxis()) {
					continue;
				}

				EnumSet<Crossing> hanging = EnumSet.noneOf(Crossing.class);
				boolean farSide = false;

				for (Crossing crossing : turning) {
					if (crossing.plane() != axis) {
						continue;
					}

					if (crossing.hangs(face)) {
						hanging.add(crossing);
					}

					farSide |= crossing.hangs(face.getOpposite());
				}

				if (!hanging.isEmpty() && !farSide) {
					cut.addAll(hanging);
				}
			}
		}

		crossings.removeAll(cut);
	}

	/**
	 * Whether every edge connection lying in the plane of that axis is there.
	 *
	 * <p>The four connections of such a plane are the ones a pane turning inside it uses; the two
	 * lying across the plane are the ones the corners of the rule above hang on.
	 */
	private static boolean closed(final Map<Direction, Edge> edges, final Direction.Axis axis) {
		for (Direction direction : FACES) {
			if (direction.getAxis() != axis && edges.get(direction) == Edge.NONE) {
				return false;
			}
		}

		return true;
	}

	/** Whether the pane is true in that plane, which is to say some corner lies in it. */
	private static boolean plane(final EnumSet<Crossing> crossings, final Direction.Axis axis) {
		for (Crossing crossing : crossings) {
			if (crossing.plane() == axis) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The completing rule.
	 *
	 * <p>A pane that is true in one plane and in no other turns in a single direction, and that
	 * whole plane is drawn: every corner connection lying in it is turned on, whether or not the
	 * world beside it offers anything. A vertical plane carries two arms, and both halves of both of
	 * them come on, even towards air; the horizontal plane carries the four corner pieces, and all
	 * four of them come on. A pane leaning on a single full face therefore still fills the plane it
	 * turns in, and only the arms of the planes it does not turn in stay off.
	 *
	 * <p>A pane true in exactly two planes turns in both of them, and the two meet along the third
	 * axis. Along that axis the pane has four sides, and each of them carries exactly two corners:
	 * the two halves of one arm, or the two corner pieces of one vertical face. One of those two
	 * being on turns the other one on as well, so a side the rules above lit anywhere is drawn
	 * whole, even towards air, and a side they left dark stays dark. The four corners of the third
	 * plane, the one that is not true, are not touched.
	 *
	 * <p>A pane true in all three planes is left exactly as the rules above left it: it already
	 * reaches every way, and completing it would draw the whole pane wherever two of its planes
	 * happen to meet.
	 *
	 * <p>Nothing here can make a plane true that was not - a corner this rule adds lies in a plane
	 * that was already true - and all three planes are read before any corner is added, so the order
	 * they are visited in cannot change the answer.
	 */
	private static void complete(final EnumSet<Crossing> crossings) {
		Direction.Axis first = null;
		Direction.Axis second = null;

		for (Direction.Axis axis : Direction.Axis.values()) {
			if (!plane(crossings, axis)) {
				continue;
			}

			if (first == null) {
				first = axis;
			} else if (second == null) {
				second = axis;
			} else {
				return;
			}
		}

		if (first == null) {
			return;
		}

		if (second == null) {
			for (Crossing crossing : Crossing.values()) {
				if (crossing.plane() == first) {
					crossings.add(crossing);
				}
			}

			return;
		}

		// The two planes meet along the axis neither of them spans; along it each side carries
		// exactly two corners, and one of the two being on covers the other one
		Direction.Axis shared = shared(first, second);
		EnumSet<Direction> sides = EnumSet.noneOf(Direction.class);

		for (Crossing crossing : crossings) {
			if (crossing.uses(shared)) {
				sides.add(crossing.beside(shared));
			}
		}

		for (Crossing crossing : Crossing.values()) {
			if (crossing.uses(shared) && sides.contains(crossing.beside(shared))) {
				crossings.add(crossing);
			}
		}
	}

	/** The axis two planes share, which is the one of the three neither of them names. */
	private static Direction.Axis shared(final Direction.Axis first, final Direction.Axis second) {
		for (Direction.Axis axis : Direction.Axis.values()) {
			if (axis != first && axis != second) {
				return axis;
			}
		}

		throw new IllegalStateException(first + " and " + second + " do not name two planes");
	}

	/**
	 * The same connection test vanilla uses, for the fallback of a pane no corner hangs on: panes,
	 * walls, or a full face turned towards us.
	 */
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

			// The twelve diagonal spaces have to be queued too: vanilla's cascade never reaches
			// them, and the corner connections read whether they hold something connectable
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
	 * <p>The corner connections read diagonals, and {@code BlockStateBase#updateNeighbourShapes} only
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
