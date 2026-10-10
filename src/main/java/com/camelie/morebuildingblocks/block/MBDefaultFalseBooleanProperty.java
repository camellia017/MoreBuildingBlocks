package com.camelie.morebuildingblocks.block;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A {@code Boolean} block state property whose values run {@code false} first, so that the default
 * state of a block carrying it is the one where the flag is off.
 *
 * <p>Vanilla's {@link net.minecraft.world.level.block.state.properties.BooleanProperty} declares its
 * values as {@code [true, false]}, and a block's default state is {@code StateDefinition.any()} -
 * the cartesian product of every property's values, taken from the first entry. A vanilla boolean
 * flag therefore defaults to {@code true}. That is right for the flags vanilla adds itself, but
 * wrong for a flag that switches on an extra piece: a pane placed after the mod is installed would
 * come out with its four corner pieces and four upper arm halves already enabled, so every read
 * would have to pin them back by hand.
 *
 * <p>Declaring the values as {@code [false, true]} moves that default to {@code false} instead, at
 * the source, and it makes the default state of a vanilla block byte for byte the state vanilla
 * would have given it.
 */
public final class MBDefaultFalseBooleanProperty extends Property<Boolean> {
	/** {@code false} first, {@code true} second: {@code any()} reads the first entry. */
	private static final List<Boolean> VALUES = List.of(false, true);

	private MBDefaultFalseBooleanProperty(final String name) {
		super(name, Boolean.class);
	}

	public static MBDefaultFalseBooleanProperty create(final String name) {
		return new MBDefaultFalseBooleanProperty(name);
	}

	@Override
	public List<Boolean> getPossibleValues() {
		return VALUES;
	}

	@Override
	public Optional<Boolean> getValue(final String name) {
		return switch (name) {
			case "true" -> Optional.of(true);
			case "false" -> Optional.of(false);
			default -> Optional.empty();
		};
	}

	@Override
	public String getName(final Boolean value) {
		return value.toString();
	}

	@Override
	public int getInternalIndex(final Boolean value) {
		return value ? 1 : 0;
	}
}
