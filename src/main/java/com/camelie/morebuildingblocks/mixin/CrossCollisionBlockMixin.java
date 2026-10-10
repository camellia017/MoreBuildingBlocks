package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneShapes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the twelve-piece panes their shape.
 *
 * <p>Vanilla builds a pane's shape once, out of the post and one full height arm per connection
 * boolean, and hands the same function to both the outline and the collision box. The pieces this
 * mod adds cannot be expressed that way - every arm is split in half, and the corners only exist at
 * mid height - so both getters answer with the shape {@link MBPaneShapes} builds from the state's
 * twelve flags instead.
 *
 * <p>Only states that carry the mod's flags take that path, and for them the mod's shape is exact:
 * with the eight flags clear it is the vanilla post and arms, so an untouched pane keeps the shape
 * it always had.
 */
@Mixin(CrossCollisionBlock.class)
public abstract class CrossCollisionBlockMixin {
	@ModifyReturnValue(method = "getShape", at = @At("RETURN"))
	private VoxelShape morebuildingblocks$paneShape(
		final VoxelShape original,
		final BlockState state,
		final BlockGetter level,
		final BlockPos pos,
		final CollisionContext context
	) {
		return MBPaneShapes.isPane(state) ? MBPaneShapes.shape(state) : original;
	}

	@ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
	private VoxelShape morebuildingblocks$paneCollisionShape(
		final VoxelShape original,
		final BlockState state,
		final BlockGetter level,
		final BlockPos pos,
		final CollisionContext context
	) {
		return MBPaneShapes.isPane(state) ? MBPaneShapes.shape(state) : original;
	}
}
