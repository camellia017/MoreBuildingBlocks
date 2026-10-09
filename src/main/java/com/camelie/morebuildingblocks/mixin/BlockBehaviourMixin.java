package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Rotates and mirrors upright slab states.
 *
 * <p>{@link SlabBlock} does not declare {@code rotate} or {@code mirror} itself - it inherits the
 * identity implementations from {@link BlockBehaviour} - so a mixin on {@code SlabBlock} has no
 * method to inject into and the injection is rejected outright. This mixin therefore targets the
 * declaring class and filters for slab states, which is where the upright half actually has to be
 * turned.
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
}
