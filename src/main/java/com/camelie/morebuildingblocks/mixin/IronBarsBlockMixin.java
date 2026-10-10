package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBPaneProperties;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Declares the mod's eight pane flags on every pane-shaped block in the game.
 *
 * <p>{@link IronBarsBlock} is the common parent of the plain glass pane, the sixteen stained glass
 * panes, iron bars and the eight copper bars, the copper ones reaching it through {@code
 * WeatheringCopperBarsBlock}. It is also the class that overrides {@code
 * createBlockStateDefinition} to add vanilla's four connection booleans and {@code waterlogged}, so
 * adding the panes' own flags at the tail of that one method gives all twenty-six blocks the same
 * eight properties: no new block, no new item, and nothing to declare once per colour.
 *
 * <p>The flags themselves, and why they are {@link MBPaneProperties} rather than vanilla's boolean
 * property, are documented in that class. Nothing else is attached here yet: the shapes and the
 * models of the twelve pieces are built from these properties later.
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
}
