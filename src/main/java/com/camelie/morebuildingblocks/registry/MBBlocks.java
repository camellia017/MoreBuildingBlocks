package com.camelie.morebuildingblocks.registry;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The 33 terracotta blocks the mod derives stair and slab forms from.
 *
 * <p>Every base block is registered inside the {@code morebuildingblocks} namespace but copies the
 * exact {@link BlockBehaviour.Properties} chain vanilla uses, so hardness, blast resistance, sound
 * group, map colour, note block instrument and push reaction all match the vanilla original:</p>
 *
 * <ul>
 *   <li>{@code terracotta} — {@code of().mapColor(COLOR_ORANGE).instrument(BASEDRUM)
 *       .requiresCorrectToolForDrops().strength(1.25F, 4.2F)} (mirrors {@code Blocks.java:3066})</li>
 *   <li>{@code <color>_terracotta} — {@code .mapColor(color.getTerracottaColor())} with the same
 *       strength as plain terracotta (mirrors {@code Blocks.java:2970})</li>
 *   <li>{@code <color>_glazed_terracotta} — {@code .mapColor(color.getMapColor())
 *       .requiresCorrectToolForDrops().strength(1.4F).pushReaction(PUSH)} (mirrors
 *       {@code Blocks.java:3713})</li>
 * </ul>
 *
 * <p>The 32 coloured names are taken straight from vanilla's own {@link ColorCollection} naming
 * helper, which is what produces {@code white_terracotta} / {@code white_glazed_terracotta}.</p>
 */
public final class MBBlocks {

	/** {@code morebuildingblocks:terracotta} — the undyed base block. */
	public static final Block TERRACOTTA;

	/** {@code morebuildingblocks:<color>_terracotta} for all 16 dye colours. */
	public static final ColorCollection<Block> DYED_TERRACOTTA;

	/** {@code morebuildingblocks:<color>_glazed_terracotta} for all 16 dye colours. */
	public static final ColorCollection<Block> GLAZED_TERRACOTTA;

	private static final List<MBBlockFamilies.Family> FAMILIES = new ArrayList<>();

	private MBBlocks() {
	}

	static {
		TERRACOTTA = MBRegistry.register(
			"terracotta",
			BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE)
				.instrument(NoteBlockInstrument.BASEDRUM)
				.requiresCorrectToolForDrops()
				.strength(1.25F, 4.2F)
		);
		DYED_TERRACOTTA = registerColored("terracotta", color -> BlockBehaviour.Properties.of()
			.mapColor(color.getTerracottaColor())
			.instrument(NoteBlockInstrument.BASEDRUM)
			.requiresCorrectToolForDrops()
			.strength(1.25F, 4.2F));
		GLAZED_TERRACOTTA = registerColored("glazed_terracotta", color -> BlockBehaviour.Properties.of()
			.mapColor(color.getMapColor())
			.instrument(NoteBlockInstrument.BASEDRUM)
			.requiresCorrectToolForDrops()
			.strength(1.4F)
			.pushReaction(PushReaction.PUSH));

		MBBlockFamilies.create("terracotta", TERRACOTTA);
		ColorCollection.zipApply(ColorCollection.VALUES, DYED_TERRACOTTA, (color, block) -> MBBlockFamilies.create(color.getName() + "_terracotta", block));
		ColorCollection.zipApply(ColorCollection.VALUES, GLAZED_TERRACOTTA, (color, block) -> MBBlockFamilies.create(color.getName() + "_glazed_terracotta", block));

		FAMILIES.addAll(MBBlockFamilies.families());
		MoreBuildingBlocks.LOGGER.info("Registered {} terracotta blocks and {} stair/slab pairs", 33, FAMILIES.size() * 2);
	}

	/** Every terracotta family (base block + stairs + slab), in registration order. */
	public static List<MBBlockFamilies.Family> families() {
		return Collections.unmodifiableList(FAMILIES);
	}

	/** Forces class initialisation; call once from the mod initialiser. */
	public static void bootstrap() {
		// The static initialiser does all the work.
	}

	private static ColorCollection<Block> registerColored(final String suffix, final java.util.function.Function<DyeColor, BlockBehaviour.Properties> propertyFactory) {
		// DyeColor's own name is the bare colour ("white", "light_blue", ...), so the
		// form suffix is appended exactly once and the ids read as they do in vanilla:
		// white_terracotta, light_blue_glazed_terracotta.
		return ColorCollection.zipMap(ColorCollection.VALUES, ColorCollection.NAMES, (color, name) -> MBRegistry.register(name + "_" + suffix, propertyFactory.apply(color)));
	}
}
