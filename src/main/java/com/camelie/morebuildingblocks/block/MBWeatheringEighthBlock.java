package com.camelie.morebuildingblocks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The eighth of a block the game weathers - copper, cut into eighths - which weathers with it.
 *
 * <p>This is vanilla's {@code WeatheringCopperFullBlock} for an eighth: it is the same
 * {@link WeatheringCopper}, so it ages on the same clock, in the same stages, at the same rate as
 * the copper it was cut from, and it counts as a neighbour the game's own copper ages against. The
 * stage it starts at is read off the block it was cut from, not written down here.</p>
 *
 * <p>A cell of eighths turns all at once, in every direction the game can turn it: whatever the
 * game replaces the block with - the next stage, the previous stage, the waxed eighth or the
 * unwaxed one - is copied onto the state that is already there, property by property, so all eight
 * switches and the facing survive. That is what {@code withPropertiesOf} does for the game's own
 * copper with its {@code waterlogged} and {@code facing}, and it is why nothing here has to know
 * about {@link MBEighthProperties}: the eighth of the next stage is an eighth with the same eight
 * switches, so they are all carried over.</p>
 *
 * <p>The pairs the game turns are registered in {@code MBEighthWeathering}, through the same maps
 * the game's own copper is in, so nothing in this class names a material.</p>
 */
public class MBWeatheringEighthBlock extends MBEighthBlock implements WeatheringCopper {
	/** The stage of the base block this eighth was cut from. */
	private final WeatheringCopper.WeatherState weatherState;

	/**
	 * @param weatherState the stage of the base block this eighth was cut from, which is the stage
	 *                     this eighth is at, and is what its neighbours age against.
	 * @param slab the slab of the same material, or {@code null}.
	 * @param stairs the stairs of the same material, or {@code null}.
	 */
	public MBWeatheringEighthBlock(final WeatheringCopper.WeatherState weatherState, final @Nullable Block slab, final @Nullable Block stairs, final Properties properties) {
		super(slab, stairs, properties);
		this.weatherState = weatherState;
	}

	@Override
	protected void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		this.changeOverTime(state, level, pos, random);
	}

	@Override
	protected boolean isRandomlyTicking(final BlockState state) {
		return WeatheringCopper.getNext(state.getBlock()).isPresent();
	}

	@Override
	public WeatheringCopper.WeatherState getAge() {
		return this.weatherState;
	}
}
