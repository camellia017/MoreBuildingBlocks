package com.camelie.morebuildingblocks.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The frame of one clicked face of a block, as the slab placement rule reads it.
 *
 * <p>Every face is divided into a middle quarter with one band along each of its four edges. Two
 * axes describe a point on it: {@code u} runs from the face's left edge towards its right one, and
 * {@code v} from its bottom edge towards its top one, both in blocks, so a whole face is the unit
 * square.
 *
 * <p>The two run along the world, not along the screen, so that a click names the half of the block
 * it points at: {@code u} grows east on the faces that face north and south, south on the faces that
 * face east and west, and {@code v} grows up the block on the four side faces and north on the two
 * horizontal ones. A click at a face's own eastern edge therefore fills the eastern half whether
 * that edge is the player's left or their right, and a click at the northern edge of a top face
 * fills the northern half, the one nearest the player.
 *
 * <p>Each frame therefore records the corner the face's left and bottom edges meet at, together
 * with the direction {@code u} grows in along the block.
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
 * @param leftEdge the world direction of the face's left edge
 * @param rightEdge the world direction of the face's right edge
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
	 * The half the clicked region of this frame fills: whichever region of the face {@code (u, v)}
	 * falls in, or the half behind the face itself when the click is in the middle quarter.
	 *
	 * <p>The middle quarter takes the half behind the face, so its visible face is the one that was
	 * clicked. Each other quarter sits against one edge and takes the half behind that edge - the
	 * half a slab has to fill to lie against the edge the player pointed at, which is the half of the
	 * block on that same side since the two axes run along the world. The middle is named first, and
	 * the caller gives it priority, so it wins in the corners where the bands overlap.
	 *
	 * <p>The answer does not depend on which cell the placement lands in. It is the same rule for a
	 * click on a plain block, where the new slab is built in the cell beyond the clicked face, and
	 * for a click on an upright slab, where the click is moved into that cell before it gets here:
	 * the region names a half of the block the click was read against, and a half of one block lines
	 * up with the same half of the block next door.
	 */
	public VerticalSlab.Fill region(final Direction face, final boolean middle, final double u, final double v) {
		if (middle) {
			// The half that face points away from, which is the one running along the face the
			// player clicked and carries a wall on straight.
			return VerticalSlab.Fill.opposite(face);
		}

		if (v < MIDDLE_MIN) {
			return floor(face);
		}

		if (v > MIDDLE_MAX) {
			return ceiling(face);
		}

		return band(u < MIDDLE_MIN);
	}

	/** The half filled by a band along the face's left ({@code true}) or right ({@code false}) edge. */
	public VerticalSlab.Fill band(final boolean nearLeftEdge) {
		return fill(nearLeftEdge ? this.leftEdge : this.rightEdge);
	}

	/** The half filled by the band along the bottom edge of {@code face}. */
	public VerticalSlab.Fill floor(final Direction face) {
		return fill(vDirection(face).getOpposite());
	}

	/** The half filled by the band along the top edge of {@code face}. */
	public VerticalSlab.Fill ceiling(final Direction face) {
		return fill(vDirection(face));
	}

	/**
	 * The half of the block whose visible face points along {@code side}, which is a side of the
	 * block: a band along an edge of a face fills the half that edge names, so a click at the
	 * eastern edge of a face fills the eastern half and a click at the northern edge of a top face
	 * the northern half.
	 *
	 * <p>A {@link Direction} and a {@link VerticalSlab.Fill} name a half in opposite terms - the
	 * direction is the way its visible face points, the fill is the end of the axis it starts from -
	 * so the two are translated here rather than by negating the direction.
	 */
	private static VerticalSlab.Fill fill(final Direction side) {
		return VerticalSlab.Fill.fillBeyond(side);
	}

	/**
	 * {@code u} of a click: how far across the face it landed, from the left edge towards the right
	 * one. It is the click's offset along the world direction {@link #uAxis()} names, so a face read
	 * from outside maps a click onto the half it points at: a click at a face's own eastern edge
	 * fills the eastern half, wherever that edge sits on the screen.
	 *
	 * <p>{@code hit} is measured from the corner of the block the click was read against, the one
	 * the frame's own corner is relative to - not from the corner of the world. The two are a whole
	 * block apart for every block but the one at the origin, so a world position here reads every
	 * click as though it had landed far off the face.
	 */
	public double u(final Vec3 hit) {
		double distance = switch (this.uAxis) {
			case X -> hit.x - this.cornerX;
			case Y -> hit.y - this.cornerY;
			case Z -> hit.z - this.cornerZ;
		};

		return this.uPositive ? distance : -distance;
	}

	/**
	 * {@code v} of a click: how far along the face it landed, from the edge {@link #vDirection}
	 * starts at towards the one it names, so a click at the bottom edge of a side face fills the
	 * lower half of the block and a click at the northern edge of a top face the northern half.
	 */
	public double v(final Vec3 hit, final Direction face) {
		Direction vAxis = vDirection(face);

		return vAxis.getStepY() != 0
			? (hit.y - this.cornerY) * vAxis.getStepY()
			: (hit.z - this.cornerZ) * vAxis.getStepZ();
	}

	/**
	 * The direction of the positive end of {@code v}: the end the top band lies at, which
	 * {@link #ceiling} fills. It is the world's up on the four side faces, so {@link #floor} - the
	 * bottom band - fills the lower half and {@code ceiling} the upper one.
	 *
	 * <p>On the two horizontal faces it is the world's north, so the band along the edge nearest
	 * the player's north fills the northern half and the band along the southern edge the
	 * southern one - the band and the half it fills are on the same side of the block, the same
	 * rule the side faces follow.
	 */
	public Direction vDirection(final Direction face) {
		return switch (face) {
			case UP, DOWN -> Direction.NORTH;
			default -> Direction.UP;
		};
	}

	private static final Map<Direction, FaceFrame> FRAMES = Map.of(
		// A side face is read with the world's east to the right on the faces that face north and
		// south, and the world's south to the right on the ones that face east and west: whichever
		// way the block is turned, the edge the player points at is the half that gets filled. Its
		// other axis runs down the world.
		Direction.NORTH, new FaceFrame(0, 0, 1, Direction.Axis.X, true, Direction.WEST, Direction.EAST),
		Direction.SOUTH, new FaceFrame(1, 0, 1, Direction.Axis.X, false, Direction.WEST, Direction.EAST),
		Direction.WEST, new FaceFrame(1, 0, 0, Direction.Axis.Z, true, Direction.NORTH, Direction.SOUTH),
		Direction.EAST, new FaceFrame(1, 0, 0, Direction.Axis.Z, true, Direction.NORTH, Direction.SOUTH),
		// The horizontal faces are read with east to the right, and their other axis runs north, so
		// the band nearest the player's north fills the northern half. Their frame starts at the
		// south-west corner for that reason: u grows east from it and v grows north.
		Direction.UP, new FaceFrame(0, 1, 1, Direction.Axis.X, true, Direction.WEST, Direction.EAST),
		Direction.DOWN, new FaceFrame(0, 0, 1, Direction.Axis.X, true, Direction.WEST, Direction.EAST)
	);
}
