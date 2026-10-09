package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.GlazedPattern;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the direction a glazed stair was placed from, so its directional pattern can be turned the
 * same way the glazed terracotta block placed instead of it would have been.
 *
 * <p>Only the stairs marked {@link GlazedPattern} carry the property; every other stair in the game,
 * vanilla's included, keeps vanilla's own 36 states. The marker is asked for rather than the concrete
 * class so that this mixin has no dependency on the registry.
 */
@Mixin(StairBlock.class)
public abstract class StairBlockMixin {

	/**
	 * Declares the extra property on a glazed stair only. {@code createBlockStateDefinition} runs
	 * from the {@code Block} constructor, which a subclass cannot get in front of, so the property
	 * cannot be declared by {@link com.camelie.morebuildingblocks.block.MBGlazedStairsBlock} itself.
	 */
	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void morebuildingblocks$addGlazedFacing(final StateDefinition.Builder<Block, BlockState> builder, final CallbackInfo ci) {
		if ((Object) this instanceof GlazedPattern) {
			builder.add(MBBlockStateProperties.GLAZED_FACING);
		}
	}

	/**
	 * Vanilla decides the stair's shape and half; this only adds the pattern direction.
	 *
	 * <p>{@code trySetValue} rather than {@code setValue}: the block being placed is not necessarily
	 * a glazed one, and placing a plain stair must not throw. {@code GlazedTerracottaBlock} sets
	 * this exact value - {@code context.getHorizontalDirection().getOpposite()} - so a player who
	 * placed the glazed terracotta itself would have got the same pattern orientation.
	 */
	@ModifyReturnValue(method = "getStateForPlacement", at = @At("RETURN"))
	private BlockState morebuildingblocks$glazedFacing(final BlockState original, final BlockPlaceContext context) {
		if (original == null) {
			return null;
		}

		return original.trySetValue(
			MBBlockStateProperties.GLAZED_FACING, context.getHorizontalDirection().getOpposite());
	}
}
