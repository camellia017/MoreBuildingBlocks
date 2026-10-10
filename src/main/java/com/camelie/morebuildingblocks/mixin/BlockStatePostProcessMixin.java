package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneConnection;
import com.camelie.morebuildingblocks.block.MBPaneShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts a naturally generated pane on the post-processing list, so its twelve pieces are worked out
 * as the chunk finishes generating.
 *
 * <p>World generation places blocks through {@code WorldGenRegion}, which calls neither the
 * placement logic nor a shape update. A pane a structure ships with - the iron bars around the
 * End's obsidian pillars, the panes of a village window - therefore keeps vanilla's four connection
 * booleans and none of the twelve pieces, and renders as the bare half-height post. Vanilla marks a
 * placed position for post-processing only when the state names one to revisit, which 26.3 spells
 * as {@code getPostProcessPos(BlockGetter, BlockPos)}, the replacement for the {@code boolean
 * hasPostProcess} of earlier versions. Answering a pane's own position puts it on that list:
 * {@code LevelChunk.postProcessGeneration} then runs {@code Block.updateFromNeighbourShapes} on it,
 * which reaches {@link MBPaneConnection#update} through {@code IronBarsBlock.updateShape}, and
 * {@link LevelChunkPostProcessMixin} sweeps the chunk once more when the pass is over.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStatePostProcessMixin {
	@Inject(method = "getPostProcessPos", at = @At("HEAD"), cancellable = true)
	private void morebuildingblocks$postProcessPanes(
		final BlockGetter level,
		final BlockPos pos,
		final CallbackInfoReturnable<BlockPos> cir
	) {
		if (MBPaneShapes.isPane((BlockState) (Object) this)) {
			cir.setReturnValue(pos);
		}
	}
}
