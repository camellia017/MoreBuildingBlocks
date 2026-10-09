package com.camelie.morebuildingblocks.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Extra block state properties that {@link com.camelie.morebuildingblocks.mixin.SlabBlockMixin}
 * attaches to every vanilla slab block.
 *
 * <p>Only two properties are needed on top of the vanilla {@code type} / {@code waterlogged} pair:
 * {@link #VERTICAL} selects the vertical shape and {@link #AXIS} selects which horizontal axis the
 * slab is thin along. Vanilla's {@code type=top|bottom} keeps describing which half of the block is
 * filled, on either the Y axis (horizontal slab) or the {@link #AXIS} axis (vertical slab).
 */
public final class MBBlockStateProperties {
	/** {@code true} for a vertical slab, {@code false} for a vanilla horizontal slab. */
	public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");

	/**
	 * The horizontal axis a vertical slab is thin along. This is vanilla's own {@code axis} property,
	 * reused so that no new serialised state name is introduced for it.
	 */
	public static final EnumProperty<Direction.Axis> AXIS =
		net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;

	/** Vertical slab against the west face: full height (Y), half depth (X 0..8), full depth (Z). */
	private static final VoxelShape VERTICAL_WEST = Shapes.box(0.0, 0.0, 0.0, 0.5, 1.0, 1.0);
	/** Vertical slab against the east face: full height (Y), half depth (X 8..16), full depth (Z). */
	private static final VoxelShape VERTICAL_EAST = Shapes.box(0.5, 0.0, 0.0, 1.0, 1.0, 1.0);
	/** Vertical slab against the north face: half depth (Z 0..8), full width (X), full height (Y). */
	private static final VoxelShape VERTICAL_NORTH = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.5);
	/** Vertical slab against the south face: half depth (Z 8..16), full width (X), full height (Y). */
	private static final VoxelShape VERTICAL_SOUTH = Shapes.box(0.0, 0.0, 0.5, 1.0, 1.0, 1.0);

	private MBBlockStateProperties() {
	}

	/**
	 * Builds the vertical slab collision/outline shape: full block height, half depth along
	 * {@code axis}.
	 *
	 * @param axis     {@link Direction.Axis#X} or {@link Direction.Axis#Z}
	 * @param positive whether the filled half is the positive half of {@code axis}
	 */
	public static VoxelShape verticalSlabShape(final Direction.Axis axis, final boolean positive) {
		return switch (axis) {
			case X -> positive ? VERTICAL_EAST : VERTICAL_WEST;
			case Z -> positive ? VERTICAL_SOUTH : VERTICAL_NORTH;
			case Y -> Shapes.block();
		};
	}
}
