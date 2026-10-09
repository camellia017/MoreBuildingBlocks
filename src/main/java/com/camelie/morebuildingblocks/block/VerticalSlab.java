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
	 * The half of the block a placement fills, read off the clicked face alone.
	 *
	 * <p>Only the clicked face matters. On that face the corner the frame starts at is the origin,
	 * the first axis runs from its left edge towards its right one and the second from its bottom
	 * edge towards its top edge; a click in the middle third of both picks the half lying behind
	 * that face, and a click in one of the four surrounding bands picks the half that band names.
	 * The middle is tested first, so it wins in the corners where the bands overlap. A click on a
	 * block's northern face therefore fills the half of the new block that lies against that face -
	 * the southern half of the cell to the north - and a click at its centre carries a wall on
	 * straight, because the region around the centre is the one that names the half behind the face.
	 *
	 * <p>Clicking an upright slab lands on one of its two broad faces, and the rule reads it the
	 * same way: the region the click lands in names a half, and a half of one block lines up with
	 * the same half of the block next door. Against its <em>outer</em> face - the one its filled
	 * half reaches towards - the click is moved out into the next block along, where filling that
	 * same half continues the wall. Against its <em>inner</em> face - the open half, which a slab
	 * reports as replaceable - the two halves are the two halves of one block, so vanilla combines
	 * them into a double slab instead of a second block appearing beside it.
	 *
	 * <p>The moved click is why the frame is read off the block the click landed on -
	 * {@link PlacementClick#morebuildingblocks$hitPos()}, which is where the face the player pointed
	 * at really is - rather than off {@code context.getClickedPos()}, the block that will be built
	 * in. Those two are the same block for every placement except that one, and for that one the
	 * click point itself lies a whole block outside the block being placed in: a click on the outer
	 * face of an upright slab at the middle of its face would read as a click on the far edge of the
	 * new block and fill the wrong half entirely.
	 */
	public static Fill placementFill(final BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		BlockPos clickedPos = context.getClickedPos();

		if (context instanceof PlacementClick click) {
			clickedPos = click.morebuildingblocks$hitPos();
		}

		FaceFrame frame = FaceFrame.of(face);

		if (frame == null) {
			return null;
		}

		// Local coordinates on the clicked face: 0 is its left/bottom edge, 1 its right/top edge.
		// Both are measured from the corner the frame starts at, which is not the same corner of
		// the block for every face, so each is offset to that corner before projecting.
		//
		// The face the player pointed at is where the click is read, and it is read the same way
		// whether the click stayed in that block or was moved out of it: the region a click lands
		// in names the half, and it names the same half of whichever cell the placement fills.
		// Flipping a band for the moved click instead put a wall's next block on the wrong side of
		// the face the player was pointing along.
		// The click read in the coordinates of the block it was made against: the frame's corner is
		// relative to that block, so the world position has to lose the block's own corner first.
		Vec3 hit = context.getClickLocation().subtract(Vec3.atLowerCornerOf(clickedPos));
		double u = frame.u(hit);
		double v = frame.v(hit, face);
		boolean middle = u > FaceFrame.middleMin() && u < FaceFrame.middleMax()
			&& v > FaceFrame.middleMin() && v < FaceFrame.middleMax();

		return frame.region(face, middle, u, v);
	}

	/** The direction this slab's visible face points: the way {@code canBeReplaced} tests a click. */
	public static Direction outward(final BlockState state) {
		return Fill.of(state).outward();
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
