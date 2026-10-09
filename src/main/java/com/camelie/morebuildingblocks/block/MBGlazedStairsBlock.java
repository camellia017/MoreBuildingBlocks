package com.camelie.morebuildingblocks.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stair cut out of a glazed terracotta block.
 *
 * <p>Vanilla's {@link StairBlock} is used unchanged for every plain stair. A glazed stair needs one
 * more property than vanilla gives a stair - {@link MBBlockStateProperties#GLAZED_FACING}, the
 * direction the player was looking when they placed it - because its pattern has to be turned to
 * match the glazed terracotta block that would have been placed instead.
 *
 * <p>The class does not declare that property itself. {@code createBlockStateDefinition} runs from
 * the {@code Block} constructor, i.e. before a subclass constructor body has had a chance to run, so
 * an override here would add a second copy after the mixin had already added one, and a state
 * definition refuses duplicates by name. {@link
 * com.camelie.morebuildingblocks.mixin.StairBlockMixin} therefore declares it, for the stairs
 * carrying this marker and for no others.
 */
public class MBGlazedStairsBlock extends StairBlock implements GlazedPattern {

	/**
	 * The state vanilla builds a stair from: the base block's own default state. Kept as a named
	 * method because it is needed while the block is being constructed, before any instance exists.
	 */
	public static BlockState baseState(final Block base) {
		return base.defaultBlockState();
	}

	public MBGlazedStairsBlock(final BlockState baseState, final Properties properties) {
		super(baseState, properties);
	}
}
