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
	 * Turns the flat slab vanilla just decided on into the half of the block the player aimed at,
	 * and records the direction a glazed pattern has to be turned by.
	 *
	 * <p>Vanilla's own state is what the answer starts from: it already carries the axis the click
	 * came in on for a layered slab, and the waterlogged flag. Only the half - and, for a glazed
	 * block, which way the pattern faces - is decided here, by the rule in
	 * {@link VerticalSlab#placementFill}. That rule lives in the block package because a record
	 * built inside a mixin has its constructed values rewritten along with the mixin, and the frame
	 * table then reads back different directions than it was written with.
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

	/**
	 * Refuses to let an upright slab be replaced from any face.
	 *
	 * <p>{@link SlabBlock#canBeReplaced} only asks whether the slab is a double, so it reports every
	 * upright slab as open from above, from below and from both of its own broad faces. That is
	 * wrong in a way the player sees immediately: a click on the top of an upright slab is moved
	 * into the slab's own cell, where {@code SlabBlock.getStateForPlacement} - blind to the axis it
	 * is adding to - combines the click with the slab already standing there, and the half slab the
	 * player just placed silently swaps the whole block out. On the broad faces it is wrong more
	 * quietly: a click there would complete the block into a full cube instead of carrying the wall
	 * on, which is the one placement the player is asking for while building one.
	 *
	 * <p>Answering {@code false} everywhere is what the reference implementation does
	 * ({@code btsdhz/vanilla-refit}'s {@code SlabBlockMixin.canBeReplaced} returns {@code false} for
	 * every state that is not its flat mode), and it is what puts every click on an upright slab
	 * into the next cell along, where {@link VerticalSlab#placementFill} reads it as the wall
	 * continuing.
	 */
	@ModifyReturnValue(method = "canBeReplaced", at = @At("RETURN"))
	private boolean morebuildingblocks$keepVertical(final boolean original, final BlockState state, final BlockPlaceContext context) {
		if (VerticalSlab.isVertical(state) && !VerticalSlab.isDouble(state)) {
			return false;
		}

		return original;
	}
}
