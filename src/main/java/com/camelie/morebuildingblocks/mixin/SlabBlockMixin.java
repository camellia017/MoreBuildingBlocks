package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes every vanilla slab block placeable upright as a "vertical slab".
 *
 * <p>Two extra state properties are attached to the vanilla {@link SlabBlock} definition: a
 * {@code vertical} flag and the reused vanilla {@code axis} (so no new serialised property name is
 * introduced). The outline shape, the placement rule, the replace rule and the inherited
 * rotate/mirror handlers are redirected for those states. No new block is registered: an upright
 * slab is the same block, and therefore the same item, seen from a different angle.
 */
@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin {
	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void morebuildingblocks$addVerticalProperties(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder, final CallbackInfo ci) {
		builder.add(MBBlockStateProperties.VERTICAL, MBBlockStateProperties.AXIS);
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
			.trySetValue(MBBlockStateProperties.AXIS, Direction.Axis.Y);
	}

	@ModifyReturnValue(method = "getShape", at = @At("RETURN"))
	private VoxelShape morebuildingblocks$verticalShape(final VoxelShape original, final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		VoxelShape vertical = VerticalSlab.verticalShape(state);
		return vertical != null ? vertical : original;
	}

	@ModifyReturnValue(method = "getStateForPlacement", at = @At("RETURN"))
	private BlockState morebuildingblocks$verticalPlacement(final BlockState original, final BlockPlaceContext context) {
		if (original == null || original.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
			return original;
		}

		BlockPos pos = context.getClickedPos();
		Vec3 hit = context.getClickLocation();
		double x = hit.x - (pos.getX() + 0.5);
		double z = hit.z - (pos.getZ() + 0.5);

		// Which way the slab faces: the thin axis follows whichever horizontal offset of
		// the CLICK dominates, so clicking a block's eastern half fills its eastern half.
		// The filled half along that axis is the "facing", so a player standing to the east
		// gets a slab facing them.
		//
		// The click location is used rather than the player's eye position: what the
		// player is aiming at is what decides how the block is oriented, and a click
		// through the middle of a block leaves it flat.
		Direction.Axis axis = Math.abs(x) >= Math.abs(z) ? Direction.Axis.X : Direction.Axis.Z;
		double dominant = axis == Direction.Axis.X ? x : z;

		if (Math.abs(dominant) < 0.15) {
			// Dead centre: nothing to face, so the slab lies flat as it always did.
			return original;
		}

		SlabType half = dominant > 0.0 ? SlabType.TOP : SlabType.BOTTOM;
		return VerticalSlab.withVertical(original, axis, half);
	}

	@ModifyReturnValue(method = "canBeReplaced", at = @At("RETURN"))
	private boolean morebuildingblocks$replaceVertical(final boolean original, final BlockState state, final BlockPlaceContext context) {
		if (original || !VerticalSlab.isVertical(state) || VerticalSlab.isDouble(state)) {
			return original;
		}

		Direction.Axis axis = VerticalSlab.axis(state);
		if (axis == Direction.Axis.Y) {
			return original;
		}

		// A vertical slab only doubles up when its open face is clicked with the same item; the open
		// face points away from the filled half.
		Direction outward = Direction.fromAxisAndDirection(
			axis,
			VerticalSlab.isPositiveHalf(state) ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE
		);
		return context.getClickedFace() == outward && context.getItemInHand().is(state.getBlock().asItem());
	}
}
