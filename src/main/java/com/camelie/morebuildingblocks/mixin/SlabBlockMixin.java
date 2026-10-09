package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.GlazedPattern;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes every vanilla slab block placeable upright as a "vertical slab".
 *
 * <p>Two extra state properties are attached to the vanilla {@link SlabBlock} definition - a {@code
 * vertical} flag and the reused vanilla {@code axis} - and the outline shape, the placement rule and
 * the replace rule are redirected for those states. No new block is registered: an upright slab is
 * the same block, and therefore the same item, seen from a different angle.
 *
 * <p>The third property, the direction a glazed pattern is turned by, is only declared for the
 * slabs marked {@link GlazedPattern}, i.e. {@link MBGlazedSlabBlock}: vanilla's hundred slabs and
 * the sixteen plain terracotta ones keep exactly the states vanilla gives them.
 *
 * <p>The declaration happens here rather than in {@code MBGlazedSlabBlock} itself because {@code
 * createBlockStateDefinition} runs from the {@code Block} constructor: a subclass override would run
 * its body only after the definition had already been built, and adding a property then is both too
 * late and a duplicate by name.
 */
@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin {
	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void morebuildingblocks$addVerticalProperties(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder, final CallbackInfo ci) {
		builder.add(MBBlockStateProperties.VERTICAL, MBBlockStateProperties.AXIS);

		if ((Object) this instanceof GlazedPattern) {
			builder.add(MBBlockStateProperties.GLAZED_FACING);
		}
	}

	/**
	 * Keeps vanilla's flat bottom slab as this block's default state.
	 *
	 * <p>This is the one place the two added properties can silently break every slab in the game.
	 * {@link StateDefinition} keeps its properties in an {@code ImmutableSortedMap} keyed by
	 * <em>name</em>, so the set becomes {@code axis, type, vertical, waterlogged}, and
	 * {@code StateDefinition.any()} is simply the first entry of the cartesian product of every
	 * property's values - which is {@code axis=x, type=top, vertical=true, waterlogged=true}.
	 * {@code SlabBlock}'s constructor only pins {@code type} and {@code waterlogged}, because it
	 * knows nothing about the properties this mixin adds, so its default state silently became an
	 * upright slab.
	 *
	 * <p>Pinning the two extras at the call site fixes it at the source rather than patching every
	 * read afterwards. {@code type} and {@code waterlogged} are intentionally left as vanilla set
	 * them.
	 *
	 * <p>The target is {@code Block.defaultBlockState()}, not {@code StateDefinition.any()}: the
	 * slab's own constructor reaches the definition through that virtual call, and it is the only
	 * place where the definition is already complete. The {@code any()} calls in the
	 * {@code Block} and {@code BlockBehaviour} constructors run before properties exist and are not
	 * touched.
	 */
	@ModifyExpressionValue(
		method = "<init>",
		at = @At(value = "INVOKE", target = "defaultBlockState()Lnet/minecraft/world/level/block/state/BlockState;")
	)
	private BlockState morebuildingblocks$flatDefaultState(final BlockState original) {
		return original
			.trySetValue(MBBlockStateProperties.VERTICAL, false)
			.trySetValue(MBBlockStateProperties.AXIS, Direction.Axis.Y)
			.trySetValue(MBBlockStateProperties.GLAZED_FACING, Direction.SOUTH);
	}

	@ModifyReturnValue(method = "getShape", at = @At("RETURN"))
	private VoxelShape morebuildingblocks$verticalShape(final VoxelShape original, final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		VoxelShape vertical = VerticalSlab.verticalShape(state);
		return vertical != null ? vertical : original;
	}

	/**
	 * Which half of the block the player is pointing at, read off the clicked face, plus the
	 * direction the player was looking, which a glazed pattern is turned by.
	 *
	 * <p>The rule itself lives in {@link VerticalSlab#placementFill}, in the block package: a record
	 * built inside this mixin has its constructed values rewritten along with the mixin, and the
	 * frame table then reads back different directions than it was written with.
	 */
	@ModifyReturnValue(method = "getStateForPlacement", at = @At("RETURN"))
	private BlockState morebuildingblocks$verticalPlacement(final BlockState original, final BlockPlaceContext context) {
		if (original == null || original.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
			return original;
		}

		VerticalSlab.Fill fill = VerticalSlab.placementFill(context);

		if (fill == null) {
			return original;
		}

		return VerticalSlab.withFill(original, fill).trySetValue(
			MBBlockStateProperties.GLAZED_FACING, context.getHorizontalDirection().getOpposite());
	}

	@ModifyReturnValue(method = "canBeReplaced", at = @At("RETURN"))
	private boolean morebuildingblocks$replaceVertical(final boolean original, final BlockState state, final BlockPlaceContext context) {
		if (original || !VerticalSlab.isVertical(state) || VerticalSlab.isDouble(state)) {
			return original;
		}

		// Only the open face - the plane the slab shows through, where its own missing half is -
		// completes the block. The two thin sides and the boundary of the cell the slab fills all
		// look at the next cell along, so a click there continues the wall instead: an upright slab
		// answers those three faces as replaceable, which is what lets a wall be built against, and
		// every one of them has to be refused here so the click moves out of the slab rather than
		// landing inside the half that is already taken.
		return context.getClickedFace() != VerticalSlab.openFace(state)
			&& context.getItemInHand().is(state.getBlock().asItem());
	}
}
