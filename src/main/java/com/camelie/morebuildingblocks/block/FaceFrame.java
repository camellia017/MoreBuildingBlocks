package com.camelie.morebuildingblocks.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The frame of one clicked face of a block, as the slab placement rule reads it.
 *
 * <p>Every face is viewed from outside and divided into a middle quarter with one band along each of
 * its four edges. Two axes describe a point on it: {@code u} runs from the face's left edge towards
 * its right edge, and {@code v} from its bottom edge towards its top edge, both in blocks, so a
 * whole face is the unit square.
 *
 * <p>A face is not a picture on a screen: the block has to be read in its own coordinates. Each
 * frame therefore records the corner the face's left and bottom edges meet at, together with the
 * direction {@code u} grows in along the block.
 *
 * <p>Looking at a face from outside, its left edge is west on a north face, east on a south face,
 * north on a west face and south on an east face. The top and bottom faces are read with the same
 * left edge - west - and their second axis runs south, so that a click near the northern edge is the
 * same band on both of them.
 *
 * <p>A click at a left or right edge fills the half lying <em>behind</em> that edge, so the frame
 * carries those two edges as world directions and turns them into halves itself.
 *
 * <p>This class deliberately lives outside the {@code ...mixin} package, and builds every record it
 * hands out itself: a record constructed inside a mixin has its values rewritten along with the
 * mixin, so a {@code Fill} built there from a direction came back as the wrong half - and a table of
 * {@code Fill}s read back out of a mixin named different halves than it was written with.
 *
 * @param cornerX x of the corner the face's left and bottom edges meet at, relative to the block
 * @param cornerY y of that corner, relative to the block
 * @param cornerZ z of that corner, relative to the block
 * @param uAxis the axis {@code u} runs along
 * @param uPositive {@code true} when {@code u} grows towards the positive end of {@code uAxis}
 * @param leftEdge the world direction of the face's left edge, seen from outside
 * @param rightEdge the world direction of the face's right edge, seen from outside
 */
public record FaceFrame(int cornerX, int cornerY, int cornerZ, Direction.Axis uAxis, boolean uPositive,
		Direction leftEdge, Direction rightEdge) {
	/**
	 * Bounds of the middle quarter of a face, a hundredth of a block in from the quarter lines, so
	 * that a click landing exactly on one - the centre of a face being the common case - stays in the
	 * middle rather than falling through to a band.
	 */
	private static final double MIDDLE_MIN = 0.24;
	private static final double MIDDLE_MAX = 0.76;

	/** The frame of {@code face}, or {@code null} for a direction that is not a face. */
	public static FaceFrame of(final Direction face) {
		return FRAMES.get(face);
	}

	/** The first coordinate of the middle quarter of this face, pulled in from the quarter line. */
	public static double middleMin() {
		return MIDDLE_MIN;
	}

	/** The last coordinate of the middle quarter of this face, pulled in from the quarter line. */
	public static double middleMax() {
		return MIDDLE_MAX;
	}

	/**
	 * The half the clicked quarter of this frame fills: whichever quarter of the face {@code (u, v)}
	 * falls in, or the whole face when the click is in the middle quarter.
	 *
	 * <p>The middle quarter takes the half behind the face itself, so its visible face is the one
	 * that was clicked. Each other quarter sits against one edge and takes the half behind that
	 * edge. The middle is named first, and the caller gives it priority, so it wins in the corners
	 * where the bands overlap.
	 *
	 * <p>{@code beyond} says the new block is not the one the click landed on, which happens when a
	 * click on an upright slab's outer face is moved out into the next block along. Every band then
	 * names a half of <em>that</em> block, one block further out: a band against the clicked face
	 * reaches past the block that was clicked and fills the half of the new one nearest to it, which
	 * is the opposite side of the new block from the face the click came through.
	 */
	public VerticalSlab.Fill region(final Direction face, final boolean middle, final boolean beyond,
			final double u, final double v) {
		if (middle) {
			return beyond ? VerticalSlab.Fill.fillBeyond(face) : fill(face);
		}

		if (v < MIDDLE_MIN) {
			return band(face, beyond, floor(face));
		}

		if (v > MIDDLE_MAX) {
			return band(face, beyond, ceiling(face));
		}

		return band(face, beyond, band(u < MIDDLE_MIN));
	}

	/**
	 * The half a band fills, seen from the block the click landed on ({@code beyond} false) or from
	 * the one it was moved out into ({@code beyond} true).
	 *
	 * <p>The two agree on every direction but one. A band against the clicked face itself reaches
	 * past the block that was clicked, so the new block's half on that side is the one <em>opposite</em>
	 * the side the band named; a band across the face reaches sideways, where the new block's two
	 * halves line up with the clicked one's, so it names the same side either way.
	 */
	private VerticalSlab.Fill band(final Direction face, final boolean beyond, final VerticalSlab.Fill named) {
		if (!beyond || named.axis() != face.getAxis()) {
			return named;
		}

		return named.outward().getAxis() == face.getAxis() && named.outward() == face
			? VerticalSlab.Fill.fillBeyond(face.getOpposite())
			: named;
	}

	/** The half filled by a band along the face's left ({@code true}) or right ({@code false}) edge. */
	public VerticalSlab.Fill band(final boolean nearLeftEdge) {
		return fill(nearLeftEdge ? this.leftEdge : this.rightEdge);
	}

	/** The half filled by the band along the bottom edge of {@code face}. */
	public VerticalSlab.Fill floor(final Direction face) {
		return fill(vDirection(face));
	}

	/** The half filled by the band along the top edge of {@code face}. */
	public VerticalSlab.Fill ceiling(final Direction face) {
		return fill(vDirection(face).getOpposite());
	}

	/**
	 * The half of the block lying behind {@code side}, which is a side of the block: an edge seen on
	 * the outside of a block has a half of the block behind it, and a click on that edge fills it.
	 * The east edge of a north face has the eastern half behind it, and the northern edge of a top
	 * face the northern half.
	 */
	private static VerticalSlab.Fill fill(final Direction side) {
		return VerticalSlab.Fill.opposite(side);
	}

	/** The corner this frame starts at, as a world position for the block at {@code pos}. */
	public Vec3 corner(final BlockPos pos) {
		return new Vec3(pos.getX() + this.cornerX, pos.getY() + this.cornerY, pos.getZ() + this.cornerZ);
	}

	/**
	 * {@code u} of a click: how far along the face's first axis it landed, measured from the frame's
	 * corner. It is the projection of the click onto that axis, taken with the sign the axis grows
	 * in, so a click past the corner counts as more than one - callers only ever compare it against
	 * the fractions of a block that divide the face.
	 */
	public double u(final Vec3 hit, final BlockPos pos) {
		Vec3 corner = corner(pos);
		double distance = switch (this.uAxis) {
			case X -> hit.x - corner.x;
			case Y -> hit.y - corner.y;
			case Z -> hit.z - corner.z;
		};

		return this.uPositive ? distance : -distance;
	}

	/** {@code v} of a click, measured the same way but along the face's second axis. */
	public double v(final Vec3 hit, final BlockPos pos, final Direction face) {
		Vec3 corner = corner(pos);
		Direction vAxis = vDirection(face);

		return vAxis.getStepY() != 0
			? (hit.y - corner.y) * vAxis.getStepY()
			: (hit.z - corner.z) * vAxis.getStepZ();
	}

	/**
	 * The direction {@code v} grows in: from the face's bottom edge towards its top edge. It is up
	 * the block on every side face, and on the two horizontal faces it is the block's south - the
	 * direction the top face is read with, and the one the bottom face is read with as well, because
	 * a face seen from outside is read the same way round whichever end of the block the viewer
	 * stands at.
	 */
	public Direction vDirection(final Direction face) {
		return switch (face) {
			case UP, DOWN -> Direction.SOUTH;
			default -> Direction.UP;
		};
	}

	private static final Map<Direction, FaceFrame> FRAMES = Map.of(
		// A side face. Its left edge is the one to the west of a north face, to the east of a south
		// face, to the north of a west face and to the south of an east face, and u runs from there
		// inwards. Behind the west edge of a north face lies its eastern half, and so on round.
		Direction.NORTH, new FaceFrame(0, 0, 1, Direction.Axis.X, true, Direction.WEST, Direction.EAST),
		Direction.SOUTH, new FaceFrame(1, 0, 1, Direction.Axis.X, false, Direction.EAST, Direction.WEST),
		Direction.WEST, new FaceFrame(1, 0, 1, Direction.Axis.Z, false, Direction.SOUTH, Direction.NORTH),
		Direction.EAST, new FaceFrame(1, 0, 1, Direction.Axis.Z, true, Direction.NORTH, Direction.SOUTH),
		// The horizontal faces: left is west on both, the second axis runs south on the top face and
		// north on the bottom one, so a click at the northern edge fills the northern half either
		// way, a click at the southern edge the southern half, and a click at the western edge the
		// western half.
		Direction.UP, new FaceFrame(1, 1, 0, Direction.Axis.X, false, Direction.WEST, Direction.EAST),
		Direction.DOWN, new FaceFrame(1, 0, 0, Direction.Axis.X, false, Direction.WEST, Direction.EAST)
	);
}
