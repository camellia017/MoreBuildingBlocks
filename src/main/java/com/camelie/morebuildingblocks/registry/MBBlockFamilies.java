package com.camelie.morebuildingblocks.registry;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBGlazedSlabBlock;
import com.camelie.morebuildingblocks.block.MBGlazedStairsBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the stair and slab form of any base block, inheriting every property the user asked to be
 * inherited: hardness, blast resistance, sound group, flammability, map colour and the note block
 * instrument all travel through {@link BlockBehaviour.Properties#ofFullCopy(BlockBehaviour)}.
 *
 * <p>Vanilla writes these two helpers privately inside {@code Blocks}, so the mod keeps its own
 * copy. Stairs use {@code ofFullCopy} (the modern path, as used by the 26.3 sulfur family); slabs
 * keep the historical {@code ofLegacyCopy} behaviour so their light/collision semantics match
 * every other vanilla slab.</p>
 */
public final class MBBlockFamilies {

	/**
	 * Vanilla declares this predicate privately in {@code Blocks}; it is re-declared here so copied
	 * blocks keep vanilla's near-plane view blocking test.
	 */
	private static final BlockBehaviour.StateArgumentPredicate<AABB> NEAR_PLANE_INTERSECTS_OUTLINE = (state, level, pos, nearPlaneBox) -> {
		for (AABB outlineBox : state.getOcclusionShape().toAabbs()) {
			if (outlineBox.move(pos).intersects(nearPlaneBox)) {
				return true;
			}
		}
		return false;
	};

	private static final List<Family> FAMILIES = new ArrayList<>();

	private MBBlockFamilies() {
	}

	/**
	 * A base block plus the two derived forms generated for it.
	 *
	 * @param glazed whether the base block is glazed terracotta, whose directional pattern cannot
	 *               use the flat texture mapping vanilla's slab and stair templates apply
	 */
	public record Family(String basePath, Block base, Block stairs, Block slab, boolean glazed) {

		/** Path of the stair block id, e.g. {@code red_terracotta_stairs}. */
		public String stairsPath() {
			return this.basePath + "_stairs";
		}

		/** Path of the slab block id, e.g. {@code red_terracotta_slab}. */
		public String slabPath() {
			return this.basePath + "_slab";
		}
	}

	/**
	 * Registers {@code <basePath>_stairs} and {@code <basePath>_slab} for an already registered
	 * base block and records the trio for datagen, crafting recipes and the creative tab.
	 */
	public static Family create(final String basePath, final Block base, final boolean glazed) {
		Block stairs = registerStairs(basePath + "_stairs", base, glazed);
		Block slab = registerSlab(basePath + "_slab", base, glazed);
		Family family = new Family(basePath, base, stairs, slab, glazed);
		FAMILIES.add(family);
		return family;
	}

	/** Every family registered so far, in registration order. */
	public static List<Family> families() {
		return Collections.unmodifiableList(FAMILIES);
	}

	/**
	 * A glazed stair remembers the direction it was placed from, so its pattern can be turned to
	 * match the glazed terracotta block the player would have got instead. That one property is
	 * declared by {@link MBGlazedStairsBlock}; every other stair is vanilla's own {@link StairBlock},
	 * with vanilla's own states.
	 */
	private static Block registerStairs(final String name, final Block base, final boolean glazed) {
		BlockBehaviour.Properties properties =
			BlockBehaviour.Properties.ofFullCopy(base).isViewBlocking(NEAR_PLANE_INTERSECTS_OUTLINE);

		if (glazed) {
			return MBRegistry.register(
				name,
				blockProperties -> new MBGlazedStairsBlock(MBGlazedStairsBlock.baseState(base), blockProperties),
				properties
			);
		}

		BlockState baseState = base.defaultBlockState();
		return MBRegistry.register(name, blockProperties -> new StairBlock(baseState, blockProperties), properties);
	}

	/**
	 * Vanilla marks {@code ofLegacyCopy} deprecated but still uses it for every slab it registers
	 * (`Blocks.registerSlab`), so the mod deliberately mirrors that call and silences the warning.
	 *
	 * <p>A glazed slab gets {@link MBGlazedSlabBlock}, which declares the pattern direction. Both
	 * kinds stand upright either way: that half of the behaviour comes from {@link
	 * com.camelie.morebuildingblocks.mixin.SlabBlockMixin}, which covers every slab in the game.
	 */
	@SuppressWarnings("deprecation")
	private static Block registerSlab(final String name, final Block base, final boolean glazed) {
		BlockBehaviour.Properties properties =
			BlockBehaviour.Properties.ofLegacyCopy(base).isViewBlocking(NEAR_PLANE_INTERSECTS_OUTLINE);

		if (glazed) {
			return MBRegistry.register(name, MBGlazedSlabBlock::new, properties);
		}

		return MBRegistry.register(name, SlabBlock::new, properties);
	}
}
