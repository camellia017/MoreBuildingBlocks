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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes a click on an upright slab build the wall one block further out, except on the one face
 * of it that means the block is only half finished.
 *
 * <p>{@link BlockPlaceContext} decides which block a placement lands in while it is being
 * constructed, and by the time the block's own {@code getStateForPlacement} runs it is far too late
 * to change its mind: {@code getClickedPos()} already returns either the clicked block or the one
 * beyond it. For an upright slab this mod wants the second answer on every face a player can point
 * at, with one exception.
 *
 * <p>The exception is the <em>open</em> face: the plane closing off the empty half, which is the
 * one face a player sees the slab as - half a block, with the cell beside it empty - and is
 * reported by {@link VerticalSlab#openFace}. Pointing there means completing this slab, so the
 * click stays in the same block for the placement rule to double up. The two thin sides, half a
 * block wide, mean the opposite - they are the wall seen end on, and a click there carries it into
 * the cell they look at - and the boundary at the far end of the filled half, which the wall's own
 * next block would stand against, continues the wall the same way. Those three all leave the click
 * on the cell beyond the clicked face.
 *
 * <p>That decision is written here rather than filtered out of the vanilla one. The slab answers
 * {@code canBeReplaced} for reasons of its own - a bottom half clicked on a side face above its
 * centre is refused, for one - and such a refusal is indistinguishable from "this is not the open
 * face" once it has become the {@code replaceClicked} flag, so leaving the vanilla answer in place
 * anywhere keeps the click on the slab and lets the placement land inside the half that is already
 * full. That reads as the slab quietly turning into a solid cube, which is the bug this replaces.
 *
 * <p>Two ways round that were not usable. {@code relativePos} is {@code private final}, and a
 * Mixin handler is not the field's initialiser, so writing it throws
 * {@code IllegalAccessError: Update to non-static final field ... attempted from a different method}.
 * What is left is the constructor's own decision, so this rewrites that: {@code replaceClicked} is
 * the finished answer to "does the placement land in the clicked block", it is written as the
 * constructor's last act and only read afterwards, so setting it at the tail changes the outcome
 * without touching the bytecode in between. Leaving it false makes {@code getClickedPos()} the
 * block beyond the clicked face - the {@code relativePos} the constructor computed one line earlier
 * - which is exactly where the wall continues.
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

	/**
	 * Whether the placement lands in the clicked block rather than the one beyond its face.
	 *
	 * <p>Also a field of the target, and not final, so {@code @Shadow} both finds it and lets the
	 * handler below write to it.
	 */
	@Shadow
	private boolean replaceClicked;

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
	 * Decides, for every click on an upright slab, whether the placement lands in the slab's own
	 * block - which it does only on the open face of one that is still half empty.
	 *
	 * <p>A double slab fills its whole block and has no empty half left, so no click on it completes
	 * it any more: its faces start a new block like the faces of any plain block do, and the
	 * placement has to move out of the cell it landed on for that to happen. Vanilla already refuses
	 * to replace a double slab, so this leaves that refusal alone; the value written here is the one
	 * {@code getClickedPos} reads.
	 *
	 * <p>Replacing the constructor's own {@code canBeReplaced} answer rather than filtering it is
	 * what makes this work. The vanilla slab refuses a replacement for reasons of its own - a
	 * bottom half clicked on a side face above its centre, for one - and those refusals have
	 * nothing to do with the mod's rule, yet the constructor's {@code replaceClicked} ends up
	 * false for both kinds of refusal. A handler that could only veto the value left the click on
	 * the slab in exactly those cases, which is the bug this replaces.
	 *
	 * <p>{@code replaceClicked} is written as the constructor's last act and read by
	 * {@code getClickedPos} afterwards, so setting it at the tail rewrites the finished decision
	 * without touching the bytecode in between.
	 */
	@Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;"
		+ "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;"
		+ "Lnet/minecraft/world/phys/BlockHitResult;)V", at = @At("TAIL"))
	private void morebuildingblocks$decideReplaceClicked(final Level level, final Player player,
			final InteractionHand hand, final ItemStack stack, final BlockHitResult hitResult,
			final CallbackInfo info) {
		BlockState clicked = level.getBlockState(hitResult.getBlockPos());

		if (VerticalSlab.isVertical(clicked) && !VerticalSlab.isDouble(clicked)) {
			this.replaceClicked = hitResult.getDirection() == VerticalSlab.openFace(clicked);
		}
	}
}
