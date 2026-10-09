package com.camelie.morebuildingblocks.block;

import net.minecraft.world.level.block.SlabBlock;

/**
 * The slab cut out of a glazed terracotta block.
 *
 * <p>Vanilla's {@link SlabBlock} is used unchanged for every plain slab, mod or vanilla. Only a
 * glazed slab needs the extra {@link MBBlockStateProperties#GLAZED_FACING} property, so only a
 * glazed slab is given its own class. The upright-placement half of the story does go on every
 * slab, because every slab can stand upright, and that lives in {@link
 * com.camelie.morebuildingblocks.mixin.SlabBlockMixin}.
 *
 * <p>The class does not declare the property itself. {@code createBlockStateDefinition} runs from
 * the {@code Block} constructor, i.e. before a subclass constructor body has had a chance to run, so
 * an override here would add a second copy after the mixin had already added one, and a state
 * definition refuses duplicates by name. {@link
 * com.camelie.morebuildingblocks.mixin.SlabBlockMixin} declares it, for the slabs carrying this
 * marker and for no others - the upright placement, the outline shape and the doubling rule come
 * from that same mixin, shared with every vanilla slab.
 */
public class MBGlazedSlabBlock extends SlabBlock implements GlazedPattern {
	public MBGlazedSlabBlock(final Properties properties) {
		super(properties);
	}
}
