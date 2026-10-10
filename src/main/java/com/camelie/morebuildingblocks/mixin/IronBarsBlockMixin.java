package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneConnection;
import com.camelie.morebuildingblocks.block.MBPaneProperties;
import com.camelie.morebuildingblocks.block.MBPaneShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Declares the mod's eight pane flags on every pane-shaped block in the game, and culls faces
 * between two such panes piece by piece.
 *
 * <p>{@link IronBarsBlock} is the common parent of the plain glass pane, the sixteen stained glass
 * panes, iron bars and the eight copper bars, the copper ones reaching it through {@code
 * WeatheringCopperBarsBlock}. It is also the class that overrides {@code
 * createBlockStateDefinition} to add vanilla's four connection booleans and {@code waterlogged}, so
 * adding the panes' own flags at the tail of that one method gives all twenty-six blocks the same
 * eight properties: no new block, no new item, and nothing to declare once per colour.
 *
 * <p>The flags themselves, and why they are {@link MBPaneProperties} rather than vanilla's boolean
 * property, are documented in that class. The shapes of the twelve pieces are built in {@link
 * CrossCollisionBlockMixin}.
 */
@Mixin(IronBarsBlock.class)
public abstract class IronBarsBlockMixin {
	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void morebuildingblocks$addPaneProperties(
		final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder,
		final CallbackInfo ci
	) {
		builder.add(
			MBPaneProperties.NORTH_UP,
			MBPaneProperties.EAST_UP,
			MBPaneProperties.SOUTH_UP,
			MBPaneProperties.WEST_UP,
			MBPaneProperties.CORNER_NORTH_EAST,
			MBPaneProperties.CORNER_SOUTH_EAST,
			MBPaneProperties.CORNER_SOUTH_WEST,
			MBPaneProperties.CORNER_NORTH_WEST
		);
	}

	/**
	 * Decides the pieces of a pane that is being placed.
	 *
	 * <p>Vanilla has already picked the four connection booleans by looking at the neighbours of the
	 * clicked position; {@link MBPaneConnection#update} then reads the same surroundings again and
	 * works out all twelve pieces from them, so a pane arrives with the arms, upper halves and
	 * corners its neighbours call for instead of settling one shape update later.
	 */
	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void morebuildingblocks$piecesOnPlacement(
		final BlockPlaceContext context,
		final CallbackInfoReturnable<BlockState> cir
	) {
		BlockState placed = cir.getReturnValue();

		if (placed != null) {
			cir.setReturnValue(MBPaneConnection.update(placed, context.getLevel(), context.getClickedPos()));
		}
	}

	/**
	 * Rebuilds the pieces whenever a neighbour changes a pane's shape.
	 *
	 * <p>Vanilla's own answer moves the one boolean that faces the changed neighbour, which was all
	 * a pane needed when an arm was a single piece. The twelve pieces depend on all six directions
	 * and on twelve diagonals that vanilla's cascade does not even reach, so the state is worked out
	 * from the world again here rather than patched - which is also what makes the result
	 * independent of the order blocks changed in.
	 */
	@Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
	private void morebuildingblocks$piecesOnNeighbourChange(
		final BlockState state,
		final LevelReader level,
		final ScheduledTickAccess ticks,
		final BlockPos pos,
		final Direction direction,
		final BlockPos neighborPos,
		final BlockState neighborState,
		final RandomSource random,
		final CallbackInfoReturnable<BlockState> cir
	) {
		cir.setReturnValue(MBPaneConnection.update(cir.getReturnValue(), level, pos));
	}

	/**
	 * Hides the face between two panes only where the pieces on either side of it line up.
	 *
	 * <p>Vanilla hides the whole face as soon as both sides have an arm, and hides every face
	 * between two stacked panes outright. Both assume a pane is a post with full height arms, so
	 * once the arms are split in half and the corners are added they blank pieces that stand alone:
	 * the upper half of one pane would face the air when the pane behind it carries no arm there.
	 * {@link MBPaneShapes#hides} compares the pieces instead and leaves anything that does not meet
	 * a matching piece on the other side visible. Panes from other mods, which never carry these
	 * flags, keep vanilla's answer.
	 */
	@Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true)
	private void morebuildingblocks$paneCulling(
		final BlockState state,
		final BlockState neighborState,
		final Direction direction,
		final CallbackInfoReturnable<Boolean> cir
	) {
		if (MBPaneShapes.isPane(state) && MBPaneShapes.isPane(neighborState)) {
			cir.setReturnValue(MBPaneShapes.hides(state, neighborState, direction));
		}
	}
}
