package com.camelie.morebuildingblocks.block;

import net.minecraft.core.BlockPos;

/**
 * The two block positions a placement context carries, which vanilla only partly hands out.
 *
 * <p>{@code BlockPlaceContext.getClickedPos()} answers one question - which block will be built in -
 * and picks between two stored positions to do it: the block the click landed on, and the block one
 * step along the clicked face, which is where a placement goes when it is treated as replacing the
 * block it hit. That single answer is all a vanilla block needs, but a slab needs to know which of
 * the two it is looking at, because the half it fills is read off the face that was clicked and the
 * click point is only meaningful against the block the click actually landed on.
 *
 * <p>Neither position is readable from outside: the first belongs to the superclass, whose accessor
 * is {@code protected}, and the second is a {@code private final} field that a mixin cannot shadow
 * (a {@code @Shadow} field is searched for in the target class alone, so a field declared by the
 * target's superclass is reported as {@code @Shadow field ... was not located in the target class}).
 * The mixin beside this interface is injected into {@code BlockPlaceContext} itself, which can see
 * both, and answers them here. Callers test with {@code instanceof} and fall back to
 * {@link net.minecraft.world.item.context.UseOnContext#getClickedPos()} when the context is some
 * other implementation.
 */
public interface PlacementClick {
	/**
	 * @return the block the click landed on - the same answer
	 *         {@link net.minecraft.world.item.context.UseOnContext#getClickedPos()} gives whenever
	 *         the click is not being treated as replacing that block.
	 */
	BlockPos morebuildingblocks$hitPos();

	/** @return the block beyond the clicked face, which vanilla calls the relative position. */
	BlockPos morebuildingblocks$relativePos();
}
