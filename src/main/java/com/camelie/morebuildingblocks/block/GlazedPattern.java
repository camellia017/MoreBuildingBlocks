package com.camelie.morebuildingblocks.block;

/**
 * Marks the stair and slab blocks cut out of a glazed terracotta block.
 *
 * <p>A glazed terracotta texture is directional: which pixel is at the top of a face depends on the
 * direction the block was placed from. Vanilla turns the whole model with a blockstate {@code y}
 * rotation to keep the pattern upright. A stair or a slab cannot do that, because their model is not
 * a full cube - rotating it would swing the cut corner round with the texture - so the mod instead
 * records the placement direction in the {@link MBBlockStateProperties#GLAZED_FACING} property and
 * gives each of its four values its own model, with the turn baked into the geometry.
 *
 * <p>That property therefore only exists on the blocks carrying this marker.
 * {@link com.camelie.morebuildingblocks.mixin.SlabBlockMixin} applies to every slab in the game, so
 * it asks for the marker before it declares or writes the property: plain slabs - vanilla's hundred
 * and the sixteen plain terracotta ones - are left exactly as vanilla defines them.
 */
public interface GlazedPattern {
}
