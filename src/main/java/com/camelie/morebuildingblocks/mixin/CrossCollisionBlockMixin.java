package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneShapes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the twelve-piece panes their shape.
 *
 * <p>Vanilla builds a pane's shape once, out of the post and one full height arm per connection
 * boolean, and hands the same function to both the outline and the collision box. The pieces this
 * mod adds cannot be expressed that way - every arm is split in half, and the corners only exist at
 * mid height - so both getters answer with the shape {@link MBPaneShapes} builds from the state's
 * twelve flags, once those flags are in play.
 *
 * <p>Until a flag is set the vanilla shape is returned untouched, which keeps an untouched pane -
 * and anything another mod added to its shape - exactly as it was.
 */
@Mixin(CrossCollisionBlock.class)
public abstract class CrossCollisionBlockMixin {
	/** The vanilla shape the pane would have without this mod. */
	@Shadow
	@Final
	private Function<BlockState, VoxelShape> shapes;

	/** The vanilla collision box the pane would have without this mod. */
	@Shadow
	@Final
	private Function<BlockState, VoxelShape> collisionShapes;

	@ModifyReturnValue(method = "getShape", at = @At("RETURN"))
	private VoxelShape morebuildingblocks$paneShape(
		final VoxelShape original,
		final BlockState state,
		final BlockGetter level,
		final BlockPos pos,
		final CollisionContext context
	) {
		return MBPaneShapes.adjustShape(original, this.shapes.apply(state), state);
	}

	@ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
	private VoxelShape morebuildingblocks$paneCollisionShape(
		final VoxelShape original,
		final BlockState state,
		final BlockGetter level,
		final BlockPos pos,
		final CollisionContext context
	) {
		return MBPaneShapes.adjustShape(original, this.collisionShapes.apply(state), state);
	}
}
