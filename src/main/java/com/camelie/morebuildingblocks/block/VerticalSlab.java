package com.camelie.morebuildingblocks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Static helpers describing an upright ("vertical") slab state. Injected into
 * {@link net.minecraft.world.level.block.SlabBlock} by
 * {@code com.camelie.morebuildingblocks.mixin.SlabBlockMixin} so that every vanilla slab block can also
 * be placed upright without registering a new block.
 *
 * <p>This class deliberately does NOT live in the {@code ...mixin} package: Mixin claims that entire
 * package and refuses to let ordinary code reference anything inside it
 * ({@code IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced
 * directly}).
 *
 * <p>The two extra block state properties live in {@link MBBlockStateProperties}. Vanilla's
 * {@code type} keeps its meaning - "which half is filled": on a flat slab it selects the bottom or
 * top half of the Y axis, and on an upright slab the negative or positive half of {@code axis}.
 *
 * <p>Every placed slab, flat or upright, fills exactly one half of exactly one axis. {@link Fill}
 * is that single sentence written down, and it is also what the placement rule decides: the rule
 * picks which half of the block the player is pointing at, in whichever axis that half lies.
 */
public final class VerticalSlab {
	private VerticalSlab() {
	}

	/** {@code true} when this state is an upright slab instead of a flat one. */
	public static boolean isVertical(final BlockState state) {
		return state.hasProperty(MBBlockStateProperties.VERTICAL) && state.getValue(MBBlockStateProperties.VERTICAL);
	}

	/** The horizontal axis an upright slab is thin along; {@link Direction.Axis#Y} for flat slabs. */
	public static Direction.Axis axis(final BlockState state) {
		return state.getValue(MBBlockStateProperties.AXIS);
	}

	/** The filled half. Always read alongside {@link #axis(BlockState)} for upright slabs. */
	public static SlabType half(final BlockState state) {
		return state.getValue(SlabBlock.TYPE);
	}

	/**
	 * @return {@code true} when the filled half is the positive half of {@link #axis(BlockState)}.
	 *
	 *         <p>Vertically this is vanilla's own reading, but an upright slab fills a horizontal
	 *         axis, where {@code top} is the half showing its face towards the negative end. The
	 *         translation lives in {@link Fill#positive()}; this is its shorthand for a state.
	 */
	public static boolean isPositiveHalf(final BlockState state) {
		return Fill.of(state).positive();
	}

	/** @return {@code true} for a double slab, which fills the whole block and is no longer upright. */
	public static boolean isDouble(final BlockState state) {
		return half(state) == SlabType.DOUBLE;
	}

	/**
	 * @return the upright outline shape of {@code state}, or {@code null} when the state should keep
	 *         its vanilla flat-slab shape.
	 */
	public static @Nullable VoxelShape verticalShape(final BlockState state) {
		if (!isVertical(state)) {
			return null;
		}

		Direction.Axis axis = axis(state);
		if (axis == Direction.Axis.Y || isDouble(state)) {
			return null;
		}

		return MBBlockStateProperties.verticalSlabShape(axis, isPositiveHalf(state));
	}

	/** @return {@code state} converted into an upright slab that fills {@code half} of {@code axis}. */
	public static BlockState withVertical(final BlockState state, final Direction.Axis axis, final SlabType half) {
		return state
			.setValue(MBBlockStateProperties.VERTICAL, true)
			.setValue(MBBlockStateProperties.AXIS, axis)
			.setValue(SlabBlock.TYPE, half);
	}

	/** @return {@code state} converted into a slab that fills the given half of the block. */
	public static BlockState withFill(final BlockState state, final Fill fill) {
		Direction.Axis axis = fill.axis();

		if (axis == Direction.Axis.Y) {
			// A flat slab always keeps axis at its default, which the mixin pins to Y.
			return state
				.setValue(MBBlockStateProperties.VERTICAL, false)
				.trySetValue(MBBlockStateProperties.AXIS, Direction.Axis.Y)
				.setValue(SlabBlock.TYPE, fill.type());
		}

		return state
			.setValue(MBBlockStateProperties.VERTICAL, true)
			.setValue(MBBlockStateProperties.AXIS, axis)
			.setValue(SlabBlock.TYPE, fill.type());
	}

	/**
	 * The half of the block a placement fills, or {@code null} for vanilla's own flat slab.
	 *
	 * <p>Every face of a block can make an upright slab, because the half to fill is read off the
	 * click rather than off the plane it landed on: on a top or bottom face the half is the one
	 * under the click, and on a side face it is the one the slab has to take to stand against the
	 * block that was clicked - the half reaching back towards the player, which is a new slab added
	 * to the end of a wall rather than a flat one laid into the gap. The one click that does not is
	 * a click on a side face whose far side is already occupied: there the player is building
	 * <em>against</em> something rather than into open air, and the flat slab vanilla computes for
	 * them is the block they asked for.
	 *
	 * <p>{@code context.getClickedPos()} is the cell the new state is being computed for: the block
	 * whose face the click landed on for every ordinary click, and the next block along whenever the
	 * click was moved out. The block the click landed on is one step back from it along the clicked
	 * face, which is how the rule asks whether an upright slab is standing there - rather than
	 * reaching for the ray trace's own hit, so that the two stay in step by construction whenever
	 * the click is moved.
	 *
	 * <p>An upright slab reports itself as not replaceable from every face it presents
	 * ({@link SlabBlock#canBeReplaced}), so a click that lands on one - on the wide face it shows
	 * the world or on the open half beside it - is always taken into the next cell along before it
	 * gets here, and the half to fill is the one on the side the clicked face came from. The new
	 * slab then stands beside the one that was clicked with its two faces flush against it. The
	 * reference implementation this rule follows ({@code btsdhz/vanilla-refit}) asks the block that
	 * was clicked the same way, rather than the one being built in.
	 */
	public static Fill placementFill(final BlockPlaceContext context) {
		Direction face = context.getClickedFace();

		if (face.getAxis().isHorizontal()
			&& isVertical(context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite())))) {
			// The wall the player is pointing at already stands here, so the new slab is built on
			// the far side of it, filling the half that reaches back towards the click.
			return Fill.opposite(face);
		}

		if (face.getAxis().isHorizontal()) {
			// A side click only reaches a cell nothing is standing in yet. An empty neighbour is
			// what a player extending a wall into open air is pointing at; a wall, a floor or a
			// ceiling already across the gap is the block being built against, and there the click
			// belongs to the slab the player is holding, exactly as vanilla reads it.
			BlockState target = context.getLevel().getBlockState(context.getClickedPos());

			if (!target.isAir() && target.getFluidState().isEmpty() && !target.canBeReplaced(context)) {
				return null;
			}
		}

		return fillFromClick(context, face, context.getClickedPos());
	}

	/**
	 * How far from the centre of the block that was clicked a click may land and still pick a half
	 * of it: a quarter of a block. Inside that radius the click is too close to the middle to be
	 * pointing at one half rather than the other, and the slab stays flat.
	 */
	private static final double MIDDLE_RADIUS = 0.25;

	/**
	 * The half to fill for a click on a face of the block being built against, or {@code null} to
	 * leave the placement flat.
	 *
	 * <p>{@code placementPos} is the cell the new state is being computed for: the block whose face
	 * was clicked for every ordinary click, and the next block along whenever the click was moved
	 * out. The click is read as a position <em>inside the block that was clicked</em> - that cell on
	 * a top or bottom face, and one step back along the face on a side - which is what makes the
	 * offsets below the click's own place in that block's 16x16 plan rather than a distance to a
	 * cell next door.
	 *
	 * <p>Which half is then decided by <em>where</em> on the face the click landed, not by which way
	 * the player is looking. On a top or bottom face both offsets already name the half that was
	 * pointed at. On a side face the same offsets name the half of the clicked block the click was
	 * nearest, and the cell being filled is the one beyond that face - so they still name the right
	 * half, read from that cell's own centre: a click on a block's northern face is a click on the
	 * southern face of the cell north of it, and the slab fills that cell's southern half, flush
	 * against the block that was clicked. Comparing the two offsets by magnitude rather than taking
	 * a frame's own left/right and bottom/top bands is what makes the rule symmetric under every
	 * rotation of the block, and it is the same comparison vanilla-refit uses
	 * ({@code getHorizontalDirectionFromClick}).
	 *
	 * <p>A click within {@link #MIDDLE_RADIUS} of that block's centre fills nothing: it is too close
	 * to the middle to point at a half, and leaves the slab flat - the same ratio the reference
	 * applies ({@code isInCenterSquare}, a side of {@code 0.5}) and the strictly greater
	 * {@code Math.abs(x) > Math.abs(z)} that answers the exact diagonal with the Z axis, so every
	 * click, including one on a boundary, lands on the same half here as it does there.
	 */
	private static @Nullable Fill fillFromClick(final BlockPlaceContext context, final Direction face,
			final BlockPos placementPos) {
		BlockPos clickedPos = face.getAxis().isVertical() ? placementPos : placementPos.relative(face.getOpposite());
		Vec3 hit = context.getClickLocation();
		double x = hit.x - clickedPos.getX() - 0.5;
		double z = hit.z - clickedPos.getZ() - 0.5;

		if (Math.abs(x) <= MIDDLE_RADIUS && Math.abs(z) <= MIDDLE_RADIUS) {
			return null;
		}

		return Math.abs(x) > Math.abs(z)
			? fillAlong(x, Direction.Axis.X)
			: fillAlong(z, Direction.Axis.Z);
	}

	/**
	 * The half of {@code axis} a click landing {@code offset} from the centre of the block that was
	 * clicked picks, measured along that axis.
	 *
	 * <p>The sign is the whole rule: a click east of that centre fills the eastern half and one to
	 * the north fills the northern half. On a side face the half that comes back belongs to the cell
	 * <em>beyond</em> the clicked block, because that is the cell an upright slab is built in, and
	 * the same sign still puts it where the player pointed: the southern half of the cell north of a
	 * block is the half flush against that block's northern face.
	 */
	private static Fill fillAlong(final double offset, final Direction.Axis axis) {
		return Fill.of(axis, offset > 0.0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	/**
	 * One half of one axis of the block: the entire shape vocabulary of a slab.
	 *
	 * <p>{@code 朝南的竖半砖} ("a south-facing upright slab") means the filled half lies on the
	 * south side of the block, and its visible face therefore points north - the {@code z} axis
	 * with the positive half filled.
	 *
	 * <p>{@link SlabType} does not name a side of an axis, it names a side of the <em>block</em>:
	 * {@code top} is the half a player sees when looking down at the slab, whatever axis it fills.
	 * On the Y axis that is the upper half, so {@code top} is the positive half. An upright slab
	 * fills a horizontal axis instead, and there {@code top} is the half whose visible face points
	 * towards the negative end - a slab filling {@code z} from 0 to 0.5 shows its face to the
	 * north. {@link #positive()} is therefore the one place that translation lives, and every
	 * constructor and reader of a {@code Fill} goes through it.
	 */
	public record Fill(Direction.Axis axis, SlabType type) {
		/** The half of {@code axis} on the given side, e.g. {@code of(Axis.Z, POSITIVE)} is "south". */
		public static Fill of(final Direction.Axis axis, final Direction.AxisDirection direction) {
			boolean positive = direction == Direction.AxisDirection.POSITIVE;
			return new Fill(axis, halfOf(axis, positive));
		}

		/** The negative half of {@code axis}. */
		public static Fill negative(final Direction.Axis axis) {
			return new Fill(axis, halfOf(axis, false));
		}

		/** The positive half of {@code axis}. */
		public static Fill positive(final Direction.Axis axis) {
			return new Fill(axis, halfOf(axis, true));
		}

		/**
		 * The {@link SlabType} naming the positive or the negative half of {@code axis}, which is
		 * the inverse of {@link #positive()}.
		 */
		private static SlabType halfOf(final Direction.Axis axis, final boolean positive) {
			if (axis == Direction.Axis.Y) {
				return positive ? SlabType.TOP : SlabType.BOTTOM;
			}

			return positive ? SlabType.BOTTOM : SlabType.TOP;
		}

		/** The half of {@code axis} whose outward (visible) face points along {@code direction}. */
		public static Fill opposite(final Direction direction) {
			return of(direction.getAxis(),
				direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
					? Direction.AxisDirection.NEGATIVE
					: Direction.AxisDirection.POSITIVE);
		}

		/**
		 * The half of the block lying behind {@code edge}, where {@code edge} is the side of the
		 * block that half reaches towards.
		 *
		 * <p>An edge seen on the outside of a block has a half of the block behind it, and a click
		 * on that edge fills that half: the east edge of a north face has the eastern half behind
		 * it, and the northern edge of a top face the northern half. This is {@link #opposite} told
		 * the other way round - a {@link Direction} names the side the half reaches towards, whereas
		 * a {@link Fill} names the half by the end of its axis it starts from, so the half reaching
		 * west is the negative half of X.
		 */
		public static Fill fillBehind(final Direction edge) {
			return opposite(edge);
		}

		/**
		 * The half of the block lying beyond {@code face}, where {@code face} is the outside face of
		 * the block. The mirror image of {@link #fillBehind}: a click on the outside of a wall reaches
		 * past it, so the half the click fills is the one on the far side of that face - clicking a
		 * north face fills the southern half, which is the visible face of the block next door.
		 */
		public static Fill fillBeyond(final Direction face) {
			return of(face.getAxis(), face.getAxisDirection());
		}

		/**
		 * The half {@code state} fills, which is the inverse of {@link VerticalSlab#withFill}: a flat
		 * slab reads as a half of the Y axis, an upright one as a half of its own axis.
		 *
		 * <p>A double slab fills both halves of the Y axis, which this vocabulary cannot describe, so
		 * it reads as the bottom half.
		 */
		public static Fill of(final BlockState state) {
			Direction.Axis axis = VerticalSlab.isVertical(state) ? VerticalSlab.axis(state) : Direction.Axis.Y;
			SlabType type = VerticalSlab.isDouble(state) ? SlabType.BOTTOM : VerticalSlab.half(state);
			return new Fill(axis, type);
		}

		/** @return {@code true} when this fill is the positive half of {@link #axis()}. */
		public boolean positive() {
			return this.axis == Direction.Axis.Y ? this.type == SlabType.TOP : this.type == SlabType.BOTTOM;
		}

		/** The other half of the same axis: the half of {@code axis} this one does not fill. */
		public Fill behind() {
			return new Fill(this.axis, positive() ? SlabType.BOTTOM : SlabType.TOP);
		}

		/**
		 * The direction this filled half reaches towards: the way its visible face points. A fill of
		 * the positive half of X is {@code EAST}, a fill of the bottom half of Y is {@code DOWN}.
		 */
		public Direction outward() {
			return Direction.fromAxisAndDirection(this.axis, positive()
				? Direction.AxisDirection.POSITIVE
				: Direction.AxisDirection.NEGATIVE);
		}
	}
}
