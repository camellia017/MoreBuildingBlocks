package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneConnection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Settles the panes of a chunk once the chunk has finished being generated.
 *
 * <p>Vanilla's post process walks the positions it marked while generating and calls
 * {@code updateFromNeighbourShapes} on each of them, in no order worth relying on. Panes that
 * generated with an arm already there, in a village or beside a mansion window, would otherwise be
 * left with whatever the placement order happened to produce. The two calls here only bookend that
 * walk, so {@link MBPaneConnection#refreshChunk} can run afterwards - and only when a pane of ours
 * was actually met, because the same method runs when a chunk is read back from disk.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkPostProcessMixin {
	@Inject(method = "postProcessGeneration", at = @At("HEAD"))
	private void morebuildingblocks$beginPaneSettle(final ServerLevel level, final CallbackInfo ci) {
		MBPaneConnection.beginPostProcess();
	}

	@Inject(method = "postProcessGeneration", at = @At("RETURN"))
	private void morebuildingblocks$settlePanes(final ServerLevel level, final CallbackInfo ci) {
		LevelChunk self = (LevelChunk) (Object) this;

		if (MBPaneConnection.endPostProcess()) {
			MBPaneConnection.refreshChunk(self, self.getLevel());
		}
	}
}
