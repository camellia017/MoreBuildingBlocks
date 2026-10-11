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
import com.camelie.morebuildingblocks.block.MBPaneShapes;

/**
 * Draws a vanilla pane out of the boxes its twelve switches ask for.
 *
 * <p>Vanilla's own blockstate files cannot describe this mod's panes. They name a
 * post and one arm per connected side, each of them full height, while a pane of this
 * mod splits every arm into a lower and an upper half, adds a wedge for each diagonal
 * connection, and carries the twelve switches that say which of them to show in its
 * own blockstate. Restating vanilla's 26 pane files would also mean restating every
 * model they name, so the boxes are written once by datagen - one template each, plus
 * one copy per vanilla pane for that pane's sprites - and which of them a state shows
 * is resolved here, the way {@link MBVanillaSlabModelPlugin} supplies the upright
 * states of vanilla slabs.
 *
 * <p>Some of the boxes answer to more than one switch. A corner is seven pixels of
 * quadrant, laid out the way vanilla lays out its own pane, and the two pixels that
 * lead from it to the plate and to the arm belong to both corners on that side, so
 * each of those edges is shown while either of its pair is on; without it a pane with
 * two corners side by side would keep a two pixel hole through the joint. The two
 * pixels where the plate and the arm cross are shown whenever a corner is on at all.
 *
 * <p>The boxes are handed to vanilla's own {@link MultiPartModel}, one condition per
 * box. It bakes each box once for the block and remembers the set a state selected,
 * so the 4096 states of a pane cost what thirteen models cost and not what 4096 of
 * them would.
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
	 * <p>The names are the models {@code MBModelProvider} writes. An arm exists as a
	 * northern and a southern box only, so the eastern and western arms name the
	 * northern and southern box again and are turned a quarter in {@link #piece}.
	 */
	private static final List<String> PIECE_TEMPLATES = List.of(
			"pane_side_lower",
			"pane_side_upper",
			"pane_side_lower",
			"pane_side_upper",
			"pane_side_lower_alt",
			"pane_side_upper_alt",
			"pane_side_lower_alt",
			"pane_side_upper_alt",
			"pane_corner",
			"pane_corner_se",
			"pane_corner_sw",
			"pane_corner_nw");

	/**
	 * Whether the box of a piece is one of the northern or southern pair, which has to
	 * be turned a quarter to show an eastern or western arm.
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
	 * The switch each box answers to: the lower and upper arm of north, east, south
	 * and west in turn, then the four corners.
	 *
	 * <p>The lower four are vanilla's own connection properties - the pane mixin adds
	 * the other eight and leaves those where they were - so an arm is drawn exactly
	 * when the pane is connected that way.
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
	 * The boxes two corners share: the northern and southern two pixels of the plate,
	 * the eastern and western two pixels of the arm, and the two pixels where the
	 * plate and the arm cross.
	 *
	 * <p>The names are the models {@code MBModelProvider} writes. None of them is
	 * turned: each already runs along the axis it belongs to.
	 */
	private static final List<SharedBox> SHARED_BOXES = List.of(
			new SharedBox("pane_edge_north",
					state -> corner(state, MBPaneProperties.CORNER_NORTH_EAST)
							|| corner(state, MBPaneProperties.CORNER_NORTH_WEST)),
			new SharedBox("pane_edge_south",
					state -> corner(state, MBPaneProperties.CORNER_SOUTH_EAST)
							|| corner(state, MBPaneProperties.CORNER_SOUTH_WEST)),
			new SharedBox("pane_edge_east",
					state -> corner(state, MBPaneProperties.CORNER_NORTH_EAST)
							|| corner(state, MBPaneProperties.CORNER_SOUTH_EAST)),
			new SharedBox("pane_edge_west",
					state -> corner(state, MBPaneProperties.CORNER_SOUTH_WEST)
							|| corner(state, MBPaneProperties.CORNER_NORTH_WEST)),
			new SharedBox("pane_middle", MBPaneShapes::hasCorner));

	/** One box two corners share, and the states that show it. */
	private record SharedBox(String template, Predicate<BlockState> shows) {
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
	 * What a lone bars block shows. Vanilla draws the ends of its bars at every state
	 * and their post only while they are connected to nothing, which is this one.
	 */
	private static final List<StickPart> BARS_STICK = List.of(
			new StickPart("_post_ends", Quadrant.R0),
			new StickPart("_post", Quadrant.R0));

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
	 * The boxes of a pane: each of the twelve switches, the boxes two corners share,
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
			selectors.add(new MultiPartModel.Selector<>(state -> corner(state, property), piece));
		}

		for (SharedBox box : SHARED_BOXES) {
			selectors.add(new MultiPartModel.Selector<>(box.shows(), sharedPart(paneId, box)));
		}

		for (StickPart part : bars ? BARS_STICK : GLASS_STICK) {
			BlockStateModel.Unbaked model = stickPart(bare, part);
			selectors.add(new MultiPartModel.Selector<>(MBPaneModelPlugin::bare, model));
		}

		return selectors;
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
	private static boolean corner(BlockState state, Property<Boolean> property) {
		return state.hasProperty(property) && state.getValue(property);
	}

	/** Whether a state shows none of the twelve boxes. */
	private static boolean bare(BlockState state) {
		for (Property<Boolean> property : PIECE_PROPERTIES) {
			if (corner(state, property)) {
				return false;
			}
		}

		return true;
	}
}
