package com.camelie.morebuildingblocks.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
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
 */
public final class VerticalSlab {
	private VerticalSlab() {
	}

	/** {@code true} when this state is an upright slab instead of a flat one. */
	public static boolean isVertical(final BlockState state) {
		return state.getValue(MBBlockStateProperties.VERTICAL);
	}

	/** The horizontal axis an upright slab is thin along; {@link Direction.Axis#Y} for flat slabs. */
	public static Direction.Axis axis(final BlockState state) {
		return state.getValue(MBBlockStateProperties.AXIS);
	}

	/** The filled half. Always read alongside {@link #axis(BlockState)} for upright slabs. */
	public static SlabType half(final BlockState state) {
		return state.getValue(SlabBlock.TYPE);
	}

	/** @return {@code true} when the filled half is the positive half of {@link #axis(BlockState)}. */
	public static boolean isPositiveHalf(final BlockState state) {
		return half(state) == SlabType.TOP;
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
}
