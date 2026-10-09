package com.camelie.morebuildingblocks.mixin;

import com.camelie.morebuildingblocks.block.PlacementClick;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * Makes a click on the outer face of an upright slab build the wall one block further out.
 *
 * <p>{@link BlockPlaceContext} decides which block a placement lands in while it is being
 * constructed, and by the time the block's own {@code getStateForPlacement} runs it is far too late
 * to change its mind: {@code getClickedPos()} already returns either the clicked block or the one
 * beyond it. The mod's own placement code always wants the block beyond the clicked face, and the
 * constructor's usual rule gets that wrong for exactly one click.
 *
 * <p>An upright slab has two faces a player can point at, and they do not mean the same thing. Its
 * <em>outer</em> face is the one the filled half shows through, and pointing there means continuing
 * the wall into the next block along. Its <em>inner</em> face is the open half of the block, and
 * pointing there means completing the slab, which the constructor's rule already gets right: the
 * slab reports that face as replaceable, so the click stays in the same block and the placement
 * rule doubles it up.
 *
 * <p>For the outer face the constructor's rule is the wrong way round. It may replace the clicked
 * block - {@code SlabBlock.canBeReplaced} reports an upright slab's outer face as open, which is
 * what lets it be built against at all - but that makes {@code getClickedPos()} return the slab
 * itself, and the new block then lands inside the one that is already there.
 *
 * <p>Two ways round that were not usable: {@code relativePos} is {@code private final}, and a
 * Mixin handler is not the field's initialiser, so writing it throws
 * {@code IllegalAccessError: Update to non-static final field ... attempted from a different method}.
 * What is left is the constructor's own decision, so this bends the value it decides from instead:
 * the {@code canBeReplaced} call whose result becomes {@code replaceClicked}. Refusing to replace
 * the slab leaves {@code replaceClicked} false, and then {@code getClickedPos()} is the block
 * beyond the clicked face - the default {@code relativePos} the constructor computed one line
 * earlier - which is exactly where the wall continues.
 *
 * <p>Moving the click also costs the placement rule the one thing it still needs: which face the
 * player pointed at and where on it. Once {@code getClickedPos()} is the new block, the click point
 * lies a whole block outside it, so {@link PlacementClick} hands out the two stored positions
 * separately and lets the rule read the click against the block it landed on.
 */
@Mixin(BlockPlaceContext.class)
public abstract class BlockPlaceContextMixin implements PlacementClick {
	/**
	 * The block beyond the clicked face, which the constructor computes for every placement.
	 *
	 * <p>This is a field of the target itself, so {@code @Shadow} finds it.
	 */
	@Shadow
	private BlockPos relativePos;

	/** {@inheritDoc} */
	@Override
	public BlockPos morebuildingblocks$hitPos() {
		// Stepping back from the block beyond the face lands on the block the face belongs to.
		// Measuring the step from the context's own answer instead would return a block that is
		// not the one the click landed on: getClickedPos() is the position being built in.
		return this.relativePos.relative(((BlockPlaceContext) (Object) this).getClickedFace().getOpposite());
	}

	/** {@inheritDoc} */
	@Override
	public BlockPos morebuildingblocks$relativePos() {
		return this.relativePos;
	}

	/**
	 * Reports an upright slab's outer face as not replaceable, so the click moves one block out.
	 *
	 * <p>Only that one click is touched. Clicking the inner face must stay replaceable, or the
	 * placement rule cannot double the slab up; and a horizontal face has no "outer" one at all, so
	 * a plain slab stays replaceable from above and below exactly like a vanilla one.
	 *
	 * <p>The target is a constructor, which captures no {@code this} to pass on, so the handler
	 * takes the constructor's own parameters after the modified value instead of the clicked state
	 * the call was made on. Mixin insists on that ordering and rejects anything else with
	 * {@code has an invalid signature. Found unexpected argument type ... expected net.minecraft.world.level.Level}.
	 */
	@ModifyExpressionValue(
		method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;"
			+ "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;"
			+ "Lnet/minecraft/world/phys/BlockHitResult;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;"
				+ "canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z"
		)
	)
	private boolean morebuildingblocks$outerFaceIsSolid(final boolean original, final Level level,
			final Player player, final InteractionHand hand, final ItemStack stack,
			final BlockHitResult hitResult) {
		BlockState clicked = level.getBlockState(hitResult.getBlockPos());

		if (!VerticalSlab.isVertical(clicked)) {
			return original;
		}

		Direction face = hitResult.getDirection();
		return face == VerticalSlab.outward(clicked) ? false : original;
	}
}
