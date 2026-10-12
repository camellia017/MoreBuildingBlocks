package com.camelie.morebuildingblocks.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.math.Quadrant;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.BlockStateResolver;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBPaneProperties;

/**
 * Draws a vanilla pane out of the boxes its twelve switches ask for.
 *
 * <p>Vanilla's own blockstate files cannot describe this mod's panes. They name a
 * post and one arm per connected side, each of them full height, while a pane of this
 * mod lays out twelve corners - a lower and an upper one for each of the four sides,
 * and one for each of the four diagonals - and carries the twelve switches that say
 * which of them to show in its own blockstate. Restating vanilla's 26 pane files would
 * also mean restating every model they name, so the boxes are written once by datagen -
 * one template each, plus one copy per vanilla pane for that pane's sprites - and which
 * of them a state shows is resolved here, the way {@link MBVanillaSlabModelPlugin}
 * supplies the upright states of vanilla slabs.
 *
 * <p>Every one of those corners is seven pixels of its quadrant or of its half, two
 * pixels thick, laid out the way vanilla lays out its own pane: seven of corner, two of
 * edge, seven of corner. The two pixels between one corner and the next belong to both
 * of them, so each of those boxes is shown for every corner that asks for it - the band
 * of a side and the two wide faces that lead out of it, the column of a half, and the
 * middle the twelve corners of the pane meet in - and a pane with two corners side by
 * side keeps no hole through the joint.
 *
 * <p>None of those shared boxes is shown while a neighbour covers it, because glass
 * does not hide what stands behind it: a face drawn inside the corner of its own half,
 * inside the corner beside it, or between the two corners of its half is seen through
 * them as a seam that is not there. So a shared box is one box per half and then one
 * model per face of it, and each of them answers to every corner of its side or half.
 *
 * <p>The models are handed to vanilla's own {@link MultiPartModel}, one condition per
 * model. It bakes each model once for the block and remembers the set a state selected,
 * so the 4096 states of a pane cost what sixty models cost and not what 4096 of them
 * would.
 *
 * <p>Once any box is shown, the pane's own post is not: the boxes are the outline and
 * the collision shape of the pane, and this mod draws the post only while the pane is
 * connected to nothing - which is the state vanilla's own files draw their post in
 * too, so a lone pane looks the same either way.
 *
 * <p>Only vanilla's panes are taken over. A pane from another mod carries the same
 * switches - the mixin adds them to every {@code IronBarsBlock} - but nothing ever
 * sets them, so the file that mod shipped still describes it.
 */
public class MBPaneModelPlugin implements ClientModInitializer {
	/** The namespace of the panes this plugin resolves. */
	private static final String VANILLA_NAMESPACE = "minecraft";

	/**
	 * The box of each of the twelve switches, in the order of
	 * {@link #PIECE_PROPERTIES}.
	 *
	 * <p>Every switch is a corner of the pane, and the eight upright ones are seven
	 * pixels of a half by seven of a quadrant. The names are the models
	 * {@code MBModelProvider} writes. A corner exists as a northern and a southern box
	 * only, so the eastern and western corners name the northern and southern box again
	 * and are turned a quarter in {@link #piece}.
	 */
	private static final List<String> PIECE_TEMPLATES = List.of(
			"pane_side_corner_lower",
			"pane_side_corner_upper",
			"pane_side_corner_lower",
			"pane_side_corner_upper",
			"pane_side_corner_lower_alt",
			"pane_side_corner_upper_alt",
			"pane_side_corner_lower_alt",
			"pane_side_corner_upper_alt",
			"pane_corner",
			"pane_corner_se",
			"pane_corner_sw",
			"pane_corner_nw");

	/**
	 * Whether the box of a piece is one of the northern or southern pair, which has to
	 * be turned a quarter to show an eastern or western corner.
	 */
	private static final List<Boolean> PIECE_TURNED = List.of(
			false,
			false,
			true,
			true,
			false,
			false,
			true,
			true,
			false,
			false,
			false,
			false);

	/**
	 * The switch each box answers to: the lower and upper corner of north, east, south
	 * and west in turn, then the four corners of the diagonals.
	 *
	 * <p>The lower four are vanilla's own connection properties - the pane mixin adds
	 * the other eight and leaves those where they were - so the lower corner of a side
	 * is drawn exactly when the pane is connected that way.
	 */
	private static final List<Property<Boolean>> PIECE_PROPERTIES = List.of(
			MBPaneProperties.lower(Direction.NORTH),
			MBPaneProperties.upper(Direction.NORTH),
			MBPaneProperties.lower(Direction.EAST),
			MBPaneProperties.upper(Direction.EAST),
			MBPaneProperties.lower(Direction.SOUTH),
			MBPaneProperties.upper(Direction.SOUTH),
			MBPaneProperties.lower(Direction.WEST),
			MBPaneProperties.upper(Direction.WEST),
			MBPaneProperties.CORNER_NORTH_EAST,
			MBPaneProperties.CORNER_SOUTH_EAST,
			MBPaneProperties.CORNER_SOUTH_WEST,
			MBPaneProperties.CORNER_NORTH_WEST);

	/**
	 * The two pixels of band one side of a pane owns, and the corners that share them.
	 *
	 * <p>{@code first} is the level corner that reaches along {@code firstFace} and
	 * {@code second} the one reaching along {@code secondFace}; those are the faces
	 * the two pixels lead to, and so the names of the models that may close them. The
	 * two upright corners of the side, above and below those pixels, share them too.
	 */
	private record SharedSide(String name, Direction direction, Property<Boolean> lower, Property<Boolean> upper,
			Property<Boolean> first, String firstFace, Property<Boolean> second, String secondFace) {
		/** Whether a state shows the upright corner of this side in one half. */
		private boolean plated(BlockState state, boolean half) {
			return shows(state, half ? this.upper : this.lower);
		}

		/** Whether a state shows any of the four corners that share this side's band. */
		private boolean cornered(BlockState state) {
			return shows(state, first) || shows(state, second) || shows(state, lower) || shows(state, upper);
		}
	}

	/** The four sides along which a corner shares its two pixels with its neighbour. */
	private static final SharedSide NORTH = new SharedSide("north", Direction.NORTH,
			MBPaneProperties.lower(Direction.NORTH), MBPaneProperties.upper(Direction.NORTH),
			MBPaneProperties.CORNER_NORTH_EAST, "east", MBPaneProperties.CORNER_NORTH_WEST, "west");
	private static final SharedSide SOUTH = new SharedSide("south", Direction.SOUTH,
			MBPaneProperties.lower(Direction.SOUTH), MBPaneProperties.upper(Direction.SOUTH),
			MBPaneProperties.CORNER_SOUTH_EAST, "east", MBPaneProperties.CORNER_SOUTH_WEST, "west");
	private static final SharedSide EAST = new SharedSide("east", Direction.EAST,
			MBPaneProperties.lower(Direction.EAST), MBPaneProperties.upper(Direction.EAST),
			MBPaneProperties.CORNER_NORTH_EAST, "north", MBPaneProperties.CORNER_SOUTH_EAST, "south");
	private static final SharedSide WEST = new SharedSide("west", Direction.WEST,
			MBPaneProperties.lower(Direction.WEST), MBPaneProperties.upper(Direction.WEST),
			MBPaneProperties.CORNER_NORTH_WEST, "north", MBPaneProperties.CORNER_SOUTH_WEST, "south");
	private static final List<SharedSide> SHARED_SIDES = List.of(NORTH, SOUTH, EAST, WEST);

	/**
	 * The boxes the corners share: the two pixels of band on each of the four sides,
	 * the two pixels of column at the end of each of those, and the two pixels where
	 * the band and the column cross - one box per half and one model per face of it.
	 *
	 * <p>The names are the models {@code MBModelProvider} writes. None of them is
	 * turned: each already runs along the axis it belongs to.
	 */
	private static final List<SharedBox> SHARED_BOXES = sharedBoxes();

	/** One box the corners share, and the states that show it. */
	private record SharedBox(String template, Predicate<BlockState> shows) {
	}

	private static List<SharedBox> sharedBoxes() {
		List<SharedBox> boxes = new ArrayList<>();
		boolean[] halves = { false, true };

		for (SharedSide side : SHARED_SIDES) {
			for (boolean half : halves) {
				String edge = "pane_edge_" + side.name() + (half ? "_upper" : "_lower");

				// The two pixels of band that lead to every corner of this side. The
				// corner of this half covers their end, so that end is a model of its
				// own and waits for the corner to be away.
				boxes.add(new SharedBox(edge, side::cornered));
				boxes.add(new SharedBox(edge + (half ? "_up" : "_down"),
						state -> side.cornered(state) && !side.plated(state, half)));

				// The wide face that leads to one of the two level corners. A corner
				// that is on closes the seam with its own plate, so the face is shown
				// while that corner is away, whichever corner the other face answers
				// to - and only while the band itself is there, or a pane with no
				// corner on this side shows two pixels of glass beside its collision.
				boxes.add(new SharedBox(edge + "_" + side.firstFace(),
						state -> side.cornered(state) && !shows(state, side.first())));
				boxes.add(new SharedBox(edge + "_" + side.secondFace(),
						state -> side.cornered(state) && !shows(state, side.second())));
			}
		}

		for (boolean half : halves) {
			String column = "pane_column_" + (half ? "upper" : "lower");
			String middle = "pane_middle_" + (half ? "upper" : "lower");

			// The two pixels of column of this half. Every upright corner of the half
			// asks for them, and each of its four sides is hidden by the corner it
			// leads to. Its end has no corner behind it - the pane ends at the face of
			// the block - so nothing but an absent column hides that.
			boxes.add(new SharedBox(column + (half ? "_up" : "_down"), state -> column(state, half)));

			for (SharedSide side : SHARED_SIDES) {
				boxes.add(new SharedBox(column + "_" + side.name(),
						state -> column(state, half) && !side.plated(state, half)));
			}

			// The floor or the ceiling of the crossing, shown while the column of this
			// half leaves a gap there at all, and its four sides, which the band of the
			// side that covers them turns away with the corners it answers to.
			boxes.add(new SharedBox(middle + (half ? "_up" : "_down"),
					state -> !bare(state) && !column(state, half)));

			for (SharedSide side : SHARED_SIDES) {
				boxes.add(new SharedBox(middle + "_" + side.name(),
						state -> !bare(state) && !side.cornered(state)));
			}
		}

		return boxes;
	}

	/** Whether a state shows the column of one half, which any upright corner asks for. */
	private static boolean column(BlockState state, boolean half) {
		for (SharedSide side : SHARED_SIDES) {
			if (side.plated(state, half)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * What a lone glass pane shows: vanilla's post and the four faces it closes it
	 * with, the five models vanilla's own file names for a pane connected to nothing.
	 */
	private static final List<StickPart> GLASS_STICK = List.of(
			new StickPart("_post", Quadrant.R0),
			new StickPart("_noside", Quadrant.R0),
			new StickPart("_noside_alt", Quadrant.R0),
			new StickPart("_noside_alt", Quadrant.R90),
			new StickPart("_noside", Quadrant.R270));

	/**
	 * What a lone bars block shows: vanilla draws the ends of its bars at every state and
	 * their post only while they are connected to nothing.
	 */
	private static final List<StickPart> BARS_STICK = List.of(
			new StickPart("_post", Quadrant.R0));

	/**
	 * The ends of the bars, which vanilla draws at every state: their two pixels at the floor
	 * and the ceiling of the block, in the middle of it.
	 */
	private static final StickPart BARS_POST_ENDS = new StickPart("_post_ends", Quadrant.R0);

	/** One of the models a pane connected to nothing is drawn from, and its turn. */
	private record StickPart(String suffix, Quadrant rotation) {
	}

	@Override
	public void onInitializeClient() {
		ModelLoadingPlugin.register(context -> {
			for (Block block : BuiltInRegistries.BLOCK) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(block);

				if (block instanceof IronBarsBlock && VANILLA_NAMESPACE.equals(id.getNamespace())) {
					context.registerBlockStateResolver(block, MBPaneModelPlugin::resolve);
				}
			}
		});
	}

	/**
	 * Gives every state of one pane the same twelve boxes, each with the switch that
	 * shows it.
	 */
	private static void resolve(BlockStateResolver.Context context) {
		Block block = context.block();
		BlockStateModel.UnbakedRoot panes = new MultiPartModel.Unbaked(
				selectors(BuiltInRegistries.BLOCK.getKey(block)));

		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			context.setModel(state, panes);
		}
	}

	/**
	 * The boxes of a pane: each of the twelve switches, the boxes the corners share,
	 * and what the pane shows while all twelve are off.
	 */
	private static List<MultiPartModel.Selector<BlockStateModel.Unbaked>> selectors(Identifier paneId) {
		List<MultiPartModel.Selector<BlockStateModel.Unbaked>> selectors = new ArrayList<>();
		String path = paneId.getPath();
		boolean bars = path.endsWith("_bars");
		String bare = bars ? path.replaceFirst("^waxed_", "") : path;

		for (int index = 0; index < PIECE_PROPERTIES.size(); index++) {
			Property<Boolean> property = PIECE_PROPERTIES.get(index);
			BlockStateModel.Unbaked piece = piece(paneId, index);
			selectors.add(new MultiPartModel.Selector<>(state -> shows(state, property), piece));
		}

		for (SharedBox box : SHARED_BOXES) {
			if (bars && acrossMiddle(box.template())) {
				continue;
			}

			selectors.add(new MultiPartModel.Selector<>(box.shows(), sharedPart(paneId, box)));
		}

		for (StickPart part : bars ? BARS_STICK : GLASS_STICK) {
			BlockStateModel.Unbaked model = stickPart(bare, part);
			selectors.add(new MultiPartModel.Selector<>(MBPaneModelPlugin::bare, model));
		}

		if (bars) {
			selectors.add(new MultiPartModel.Selector<>(state -> true, stickPart(bare, BARS_POST_ENDS)));
		}

		return selectors;
	}

	/**
	 * Whether one of the shared boxes lies across the middle of the block: the sides of the column
	 * and of the crossing, which carry the two pixels on the far side of the plane an arm stands in.
	 * The bars draw those two pixels from the arms, which reach the middle of the block, so the
	 * copies of these boxes are left to the panes, which keep them in the glass they belong to.
	 */
	private static boolean acrossMiddle(String template) {
		return (template.startsWith("pane_column_") || template.startsWith("pane_middle_"))
				&& !template.endsWith("_down") && !template.endsWith("_up");
	}

	/**
	 * One box of one pane: the copy datagen wrote for that pane, turned a quarter when
	 * the box it names faces north or south but the piece faces east or west.
	 */
	private static BlockStateModel.Unbaked piece(Identifier paneId, int index) {
		Identifier model = Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID,
				"block/" + PIECE_TEMPLATES.get(index) + "_" + paneId.getPath());
		Variant variant = new Variant(model);

		if (PIECE_TURNED.get(index)) {
			variant = variant.withYRot(Quadrant.R90);
		}

		return new SingleVariant.Unbaked(variant);
	}

	/** One shared box of one pane: the copy datagen wrote for that pane. */
	private static BlockStateModel.Unbaked sharedPart(Identifier paneId, SharedBox box) {
		Identifier model = Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID,
				"block/" + box.template() + "_" + paneId.getPath());

		return new SingleVariant.Unbaked(new Variant(model));
	}

	/** One model a pane connected to nothing is drawn from, under vanilla's namespace. */
	private static BlockStateModel.Unbaked stickPart(String path, StickPart part) {
		Variant variant = new Variant(
				Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/" + path + part.suffix()));

		if (part.rotation() != Quadrant.R0) {
			variant = variant.withYRot(part.rotation());
		}

		return new SingleVariant.Unbaked(variant);
	}

	/** Whether a state shows one of the pane's switches. */
	private static boolean shows(BlockState state, Property<Boolean> property) {
		return state.hasProperty(property) && state.getValue(property);
	}

	/** Whether a state shows none of the twelve boxes. */
	private static boolean bare(BlockState state) {
		for (Property<Boolean> property : PIECE_PROPERTIES) {
			if (shows(state, property)) {
				return false;
			}
		}

		return true;
	}
}
