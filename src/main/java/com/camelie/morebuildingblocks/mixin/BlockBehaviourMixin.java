package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneConnection;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates and mirrors upright slab states, and wakes the panes that a block change touches
 * diagonally.
 *
 * <p>{@link SlabBlock} does not declare {@code rotate} or {@code mirror} itself - it inherits the
 * identity implementations from {@link BlockBehaviour} - so a mixin on {@code SlabBlock} has no
 * method to inject into and the injection is rejected outright. This mixin therefore targets the
 * declaring class and filters for slab states, which is where the upright half actually has to be
 * turned.
 *
 * <p>{@code updateIndirectNeighbourShapes} is declared here as well, and for the same reason: it is
 * the step where vanilla already reaches past the six orthogonal neighbours, which is exactly what
 * a pane's twelve diagonal spaces need. See {@link MBPaneConnection#refreshDiagonals}.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
	@ModifyReturnValue(method = "rotate", at = @At("RETURN"))
	private BlockState morebuildingblocks$rotateVertical(final BlockState original, final BlockState state, final Rotation rotation) {
		if (!(state.getBlock() instanceof SlabBlock) || !VerticalSlab.isVertical(state) || VerticalSlab.isDouble(state)) {
			return original;
		}

		Direction positive = Direction.fromAxisAndDirection(VerticalSlab.axis(state), Direction.AxisDirection.POSITIVE);
		Direction rotated = rotation.rotate(positive);
		return VerticalSlab.withVertical(state, rotated.getAxis(), VerticalSlab.half(state));
	}

	@ModifyReturnValue(method = "mirror", at = @At("RETURN"))
	private BlockState morebuildingblocks$mirrorVertical(final BlockState original, final BlockState state, final Mirror mirror) {
		if (!(state.getBlock() instanceof SlabBlock) || !VerticalSlab.isVertical(state) || VerticalSlab.isDouble(state)) {
			return original;
		}

		Direction positive = Direction.fromAxisAndDirection(VerticalSlab.axis(state), Direction.AxisDirection.POSITIVE);
		Direction mirrored = mirror.mirror(positive);
		return VerticalSlab.withVertical(state, mirrored.getAxis(), VerticalSlab.half(state));
	}

	/**
	 * Tells the panes standing diagonally around a change that their surroundings moved.
	 *
	 * <p>Vanilla only wakes the six orthogonal neighbours in the shape cascade, so a block placed or
	 * broken on a diagonal would leave a pane alone until something else happened to touch it - which
	 * is why some iron bars used to stay unconnected until a block was placed beside them. The three
	 * rules that read diagonals make that visible; {@link MBPaneConnection#refreshDiagonals} walks
	 * the twelve of them from here, the same trick vanilla's redstone wire uses.
	 *
	 * <p>This runs for every block change in the world, so it does nothing but read the twelve
	 * neighbour states and drop the ones that are not panes; panes from other mods, and changes with
	 * no pane anywhere near, cost no more than those reads.
	 */
	@Inject(method = "updateIndirectNeighbourShapes", at = @At("HEAD"))
	private void morebuildingblocks$paneDiagonals(
		final BlockState state,
		final LevelAccessor level,
		final BlockPos pos,
		final int updateFlags,
		final int updateLimit,
		final CallbackInfo ci
	) {
		MBPaneConnection.refreshDiagonals(level, state, pos, updateFlags, updateLimit);
	}
}
