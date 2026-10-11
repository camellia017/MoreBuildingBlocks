package com.camelie.morebuildingblocks.block;

import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Turns the eight pane flags this mod adds when a structure is rotated or mirrored.
 *
 * <p>Vanilla's {@code CrossCollisionBlock} moves its own four connection booleans - which under this
 * mod name the lower halves of the arms - and nothing else, so the four upper halves and the four
 * corners would keep pointing at the faces they had before the turn. A turned pane would then
 * describe pieces its neighbours no longer call for: the shape cascade and the post process pass
 * that reads a chunk back do work the state out again, but nothing turns it while it is being
 * rotated, and nothing turns a state that is only ever held, such as a structure template's.
 * Following the four vanilla flags here means the state is right the moment it is turned.
 *
 * <p>Only the eight flags this mod adds are moved. The four vanilla ones are already turned by the
 * call this wraps, and turning them twice would send them a second quarter turn away from the block
 * they belong to. A state that {@link MBPaneShapes#isPane} does not claim - another mod's pane, or
 * one of the fences that share the parent class - comes back unchanged, because it has no flags in
 * play and {@code MBPaneProperties} cannot read the ones that are missing. {@link Rotation#NONE} and
 * {@link Mirror#NONE} are the identity and are answered with the state itself.
 */
public final class MBPaneRotation {
	/** The four faces an arm points at, each with an upper half of this mod's. */
	private static final Direction[] ARMS = {
		Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
	};

	private MBPaneRotation() {
	}

	/**
	 * The state with every flag of this mod's moved to the face it turns into: the upper half of one
	 * arm becomes the upper half of the arm a quarter turn further on, and a corner becomes the
	 * corner between the two faces its own two faces turn into.
	 */
	public static BlockState rotate(final BlockState state, final Rotation rotation) {
		if (rotation == Rotation.NONE) {
			return state;
		}

		return turn(state, rotation::rotate);
	}

	/** The same for a mirror, which swaps the faces on either side of the plane it mirrors across. */
	public static BlockState mirror(final BlockState state, final Mirror mirror) {
		if (mirror == Mirror.NONE) {
			return state;
		}

		return turn(state, mirror::mirror);
	}

	/**
	 * Moves the eight flags along one face mapping: a piece that pointed at a face ends up pointing
	 * at the face that mapping sends it to. The four vanilla flags are left as they are, so whatever
	 * the call being wrapped did to them stands.
	 */
	private static BlockState turn(final BlockState state, final UnaryOperator<Direction> mapping) {
		if (!MBPaneShapes.isPane(state)) {
			return state;
		}

		BlockState turned = state;

		for (Direction arm : ARMS) {
			turned = turned.setValue(MBPaneProperties.upper(mapping.apply(arm)),
					state.getValue(MBPaneProperties.upper(arm)));
		}

		for (Corner corner : Corner.values()) {
			Corner moved = Corner.between(mapping.apply(corner.first), mapping.apply(corner.second));
			turned = turned.setValue(moved.piece, state.getValue(corner.piece));
		}

		return turned;
	}

	/**
	 * One of the four corner pieces and the two faces it stands between.
	 *
	 * <p>A rotation and a mirror both send neighbouring faces to neighbouring faces, so the corner a
	 * piece lands on is always one of these four; the table is walked, and either order of the two
	 * faces is accepted, because a quarter turn can hand the pair over the other way round.
	 */
	private enum Corner {
		NORTH_EAST(MBPaneProperties.CORNER_NORTH_EAST, Direction.NORTH, Direction.EAST),
		SOUTH_EAST(MBPaneProperties.CORNER_SOUTH_EAST, Direction.SOUTH, Direction.EAST),
		SOUTH_WEST(MBPaneProperties.CORNER_SOUTH_WEST, Direction.SOUTH, Direction.WEST),
		NORTH_WEST(MBPaneProperties.CORNER_NORTH_WEST, Direction.NORTH, Direction.WEST);

		private final Property<Boolean> piece;
		private final Direction first;
		private final Direction second;

		Corner(final Property<Boolean> piece, final Direction first, final Direction second) {
			this.piece = piece;
			this.first = first;
			this.second = second;
		}

		/** Whether this corner stands between those two faces, in either order. */
		private boolean joins(final Direction one, final Direction other) {
			return (this.first == one && this.second == other)
					|| (this.first == other && this.second == one);
		}

		/** The corner standing between the two faces a mapping sent this one's two faces to. */
		private static Corner between(final Direction one, final Direction other) {
			for (Corner corner : values()) {
				if (corner.joins(one, other)) {
					return corner;
				}
			}

			throw new IllegalStateException(one + " and " + other + " are not corners of a pane");
		}
	}
}
