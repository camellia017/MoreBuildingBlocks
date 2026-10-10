package com.camelie.morebuildingblocks.registry;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBWeatheringEighthBlock;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Puts the eighths of the materials the game weathers - copper, cut into eighths - into the two maps
 * the game weathers its own copper in, which is all it takes for them to weather with it.
 *
 * <p>Nothing here re-implements a rule. The game already owns all four of them:
 * {@link WeatheringCopper#NEXT_BY_BLOCK} says which block a stage turns into as it ages, and which
 * one an axe takes it back to; {@link HoneycombItem#WAXABLES} says which block a honeycomb waxes it
 * into, and which one an axe scrapes the wax back off to; a lightning strike walks {@code
 * WeatheringCopper} blocks back towards their first stage. Every one of those maps is keyed by
 * block, so an eighth enters them as the block it is, next to the copper it was cut from, and the
 * game then turns it exactly as it turns that copper - including when the eighth is a neighbour the
 * game's own copper ages against.</p>
 *
 * <p>{@link OxidizableBlocksRegistry} is Fabric's door into those maps, and it does the other half
 * of the job vanilla does when it builds them: it also adds the axe entries to the game's axe
 * transformer, so scraping rust and scraping wax work on a modded block the way they work on copper,
 * and it refreshes the randomly-ticking cache of both blocks it is handed.</p>
 *
 * <p>Waxed eighths are left out on purpose: wax is what stops a copper block weathering, so a waxed
 * eighth is a plain {@link com.camelie.morebuildingblocks.block.MBEighthBlock} that never ticks -
 * exactly like the waxed copper it was cut from, which the game registers as a plain block too.</p>
 */
public final class MBEighthWeathering {

	private MBEighthWeathering() {
	}

	/** Wires every weathering eighth the mod registered into the game's own weathering maps. */
	public static void bootstrap() {
		int wired = 0;

		for (MBEighths.Material material : MBEighths.materials()) {
			if (material.eighth() instanceof MBWeatheringEighthBlock) {
				registerNextStage(material);
				registerWaxable(material);
				wired++;
			}
		}

		MoreBuildingBlocks.LOGGER.info("Wired {} weathering eighths into the game's copper maps", wired);
	}

	/** Ages the eighth into the stage its base block ages into, and back with an axe. */
	private static void registerNextStage(final MBEighths.Material material) {
		WeatheringCopper.getNext(material.base())
			.map(MBEighths::byBase)
			.ifPresent(next -> OxidizableBlocksRegistry.registerNextStage(material.eighth(), next.eighth()));
	}

	/** Waxes the eighth with a honeycomb into the eighth of the waxed block, and back with an axe. */
	private static void registerWaxable(final MBEighths.Material material) {
		HoneycombItem.getWaxed(material.base().defaultBlockState())
			.map(BlockState::getBlock)
			.map(MBEighths::byBase)
			.ifPresent(waxed -> OxidizableBlocksRegistry.registerWaxable(material.eighth(), waxed.eighth()));
	}
}
