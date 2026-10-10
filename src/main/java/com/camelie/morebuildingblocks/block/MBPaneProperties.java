package com.camelie.morebuildingblocks.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The eight flags the mod hangs on every pane-shaped block, which turn one vanilla pane into twelve
 * separately switchable pieces.
 *
 * <p>Vanilla describes a pane with one boolean per horizontal direction, and those four keep their
 * names here: under the mod each of them means the <em>lower</em> half of that arm, eight pixels
 * tall. The mod adds the upper half of each arm as {@link #NORTH_UP} and its three siblings, and the
 * quarter-sized piece for each of the four corners as the four {@code CORNER_*} flags. Every piece
 * is two pixels thick, exactly as thick as the vanilla arm, so the outline shape and the collision
 * shape of a pane assembled this way still agree with each other.
 *
 * <p>All eight default to {@code false}, which is the point of {@link
 * MBDefaultFalseBooleanProperty}: with every flag off the block is precisely the pane vanilla draws,
 * post and arms included. A pane standing alone, and every pane in an old save, therefore keeps
 * looking and behaving the way it always did.
 */
public final class MBPaneProperties {
	/** The upper half of the arm pointing north: x 7..9, y 8..16, z 0..8. */
	public static final Property<Boolean> NORTH_UP = MBDefaultFalseBooleanProperty.create("pane_north_up");

	/** The upper half of the arm pointing east: x 8..16, y 8..16, z 7..9. */
	public static final Property<Boolean> EAST_UP = MBDefaultFalseBooleanProperty.create("pane_east_up");

	/** The upper half of the arm pointing south: x 7..9, y 8..16, z 8..16. */
	public static final Property<Boolean> SOUTH_UP = MBDefaultFalseBooleanProperty.create("pane_south_up");

	/** The upper half of the arm pointing west: x 0..8, y 8..16, z 7..9. */
	public static final Property<Boolean> WEST_UP = MBDefaultFalseBooleanProperty.create("pane_west_up");

	/** The horizontal quarter piece filling the north-east corner at mid height: x 8..16, z 0..8. */
	public static final Property<Boolean> CORNER_NORTH_EAST =
		MBDefaultFalseBooleanProperty.create("pane_corner_north_east");

	/** The horizontal quarter piece filling the south-east corner at mid height: x 8..16, z 8..16. */
	public static final Property<Boolean> CORNER_SOUTH_EAST =
		MBDefaultFalseBooleanProperty.create("pane_corner_south_east");

	/** The horizontal quarter piece filling the south-west corner at mid height: x 0..8, z 8..16. */
	public static final Property<Boolean> CORNER_SOUTH_WEST =
		MBDefaultFalseBooleanProperty.create("pane_corner_south_west");

	/** The horizontal quarter piece filling the north-west corner at mid height: x 0..8, z 0..8. */
	public static final Property<Boolean> CORNER_NORTH_WEST =
		MBDefaultFalseBooleanProperty.create("pane_corner_north_west");

	private MBPaneProperties() {
	}

	/**
	 * The vanilla flag describing the lower half of the arm pointing at {@code direction}, so that a
	 * shape or a model can be built from both halves of one arm without a second lookup table.
	 *
	 * @throws IllegalArgumentException if {@code direction} is vertical, which no arm is
	 */
	public static BooleanProperty lower(final Direction direction) {
		return switch (direction) {
			case NORTH -> PipeBlock.NORTH;
			case EAST -> PipeBlock.EAST;
			case SOUTH -> PipeBlock.SOUTH;
			case WEST -> PipeBlock.WEST;
			default -> throw new IllegalArgumentException("A pane arm points horizontally, not " + direction);
		};
	}

	/**
	 * The mod's flag describing the upper half of the arm pointing at {@code direction}.
	 *
	 * @throws IllegalArgumentException if {@code direction} is vertical, which no arm is
	 */
	public static Property<Boolean> upper(final Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH_UP;
			case EAST -> EAST_UP;
			case SOUTH -> SOUTH_UP;
			case WEST -> WEST_UP;
			default -> throw new IllegalArgumentException("A pane arm points horizontally, not " + direction);
		};
	}
}
