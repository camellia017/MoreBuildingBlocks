package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneRotation;
import com.camelie.morebuildingblocks.block.MBPaneShapes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the twelve-piece panes their shape, and turns their flags along with the block.
 *
 * <p>Vanilla builds a pane's shape once, out of the post and one full height arm per connection
 * boolean, and hands the same function to both the outline and the collision box. The pieces this
 * mod adds cannot be expressed that way - every arm is split in half, and the corners only exist at
 * mid height - so both getters answer with the shape {@link MBPaneShapes} builds from the state's
 * twelve flags, once those flags are in play.
 *
 * <p>Until a flag is set the vanilla shape is returned untouched, which keeps an untouched pane -
 * and anything another mod added to its shape - exactly as it was.
 *
 * <p>{@code rotate} and {@code mirror} are declared here rather than in {@code IronBarsBlock}, which
 * is why a pane has to be turned from this class at all, and they move vanilla's four connection
 * booleans alone: a pane turned with the structure around it would otherwise keep its four upper
 * halves and four corners pointing where they were. {@link MBPaneRotation} turns those eight with
 * the same mapping. Fences share this parent class and reach the same injections, so the helper
 * checks that it is looking at a pane before it reads or writes anything.
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

	/**
	 * Turns the four upper halves and the four corners a quarter turn, half turn or mirror along with
	 * the four vanilla flags, so the state describes the pieces the turned pane actually stands with.
	 *
	 * <p>The state vanilla hands back has already had its own four flags moved and still carries this
	 * mod's eight as they were, which is exactly what {@link MBPaneRotation} reads from.
	 */
	@ModifyReturnValue(method = "rotate", at = @At("RETURN"))
	private BlockState morebuildingblocks$panePiecesRotate(
		final BlockState original,
		final BlockState state,
		final Rotation rotation
	) {
		return MBPaneRotation.rotate(original, rotation);
	}

	/** The same for a mirror, which swaps the two faces either side of the plane it mirrors across. */
	@ModifyReturnValue(method = "mirror", at = @At("RETURN"))
	private BlockState morebuildingblocks$panePiecesMirror(
		final BlockState original,
		final BlockState state,
		final Mirror mirror
	) {
		return MBPaneRotation.mirror(original, mirror);
	}
}
