package com.camelie.morebuildingblocks.registry;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The 33 <em>vanilla</em> terracotta blocks the mod derives stair and slab forms from.
 *
 * <p>The mod used to register its own copies of these blocks, which was wrong: it duplicated blocks
 * the game already ships, so two visually identical blocks existed, only one of which had stairs
 * and slabs, and the mod's copies appeared in the creative tab as if they were new content. The
 * bases are now referenced where they already live - {@link Blocks#TERRACOTTA},
 * {@link Blocks#DYED_TERRACOTTA} and {@link Blocks#GLAZED_TERRACOTTA} - and the mod registers only
 * the 66 stair and slab forms.
 *
 * <p>Referencing rather than copying also inherits every property the user asked to be inherited
 * (hardness, blast resistance, sound group, flammability, map colour, note block instrument) from
 * the real block instead of from a hand-written copy of its property chain: the forms are built
 * with {@code ofFullCopy}/{@code ofLegacyCopy} on the vanilla block itself.
 */
public final class MBBlocks {

	/** The undyed base block, i.e. vanilla {@code minecraft:terracotta}. */
	public static final Block TERRACOTTA = Blocks.TERRACOTTA;

	/** Vanilla {@code minecraft:<color>_terracotta} for all 16 dye colours. */
	public static final ColorCollection<Block> DYED_TERRACOTTA = Blocks.DYED_TERRACOTTA;

	/** Vanilla {@code minecraft:<color>_glazed_terracotta} for all 16 dye colours. */
	public static final ColorCollection<Block> GLAZED_TERRACOTTA = Blocks.GLAZED_TERRACOTTA;

	private static final List<MBBlockFamilies.Family> FAMILIES = new ArrayList<>();

	private MBBlocks() {
	}

	static {
		MBBlockFamilies.create("terracotta", TERRACOTTA, false);
		ColorCollection.zipApply(ColorCollection.VALUES, DYED_TERRACOTTA,
			(color, block) -> MBBlockFamilies.create(color.getName() + "_terracotta", block, false));
		ColorCollection.zipApply(ColorCollection.VALUES, GLAZED_TERRACOTTA,
			(color, block) -> MBBlockFamilies.create(color.getName() + "_glazed_terracotta", block, true));

		FAMILIES.addAll(MBBlockFamilies.families());
		MoreBuildingBlocks.LOGGER.info("Registered {} stair/slab pairs on top of vanilla terracotta blocks", FAMILIES.size() * 2);
	}

	/** Every terracotta family (vanilla base block + this mod's stairs + slab), in registration order. */
	public static List<MBBlockFamilies.Family> families() {
		return Collections.unmodifiableList(FAMILIES);
	}

	/** Forces class initialisation; call once from the mod initialiser. */
	public static void bootstrap() {
		// The static initialiser does all the work.
	}
}
