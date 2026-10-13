package com.camelie.morebuildingblocks.client.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.GlazedPattern;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.MBEighthBlock;
import com.camelie.morebuildingblocks.block.MBEighthProperties;
import com.camelie.morebuildingblocks.block.MBPaneProperties;
import com.camelie.morebuildingblocks.block.MBPaneShapes;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.camelie.morebuildingblocks.block.VerticalSlab.Fill;
import com.camelie.morebuildingblocks.registry.MBBlockFamilies;
import com.camelie.morebuildingblocks.registry.MBEighths;
import com.camelie.morebuildingblocks.registry.MBItems;

/**
 * Renders this mod's blocks in a real client and writes screenshots, so the
 * models can be inspected without a human flying around a world.
 *
 * <p>Run it with
 * {@code -Dfabric.client.gametest -Dfabric.client.gametest.modid=morebuildingblocks}
 * on the client, which is what {@code gradlew runClientGameTest} does.
 */
public class MBClientGameTest implements FabricClientGameTest {
	/**
	 * Screenshot names. The harness writes them into its own output directory, so
	 * these must stay bare file names: it does not create subdirectories.
	 */
	private static final String FAMILY_SHOT = "terracotta-family.png";
	private static final String GLAZED_SHOT = "glazed-terracotta-closeup.png";
	private static final String STAIR_SHOT = "glazed-stair-closeup.png";
	private static final String VERTICAL_SHOT = "vertical-slabs-a.png";
	private static final String VERTICAL_NEAR_SHOT = "vertical-slabs-b.png";
	private static final String VERTICAL_FAR_SHOT = "vertical-slabs-c.png";

	/**
	 * The glazed comparison: vanilla's own block and the mod's cut forms, all filled with the
	 * same pattern and placed for the same direction, each photographed straight down from
	 * above so the reference and the candidate are the same pixels when the orientation is
	 * right and a quarter turn out when it is not.
	 */
	private static final String MEASURE_SHOT = "glazed-measure-";

	/** The world's minimum build height, which is where a flat test world builds. */
	private static final int FLOOR_Y = -63;

	/** Ground level of the showcase, one above the floor. */
	private static final int FIRST_ROW_Y = FLOOR_Y + 1;

	/** How far the camera stands back from the terracotta wall. */
	private static final double VIEW_DISTANCE = 10.0;

	/** The upright wall is a thin line seen edge on, so it is photographed from nearer. */
	private static final double VERTICAL_VIEW_DISTANCE = 8.0;

	/** The comparison row builds one above the floor, clear of the showcase behind it. */
	private static final int MEASURE_Y = FLOOR_Y + 2;

	/**
	 * Where the comparison patch is built. It stands clear of the showcase, which is
	 * photographed from the other side, so neither camera ever has the other's subject in
	 * the frame.
	 */
	private static final int CELL_X = -36;
	private static final int CELL_Z = -34;

	/**
	 * How high the comparison camera looks down from. The patch is photographed from its own
	 * centre line, so its middle block is seen straight on and has the whole field of view
	 * to itself: a block covers about ninety pixels here, which is ample for the pattern.
	 */
	private static final double MEASURE_EYE_ABOVE = 12.0;

	private static final int WALL_Z = -6;
	private static final int VERTICAL_WALL_Z = -20;
	private static final int COLUMN_X0 = -8;
	private static final int COLUMNS = 101;
	private static final int X0 = COLUMN_X0 - 4;
	private static final int X1 = COLUMN_X0 + COLUMNS + 4;

	/**
	 * Where the weathering checks build: a clear patch of the flat world east of the showcase, one
	 * cell per check and three blocks apart, so a lightning strike's random steps cannot reach a
	 * cell another check is looking at.
	 */
	private static final int WEATHER_X = 20;
	private static final int WEATHER_Z = 20;
	private static final int WEATHER_Y = FLOOR_Y + 3;

	/**
	 * Where the pane connection checks build: a clear patch of the flat world south of the
	 * showcase and west of every other check, one scenario every four blocks.
	 */
	private static final int PANE_X = -80;
	private static final int PANE_Y = FLOOR_Y + 2;
	private static final int PANE_Z = 60;

	/**
	 * The twelve pieces of a pane, in the order the state writes them: four lower halves
	 * (vanilla's own connection properties), four upper halves and four corners.
	 */
	private static final List<Property<Boolean>> PANE_PIECES = List.of(
			MBPaneProperties.lower(Direction.NORTH), MBPaneProperties.upper(Direction.NORTH),
			MBPaneProperties.lower(Direction.EAST), MBPaneProperties.upper(Direction.EAST),
			MBPaneProperties.lower(Direction.SOUTH), MBPaneProperties.upper(Direction.SOUTH),
			MBPaneProperties.lower(Direction.WEST), MBPaneProperties.upper(Direction.WEST),
			MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
			MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST);

	/**
	 * How many random ticks a lone cell is given to age once. Its chance is about one call in
	 * twenty, so ten thousand calls without an age is a failure rather than bad luck.
	 */
	private static final int AGING_TICKS = 10_000;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			TestServerConnection connection = singleplayer.getConnection();

			connection.waitForChunksRender();

			List<Block> family = familyBlocks();
			List<Block> glazed = glazedBaseBlocks();
			List<Block> vanillaSlabs = vanillaSlabs();

			server.runOnServer(minecraftServer -> {
				ServerLevel level = minecraftServer.overworld();

				// A long strip of floor at ground level for each wall to stand on.
				for (int x = X0; x <= X1; x++) {
					for (int z = WALL_Z; z <= WALL_Z + 6; z++) {
						level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
					}

					for (int z = VERTICAL_WALL_Z; z <= VERTICAL_WALL_Z + 6; z++) {
						level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
					}
				}

				// Clear the view. A flat test world has its own terrain at this height, and
				// a camera low enough to see a three-block-tall showcase against the sky
				// ends up standing inside it. Everything above ground level in front of the
				// walls and along the line of sight is opened up, so the picture is the
				// showcase on empty background rather than grass against a hillside.
				for (int x = X0; x <= X1; x++) {
					for (int y = FIRST_ROW_Y; y <= FLOOR_Y + 12; y++) {
						for (int z = VERTICAL_WALL_Z; z <= WALL_Z + (int) VIEW_DISTANCE + 4; z++) {
							if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) {
								continue;
							}

							level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
						}
					}
				}

				// The comparison patch gets a room of its own: the flat world's own terrain
				// would otherwise stand in the camera's way, and the showcase clearing above
				// does not reach this far. Everything above the surface goes, and the surface
				// itself is left where it is as the floor.
				for (int x = CELL_X - 10; x <= CELL_X + 10; x++) {
					for (int z = CELL_Z - 10; z <= CELL_Z + 10; z++) {
						for (int y = FLOOR_Y + 2; y <= MEASURE_Y + 20; y++) {
							level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
						}
					}
				}

				// A: the whole terracotta family, three rows deep rather than stacked into
				// columns, so the stair in the middle of the frame does not hide the slab
				// behind it. The cube below each pair is vanilla's own block, which is
				// exactly what the stair and slab above it are cut from. 33 of 101
				// columns are used.
				for (int i = 0; i < family.size(); i++) {
					Block block = family.get(i);
					level.setBlockAndUpdate(new BlockPos(COLUMN_X0 + i, FIRST_ROW_Y, WALL_Z), block.defaultBlockState());
					level.setBlockAndUpdate(new BlockPos(COLUMN_X0 + i, FIRST_ROW_Y + 1, WALL_Z), slab(block));
					level.setBlockAndUpdate(new BlockPos(COLUMN_X0 + i, FIRST_ROW_Y + 2, WALL_Z), stairs(block));
				}

				// B: every vanilla slab standing upright, one column per slab. Upright slabs
				// are a thin vertical sheet, and a single one seen edge on reads as a strip,
				// so each slab is stacked three high: the X-facing form, the Z-facing form,
				// and one placed as the upper half.
				for (int i = 0; i < vanillaSlabs.size(); i++) {
					Block slab = vanillaSlabs.get(i);
					int x = COLUMN_X0 + i;

					level.setBlockAndUpdate(new BlockPos(x, FIRST_ROW_Y, VERTICAL_WALL_Z),
							vertical(slab, Direction.Axis.X, SlabType.BOTTOM));
					level.setBlockAndUpdate(new BlockPos(x, FIRST_ROW_Y + 1, VERTICAL_WALL_Z),
							vertical(slab, Direction.Axis.Z, SlabType.BOTTOM));
					level.setBlockAndUpdate(new BlockPos(x, FIRST_ROW_Y + 2, VERTICAL_WALL_Z),
							vertical(slab, Direction.Axis.X, SlabType.TOP));
				}
			});

			context.waitTicks(20);
			connection.waitForChunksRender();

			// All three forms of all 33 columns: the vanilla terracotta cube the mod does
			// not register, with its own slab and stair above it.
			int middle = COLUMN_X0 + family.size() / 2;
			look(context, server, middle, FIRST_ROW_Y + 1.0, WALL_Z, VIEW_DISTANCE, 0.6);
			context.takeScreenshot(FAMILY_SHOT);

			// The glazed terracotta, close enough that the pattern orientation across a
			// cut corner is actually judgeable.
			look(context, server, COLUMN_X0 + glazed.size() / 2.0, FIRST_ROW_Y + 1.0, WALL_Z, 5.0, 0.6);
			context.takeScreenshot(GLAZED_SHOT);

			// One glazed stair and its matching slab, filling the frame. The pattern on a
			// cut corner can only be judged at this range.
			look(context, server, COLUMN_X0 + 20.0, FIRST_ROW_Y + 2.0, WALL_Z, 3.0, 1.0);
			context.takeScreenshot(STAIR_SHOT);

			// Upright slabs, 101 of them, so the run is captured in three passes.
			look(context, server, COLUMN_X0 + 10, FIRST_ROW_Y + 1.0, VERTICAL_WALL_Z, VERTICAL_VIEW_DISTANCE, 1.2);
			context.takeScreenshot(VERTICAL_SHOT);

			look(context, server, COLUMN_X0 + 42, FIRST_ROW_Y + 1.0, VERTICAL_WALL_Z, VERTICAL_VIEW_DISTANCE, 1.2);
			context.takeScreenshot(VERTICAL_NEAR_SHOT);

			look(context, server, COLUMN_X0 + 75, FIRST_ROW_Y + 1.0, VERTICAL_WALL_Z, VERTICAL_VIEW_DISTANCE, 1.2);
			context.takeScreenshot(VERTICAL_FAR_SHOT);

			// The item icons are covered by the model assertions instead of a screenshot:
			// 26.3 draws screens through a render-state extractor whose API cannot be
			// guessed, and the hotbar in every screenshot above already shows the icons.

			measureGlazed(context, server);

			// Geometry and loot come last: a failed assertion aborts the run, and the
			// screenshots above are the evidence for the checks a log cannot make.
			verifyShapes(server);
			verifyPaneProperties(server);
			verifyPaneRotation(server);
			verifyPaneShapes(server);
			verifyPaneConnection(server);
			verifyPaneBars(server);
			verifyPlacement(server);
			verifyUprightStacking(server);
			verifyWallContinuation(server);
			verifyFlatContinuation(server);
			verifyDoubleSlabClicks(server);
			verifyGlazedSlabs(server);
			verifyEighths(server);
			verifyDrops(context, server);
			verifyCreativeTab(server);
			verifyVanillaTabs(server);
			verifyWeathering(context, server);
		}

		// The singleplayer world has to be closed first: the client can only be in one
		// world at a time, and this join is the multiplayer half of the acceptance run.
		verifyDedicatedServer(context);
	}

	/**
	 * Checks that the eight pane flags reached every pane-shaped block and that they default to
	 * {@code false}.
	 *
	 * <p>This is the guard behind the panes' whole design. A piece is switched on by the
	 * connection rules and never by a block being placed, so the default state of a pane has to
	 * stay the vanilla pane: post and arms, nothing else. Vanilla's own boolean property would
	 * have made it the opposite, because its values run {@code true} first, which is why the
	 * flags are the mod's property class instead.
	 */
	private static void verifyPaneProperties(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			List<Property<Boolean>> flags = List.of(
					MBPaneProperties.NORTH_UP,
					MBPaneProperties.EAST_UP,
					MBPaneProperties.SOUTH_UP,
					MBPaneProperties.WEST_UP,
					MBPaneProperties.CORNER_NORTH_EAST,
					MBPaneProperties.CORNER_SOUTH_EAST,
					MBPaneProperties.CORNER_SOUTH_WEST,
					MBPaneProperties.CORNER_NORTH_WEST);

			List<Block> panes = BuiltInRegistries.BLOCK.stream()
					.filter(block -> block instanceof IronBarsBlock)
					.toList();

			require(panes.size() == 26,
					"found " + panes.size() + " pane-shaped blocks, expected the 26 vanilla ones");

			for (Block pane : panes) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(pane);
				BlockState state = pane.defaultBlockState();

				for (Property<Boolean> flag : flags) {
					require(state.hasProperty(flag), id + " does not declare " + flag.getName());
					require(!state.getValue(flag),
							id + " defaults to " + flag.getName() + "=true, expected the vanilla pane");
				}
			}

			for (Property<Boolean> flag : flags) {
				require(flag.getPossibleValues().equals(List.of(false, true)),
						flag.getName() + " lists " + flag.getPossibleValues() + ", expected false first");
				require(flag.getInternalIndex(false) == 0 && flag.getInternalIndex(true) == 1,
						flag.getName() + " indexes its values in the wrong order");
			}
		});
	}

	/**
	 * Checks that the twelve plates add up to the shapes they should.
	 *
	 * <p>The stick - the post and its faces - is what the models draw while every flag is false, so
	 * only an untouched pane is the vanilla post. A connection boolean is the lower plate of an arm
	 * and the mod's {@code *_up} flag is its upper one, so a state with both set is the arm's
	 * quadrant, its band and the column: nine pixels deep, the full height of the pane. A state with
	 * only the lower plate set is the lower half of all that.
	 *
	 * <p>The rest cover the reason the mod answers {@code skipRendering} itself: two panes hide the
	 * face between them only where the pieces on either side of that face meet, so a plate facing
	 * air stays visible, and a corner - which sits at mid height - keeps its up and down faces even
	 * when a pane without a plate there stands above it.
	 */
	private static void verifyPaneShapes(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			VoxelShape post = Block.column(2.0, 0.0, 16.0);
			BlockState plain = Blocks.GLASS_PANE.defaultBlockState();
			BlockState lower = plain.setValue(CrossCollisionBlock.NORTH, true);
			BlockState arm = lower.setValue(MBPaneProperties.NORTH_UP, true);
			BlockState mirrored = plain
					.setValue(CrossCollisionBlock.SOUTH, true)
					.setValue(MBPaneProperties.SOUTH_UP, true);
			BlockState corner = plain.setValue(MBPaneProperties.CORNER_NORTH_EAST, true);
			// Two corners on one side of the plate: the shared edge between them and the middle where
			// the plate and the arm cross are what close the joint they would otherwise leave open
			BlockState eastCorners = plain
					.setValue(MBPaneProperties.CORNER_NORTH_EAST, true)
					.setValue(MBPaneProperties.CORNER_SOUTH_EAST, true);
			BlockState westCorners = plain
					.setValue(MBPaneProperties.CORNER_NORTH_WEST, true)
					.setValue(MBPaneProperties.CORNER_SOUTH_WEST, true);
			// The north arm with the corner east of it: it lines up with the pane above and with the
			// mirrored arm to the north, and its corner must survive the first of those two
			BlockState armWithCorner = arm.setValue(MBPaneProperties.CORNER_NORTH_EAST, true);
			BlockState mirroredEastCorner = mirrored.setValue(MBPaneProperties.CORNER_SOUTH_EAST, true);
			BlockState mirroredWestCorner = mirrored.setValue(MBPaneProperties.CORNER_SOUTH_WEST, true);

			require(sameShape(shapeOf(plain, level), post),
					"a pane with no pieces set is not the vanilla post");
			require(sameShape(shapeOf(arm, level), Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 9.0)),
					"an arm with both plates set is not the arm, its band and the column");
			require(sameShape(shapeOf(lower, level), Block.box(7.0, 0.0, 0.0, 9.0, 9.0, 9.0)),
					"an arm with only its lower plate set is not the lower half of the pane");
			require(sameShape(shapeOf(corner, level), Shapes.or(
					Block.box(9.0, 7.0, 0.0, 16.0, 9.0, 7.0),
					Block.box(7.0, 7.0, 0.0, 9.0, 9.0, 7.0),
					Block.box(9.0, 7.0, 7.0, 16.0, 9.0, 9.0),
					Block.box(7.0, 7.0, 7.0, 9.0, 9.0, 9.0))),
					"a corner piece is not the quadrant it sits in");
			require(sameShape(shapeOf(eastCorners, level), Block.box(7.0, 7.0, 0.0, 16.0, 9.0, 16.0)),
					"two corners on the east side do not close the joint between them");
			require(sameShape(shapeOf(westCorners, level), Block.box(0.0, 7.0, 0.0, 9.0, 9.0, 16.0)),
					"two corners on the west side do not close the joint between them");
			require(sameShape(arm.getCollisionShape(level, BlockPos.ZERO), shapeOf(arm, level)),
					"a pane collides with something other than its outline");

			require(MBPaneShapes.hides(arm, lower, Direction.UP),
					"a full arm no longer hides against the lower half above it");
			require(!MBPaneShapes.hides(lower, arm, Direction.UP),
					"a lower half hides against the air above it");
			require(!MBPaneShapes.hides(armWithCorner, lower, Direction.UP),
					"a corner hides its up face between stacked panes");
			require(MBPaneShapes.hides(arm, mirrored, Direction.NORTH),
					"two matching arms no longer hide the face between them");
			require(!MBPaneShapes.hides(arm, plain, Direction.NORTH),
					"an arm hides its face against a pane carrying no arm");
			require(MBPaneShapes.hides(armWithCorner, mirroredEastCorner, Direction.NORTH),
					"two corners facing each other no longer hide the face between them");
			require(MBPaneShapes.hides(armWithCorner, eastCorners, Direction.NORTH),
					"a corner hides its face against a pane carrying that corner and no arm");
			require(!MBPaneShapes.hides(armWithCorner, mirroredWestCorner, Direction.NORTH),
					"a corner hides its face against a corner across the block");
		});
	}

	/**
	 * The connection rules, checked on a real level.
	 *
	 * <p>{@link #verifyPaneShapes} proves that the twelve pieces build the shape they
	 * should; this proves the rules that switch them on, which is where a wrong reading of a
	 * full face, of a corner partner or of a vertical neighbour shows up. The scenarios
	 * follow the framework of {@code MBPaneConnection}, in which the twelve pieces are the
	 * twelve corners and nothing else: a pane with nothing to lean on, which is vanilla's own
	 * fallback, a pane between two full faces, a pane whose corner partners are panes, four
	 * panes meeting in a square, a pane whose piece hangs on a partner above it, and the
	 * pruning rule, where a pane that turns inside a ring of panes beside a row of full faces
	 * keeps the corners of the ring and loses the rest, and a pane flanked by full faces east
	 * and west, of which only the face a corner hangs on shows anything. The last two put the
	 * same wall beside a pane with a corner and beside one without: vanilla's own connection
	 * test is the only thing that ever counts a wall, so the pane with the corner must not
	 * take the arm that wall would give it, while the pane without one must.
	 *
	 * <p>States are asked for the way the game asks for them, through {@code
	 * getStateForPlacement}, which is one of the entry points. Three of the scenarios then
	 * read the world back, because they need the change to travel: through a neighbour of the
	 * pane (the direct shape update) and through a block in a corner, which is no neighbour
	 * of the pane at all.
	 */
	private static void verifyPaneConnection(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			BlockPos origin = new BlockPos(PANE_X, PANE_Y, PANE_Z);

			clearPanes(level, origin);

			// The fallback: a pane no corner hangs on is vanilla's own pane, drawn from its
			// four connections. In the air it has nothing to connect to, so all twelve
			// pieces stay off and the block is drawn the way vanilla draws a lone pane. The
			// stone put north of it and taken away again travels the direct shape update.
			BlockPos lone = origin;
			requirePieces(paneAt(player, level, lone), "a pane hanging in the air", pieces());

			level.setBlockAndUpdate(lone, paneAt(player, level, lone));
			level.setBlockAndUpdate(lone.north(), Blocks.STONE.defaultBlockState());
			requirePieces(level.getBlockState(lone), "a pane with a block north of it",
					pieces(lowerHalf(Direction.NORTH), upperHalf(Direction.NORTH)));

			level.setBlockAndUpdate(lone.north(), Blocks.AIR.defaultBlockState());
			requirePieces(level.getBlockState(lone), "a pane whose northern block was taken away",
					pieces());

			// Two full faces meet in a corner whether or not the space between them holds
			// anything, so this pane turns the corner between them. A thin side such as a
			// slab's would not count here: only a full 1x1 face does. That one corner is
			// enough for the completing rule, which fills the horizontal plane it lies in:
			// all four corner pieces are drawn and no arm is, because no corner of this pane
			// lies above or below one of its edges.
			BlockPos cornered = origin.offset(4, 0, 0);
			level.setBlockAndUpdate(cornered.north(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(cornered.east(), Blocks.STONE.defaultBlockState());
			requirePieces(paneAt(player, level, cornered),
					"a pane with full faces north and east of it and air between them",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));

			// A corner is only there while both of the blocks beside it reach across: with
			// panes north, east and between them, the pane turns that corner, and the
			// completing rule fills the plane that corner lies in with all four of that
			// plane's corners. The third pane is no neighbour of this one, so taking it away
			// reaches the pane only through the indirect shape update the mod adds; without
			// it the corner would stay behind. With that corner gone the pane has none left,
			// no plane of its is true, and only then is it vanilla's own: it falls back to
			// vanilla's connections and is armed towards both of the panes beside it.
			BlockPos partners = origin.offset(8, 0, 0);
			level.setBlockAndUpdate(partners.north(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(partners.east(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(partners.north().east(), Blocks.GLASS_PANE.defaultBlockState());
			requirePieces(paneAt(player, level, partners),
					"a pane with partners north, east and in the corner between them",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));

			level.setBlockAndUpdate(partners, paneAt(player, level, partners));
			level.setBlockAndUpdate(partners.north().east(), Blocks.AIR.defaultBlockState());
			requirePieces(level.getBlockState(partners), "a pane whose corner partner was taken away",
					pieces(lowerHalf(Direction.NORTH), upperHalf(Direction.NORTH),
							lowerHalf(Direction.EAST), upperHalf(Direction.EAST)));

			// Four panes meeting in a square: each of them shares an edge with two of the
			// others and meets the fourth one in the corner, so each turns that corner and the
			// completing rule then fills the horizontal plane with all four of its corner
			// pieces. No arm is drawn: an edge on its own is nothing an arm can stand on, and
			// no corner of any of these panes lies above or below one, so no vertical plane of
			// theirs is true.
			BlockPos square = origin.offset(0, 0, 4);
			level.setBlockAndUpdate(square, Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(square.east(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(square.south(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(square.south().east(), paneAt(player, level, square.south().east()));

			requirePieces(level.getBlockState(square), "the north-western pane of four in a square",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));
			requirePieces(level.getBlockState(square.east()), "the north-eastern pane of four",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));
			requirePieces(level.getBlockState(square.south()), "the south-western pane of four",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));
			requirePieces(level.getBlockState(square.south().east()), "the south-eastern pane of four",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));

			// A vertical neighbour is what lights the halves of an arm, through the corners
			// it takes part in: this pane has panes above and to the north, and the third one
			// above and north fills the space both of them reach towards, so the corner above
			// the northern edge is on and with it the upper half of the northern arm. That is
			// one corner in a vertical plane, and the completing rule fills that plane: the
			// northern arm is drawn whole, and the southern one with it, towards nothing at
			// all. The block below is no partner across the edge above it - the space north
			// of it is empty - so it lights no corner of its own.
			BlockPos vertical = origin.offset(5, 0, 4);
			level.setBlockAndUpdate(vertical.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(vertical.north(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(vertical.above(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(vertical.above().north(), Blocks.GLASS_PANE.defaultBlockState());
			requirePieces(paneAt(player, level, vertical), "a pane under a partner with a block below it",
					pieces(upperHalf(Direction.NORTH), lowerHalf(Direction.NORTH),
							upperHalf(Direction.SOUTH), lowerHalf(Direction.SOUTH)));

			// The pruning rule, and the example the framework was written around: this pane
			// turns inside a ring of panes with a full face to its south. The pane is true in
			// the plane its ring lies in, which does not hold that face, while the far side of
			// the plane the face does lie in holds nothing, so the two southern corners the
			// face lit are dropped and the southern arm halves with them. The four corners of
			// the ring stay.
			BlockPos turned = origin.offset(8, 0, 4);
			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dy = -1; dy <= 1; dy++) {
					level.setBlockAndUpdate(turned.offset(dx, dy, 0),
							Blocks.GLASS_PANE.defaultBlockState());
				}
			}

			level.setBlockAndUpdate(turned.above(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(turned.below(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(turned.south(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(turned.south().above(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(turned.south().below(), Blocks.STONE.defaultBlockState());
			requirePieces(paneAt(player, level, turned),
					"a pane turning inside a ring of panes beside a row of full faces",
					pieces(lowerHalf(Direction.EAST), upperHalf(Direction.EAST),
							lowerHalf(Direction.WEST), upperHalf(Direction.WEST)));

			// This pane is flanked by full faces east and west and turns the corner between
			// its northern pane and the eastern face. That corner hangs on the east and
			// north faces only, and the pruning leaves it alone because the pane turns
			// across both of them rather than within the plane of either. The completing
			// rule then fills the horizontal plane that corner lies in, so all four corner
			// pieces are drawn; the flanking faces still add nothing of their own, because
			// no corner of this pane lies above or below one of its edges: neither vertical
			// plane is true and no arm is drawn. The box is swept clean first because this
			// is the one scenario wired outside the ground the others cleared, and a face
			// left lying beside it would light a corner the wrong way.
			BlockPos flanked = origin.offset(5, 0, 8);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 2; dz++) {
						level.setBlockAndUpdate(flanked.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}

			level.setBlockAndUpdate(flanked.east(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(flanked.west(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(flanked.north(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(flanked.north().east(), Blocks.STONE.defaultBlockState());
			requirePieces(paneAt(player, level, flanked),
					"a pane flanked by full faces east and west with a pane to its north",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));

			// The ring of the pruning scenario with the pane below it taken away. The full faces
			// to the south still light the southern corner above, and the pane still turns in
			// the plane that corner lies in, but that plane is no longer closed: of its four
			// edge connections the eastern, the western and the upper one are there and the
			// lower one is not. The corner is what holds that side up now, so the rule leaves
			// it, and the pane shows the upper half of its southern arm beside the two upper
			// corners of the ring. Those two lie in two planes that meet along the vertical
			// axis - one in the plane the eastern and western arms lie in, the other in the
			// plane the northern and southern arms lie in - and along that axis the pane has
			// four sides of two corners each: east the two halves of the eastern arm, west
			// those of the western arm, south those of the southern arm, north those of the
			// arm that faces air. East, west and south each have one of their two corners on,
			// so the completing rule turns the other one on as well and all three arms are
			// drawn whole, even the halves that face air; the northern side has neither of its
			// two corners on and stays empty.
			BlockPos openBelow = origin.offset(8, 0, 8);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 2; dz++) {
						level.setBlockAndUpdate(openBelow.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}

			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dy = -1; dy <= 1; dy++) {
					level.setBlockAndUpdate(openBelow.offset(dx, dy, 0),
							Blocks.GLASS_PANE.defaultBlockState());
				}
			}

			level.setBlockAndUpdate(openBelow.above(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(openBelow.south(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(openBelow.south().above(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(openBelow.south().below(), Blocks.STONE.defaultBlockState());
			requirePieces(paneAt(player, level, openBelow),
					"a pane turning beside a row of full faces with nothing below it",
					pieces(upperHalf(Direction.EAST), lowerHalf(Direction.EAST),
							upperHalf(Direction.SOUTH), lowerHalf(Direction.SOUTH),
							upperHalf(Direction.WEST), lowerHalf(Direction.WEST)));

			// The fallback is for a pane the rules leave without a corner and for no other
			// pane, and vanilla's own connection test counts a wall whatever its shape. The
			// same wall beside the two panes below therefore shows which of the two is being
			// asked: the pane with full faces north and east of it turns the corner between
			// them and the completing rule fills the horizontal plane it turns in, so it is
			// drawn from four corner pieces and from nothing towards the wall south of it,
			// which - no full face - lights nothing, while the pane that has no corner at all
			// falls back to vanilla and is armed towards that very wall. The box the two panes
			// read is swept first, so that what stands around them is only what this scenario
			// puts there.
			BlockPos walled = origin.offset(0, 0, 12);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 3; dz++) {
						level.setBlockAndUpdate(walled.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}

			level.setBlockAndUpdate(walled.north(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(walled.east(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(walled.south(), Blocks.COBBLESTONE_WALL.defaultBlockState());
			requirePieces(paneAt(player, level, walled),
					"a pane with a corner beside a wall",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							MBPaneProperties.CORNER_SOUTH_WEST, MBPaneProperties.CORNER_NORTH_WEST));

			BlockPos walledAlone = walled.south().south();
			requirePieces(paneAt(player, level, walledAlone),
					"a pane with no corner beside a wall",
					pieces(lowerHalf(Direction.NORTH), upperHalf(Direction.NORTH)));

			// A pane can be true in all three planes at once, and this one is: full faces north,
			// east and above it light the corner between the northern and the eastern one, the
			// corner above the northern edge and the corner above the eastern edge, one in each
			// plane. The completing rule fills a plane for a pane that turns in one plane and no
			// other, so it adds nothing here: the pane is drawn from those three corners alone,
			// and the sides nothing stands at - west, south and below - stay empty. The box is
			// swept first, so that only these three faces stand around the pane.
			BlockPos threeWays = origin.offset(4, 0, 12);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						level.setBlockAndUpdate(threeWays.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}

			level.setBlockAndUpdate(threeWays.north(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(threeWays.east(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(threeWays.above(), Blocks.STONE.defaultBlockState());
			requirePieces(paneAt(player, level, threeWays),
					"a pane turning in all three planes at once",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, upperHalf(Direction.EAST),
							upperHalf(Direction.NORTH)));

			// Two planes can be true without a third. This pane has full faces north and above
			// it and a pane to its east whose northern neighbour is a full face as well, which
			// lights the corner between the northern and the eastern edge, in the horizontal
			// plane, and the corner above the northern edge, in the vertical plane the northern
			// and the southern arm lie in. Those two planes meet along the axis running north,
			// and along it each of the four sides carries two corners: the two halves of the
			// northern arm, of the southern arm, and the two corner pieces of the eastern and of
			// the western face. The corner above the northern edge is on, so the upper half of
			// the southern arm comes on with it, and the corner between the northern and the
			// eastern edge being on brings the south eastern piece with it; the western and the
			// lower sides have neither of their two corners on and stay dark.
			BlockPos twoWaysAlongZ = origin.offset(12, 0, 12);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						level.setBlockAndUpdate(twoWaysAlongZ.offset(dx, dy, dz),
								Blocks.AIR.defaultBlockState());
					}
				}
			}

			level.setBlockAndUpdate(twoWaysAlongZ.north(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(twoWaysAlongZ.above(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(twoWaysAlongZ.east(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(twoWaysAlongZ.north().east(), Blocks.STONE.defaultBlockState());
			requirePieces(paneAt(player, level, twoWaysAlongZ),
					"a pane turning in two planes that meet along the northern axis",
					pieces(MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_SOUTH_EAST,
							upperHalf(Direction.NORTH), upperHalf(Direction.SOUTH)));

			// The other pair of planes meets along an axis that runs east and west. This pane
			// has full faces east and above it and a pane to its north whose eastern neighbour
			// is a full face, which lights the corner above the eastern edge, in the vertical
			// plane the eastern and western arms lie in, and the corner between the northern and
			// the eastern edge, in the horizontal plane. The two meet along the axis running
			// east, and along it each of the four sides carries two corners: the two halves of
			// the eastern arm, of the western arm, and the two corner pieces of the northern and
			// of the southern face. The corner above the eastern edge is on, so the upper half
			// of the western arm comes on with it, and the corner between the northern and the
			// eastern edge brings the north western piece; the southern and the lower sides have
			// neither of their two corners on and stay dark.
			BlockPos twoWaysAlongX = origin.offset(16, 0, 12);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						level.setBlockAndUpdate(twoWaysAlongX.offset(dx, dy, dz),
								Blocks.AIR.defaultBlockState());
					}
				}
			}

			level.setBlockAndUpdate(twoWaysAlongX.east(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(twoWaysAlongX.above(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(twoWaysAlongX.north(), Blocks.GLASS_PANE.defaultBlockState());
			level.setBlockAndUpdate(twoWaysAlongX.north().east(), Blocks.STONE.defaultBlockState());
			requirePieces(paneAt(player, level, twoWaysAlongX),
					"a pane turning in two planes that meet along the eastern axis",
					pieces(upperHalf(Direction.EAST), upperHalf(Direction.WEST),
							MBPaneProperties.CORNER_NORTH_EAST, MBPaneProperties.CORNER_NORTH_WEST));
		});
	}

	/**
	 * Ages a copper bar that carries every corner piece of the pane model on a random tick and waxes it
	 * with a honeycomb, and checks the pieces come through both turns untouched.
	 *
	 * <p>Both turns rebuild the state the game's own way: the next block of the metal is taken and
	 * the old state's properties are copied onto it ({@code Block#withPropertiesOf}). That copy is
	 * the one step in these blocks that is not this mod's code, and the mod's four upper halves and
	 * four corners live on {@code IronBarsBlock}'s own state definition, so they are on both sides
	 * of it. This is the test that says so: pieces that did not come through would leave a lone
	 * post where a walled-in bar stood - the shapes and the collision follow the pieces - and
	 * nothing would put them back until a neighbour of the bar changed or its chunk was read again.
	 *
	 * <p>The bar is the game's block, not one of the mod's panes, so it is also the check that the
	 * connection rules of a pane are the rules of the vanilla block family they were written for.
	 */
	private static void verifyPaneBars(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			BlockPos bar = new BlockPos(PANE_X, PANE_Y, PANE_Z).offset(20, 0, 12);
			Block fresh = Blocks.COPPER_BARS.weathering().unaffected();
			List<Property<Boolean>> corners = pieces(MBPaneProperties.CORNER_NORTH_EAST,
					MBPaneProperties.CORNER_SOUTH_EAST, MBPaneProperties.CORNER_SOUTH_WEST,
					MBPaneProperties.CORNER_NORTH_WEST);

			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						level.setBlockAndUpdate(bar.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}

			// A whole block on all four sides turns the corner between every pair of them, and the
			// completing rule then fills the horizontal plane with all four of that plane's
			// corners: the same answer the pane scenes check for a pane with two full faces beside
			// it. No arm is drawn, and that is part of the test - eight of the twelve flags stay
			// off, so a copy that came back all false would not pass either.
			for (Direction side : Direction.Plane.HORIZONTAL) {
				level.setBlockAndUpdate(bar.relative(side), Blocks.STONE.defaultBlockState());
			}

			BlockState placed = placedAt(player, level, bar, fresh);

			requirePieces(placed, "a copper bar walled in on all four sides", corners);

			level.setBlockAndUpdate(bar, placed);
			requirePieces(level.getBlockState(bar), "a copper bar standing in the world", corners);

			// The random tick the game ages a block on, called on the state the game would call it
			// on, which is how the weathering test reaches the same turn.
			int ticks = 0;

			while (level.getBlockState(bar).getBlock() == fresh && ticks < AGING_TICKS) {
				level.getBlockState(bar).randomTick(level, bar, level.getRandom());
				ticks++;
			}

			Block weathered = Blocks.COPPER_BARS.weathering().pick(WeatheringCopper.WeatherState.EXPOSED);

			require(ticks < AGING_TICKS,
					"the random tick left " + name(fresh) + " alone " + AGING_TICKS + " times");
			require(level.getBlockState(bar).getBlock() == weathered,
					"a copper bar aged into " + name(level.getBlockState(bar).getBlock()) + " instead of "
							+ name(weathered));
			requirePieces(level.getBlockState(bar), "a copper bar that aged on a random tick", corners);

			// The turn a player makes, with the real item in the real hand.
			InteractionResult waxedOn = click(player, Items.HONEYCOMB, bar);
			Block waxed = Blocks.COPPER_BARS.waxed().pick(WeatheringCopper.WeatherState.EXPOSED);

			require(level.getBlockState(bar).getBlock() == waxed,
					"a honeycomb (" + waxedOn + ") turned a weathered bar into "
							+ name(level.getBlockState(bar).getBlock()) + " instead of " + name(waxed));
			requirePieces(level.getBlockState(bar),
					"a copper bar a honeycomb was used on (" + waxedOn + ")", corners);
		});
	}

	/**
	 * Checks that turning a pane turns the eight flags this mod adds along with the four vanilla ones.
	 *
	 * <p>Vanilla's own {@code rotate} and {@code mirror} move its four connection booleans - here the
	 * lower halves of the arms - and nothing else, so without this the upper halves and the corners
	 * would stay on the faces they were turned away from, and a pane loaded from a rotated structure
	 * would describe pieces its neighbours do not call for. Every scene turns one state that carries
	 * one of each kind of piece and checks where the three of them land; the last one turns a fence,
	 * which is not a pane but shares the block class that does the turning.
	 */
	private static void verifyPaneRotation(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			BlockState piece = Blocks.GLASS_PANE.defaultBlockState()
					.setValue(lowerHalf(Direction.WEST), true)
					.setValue(upperHalf(Direction.NORTH), true)
					.setValue(MBPaneProperties.CORNER_NORTH_EAST, true);

			requirePieces(piece, "a pane before it is turned",
					pieces(lowerHalf(Direction.WEST), upperHalf(Direction.NORTH),
							MBPaneProperties.CORNER_NORTH_EAST));

			// A quarter turn clockwise: north to east, east to south, west to north.
			requirePieces(piece.rotate(Rotation.CLOCKWISE_90), "a pane turned a quarter turn clockwise",
					pieces(lowerHalf(Direction.NORTH), upperHalf(Direction.EAST),
							MBPaneProperties.CORNER_SOUTH_EAST));

			// The other way round: north to west, west to south, east to north.
			requirePieces(piece.rotate(Rotation.COUNTERCLOCKWISE_90), "a pane turned the other quarter turn",
					pieces(lowerHalf(Direction.SOUTH), upperHalf(Direction.WEST),
							MBPaneProperties.CORNER_NORTH_WEST));

			// Half a turn swaps every arm with the one opposite it.
			requirePieces(piece.rotate(Rotation.CLOCKWISE_180), "a pane turned half a turn",
					pieces(lowerHalf(Direction.EAST), upperHalf(Direction.SOUTH),
							MBPaneProperties.CORNER_SOUTH_WEST));

			// A left-right mirror swaps north with south and leaves east and west alone;
			// a front-back one does the opposite.
			requirePieces(piece.mirror(Mirror.LEFT_RIGHT), "a pane mirrored left to right",
					pieces(lowerHalf(Direction.WEST), upperHalf(Direction.SOUTH),
							MBPaneProperties.CORNER_SOUTH_EAST));
			requirePieces(piece.mirror(Mirror.FRONT_BACK), "a pane mirrored front to back",
					pieces(lowerHalf(Direction.EAST), upperHalf(Direction.NORTH),
							MBPaneProperties.CORNER_NORTH_WEST));

			// Turning or mirroring and going back has to leave the state that was there before.
			BlockState turnedBack = piece.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.COUNTERCLOCKWISE_90);
			require(turnedBack.equals(piece), "a pane turned a quarter turn and back is " + turnedBack
					+ ", expected " + piece);

			BlockState mirroredBack = piece.mirror(Mirror.LEFT_RIGHT).mirror(Mirror.LEFT_RIGHT);
			require(mirroredBack.equals(piece), "a pane mirrored twice is " + mirroredBack
					+ ", expected " + piece);

			// Neither kind of identity touches anything, and a pane with every piece on stays full:
			// a turn can only move pieces, never turn one on or off.
			require(piece.rotate(Rotation.NONE).equals(piece), "a pane turned by nothing is "
					+ piece.rotate(Rotation.NONE) + ", expected " + piece);
			require(piece.mirror(Mirror.NONE).equals(piece), "a pane mirrored by nothing is "
					+ piece.mirror(Mirror.NONE) + ", expected " + piece);

			BlockState full = Blocks.GLASS_PANE.defaultBlockState();

			for (Property<Boolean> flag : PANE_PIECES) {
				full = full.setValue(flag, true);
			}

			requirePieces(full.rotate(Rotation.CLOCKWISE_90), "a pane with every piece on, turned",
					PANE_PIECES);

			// A fence is not a pane, so vanilla's own answer for it has to come through untouched.
			BlockState fence = Blocks.OAK_FENCE.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true);
			BlockState turnedFence = fence.rotate(Rotation.CLOCKWISE_90);

			require(turnedFence.getValue(CrossCollisionBlock.EAST)
					&& !turnedFence.getValue(CrossCollisionBlock.NORTH),
					"a fence turned a quarter turn clockwise has east="
							+ turnedFence.getValue(CrossCollisionBlock.EAST) + " north="
							+ turnedFence.getValue(CrossCollisionBlock.NORTH) + ": " + turnedFence);
		});
	}

	/** The state a pane is placed with at {@code pos}, asked the way the game asks for it. */
	private static BlockState paneAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return placedAt(player, level, pos, Blocks.GLASS_PANE);
	}

	/**
	 * The state {@code pane} is placed with at {@code pos}, asked the way the game asks for it: a
	 * click into the empty block with the block's own item in hand, handed to the block's own
	 * {@code getStateForPlacement}, which is where the connection rules run. A bar is asked the
	 * same way a pane is, so the copper bars go through the same scenes.
	 */
	private static BlockState placedAt(ServerPlayer player, ServerLevel level, BlockPos pos, Block pane) {
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(pane.asItem()),
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));

		require(context.getClickedPos().equals(pos),
				"a click into the empty block at " + pos + " aimed at " + context.getClickedPos()
						+ ", expected the clicked block itself");

		BlockState placed = pane.getStateForPlacement(context);

		require(placed != null, "no block state was produced for a pane at " + pos);
		return placed;
	}

	/** Checks that exactly the named pieces are on and every other piece is off. */
	private static void requirePieces(BlockState state, String what, Collection<Property<Boolean>> on) {
		Set<Property<Boolean>> expected = new HashSet<>(on);

		for (Property<Boolean> piece : PANE_PIECES) {
			boolean actual = state.getValue(piece);

			require(expected.contains(piece) == actual, "the " + what + " has " + piece.getName()
					+ "=" + actual + ", expected " + expected.contains(piece) + ": " + state);
		}
	}

	/** The named pieces, for {@link #requirePieces}. */
	@SafeVarargs
	private static List<Property<Boolean>> pieces(Property<Boolean>... on) {
		return List.of(on);
	}

	/** The lower half of an arm, which is one of vanilla's own connection properties. */
	private static Property<Boolean> lowerHalf(Direction direction) {
		return MBPaneProperties.lower(direction);
	}

	/** The upper half of an arm, which is one of the properties this mod adds. */
	private static Property<Boolean> upperHalf(Direction direction) {
		return MBPaneProperties.upper(direction);
	}

	/** Opens up the volume the pane scenarios build in. */
	private static void clearPanes(ServerLevel level, BlockPos origin) {
		for (int x = -2; x <= 10; x++) {
			for (int z = -2; z <= 8; z++) {
				for (int y = -2; y <= 3; y++) {
					level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** The outline of a state, read at an empty position in the overworld. */
	private static VoxelShape shapeOf(BlockState state, ServerLevel level) {
		return state.getShape(level, BlockPos.ZERO);
	}

	/** Whether two shapes cover exactly the same voxels. */
	private static boolean sameShape(VoxelShape first, VoxelShape second) {
		return !Shapes.joinIsNotEmpty(first, second, BooleanOp.ONLY_FIRST)
				&& !Shapes.joinIsNotEmpty(first, second, BooleanOp.ONLY_SECOND);
	}

	/**
	 * Checks that the mod's creative tab exists and holds every block the mod adds, each
	 * one exactly once. The order the tab lists them in is not asserted here.
	 *
	 * <p>Registering the tab a second time is safe: {@code MBItems.register()} is guarded,
	 * so this also proves the guard works from a second call site.
	 */
	private static void verifyCreativeTab(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			CreativeModeTab tab = MBItems.register();

			require(tab != null, "MBItems.register() returned no creative tab");
			require(BuiltInRegistries.CREATIVE_MODE_TAB.getValue(MBItems.MAIN_TAB_KEY) == tab,
					"the creative tab is not the one registered under " + MBItems.MAIN_TAB_KEY.identifier());

			List<Block> displayOrder = MBItems.displayOrder();
			List<Block> expected = new ArrayList<>();

			// The order is part of the tab: every mod family is listed as its stairs, its slab
			// and then its eighth block, and the eighths of the vanilla materials follow in the
			// order MBEighths declares them - grouped by material and colour, not by name.
			for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
				expected.add(family.stairs());
				expected.add(family.slab());

				MBEighths.Material material = MBEighths.byBase(family.base());

				if (material != null) {
					expected.add(material.eighth());
				}
			}

			for (MBEighths.Material material : MBEighths.materials()) {
				if (!material.modForms()) {
					expected.add(material.eighth());
				}
			}

			require(displayOrder.size() == expected.size(),
					"the tab lists " + displayOrder.size() + " blocks, expected " + expected.size());
			require(displayOrder.equals(expected),
					"the tab is listed in the wrong order: " + describeFirstMismatch(displayOrder, expected));

			// Spot checks that read the order the way the player does, so that a table sorted by
			// name again would fail here even if the list above were sorted with it.
			assertFollowed(displayOrder, eighthOf(Blocks.OAK_PLANKS), eighthOf(Blocks.SPRUCE_PLANKS));
			assertFollowed(displayOrder, eighthOf(Blocks.WOOL.white()), eighthOf(Blocks.WOOL.orange()));
			assertFollowed(displayOrder, eighthOf(Blocks.WOOL.black()), eighthOf(Blocks.CONCRETE.white()));
			assertFollowed(displayOrder, eighthOf(Blocks.STONE), eighthOf(Blocks.STONE_BRICKS));
			require(displayOrder.indexOf(eighthOf(Blocks.OAK_PLANKS))
					< displayOrder.indexOf(eighthOf(Blocks.ACACIA_PLANKS)),
					"the eighths are still listed in name order, not in material order");

			Identifier tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
			require(MoreBuildingBlocks.MOD_ID.equals(tabId.getNamespace()),
					"the tab is registered in namespace " + tabId.getNamespace());

			// Every listed block must have an item, or the tab would show a gap.
			for (Block block : displayOrder) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(block);
				require(MoreBuildingBlocks.MOD_ID.equals(id.getNamespace()),
						"the tab lists a foreign block: " + id + " (families: " + expected.size()
								+ ", listed: " + displayOrder.size() + ", tab: " + tabId + ")");
				require(Item.BY_BLOCK.get(block) != null, "no item for " + id);
			}
		});
	}

	/** The eighth block of a vanilla base block, failing the test if it has none. */
	private static Block eighthOf(Block base) {
		MBEighths.Material material = MBEighths.byBase(base);

		require(material != null, "no eighth block for " + BuiltInRegistries.BLOCK.getKey(base));

		return material.eighth();
	}

	/** Requires {@code next} to be listed directly after {@code previous} in the creative tab. */
	private static void assertFollowed(List<Block> order, Block previous, Block next) {
		int index = order.indexOf(previous);

		require(index >= 0, "the tab does not list " + BuiltInRegistries.BLOCK.getKey(previous));
		require(index + 1 < order.size() && order.get(index + 1) == next,
				"the tab does not list " + BuiltInRegistries.BLOCK.getKey(next) + " after "
						+ BuiltInRegistries.BLOCK.getKey(previous));
	}

	/** Names the first block the tab lists somewhere else than expected, for a readable failure. */
	private static String describeFirstMismatch(List<Block> actual, List<Block> expected) {
		for (int index = 0; index < Math.min(actual.size(), expected.size()); index++) {
			if (actual.get(index) != expected.get(index)) {
				return "index " + index + " is " + BuiltInRegistries.BLOCK.getKey(actual.get(index))
						+ " where " + BuiltInRegistries.BLOCK.getKey(expected.get(index)) + " belongs";
			}
		}

		return actual.size() < expected.size() ? "it stops after " + actual.size() + " blocks"
				: "it lists " + actual.size() + " blocks, expected " + expected.size();
	}

	/**
	 * Checks where the eighths sit in the game's own two tabs: not inside a material's own run of
	 * blocks, stairs and slabs, but as one run of their own behind it, colour by colour, behind the
	 * slabs of the material they are cut from.
	 *
	 * <p>This is what the player sees in the creative menu, so it is read from the tabs' own display
	 * items rather than from the plugin that puts the eighths there.
	 */
	private static void verifyVanillaTabs(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
					minecraftServer.getWorldData().enabledFeatures(), true, minecraftServer.registryAccess());
			List<Item> coloured = displayedItems(tab(CreativeModeTabs.COLORED_BLOCKS), parameters);
			List<Item> building = displayedItems(tab(CreativeModeTabs.BUILDING_BLOCKS), parameters);

			require(!coloured.isEmpty() && !building.isEmpty(),
					"the game's own tabs list nothing to check: " + coloured.size() + " coloured items, "
							+ building.size() + " building items");

			// The colours are read back from the game's own run of wool: DyeColor keeps its constants
			// in the order the dyes were added, not in the order the tab lists them.
			List<DyeColor> colours = colourOrder(coloured);

			require(colours.size() == DyeColor.values().length,
					"the coloured tab lists " + colours.size() + " colours of wool, expected "
							+ DyeColor.values().length + ": " + colours);

			// Wool and concrete: the sixteen eighths of the material, in colour order, as one run
			// behind the whole run of that material's slabs.
			assertRunBehind(coloured, dyedEighths(colours, Blocks.WOOL), dyedItems(colours, Blocks.WOOL_SLAB),
					"the wool eighths");
			assertRunBehind(coloured, dyedEighths(colours, Blocks.CONCRETE), dyedItems(colours, Blocks.CONCRETE_SLAB),
					"the concrete eighths");

			// Terracotta: the game cut no stairs and no slabs from it, so the forms this mod built go
			// in behind the game's own dyed blocks - the plain family first, then the sixteen dyed.
			List<Item> terracottaStairs = new ArrayList<>();
			List<Item> terracottaSlabs = new ArrayList<>();
			List<Item> terracottaEighths = new ArrayList<>();
			MBEighths.Material plain = terracottaFamily(Blocks.TERRACOTTA);
			terracottaStairs.add(plain.stairs().asItem());
			terracottaSlabs.add(plain.slab().asItem());
			terracottaEighths.add(plain.eighth().asItem());

			List<Item> glazedStairs = new ArrayList<>();
			List<Item> glazedSlabs = new ArrayList<>();
			List<Item> glazedEighths = new ArrayList<>();

			for (DyeColor colour : colours) {
				MBEighths.Material dyed = terracottaFamily(Blocks.DYED_TERRACOTTA.pick(colour));
				terracottaStairs.add(dyed.stairs().asItem());
				terracottaSlabs.add(dyed.slab().asItem());
				terracottaEighths.add(dyed.eighth().asItem());

				MBEighths.Material glazed = terracottaFamily(Blocks.GLAZED_TERRACOTTA.pick(colour));
				glazedStairs.add(glazed.stairs().asItem());
				glazedSlabs.add(glazed.slab().asItem());
				glazedEighths.add(glazed.eighth().asItem());
			}

			List<Item> terracotta = new ArrayList<>(terracottaStairs);
			terracotta.addAll(terracottaSlabs);
			terracotta.addAll(terracottaEighths);

			assertRunBehind(coloured, terracotta, dyedItems(colours, Blocks.DYED_TERRACOTTA),
					"the terracotta stairs, slabs and eighths of this mod");

			List<Item> glazed = new ArrayList<>(glazedStairs);
			glazed.addAll(glazedSlabs);
			glazed.addAll(glazedEighths);

			assertRunBehind(coloured, glazed, dyedItems(colours, Blocks.GLAZED_TERRACOTTA),
					"the glazed terracotta stairs, slabs and eighths of this mod");

			// Copper: the four weatherings this mod cut an eighth from, and then the same four waxed,
			// which the game lists as a second family of their own.
			List<Block> copperBases = List.of(Blocks.CUT_COPPER.weathering().unaffected(), Blocks.CUT_COPPER.weathering().exposed(),
					Blocks.CUT_COPPER.weathering().weathered(), Blocks.CUT_COPPER.weathering().oxidized());
			List<Item> copper = eighthsOf(Set.copyOf(copperBases));

			require(copper.equals(copperBases.stream().map(base -> eighthOf(base).asItem()).toList()),
					"the copper eighths are not declared from the least to the most weathered: " + copper);

			assertRunBehind(building, copper, copperForms(), "the copper eighths");

			List<Block> waxedBases = List.of(vanillaBlock("waxed_cut_copper"), vanillaBlock("waxed_exposed_cut_copper"),
					vanillaBlock("waxed_weathered_cut_copper"), vanillaBlock("waxed_oxidized_cut_copper"));
			List<Item> waxed = eighthsOf(Set.copyOf(waxedBases));

			require(waxed.equals(waxedBases.stream().map(base -> eighthOf(base).asItem()).toList()),
					"the waxed copper eighths are not declared from the least to the most weathered: " + waxed);

			assertRunBehind(building, waxed, formsOf("waxed_cut_copper_stairs", "waxed_cut_copper_slab",
					"waxed_exposed_cut_copper_stairs", "waxed_exposed_cut_copper_slab",
					"waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab",
					"waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab"), "the waxed copper eighths");
		});
	}

	/** The order the game's own tab lists the dye colours in, read back from its run of wool. */
	private static List<DyeColor> colourOrder(List<Item> order) {
		List<DyeColor> colours = new ArrayList<>();

		for (Item item : order) {
			for (DyeColor colour : DyeColor.values()) {
				if (!colours.contains(colour) && item == Blocks.WOOL.pick(colour).asItem()) {
					colours.add(colour);
					break;
				}
			}
		}

		return colours;
	}

	/** The eighth block items of one dyed material, in the order the tab lists the colours. */
	private static List<Item> dyedEighths(List<DyeColor> colours, ColorCollection<Block> blocks) {
		List<Item> items = new ArrayList<>();

		for (DyeColor colour : colours) {
			items.add(eighthOf(blocks.pick(colour)).asItem());
		}

		return items;
	}

	/** The items of one dyed block - wool, concrete or terracotta - in the order the tab lists them. */
	private static List<Item> dyedItems(List<DyeColor> colours, ColorCollection<Block> blocks) {
		List<Item> items = new ArrayList<>();

		for (DyeColor colour : colours) {
			items.add(blocks.pick(colour).asItem());
		}

		return items;
	}

	/** The stairs, the slab and the eighth this mod built for one terracotta family. */
	private static MBEighths.Material terracottaFamily(Block base) {
		MBEighths.Material material = MBEighths.byBase(base);

		require(material != null && material.modForms(),
				"the mod has no own stairs and slab for the terracotta block " + base);

		return material;
	}

	/** The items of the vanilla blocks with these ids. */
	private static Set<Item> formsOf(String... paths) {
		Set<Item> items = new HashSet<>();

		for (String path : paths) {
			items.add(vanillaBlock(path).asItem());
		}

		return items;
	}

	/** The vanilla block with this id, failing the test if the game has none. */
	private static Block vanillaBlock(String path) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path))
				.orElseThrow(() -> new IllegalStateException("vanilla has no block " + path));
	}

	/** The items of the eighth blocks cut from one of {@code bases}, in the order the mod declares them. */
	private static List<Item> eighthsOf(Set<Block> bases) {
		List<Item> items = new ArrayList<>();

		for (MBEighths.Material material : MBEighths.materials()) {
			if (bases.contains(material.base())) {
				items.add(material.eighth().asItem());
			}
		}

		return items;
	}

	/** The items of the stairs and slabs of the four cut copper weatherings. */
	private static Set<Item> copperForms() {
		Set<Item> items = new HashSet<>();

		for (Block block : List.of(Blocks.CUT_COPPER_STAIRS.weathering().unaffected(), Blocks.CUT_COPPER_STAIRS.weathering().exposed(),
				Blocks.CUT_COPPER_STAIRS.weathering().weathered(), Blocks.CUT_COPPER_STAIRS.weathering().oxidized(),
				Blocks.CUT_COPPER_SLAB.weathering().unaffected(), Blocks.CUT_COPPER_SLAB.weathering().exposed(),
				Blocks.CUT_COPPER_SLAB.weathering().weathered(), Blocks.CUT_COPPER_SLAB.weathering().oxidized())) {
			items.add(block.asItem());
		}

		return items;
	}

	/**
	 * Requires {@code run} to be listed in {@code order} as one uninterrupted run, starting behind
	 * every item of {@code anchors}: the whole point of leaving a material's own run of blocks,
	 * stairs and slabs alone.
	 */
	private static void assertRunBehind(List<Item> order, List<Item> run, Collection<Item> anchors, String what) {
		require(!run.isEmpty(), "there is nothing to check for " + what);

		int first = order.indexOf(run.get(0));

		require(first >= 0, "the tab lists none of " + what);

		for (int index = 0; index < run.size(); index++) {
			require(order.indexOf(run.get(index)) == first + index,
					what + " are not listed one after another: " + run);
		}

		int lastAnchor = -1;

		for (Item anchor : anchors) {
			lastAnchor = Math.max(lastAnchor, order.indexOf(anchor));
		}

		require(lastAnchor >= 0, "the tab lists nothing " + what + " could belong behind");
		require(first > lastAnchor,
				what + " cut into the run they belong behind: they start at " + first
						+ ", the last of the blocks they follow is at " + lastAnchor);
	}

	/**
	 * The items a tab shows, in the order it shows them, after the game has built its contents: a tab
	 * nobody opened yet lists nothing, and building it is what lets the mod's plugin fill it in.
	 */
	private static List<Item> displayedItems(CreativeModeTab tab, CreativeModeTab.ItemDisplayParameters parameters) {
		tab.buildContents(parameters);

		return tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
	}

	/** The tab the game registered under {@code key}, failing the test when it has none. */
	private static CreativeModeTab tab(ResourceKey<CreativeModeTab> key) {
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(key);

		require(tab != null, "the game has no creative tab " + key.identifier());

		return tab;
	}

	/**
	 * Checks that the copper eighths weather the way the game's own copper does: the same four
	 * stages in the same order, the same maps deciding what ages into what and what waxes into
	 * what, and the same four things that turn a copper block - the game's random tick, an axe, a
	 * honeycomb, and a lightning strike.
	 *
	 * <p>The eighths carry no rules of their own: they are written into the two maps the game reads
	 * and implement the game's own interface, so what is checked here is that the wiring is in
	 * place and that a cell turns as one block. The game turns a block by copying the properties of
	 * the state it replaces onto the new one, so every check turns a cell with three eighths in it
	 * and facing away from the default, and then reads back both the block it became and the pieces
	 * it still holds.
	 *
	 * <p>The age check calls the tick the game ages blocks on rather than waiting for one to come
	 * around: a lone cell ages on about one call in twenty, so this calls it until it takes effect
	 * instead of hoping the random ticks of a short test run happen to land on it.
	 */
	private static void verifyWeathering(ClientGameTestContext context, TestServerContext server) {
		List<Block> plain = new ArrayList<>();
		List<Block> waxed = new ArrayList<>();

		for (WeatheringCopper.WeatherState stage : WeatheringCopper.WeatherState.values()) {
			plain.add(eighthOf(Blocks.CUT_COPPER.weathering().pick(stage)));
			waxed.add(eighthOf(Blocks.CUT_COPPER.waxed().pick(stage)));
		}

		// Writing the eighths into the game's maps must not take out what the game itself put there.
		require(WeatheringCopper.getNext(Blocks.CUT_COPPER.weathering().unaffected())
				.filter(next -> next == Blocks.CUT_COPPER.weathering().exposed()).isPresent(),
				"the game's own cut copper no longer ages into its next stage");

		for (int stage = 0; stage < plain.size(); stage++) {
			Block eighth = plain.get(stage);
			Block copper = Blocks.CUT_COPPER.weathering().pick(WeatheringCopper.WeatherState.values()[stage]);
			Block waxedHere = waxed.get(stage);

			// The same stage and the same rate as the block it was cut from: the age a block reports
			// is what the game reads to see whether its neighbours are older or younger, and the
			// chance modifier is what makes plain copper the slowest of the four.
			require(weathering(eighth).getAge() == weathering(copper).getAge(),
					name(eighth) + " calls itself " + weathering(eighth).getAge()
							+ " where the game calls its copper " + weathering(copper).getAge());
			require(weathering(eighth).getChanceModifier() == weathering(copper).getChanceModifier(),
					name(eighth) + " ages at " + weathering(eighth).getChanceModifier()
							+ " where the game's copper ages at " + weathering(copper).getChanceModifier());

			if (stage + 1 == plain.size()) {
				require(WeatheringCopper.getNext(eighth).isEmpty(), "the most weathered eighth, " + name(eighth)
						+ ", still ages into " + name(WeatheringCopper.getNext(eighth)));
			} else {
				Block next = plain.get(stage + 1);

				require(WeatheringCopper.getNext(eighth).filter(candidate -> candidate == next).isPresent(),
						name(eighth) + " ages into " + name(WeatheringCopper.getNext(eighth)) + " instead of " + name(next));
			}

			// A honeycomb waxes the stage the cell is at, and a waxed eighth is not aged at all.
			require(HoneycombItem.getWaxed(eighth.defaultBlockState()).map(BlockState::getBlock)
					.filter(candidate -> candidate == waxedHere).isPresent(),
					"a honeycomb does not turn " + name(eighth) + " into " + name(waxedHere));
			require(WeatheringCopper.getNext(waxedHere).isEmpty(),
					"the waxed eighth " + name(waxedHere) + " is still aged by the game");
		}

		// The same turns as states, where the pieces and the facing are visible.
		BlockState cell = weatheringCell(plain.get(2), Direction.WEST, 0, 3, 7);

		assertSameCell(weathering(plain.get(2)).getNext(cell)
						.orElseThrow(() -> new AssertionError("a cell of " + name(plain.get(2)) + " no longer ages")),
				cell, plain.get(3), "aging");
		assertSameCell(WeatheringCopper.getPrevious(cell)
						.orElseThrow(() -> new AssertionError("a cell of " + name(plain.get(2)) + " can no longer be scraped")),
				cell, plain.get(1), "an axe");
		assertSameCell(HoneycombItem.getWaxed(cell)
						.orElseThrow(() -> new AssertionError("a cell of " + name(plain.get(2)) + " can no longer be waxed")),
				cell, waxed.get(2), "a honeycomb");
		assertSameCell(WeatheringCopper.getFirst(cell), cell, plain.get(0), "a lightning strike");

		BlockPos agedPos = new BlockPos(WEATHER_X, WEATHER_Y, WEATHER_Z);
		BlockPos clickedPos = agedPos.offset(3, 0, 0);
		BlockPos struckPos = agedPos.offset(6, 0, 0);
		BlockState fresh = weatheringCell(plain.get(0), Direction.EAST, 1, 6, 7);
		BlockState oxidized = weatheringCell(plain.get(3), Direction.WEST, 0, 3, 7);
		BlockState struck = weatheringCell(plain.get(3), Direction.NORTH, 2, 5, 7);

		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);

			// The random tick the game ages blocks on, called on the state the game would call it on.
			level.setBlockAndUpdate(agedPos, fresh);

			int ticks = 0;

			while (level.getBlockState(agedPos).getBlock() == fresh.getBlock() && ticks < AGING_TICKS) {
				level.getBlockState(agedPos).randomTick(level, agedPos, level.getRandom());
				ticks++;
			}

			require(ticks < AGING_TICKS,
					"the random tick left " + name(fresh.getBlock()) + " alone " + AGING_TICKS + " times");
			assertSameCell(level.getBlockState(agedPos), fresh, plain.get(1), "a random tick");

			// The turns a player makes, each with the real item in the real hand: an axe scrapes one
			// stage off the whole cell, a honeycomb waxes it where it stands, and the axe takes the
			// wax off again.
			level.setBlockAndUpdate(clickedPos, oxidized);

			InteractionResult scraped = click(player, Items.IRON_AXE, clickedPos);
			assertSameCell(level.getBlockState(clickedPos), oxidized, plain.get(2), "an axe (" + scraped + ")");

			BlockState weathered = level.getBlockState(clickedPos);
			InteractionResult waxedOn = click(player, Items.HONEYCOMB, clickedPos);
			assertSameCell(level.getBlockState(clickedPos), weathered, waxed.get(2), "a honeycomb (" + waxedOn + ")");

			BlockState waxedCell = level.getBlockState(clickedPos);
			InteractionResult waxedOff = click(player, Items.IRON_AXE, clickedPos);
			assertSameCell(level.getBlockState(clickedPos), waxedCell, plain.get(2),
					"an axe on a waxed cell (" + waxedOff + ")");

			level.setBlockAndUpdate(struckPos, struck);
		});

		// Lightning is the one turn that skips the stages: the bolt takes the whole cell back to the
		// youngest stage of its metal, and it is a real bolt in the world that does it.
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);

			require(bolt != null, "the game has no lightning bolt entity to strike with");

			bolt.setPos(struckPos.getX() + 0.5, struckPos.getY() + 1.0, struckPos.getZ() + 0.5);
			level.addFreshEntity(bolt);
		});

		context.waitTicks(4);

		server.runOnServer(minecraftServer -> assertSameCell(minecraftServer.overworld().getBlockState(struckPos),
				struck, plain.get(0), "a lightning strike"));
	}

	/** @return the state of {@code eighth} with the pieces at {@code pieces} in it and facing {@code facing}. */
	private static BlockState weatheringCell(Block eighth, Direction facing, int... pieces) {
		BlockState state = eighth.defaultBlockState();

		for (int index = 0; index < MBEighthProperties.BITS.length; index++) {
			boolean present = false;

			for (int piece : pieces) {
				present = present || piece == index;
			}

			state = state.setValue(MBEighthProperties.BITS[index], present);
		}

		return state.setValue(MBEighthProperties.FACING, facing);
	}

	/**
	 * Requires {@code turned} to be the same cell as {@code before} - the same pieces and the same
	 * facing - in the block of the stage it was turned into.
	 */
	private static void assertSameCell(BlockState turned, BlockState before, Block expected, String what) {
		require(turned.getBlock() == expected, what + " turned " + name(before.getBlock()) + " into "
				+ name(turned.getBlock()) + " instead of " + name(expected));
		require(MBEighthProperties.mask(turned) == MBEighthProperties.mask(before), what + " left "
				+ Integer.bitCount(MBEighthProperties.mask(turned)) + " eighths in the cell where there were "
				+ Integer.bitCount(MBEighthProperties.mask(before)) + ": " + turned);
		require(turned.getValue(MBEighthProperties.FACING) == before.getValue(MBEighthProperties.FACING),
				what + " turned the cell from " + before.getValue(MBEighthProperties.FACING) + " to "
						+ turned.getValue(MBEighthProperties.FACING));
	}

	/** Clicks {@code pos} with a fresh stack of {@code item} held the way a player holds it. */
	private static InteractionResult click(ServerPlayer player, Item item, BlockPos pos) {
		ItemStack stack = new ItemStack(item);

		player.setItemInHand(InteractionHand.MAIN_HAND, stack);

		return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
	}

	/** The block as the game's own weathering interface, failing the test when it does not implement it. */
	private static WeatheringCopper weathering(Block block) {
		require(block instanceof WeatheringCopper, name(block) + " is not a block the game can weather");

		return (WeatheringCopper) block;
	}

	/** @return the id of {@code block}, for failure messages. */
	private static String name(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	/** @return the id of {@code block}, or {@code nothing} when the game ages it into nothing. */
	private static String name(Optional<Block> block) {
		return block.map(MBClientGameTest::name).orElse("nothing");
	}

	/**
	 * Joins a dedicated server and checks that the mod's blocks exist on both ends and
	 * that a block change made on the server arrives at the client.
	 *
	 * <p>This is the multiplayer half of the acceptance criteria. The test owns the
	 * server process, so it does not depend on the developer client having a session
	 * with the Mojang session server.
	 */
	private static void verifyDedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext dedicatedServer = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = dedicatedServer.connect()) {
			connection.waitForChunksRender();

			BlockPos pos = new BlockPos(1, -60, 1);
			BlockPos eighthPos = new BlockPos(2, -60, 1);

			dedicatedServer.runOnServer(minecraftServer -> {
				Bootstrap.validate();
				ServerLevel level = minecraftServer.overworld();

				int serverBlocks = 0;
				int serverEighths = 0;
				Map<Identifier, Block> serverById = new HashMap<>();

				for (Block block : MBItems.displayOrder()) {
					if (block instanceof StairBlock || block instanceof SlabBlock) {
						serverById.put(BuiltInRegistries.BLOCK.getKey(block), block);
						serverBlocks++;
					} else if (block instanceof MBEighthBlock) {
						serverEighths++;
					}
				}

				// 33 stair/slab pairs and nothing else: the terracotta blocks they are
				// cut from are vanilla's own and are not registered by this mod.
				require(serverBlocks == 66, "the dedicated server registered " + serverBlocks
						+ " stair/slab blocks, expected 66");
				require(serverEighths == MBEighths.materials().size(),
						"the dedicated server registered " + serverEighths + " eighth blocks, expected "
								+ MBEighths.materials().size());
				require(serverById.containsKey(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta_slab")),
						"the dedicated server is missing morebuildingblocks:terracotta_slab");
				// Registry#getValue answers with the registry's default entry (air) for an
				// unknown id, so only getOptional can tell whether the block exists at all.
				require(BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta")).isEmpty(),
						"the mod still registers its own minecraft-like terracotta block: "
								+ BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta")));

				level.setBlockAndUpdate(pos, serverById
						.get(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta_slab"))
						.defaultBlockState());

				// An eighth block has eight booleans plus a facing, so its state ids are the real
				// test of the wire format: a cell with one piece taken out has to arrive with that
				// piece still missing.
				MBEighths.Material stoneEighths = MBEighths.byBase(Blocks.STONE);

				if (stoneEighths == null) {
					throw new AssertionError("stone has no eighth block");
				}

				BlockState holed = stoneEighths.eighth().defaultBlockState();
				holed = holed.setValue(MBEighthProperties.bit(3), false);
				level.setBlockAndUpdate(eighthPos, holed);
			});

			// The change has to survive the round trip, which also exercises the mod's
			// block state ids on the wire.
			int waited = connection.waitForChunksDownload();
			require(waited >= 0, "the client never finished downloading chunks from the dedicated server");

			context.waitTicks(20);

			context.runOnClient(minecraft -> {
				if (minecraft.level == null) {
					throw new AssertionError("the client has no level after joining the dedicated server");
				}

				Map<Identifier, Block> clientById = new HashMap<>();

				for (Item item : BuiltInRegistries.ITEM) {
					if (item instanceof BlockItem blockItem
							&& MoreBuildingBlocks.MOD_ID.equals(item.builtInRegistryHolder().key().identifier().getNamespace())) {
						clientById.put(BuiltInRegistries.ITEM.getKey(item), blockItem.getBlock());
					}
				}

				int expectedItems = 66 + MBEighths.materials().size();

				require(clientById.size() == expectedItems,
						"the client knows " + clientById.size() + " mod items, expected " + expectedItems);
				require(clientById.containsKey(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta_slab")),
						"the client is missing morebuildingblocks:terracotta_slab");

				BlockState received = minecraft.level.getBlockState(pos);
				require(received.is(clientById.get(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta_slab"))),
						"the block the server placed did not arrive at the client: " + received);

				BlockState receivedEighth = minecraft.level.getBlockState(eighthPos);
				require(receivedEighth.is(clientById.get(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "eighth_stone"))),
						"the eighth block the server placed did not arrive at the client: " + receivedEighth);
				require(!receivedEighth.getValue(MBEighthProperties.bit(3)),
						"the eighth block arrived with a piece the server had taken out: " + receivedEighth);
			});
		}
	}

	/**
	 * Places a slab through the real {@link BlockPlaceContext} path and checks that the
	 * upright rule chose the intended axis and half.
	 *
	 * <p>This is the one part of the feature a screenshot cannot prove: a slab whose axis
	 * is wrong still renders as a plausible thin wall, and the rule reads the click
	 * position on the clicked face rather than the camera. The clicked face is divided
	 * into a middle third and four bands around it: the middle takes the half behind
	 * that face, and each band takes the half of the block on the same side as the band -
	 * a click at a face's own eastern edge fills the eastern half, and a click at the
	 * northern edge of a top face the northern half.
	 */
	private static void verifyPlacement(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(0, FLOOR_Y + 24, 0);

			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());

			// The top face is read with east to the right and north upwards, so each band fills the
			// half on its own side of the block: north band the northern half, south band the
			// southern, west band the western half and east band the eastern one.
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.negative(Direction.Axis.Y), "top face centre");
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.1), Direction.UP,
					Fill.negative(Direction.Axis.Z), "top face north band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.9), Direction.UP,
					Fill.positive(Direction.Axis.Z), "top face south band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.negative(Direction.Axis.X), "top face west band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.positive(Direction.Axis.X), "top face east band");

			// A side face that looks north carries the same two axes: east to the right and down the
			// face, so its bands name the same halves as the top face's do. The centre fills the half
			// behind the face - the southern half - which carries a wall on straight.
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.positive(Direction.Axis.Z), "north face centre");
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.1, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Y), "north face bottom band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.9, 0.05), Direction.NORTH,
					Fill.positive(Direction.Axis.Y), "north face top band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.positive(Direction.Axis.X), "north face east band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.X), "north face west band");

			// A face that looks east is read with south to the right and down the face, so its north
			// band fills the northern half and its south band the southern one. Its right band fills
			// the southern half as well, both being the half on the side those two edges sit on.
			checkPlacement(player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.5), Direction.EAST,
					Fill.negative(Direction.Axis.X), "east face centre");
			checkPlacement(player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.1), Direction.EAST,
					Fill.negative(Direction.Axis.Z), "east face north band");
			checkPlacement(player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.9), Direction.EAST,
					Fill.positive(Direction.Axis.Z), "east face south band");
			checkPlacement(player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.1, 0.5), Direction.EAST,
					Fill.negative(Direction.Axis.Y), "east face bottom band");
			checkPlacement(player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.9, 0.5), Direction.EAST,
					Fill.positive(Direction.Axis.Y), "east face top band");

			// The bottom face is read the same way as the top one, mirrored: east is still to the
			// right, but its second axis runs north as well, so the band against its northern edge
			// fills the northern half all the same.
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.positive(Direction.Axis.Y), "bottom face centre");
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.1), Direction.DOWN,
					Fill.negative(Direction.Axis.Z), "bottom face north band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.9), Direction.DOWN,
					Fill.positive(Direction.Axis.Z), "bottom face south band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.negative(Direction.Axis.X), "bottom face west band");
			checkPlacement(player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.positive(Direction.Axis.X), "bottom face east band");

			verifyClickFrame(level, player, slabBlock);
		});

		verifyBandSides(server);
	}

	/** One band of one face: where on that face the click lands, and the half it has to fill. */
	private record Band(Direction face, double x, double y, double z, Fill half, String what) {
	}

	/**
	 * Clicks every band of every face at a block that is not the origin, with the click
	 * positions written in the world's own coordinates rather than in the rule's.
	 *
	 * <p>This is the check the rest of the placement test cannot make. {@code verifyPlacement}
	 * chooses its click positions from the same reading of a face that the rule uses, so a face
	 * whose frame is mirrored labels its own bands backwards and both agree: the click it calls
	 * the eastern band is the western one, and the half that comes back is the half that band
	 * names, so every assertion passes while the rule fills the wrong side of the block for a
	 * player. The bands below are named by where they are on the block - the eastern band is the
	 * one at the larger x - so a mirrored frame fails here instead of agreeing with itself.
	 *
	 * <p>The block sits at {@code (3, ..., 7)} so that a rule reading the click as a world
	 * position rather than as an offset from the block's corner disagrees by whole blocks here
	 * too. The expected halves are named by their visible face, which is the half the band lies
	 * against: the eastern half of the block is the one a slab shows to the east.
	 */
	private static void verifyBandSides(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(3, FLOOR_Y + 30, 7);

			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());

			double y = pos.getY() + 0.5;

			for (Band band : List.of(
					// The four side faces: one band against each end of each axis they run along.
					new Band(Direction.NORTH, pos.getX() + 0.1, y, pos.getZ(), Fill.negative(Direction.Axis.X),
							"north face west band"),
					new Band(Direction.NORTH, pos.getX() + 0.9, y, pos.getZ(), Fill.positive(Direction.Axis.X),
							"north face east band"),
					new Band(Direction.SOUTH, pos.getX() + 0.1, y, pos.getZ() + 1.0, Fill.negative(Direction.Axis.X),
							"south face west band"),
					new Band(Direction.SOUTH, pos.getX() + 0.9, y, pos.getZ() + 1.0, Fill.positive(Direction.Axis.X),
							"south face east band"),
					new Band(Direction.WEST, pos.getX(), y, pos.getZ() + 0.1, Fill.negative(Direction.Axis.Z),
							"west face north band"),
					new Band(Direction.WEST, pos.getX(), y, pos.getZ() + 0.9, Fill.positive(Direction.Axis.Z),
							"west face south band"),
					new Band(Direction.EAST, pos.getX() + 1.0, y, pos.getZ() + 0.1, Fill.negative(Direction.Axis.Z),
							"east face north band"),
					new Band(Direction.EAST, pos.getX() + 1.0, y, pos.getZ() + 0.9, Fill.positive(Direction.Axis.Z),
							"east face south band"),
					// The two horizontal faces: their second axis is the world's north, so the band
					// nearest the player's north is the one at the smaller z.
					new Band(Direction.UP, pos.getX() + 0.1, pos.getY() + 1.0, pos.getZ() + 0.5,
							Fill.negative(Direction.Axis.X), "top face west band"),
					new Band(Direction.UP, pos.getX() + 0.9, pos.getY() + 1.0, pos.getZ() + 0.5,
							Fill.positive(Direction.Axis.X), "top face east band"),
					new Band(Direction.UP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.1,
							Fill.negative(Direction.Axis.Z), "top face south band"),
					new Band(Direction.UP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.9,
							Fill.positive(Direction.Axis.Z), "top face north band"),
					new Band(Direction.DOWN, pos.getX() + 0.1, pos.getY(), pos.getZ() + 0.5,
							Fill.negative(Direction.Axis.X), "bottom face west band"),
					new Band(Direction.DOWN, pos.getX() + 0.9, pos.getY(), pos.getZ() + 0.5,
							Fill.positive(Direction.Axis.X), "bottom face east band"),
					new Band(Direction.DOWN, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.1,
							Fill.negative(Direction.Axis.Z), "bottom face south band"),
					new Band(Direction.DOWN, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.9,
							Fill.positive(Direction.Axis.Z), "bottom face north band"))) {
				checkPlacement(player, slabBlock, pos, new Vec3(band.x(), band.y(), band.z()), band.face(),
						band.half(), band.what());
			}
		});
	}

	/**
	 * Reads the same clicks off a block that does not sit at the origin.
	 *
	 * <p>The rule measures a click from the corner of the block it was made against, while a
	 * {@link BlockPlaceContext} hands out the click as a position in the world. Every other case in
	 * this test clicks a block at the origin, where the two are the same vector and a rule that
	 * forgot to subtract the block's own corner would still read every click correctly. The block
	 * here is far enough from the origin that the two disagree by whole blocks, so a click at the
	 * centre of one of its faces comes back as the middle third of that face - and not as some
	 * point blocks away, which is what reading the world position as a local one produces.
	 */
	private static void verifyClickFrame(ServerLevel level, ServerPlayer player, Block slabBlock) {
		BlockPos pos = new BlockPos(11, FLOOR_Y + 24, -7);
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());

		checkPlacement(player, slabBlock, pos, new Vec3(pos.getX() + 0.5, FLOOR_Y + 25.0, pos.getZ() + 0.5),
				Direction.UP, Fill.negative(Direction.Axis.Y), "top face centre far from the origin");
		checkPlacement(player, slabBlock, pos, new Vec3(pos.getX() + 0.5, FLOOR_Y + 25.0, pos.getZ() + 0.9),
				Direction.UP, Fill.positive(Direction.Axis.Z), "top face north band far from the origin");
		checkPlacement(player, slabBlock, pos, new Vec3(pos.getX() + 0.5, FLOOR_Y + 24.5, pos.getZ()),
				Direction.NORTH, Fill.positive(Direction.Axis.Z), "north face centre far from the origin");
	}

	/**
	 * Places a glazed slab everywhere a plain slab can be placed and checks that every one of them
	 * comes out the same shape, from the six faces a player can point at and from the four
	 * directions a player can stand in.
	 *
	 * <p>A glazed slab is its own block class, {@link
	 * com.camelie.morebuildingblocks.block.MBGlazedSlabBlock}, registered by the mod, and every
	 * other placement check here picks its slab out of the vanilla registry and so skips all
	 * sixteen of them. They are meant to place exactly as a plain slab does - the upright rule is a
	 * mixin on {@code SlabBlock} and a glazed slab only adds the property the pattern is turned by
	 * - so this is where that claim is measured rather than assumed.
	 *
	 * <p>It is not enough to click the ground: an upright slab is placed as one block and read as
	 * another, and a click against a slab that is already standing takes a different branch of the
	 * rule than a click against an empty cell. So each block is tried twice - once in an empty cell,
	 * and once as the upright slab itself, with the cell the click aims at cleared first. This
	 * second half is the one that covers the reported bug: with the two broad faces swapped, a
	 * straight click on a standing glazed slab goes to the neighbouring cell instead of doubling it
	 * up, and nothing in the empty-cell half can see that.
	 *
	 * <p>The two placements are compared half by half rather than as whole states, because they are
	 * different blocks and their states can never be equal as they stand. What has to match is the
	 * shape: the same half filled, on the same axis, with the same upright flag. The pattern's own
	 * direction is not part of that comparison but is checked alongside it: a slab placed into a
	 * cell of its own is turned to the player's facing rather than to a fixed direction, while one
	 * that doubles the slab it landed on keeps the pattern that half was placed with.
	 */
	private static void verifyGlazedSlabs(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block plain = vanillaSlabs().get(0);
			BlockPos plainFloor = new BlockPos(2, FLOOR_Y + 33, 2);
			BlockPos glazedFloor = new BlockPos(2, FLOOR_Y + 33, 8);
			BlockPos plainWall = new BlockPos(8, FLOOR_Y + 33, 2);
			BlockPos glazedWall = new BlockPos(8, FLOOR_Y + 33, 8);

			level.setBlockAndUpdate(plainFloor.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(glazedFloor.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(plainWall.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(glazedWall.below(), Blocks.STONE.defaultBlockState());

			for (Block glazed : glazedSlabs()) {
				// Every click this check makes, on the floor and against the standing slab, is the
				// same click on both blocks, so they are collected once and replayed twice.
				for (Direction face : Direction.values()) {
					for (Direction facing : Direction.Plane.HORIZONTAL) {
						player.setYRot(facing.toYRot());

						compareStates(glazed, level, player, plain, plainFloor, glazedFloor, face, facing);
					}
				}

				for (Fill fill : List.of(Fill.negative(Direction.Axis.Z), Fill.positive(Direction.Axis.Z),
						Fill.negative(Direction.Axis.X), Fill.positive(Direction.Axis.X))) {
					level.setBlockAndUpdate(plainWall, VerticalSlab.withFill(plain.defaultBlockState(), fill));
					level.setBlockAndUpdate(glazedWall, VerticalSlab.withFill(glazed.defaultBlockState(), fill));

					String what = fill.axis() + " half " + (fill.positive() ? "positive" : "negative");

					for (Direction face : Direction.values()) {
						clearAround(level, plainWall);
						clearAround(level, glazedWall);
						player.setYRot(0.0F);

						compareStates(glazed, level, player, plain, plainWall, glazedWall, face,
								"the " + face + " face of an upright slab filling the " + what);
						checkCompletingIsInPlace(level, player, glazed, glazedWall, face,
								"the " + face + " face of an upright slab filling the " + what);
					}

					level.setBlockAndUpdate(plainWall, Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(glazedWall, Blocks.AIR.defaultBlockState());
				}
			}

			level.setBlockAndUpdate(plainFloor, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(glazedFloor, Blocks.AIR.defaultBlockState());
		});
	}

	/**
	 * Clicks one face of both blocks and checks that the glazed slab was placed as the plain one
	 * was.
	 *
	 * @param what what is being clicked, for the failure message
	 */
	private static void compareStates(Block glazed, ServerLevel level, ServerPlayer player, Block plain,
			BlockPos plainPos, BlockPos glazedPos, Direction face, String what) {
		BlockState plainState = clickBlock(level, player, plain, plainPos, face);
		BlockState glazedState = clickBlock(level, player, glazed, glazedPos, face);
		Identifier id = BuiltInRegistries.BLOCK.getKey(glazed);

		require(plainState != null && glazedState != null,
				"clicking " + what + " with " + id + " produced no state for the plain slab or the glazed one: "
						+ plainState + " and " + glazedState);

		require(Fill.of(plainState).equals(Fill.of(glazedState)),
				"clicking " + what + " with " + id + " filled " + Fill.of(glazedState) + ", but the same click on a"
						+ " plain slab filled " + Fill.of(plainState) + "; a glazed slab carries the pattern's"
						+ " direction and nothing else, so the two have to fill the same half");

		require(VerticalSlab.isVertical(plainState) == VerticalSlab.isVertical(glazedState),
				"clicking " + what + " with " + id + " stood the glazed slab "
						+ (VerticalSlab.isVertical(glazedState) ? "up" : "flat") + " where a plain slab went "
						+ (VerticalSlab.isVertical(plainState) ? "up" : "flat") + ": " + glazedState);

		Direction turned = glazedState.getValue(MBBlockStateProperties.GLAZED_FACING);
		// A click that doubles the slab it landed on fills in the rest of that block, so the
		// pattern stays the one the half already there was placed with; only a slab going into a
		// cell of its own takes the player's facing.
		BlockState landedOn = level.getBlockState(glazedPos);
		Direction expected = glazedState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE && landedOn.is(glazed)
				? landedOn.getValue(MBBlockStateProperties.GLAZED_FACING)
				: player.getDirection().getOpposite();

		require(turned == expected,
				"clicking " + what + " with " + id + " turned the pattern to " + turned + " for a player looking "
						+ player.getDirection() + ", expected " + expected + "; a doubled slab keeps the pattern of"
						+ " the half that was there first, and only a slab placed in a cell of its own takes the"
						+ " player's facing");
	}

	/** Clicks one face of both blocks, with the player standing the way {@code facing} says. */
	private static void compareStates(Block glazed, ServerLevel level, ServerPlayer player, Block plain,
			BlockPos plainPos, BlockPos glazedPos, Direction face, Direction facing) {
		compareStates(glazed, level, player, plain, plainPos, glazedPos, face,
				"the " + face + " face of " + BuiltInRegistries.BLOCK.getKey(glazed) + " from " + facing);
	}

	/**
	 * Checks the one thing a glazed slab is asked for by itself: that a click on the open face of an
	 * upright slab completes that slab rather than placing another one beside it.
	 *
	 * <p>Every other check in this fixture compares the glazed slab against a plain one, and a
	 * comparison cannot see a rule that is wrong the same way on both sides - invert which face
	 * completes an upright slab and both slabs still answer identically, so they agree with each
	 * other while both are wrong.
	 *
	 * <p>Asking one block is not enough on its own either: the face this check picks and the face the
	 * rule answers with are read from the same method, so a plane that was chosen wrongly still
	 * agrees with itself. What breaks the circle is the shape. The face is taken from
	 * {@link VerticalSlab#openFace}, and the half it is supposed to close off is measured off the
	 * block's own bounds - it has to be the empty half, or the "open" face is really the boundary at
	 * the far end of the filled one, and the click this check makes is being made on the wrong side
	 * of the slab. That is a real fault and a silent one: everything in the log looks right, and two
	 * slabs placed by pointing at the wall come out on opposite sides of it.
	 */
	private static void checkCompletingIsInPlace(ServerLevel level, ServerPlayer player, Block glazed, BlockPos pos,
			Direction face, String what) {
		BlockState clicked = level.getBlockState(pos);

		if (!VerticalSlab.isVertical(clicked) || VerticalSlab.isDouble(clicked)
				|| face != VerticalSlab.openFace(clicked)) {
			return;
		}

		Identifier id = BuiltInRegistries.BLOCK.getKey(glazed);
		Direction.Axis axis = clicked.getValue(MBBlockStateProperties.AXIS);
		AABB box = clicked.getShape(level, pos).bounds();
		double filled = box.max(axis) - box.min(axis);
		boolean emptyTowardsFace = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
				? box.max(axis) < 1.0
				: box.min(axis) > 0.0;

		require(emptyTowardsFace,
				"the open face of a vertical slab filling " + axis + " from " + box.min(axis) + " to " + box.max(axis)
						+ " is read as " + face + ", which is the boundary of the filled half; the open face closes"
						+ " off the empty half, and a click made on the wrong one of the two planes goes to the"
						+ " wrong side of the wall");

		BlockPlaceContext context = clickContext(level, player, glazed, pos, face);
		BlockState placed = glazed.getStateForPlacement(context);

		require(placed != null, "clicking " + what + " with " + id + " placed nothing");
		require(context.getClickedPos().equals(pos),
				"clicking " + what + " with " + id + " aimed the placement at " + context.getClickedPos()
						+ " instead of the slab's own cell at " + pos + "; the open face completes the slab, so"
						+ " the click belongs to the block that is there");
		require(placed.getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
				"clicking " + what + " with " + id + " produced " + placed.getValue(SlabBlock.TYPE)
						+ " rather than a double slab; a click on the open face fills the empty half of the"
						+ " slab it is on");
		require(filled == 0.5,
				"the slab at " + pos + " fills " + filled + " of its cell rather than half, so its open face has"
						+ " no empty half to close off");
	}

	/** Empties the six cells around {@code pos}, so each click is made against the slab alone. */
	private static void clearAround(ServerLevel level, BlockPos pos) {
		for (Direction face : Direction.values()) {
			BlockPos side = pos.relative(face);

			if (!level.getBlockState(side).isAir()) {
				level.setBlockAndUpdate(side, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** Every glazed slab the mod registers. */
	private static List<Block> glazedSlabs() {
		List<Block> slabs = new ArrayList<>();

		BuiltInRegistries.BLOCK.forEach(block -> {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);

			if (block instanceof GlazedPattern && block instanceof SlabBlock
					&& MoreBuildingBlocks.MOD_ID.equals(id.getNamespace())) {
				slabs.add(block);
			}
		});

		return slabs;
	}

	/**
	 * Clicks the centre of one face of {@code pos} with {@code block} in hand and returns the state
	 * the block would be placed as, or {@code null} when the click places nothing.
	 *
	 * <p>The click is put on the real face of whatever is in the cell: the empty cell the tests
	 * build in has no shape at all, and asking one for its bounds throws, so a cell holding air is
	 * clicked at the boundary of the block itself - which is where its face is anyway.
	 */
	private static BlockState clickBlock(ServerLevel level, ServerPlayer player, Block block, BlockPos pos,
			Direction face) {
		return block.getStateForPlacement(clickContext(level, player, block, pos, face));
	}

	/**
	 * Builds the click the tests place blocks with, so that a check can ask where the click landed as
	 * well as what came of it.
	 *
	 * <p>The context is the thing the placement rule reads, and it carries the answer to the question
	 * this fixture exists for: which cell the block is placed into. A state alone cannot say whether
	 * a click on an upright slab's open face was taken as completing that slab - which replaces the
	 * block in its own cell - or as an ordinary placement in the cell beyond it, and only the context
	 * distinguishes the two.
	 */
	private static BlockPlaceContext clickContext(ServerLevel level, ServerPlayer player, Block block, BlockPos pos,
			Direction face) {
		BlockState clicked = level.getBlockState(pos);
		double extent = 0.0;

		if (!clicked.isAir()) {
			AABB box = clicked.getShape(level, pos).bounds();
			extent = face.getAxis().choose(box.getXsize(), box.getYsize(), box.getZsize()) / 2.0;
		}

		Vec3 hit = Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(extent));

		return new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(block.asItem()), new BlockHitResult(hit, face, pos, false));
	}

	/**
	 * Clicks the two faces of an upright slab that look at the empty half - the open one, and the
	 * far boundary of the filled one - and checks which block and which half each lands in.
	 *
	 * <p>Those two faces are the same plane seen from either side, and the difference between them
	 * is what the fix is about. The fixture wall is {@code axis=z, type=bottom}, which fills the
	 * southern half of its cell - an upright {@code bottom} is the half showing its face towards the
	 * positive end of the axis, unlike a flat slab - so the plane it shows a player is its northern
	 * face, at the empty half beside it, and the boundary the filled half runs up to is its southern
	 * one.
	 *
	 * <ul>
	 *   <li>The <em>open</em> face - the plane closing off the empty half, the one the slab is seen
	 *       as, {@link VerticalSlab#openFace} - is at the north. Pointing at it means the block is
	 *       only half done: the click stays on the slab so the placement rule sees it, and the
	 *       answer there is a double slab rather than a second block beside it.</li>
	 *   <li>The far boundary of the filled half - the plane of the block's own interior on the other
	 *       side, where the next block of this wall would stand - is at the south. Pointing at it
	 *       means "keep this wall going", so the new slab belongs in the neighbouring cell, filling
	 *       the very half the clicked one fills so the two line up into one plane rather than
	 *       turning the wall a quarter turn at the joint.</li>
	 * </ul>
	 *
	 * <p>Both clicks used to be read as if they were the same face: the open one completed the
	 * block from the wrong side of it, and a click on the far boundary stayed in the clicked slab's
	 * own cell and doubled it up instead of continuing the wall.
	 */
	private static void verifyUprightStacking(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(6, FLOOR_Y + 24, 0);
			BlockState wall = vertical(slabBlock, Direction.Axis.Z, SlabType.BOTTOM);

			// The open face is geometry, not a naming choice, so it is checked against the shape the
			// game itself builds for this state before anything is placed.
			Direction open = VerticalSlab.openFace(wall);

			checkOpenFaceGeometry(wall, open);

			Direction far = open.getOpposite();
			BlockPos beyond = pos.relative(far);
			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(beyond.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(pos, wall);
			level.setBlockAndUpdate(beyond, Blocks.AIR.defaultBlockState());

			// The click has to be taken out of the slab: the far boundary of the filled half is a
			// plane of the block's own interior, so a placement pointed at it starts the next block
			// and getClickedPos() is the cell beyond. That face is the wall seen from its side - a
			// whole face of the block like any face of a plain block - so the click is read by the
			// frame, and a click at its centre fills the half lying against this wall: the same plane
			// and thickness, which is what pointing at the side of a wall means. Clicks are written in
			// world coordinates like every other one in this test, so they are built from the block's
			// own centre rather than assuming the block sits at the origin. The hit point sits exactly
			// on the boundary between the two cells, which is where the shared plane is.
			Vec3 centre = Vec3.atCenterOf(pos);

			BlockPlaceContext continuation = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
					new ItemStack(slabBlock.asItem()),
					new BlockHitResult(centre.add(far.getUnitVec3().scale(0.5)), far, pos, false));

			require(continuation.getClickedPos().equals(beyond),
					"clicking the far boundary of an upright slab's filled half aimed at " + continuation.getClickedPos()
							+ ", expected the block beyond it at " + beyond);
			BlockState continued = slabBlock.getStateForPlacement(continuation);

			require(continued != null, "no block state was produced for a click on an upright slab's far boundary");
			require(continued.getValue(MBBlockStateProperties.VERTICAL),
					"the slab placed against the far boundary of an upright slab came out flat: " + continued);

			require(Fill.of(continued).equals(Fill.opposite(far)),
					"the slab placed against the far boundary of a slab filling " + Fill.of(wall) + " filled "
							+ Fill.of(continued) + ", expected the half lying against that wall ("
							+ Fill.opposite(far) + ") so the wall carries on in its own plane: " + continued);

			// The open face is the plane closing off the empty half, and pointing at it means the
			// block is only half done: the click is left on the slab so the placement rule sees it,
			// and the answer there is a double slab rather than a second block beside it. This is
			// the one broad face of an upright slab that is left alone; the two thin sides hand the
			// click on to the next cell, and the far boundary is read like a plain block's face.
			BlockPos openSide = pos.relative(open);
			level.setBlockAndUpdate(openSide, Blocks.AIR.defaultBlockState());

			checkAgainstOpenFace(player, slabBlock, pos, wall);

			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(beyond, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(openSide, Blocks.AIR.defaultBlockState());
		});
	}

	/**
	 * Clicks the faces of an upright slab that carry the wall into the next cell - the two thin
	 * sides - and checks the wall lands there in the clicked slab's own half, then clicks the far
	 * boundary and checks that face is read by position instead, and the open face and checks that
	 * it completes the block.
	 *
	 * <p>The two thin sides are the reason the wall rule is a test of its own. A pick ray aimed at a
	 * thin side ends on a half-block-wide plane, and the face a player is looking at and the half
	 * being filled are then different axes entirely: reading the click position to choose the half
	 * fills the wrong one, which turns the wall into a block and a half at that joint.
	 *
	 * <p>The far boundary is the other half of that story, and is why the rule is not simply "every
	 * face of a slab but the open one". It is the side of the wall, so a player pointing at it is
	 * pointing at a whole face of a block, exactly as they would at a plain block's face - and it is
	 * read that way too, which is what keeps a wall's side usable for starting another one.
	 */
	private static void verifyWallContinuation(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(6, FLOOR_Y + 30, 0);

			// "South-facing": the filled half is the positive half of Z, so the plane the slab shows
			// a player - the empty half's boundary, the one that completes the block - looks north,
			// and the boundary the filled half runs up to, the one that carries the wall onward, is
			// the south one.
			BlockState wall = vertical(slabBlock, Direction.Axis.Z, SlabType.BOTTOM);
			level.setBlockAndUpdate(pos, wall);

			// Both thin sides are the wall seen end on, and both carry it into the cell they look
			// at, filled with the clicked slab's own half so the wall keeps its plane and thickness.
			checkAgainstWall(player, slabBlock, pos, wall, Direction.EAST,
					new Vec3(pos.getX() + 1.0, FLOOR_Y + 30.5, pos.getZ() + 0.5), pos.east());
			checkAgainstWall(player, slabBlock, pos, wall, Direction.WEST,
					new Vec3(pos.getX(), FLOOR_Y + 30.5, pos.getZ() + 0.5), pos.west());

			// The boundary the filled half runs up to - the far side of the block's own interior - is
			// the wall seen from its side, a whole face of the block like any face of a plain block,
			// so the click is read by its position instead of being handed the clicked slab's half.
			// At the centre the region names the half lying against this wall, which carries the wall
			// on in its own plane; a band names the half its own edge sits on instead.
			checkAgainstBoundary(player, slabBlock, pos, Direction.SOUTH,
					new Vec3(pos.getX() + 0.5, FLOOR_Y + 30.5, pos.getZ() + 1.0),
					Fill.opposite(Direction.SOUTH), "centre of the far boundary");
			checkAgainstBoundary(player, slabBlock, pos, Direction.SOUTH,
					new Vec3(pos.getX() + 0.5, FLOOR_Y + 30.8, pos.getZ() + 1.0),
					Fill.positive(Direction.Axis.Y), "top band of the far boundary");
			checkAgainstBoundary(player, slabBlock, pos, Direction.SOUTH,
					new Vec3(pos.getX() + 0.1, FLOOR_Y + 30.5, pos.getZ() + 1.0),
					Fill.negative(Direction.Axis.X), "west band of the far boundary");

			// The open face is the one face that finishes the block instead.
			checkAgainstOpenFace(player, slabBlock, pos, wall);

			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		});
	}

	/**
	 * Clicks the four edges of a flat slab and checks that its half carries on into the cell beside
	 * it, the way an upright slab's thin sides carry its wall on.
	 *
	 * <p>An edge of a flat slab is half a block deep, so there is no frame inside it to read: a
	 * click anywhere on one means the cell it looks at, filled with the clicked slab's own half.
	 * Reading the click position instead stands an upright slab up against the joint - a quarter
	 * turn away from the floor the player is laying - so each edge is clicked at both of its ends
	 * here. The face the filled half runs up to is a whole one and is still read by position, that
	 * being the face a player starts a wall against.
	 */
	private static void verifyFlatContinuation(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(6, FLOOR_Y + 42, 6);
			BlockState flat = slabBlock.defaultBlockState();

			require(!VerticalSlab.isVertical(flat), "the check did not build a flat slab: " + flat);

			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(pos, flat);

			double low = pos.getY() + 0.25;

			checkFlatEdge(player, slabBlock, pos, flat, Direction.NORTH,
					new Vec3(pos.getX() + 0.1, low, pos.getZ()), pos.north());
			checkFlatEdge(player, slabBlock, pos, flat, Direction.NORTH,
					new Vec3(pos.getX() + 0.9, low, pos.getZ()), pos.north());
			checkFlatEdge(player, slabBlock, pos, flat, Direction.EAST,
					new Vec3(pos.getX() + 1.0, low, pos.getZ() + 0.1), pos.east());
			checkFlatEdge(player, slabBlock, pos, flat, Direction.EAST,
					new Vec3(pos.getX() + 1.0, low, pos.getZ() + 0.9), pos.east());
			checkFlatEdge(player, slabBlock, pos, flat, Direction.SOUTH,
					new Vec3(pos.getX() + 0.1, low, pos.getZ() + 1.0), pos.south());
			checkFlatEdge(player, slabBlock, pos, flat, Direction.SOUTH,
					new Vec3(pos.getX() + 0.9, low, pos.getZ() + 1.0), pos.south());
			checkFlatEdge(player, slabBlock, pos, flat, Direction.WEST,
					new Vec3(pos.getX(), low, pos.getZ() + 0.1), pos.west());
			checkFlatEdge(player, slabBlock, pos, flat, Direction.WEST,
					new Vec3(pos.getX(), low, pos.getZ() + 0.9), pos.west());

			// The boundary the filled half runs up to is a whole face, read by position like any
			// plain block's face: a band of it starts the half that band names rather than carrying
			// this slab's own half on.
			checkAgainstBoundary(player, slabBlock, pos, Direction.DOWN,
					new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.1),
					Fill.negative(Direction.Axis.Z), "band of a flat slab's boundary");

			// The open face still finishes this block rather than placing a second one beside it.
			checkAgainstOpenFace(player, slabBlock, pos, flat);

			// The upper half of the block reads the same way, carrying its own - upper - half on.
			BlockState upper = flat.setValue(SlabBlock.TYPE, SlabType.TOP);
			level.setBlockAndUpdate(pos, upper);
			checkFlatEdge(player, slabBlock, pos, upper, Direction.NORTH,
					new Vec3(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ()), pos.north());

			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
		});
	}

	/**
	 * Clicks the face of a completed - double - upright slab that used to be its open face, once
	 * with the same slab in hand and once with an unrelated block, and checks that both land in the
	 * cell beyond rather than in the slab's own cell.
	 *
	 * <p>A double slab fills its whole block and has no empty half, so no click on it completes it
	 * any more. Reading that face as the open one is what left the click inside the block: a double
	 * cannot be replaced, so the slab in hand was refused and did nothing at all, while any other
	 * block pointed at the same face replaced the slab. Nothing about that click is special once the
	 * face is read like the face of a plain block, which is what this checks - and the half it fills
	 * is the one lying against the slab, so a wall carries on through the block that was completed.
	 */
	private static void verifyDoubleSlabClicks(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(6, FLOOR_Y + 36, 0);
			BlockState wall = vertical(slabBlock, Direction.Axis.Z, SlabType.BOTTOM);
			BlockState doubled = wall.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
			BlockPos beyond = pos.relative(Direction.NORTH);

			require(VerticalSlab.isDouble(doubled), "the check did not build a double upright slab: " + doubled);

			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(pos, doubled);
			level.setBlockAndUpdate(beyond, Blocks.AIR.defaultBlockState());

			Vec3 centre = Vec3.atCenterOf(pos);
			Vec3 hit = centre.add(Direction.NORTH.getUnitVec3().scale(0.5));

			// The face the slab used to show half full is a whole face of a full block now, so it is
			// read the way a plain block's face is: the click starts the next block in the cell the
			// face looks at, filled with the half lying against this one.
			BlockPlaceContext same = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
					new ItemStack(slabBlock.asItem()),
					new BlockHitResult(hit, Direction.NORTH, pos, false));

			require(same.getClickedPos().equals(beyond),
					"clicking a completed upright slab with the same slab aimed at " + same.getClickedPos()
							+ ", expected the cell beyond its face at " + beyond);

			BlockState placed = slabBlock.getStateForPlacement(same);

			require(placed != null, "no block state was produced for a click on a completed upright slab");
			require(!VerticalSlab.isDouble(placed),
					"clicking a completed upright slab with the same slab doubled something: " + placed);
			require(Fill.of(placed).equals(Fill.opposite(Direction.NORTH)),
					"the slab placed against a completed upright slab filled " + Fill.of(placed)
							+ ", expected the half lying against it " + Fill.opposite(Direction.NORTH)
							+ " so the wall carries on: " + placed);

			// Any other block aimed at the same face has to land beside the slab as well: the whole
			// point of the click moving out is that the cell being built in is not the one clicked.
			BlockPlaceContext other = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
					new ItemStack(Blocks.STONE), new BlockHitResult(hit, Direction.NORTH, pos, false));

			require(other.getClickedPos().equals(beyond),
					"clicking a completed upright slab with another block aimed at " + other.getClickedPos()
							+ ", expected the cell beyond its face at " + beyond);

			level.setBlockAndUpdate(other.getClickedPos(), Blocks.STONE.defaultBlockState());

			require(level.getBlockState(pos).equals(doubled),
					"the completed upright slab was replaced by the block placed against it: "
							+ level.getBlockState(pos));

			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(beyond, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
		});
	}

	/**
	 * Checks what makes a face the open one against the shape the game itself builds for the state:
	 * the open face is the plane closing off the half the slab does not fill, so the slab covers
	 * exactly half of it - the hollow a player sees, with the empty cell behind. The boundary the
	 * filled half runs up to is covered completely, and the two thin sides are only a sliver of
	 * their faces. A single coordinate cannot tell those apart, which is why this measures the whole
	 * face.
	 */
	private static void checkOpenFaceGeometry(BlockState wall, Direction open) {
		AABB box = wall.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
		Direction.Axis axis = open.getAxis();
		Direction.Axis u = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
		Direction.Axis v = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;

		// The open face has to be the side the empty half is on: the space between it and the cell
		// boundary it faces is the half the slab does not fill, and the space on its other side is
		// the half it does. Measuring the first of those as a volume is what this is for - a plane
		// in the middle of the cell would leave a quarter of the block empty and fail, and so would
		// the far boundary of the filled half, which has no empty space behind it at all.
		double depth = open.getAxisDirection() == Direction.AxisDirection.POSITIVE
				? 1.0 - box.max(axis)
				: box.min(axis);
		double empty = (box.max(u) - box.min(u)) * (box.max(v) - box.min(v)) * depth;

		require(empty == 0.5,
				"an upright slab built as " + wall + " has its box at " + box + ", so the space behind its " + open
						+ " face holds " + empty + " of a block, yet that is the face it calls open; the open face is"
						+ " the plane closing off the half it does not fill, with that half empty behind it");
	}

	/**
	 * Clicks one of the two thin sides of an upright slab - the ends of the wall, which look at the
	 * next cell along - and checks that the wall continues into that cell.
	 *
	 * <p>The half is the clicked slab's own, which is the whole point: reading the click position
	 * instead would fill the half nearest the click every time, and since the two thin sides are
	 * opposite faces of the same wall, one of them would come out at the wrong thickness.
	 */
	private static void checkAgainstWall(ServerPlayer player, Block slabBlock, BlockPos pos,
			BlockState wall, Direction face, Vec3 hit, BlockPos expectedPos) {
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(slabBlock.asItem()), new BlockHitResult(hit, face, pos, false));

		require(context.getClickedPos().equals(expectedPos),
				"clicking the " + face + " face of an upright slab aimed at " + context.getClickedPos()
						+ ", expected the cell beyond it at " + expectedPos);

		BlockState placed = slabBlock.getStateForPlacement(context);

		require(placed != null, "no block state was produced for a click on the " + face + " face of an upright slab");
		require(SlabType.DOUBLE != placed.getValue(SlabBlock.TYPE),
				"clicking the " + face + " face of an upright slab doubled it: " + placed);
		require(placed.getValue(MBBlockStateProperties.VERTICAL),
				"the slab placed against the " + face + " face of an upright slab came out flat: " + placed);
		require(Fill.of(placed).equals(Fill.of(wall)),
				"the slab placed against the " + face + " face of an upright slab filled " + Fill.of(placed)
						+ ", expected the clicked slab's own half " + Fill.of(wall) + " so the wall keeps its plane: "
						+ placed);
	}

	/**
	 * Clicks one edge of a flat slab - the half-block-deep side of it, which looks at the next cell
	 * along - and checks that the cell beyond is filled with the clicked slab's own half, laid flat
	 * rather than stood up.
	 */
	private static void checkFlatEdge(ServerPlayer player, Block slabBlock, BlockPos pos, BlockState flat,
			Direction face, Vec3 hit, BlockPos expectedPos) {
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(slabBlock.asItem()), new BlockHitResult(hit, face, pos, false));

		require(context.getClickedPos().equals(expectedPos),
				"clicking the " + face + " edge of a flat slab aimed at " + context.getClickedPos()
						+ ", expected the cell beyond it at " + expectedPos);

		BlockState placed = slabBlock.getStateForPlacement(context);

		require(placed != null, "no block state was produced for a click on the " + face + " edge of a flat slab");
		require(SlabType.DOUBLE != placed.getValue(SlabBlock.TYPE),
				"clicking the " + face + " edge of a flat slab doubled it: " + placed);
		require(!VerticalSlab.isVertical(placed),
				"the slab placed against the " + face + " edge of a flat slab stood up: " + placed);
		require(Fill.of(placed).equals(Fill.of(flat)),
				"the slab placed against the " + face + " edge of a flat slab filled " + Fill.of(placed)
						+ ", expected the clicked slab's own half " + Fill.of(flat) + " so the floor keeps its"
						+ " plane and its level: " + placed);
	}

	/**
	 * Clicks a point on a whole face of a slab - the boundary its filled half runs up to - and
	 * checks the placement starts the next block in the cell beyond, filling the half the click
	 * position names rather than the clicked slab's own half.
	 *
	 * <p>This is the face a player uses to build against the side of a wall, so it is read the way
	 * any plain block's face is: a click at its centre carries the wall on in the same plane, and a
	 * click in one of its bands names the half that band's own edge sits on.
	 */
	private static void checkAgainstBoundary(ServerPlayer player, Block slabBlock, BlockPos pos,
			Direction face, Vec3 hit, Fill expected, String what) {
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(slabBlock.asItem()), new BlockHitResult(hit, face, pos, false));

		require(context.getClickedPos().equals(pos.relative(face)),
				"clicking the " + what + " of a slab aimed at " + context.getClickedPos()
						+ ", expected the cell beyond it at " + pos.relative(face));

		BlockState placed = slabBlock.getStateForPlacement(context);

		require(placed != null, "no block state was produced for a click on the " + what + " of a slab");
		require(!VerticalSlab.isDouble(placed),
				"clicking the " + what + " of a slab doubled the slab up: " + placed);
		require(Fill.of(placed).equals(expected),
				"clicking the " + what + " of a slab filled " + Fill.of(placed) + ", expected "
						+ expected + " from the click position: " + placed);
	}

	/**
	 * Clicks the open face - the plane closing off the empty half, which is the one a player sees
	 * the slab as - and checks that it completes the clicked block and doubles it.
	 */
	private static void checkAgainstOpenFace(ServerPlayer player, Block slabBlock, BlockPos pos,
			BlockState wall) {
		Direction face = VerticalSlab.openFace(wall);
		Vec3 hit = Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(0.5));
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(slabBlock.asItem()), new BlockHitResult(hit, face, pos, false));

		require(context.getClickedPos().equals(pos),
				"clicking the open face of a slab aimed at " + context.getClickedPos()
						+ ", expected the clicked block itself at " + pos);

		// The same click with an item that is not the slab's own completes nothing: the flag this
		// context decides with is the only gate a placement passes, so a click left in the slab's
		// own cell would land the foreign block on the slab and take its place. It has to move out
		// of the cell, the way a click on the other three faces of the slab does.
		BlockPlaceContext foreign = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(Items.STONE), new BlockHitResult(hit, face, pos, false));

		require(foreign.getClickedPos().equals(pos.relative(face)),
				"clicking the open face of a slab with a block that is not the slab aimed at "
						+ foreign.getClickedPos() + ", expected the cell beyond it at " + pos.relative(face));

		BlockState doubled = slabBlock.getStateForPlacement(context);

		require(doubled != null, "no block state was produced for a click on the open face of a slab");
		require(SlabType.DOUBLE == doubled.getValue(SlabBlock.TYPE),
				"the click on the open face of a slab produced " + doubled
						+ ", expected a double slab completing the same block");
	}

	private static void checkPlacement(ServerPlayer player, Block slabBlock, BlockPos pos,
			Vec3 hit, Direction face, Fill expected, String what) {
		BlockState placed = placement(player, slabBlock, pos, hit, face);

		require(placed != null, "no block state was produced for the " + what + " placement");
		require(SlabType.DOUBLE != placed.getValue(SlabBlock.TYPE),
				"the " + what + " placement doubled the slab: " + placed);
		require(Fill.of(placed).equals(expected),
				"the " + what + " placement filled " + Fill.of(placed) + ", expected " + expected
						+ " (clicked " + face + " at " + hit + "): " + placed);
	}

	private static BlockState placement(ServerPlayer player, Block slabBlock, BlockPos pos,
			Vec3 hit, Direction face) {
		ItemStack stack = new ItemStack(slabBlock.asItem());
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
				new BlockHitResult(hit, face, pos, false));
		return slabBlock.getStateForPlacement(context);
	}

	/**
	 * Checks the collision geometry of every form the mod touches, straight from the
	 * block state. This is what the {@code vertical} and {@code axis} properties feed
	 * into, and it is the part of the mixin a screenshot cannot prove: a wrong shape
	 * still photographs as a nice thin wall.
	 */
	private static void verifyShapes(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			for (Block slabBlock : vanillaSlabs()) {
				for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
					BlockState state = vertical(slabBlock, axis, SlabType.BOTTOM);
					VoxelShape shape = state.getShape(minecraftServer.overworld(), BlockPos.ZERO);
					AABB box = shape.bounds();

					// Half a block thick, a full block in the other two directions.
					double thickness = axis == Direction.Axis.X ? box.maxX - box.minX : box.maxZ - box.minZ;
					double width = axis == Direction.Axis.X ? box.maxZ - box.minZ : box.maxX - box.minX;

					require(Math.abs(thickness - 0.5) < 1.0E-4, slabBlock + " vertical " + axis
							+ " thickness was " + thickness + ", expected 0.5");
					require(Math.abs(width - 1.0) < 1.0E-4, slabBlock + " vertical " + axis
							+ " width was " + width + ", expected 1.0");
					require(box.minY == 0.0 && box.maxY == 1.0, slabBlock + " vertical " + axis
							+ " was not full height: " + box);
				}

				// The flat slab must still be exactly the vanilla box: the mixin adds two
				// properties, and a mistake there would quietly reshape every slab in the game.
				// It must also still be a FLAT slab: the two added properties are sorted into the
				// state definition by name, so a careless default would leave every slab upright.
				BlockState defaults = slabBlock.defaultBlockState();
				AABB flat = defaults.getShape(minecraftServer.overworld(), BlockPos.ZERO).bounds();
				require(!defaults.getValue(MBBlockStateProperties.VERTICAL) && defaults.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
						slabBlock + " default state is not a flat bottom slab: vertical="
								+ defaults.getValue(MBBlockStateProperties.VERTICAL) + " type="
								+ defaults.getValue(SlabBlock.TYPE));
				require(flat.minY == 0.0 && flat.maxY == 0.5 && flat.minX == 0.0 && flat.maxX == 1.0,
						slabBlock + " flat bottom slab shape changed: " + flat);
			}

			// The mod's own 66 forms, both halves and both axes.
			for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
				AABB stairs = family.stairs().defaultBlockState()
						.getShape(minecraftServer.overworld(), BlockPos.ZERO).bounds();
				require(Math.abs(stairs.maxY - 1.0) < 1.0E-4, family.stairsPath() + " stair shape: " + stairs);

				AABB slab = family.slab().defaultBlockState()
						.getShape(minecraftServer.overworld(), BlockPos.ZERO).bounds();
				require(Math.abs(slab.maxY - 0.5) < 1.0E-4, family.slabPath() + " slab shape: " + slab);
			}
		});
	}

	/**
	 * Checks the eighth blocks: one for every material, each with an item, the eight pieces adding
	 * up to the whole block, and a click choosing the piece it landed on - the same cell when it
	 * landed on a middle plane of it, the cell beyond the face when it landed on the block itself.
	 *
	 * <p>This is the part of the feature a screenshot cannot prove: a piece in the wrong eighth of
	 * the cell photographs as a small cube either way, and whether a click stayed in the cell it
	 * was made on is only visible in the placement context it is read from.
	 */
	private static void verifyEighths(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			List<MBEighths.Material> materials = MBEighths.materials();

			// The 33 terracotta families of the mod's own and the 100 blocks the game cuts slabs from.
			require(materials.size() == 133, "found " + materials.size() + " eighth materials, expected 133");

			for (MBEighths.Material material : materials) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(material.eighth());

				require(id.getPath().equals(material.eighthPath()),
						"the eighth block of " + material.basePath() + " is called " + id);
				require(Item.BY_BLOCK.get(material.eighth()) != null, "no item for " + id);
			}

			MBEighths.Material stone = MBEighths.byBase(Blocks.STONE);

			if (stone == null) {
				throw new AssertionError("stone has no eighth block");
			}

			Block eighth = stone.eighth();
			BlockPos pos = new BlockPos(6, FIRST_ROW_Y, 6);

			// Full by default: the item icon is the parent block itself, so a cell nothing has been
			// taken out of has to look like it.
			BlockState full = eighth.defaultBlockState();

			require(MBEighthProperties.isFull(full), "the default state of " + eighth + " is not full: " + full);
			require(sameShape(shapeOf(full, level), Shapes.block()),
					"a full cell of eighths is not the whole block: " + shapeOf(full, level).bounds());

			// One piece is the eighth of the cell its name says, and two of them are their union.
			require(sameShape(shapeOf(piece(eighth, 0), level), Block.box(0.0, 0.0, 0.0, 8.0, 8.0, 8.0)),
					"the lower north west piece is not the corner it is named after");
			require(sameShape(shapeOf(piece(eighth, 7), level), Block.box(8.0, 8.0, 8.0, 16.0, 16.0, 16.0)),
					"the upper south east piece is not the corner it is named after");
			require(sameShape(shapeOf(twoPieces(eighth, 0, 7), level),
					Shapes.or(Block.box(0.0, 0.0, 0.0, 8.0, 8.0, 8.0), Block.box(8.0, 8.0, 8.0, 16.0, 16.0, 16.0))),
					"two pieces of a cell do not add up to the shapes they name");
			require(sameShape(piece(eighth, 0).getCollisionShape(level, BlockPos.ZERO),
					shapeOf(piece(eighth, 0), level)),
					"an eighth block collides with something other than its own outline");

			// A click on the top of a block at the north west quarter of the cell above it fills the
			// piece under the click, which is the one touching the face that was clicked.
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());

			BlockState placed = eighthPlacement(player, eighth, pos,
					new Vec3(pos.getX() + 0.25, pos.getY() + 1.0, pos.getZ() + 0.25), Direction.UP);

			require(placed != null, "a click on the top of a block placed no eighth block");
			require(MBEighthProperties.mask(placed) == MBEighthProperties.mask(piece(eighth, 0)),
					"a click at the north west quarter placed " + placed + ", expected its lower north west piece");

			BlockState far = eighthPlacement(player, eighth, pos,
					new Vec3(pos.getX() + 0.75, pos.getY() + 1.0, pos.getZ() + 0.75), Direction.UP);

			require(far != null && MBEighthProperties.mask(far) == MBEighthProperties.mask(piece(eighth, 5)),
					"a click at the south east quarter placed " + far + ", expected its lower south east piece");

			// A click on a middle plane of a cell - a seam - stays in the cell and fills the eighth
			// beside the piece it landed on.
			level.setBlockAndUpdate(pos, piece(eighth, 0));

			BlockState beside = eighthPlacement(player, eighth, pos,
					new Vec3(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.25), Direction.EAST);

			require(beside != null, "a click on a seam of a cell of eighths placed nothing");
			require(MBEighthProperties.mask(beside) == MBEighthProperties.mask(twoPieces(eighth, 0, 4)),
					"a click on the east seam made " + beside
							+ ", expected its lower north east piece filled in beside the one already there");

			// Every face of a full cell is the boundary of the cell, so a click on one is read as a
			// click on the cell beyond it: the ninth click on a cell always lands next door.
			level.setBlockAndUpdate(pos, full);

			BlockPlaceContext nextDoor = eighthContext(player, eighth, pos,
					new Vec3(pos.getX() + 0.25, pos.getY() + 1.0, pos.getZ() + 0.25), Direction.UP);

			require(nextDoor.getClickedPos().equals(pos.above()), "a click on a full cell of eighths aimed at "
					+ nextDoor.getClickedPos() + ", expected the cell above it");

			// A second material never takes over a cell of eighths: its click carries on to the cell
			// beyond the face instead.
			MBEighths.Material planks = MBEighths.byBase(Blocks.OAK_PLANKS);

			if (planks == null) {
				throw new AssertionError("oak planks have no eighth block");
			}

			BlockPlaceContext otherMaterial = eighthContext(player, planks.eighth(), pos,
					new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP);

			require(otherMaterial.getClickedPos().equals(pos.above()), "another material's eighth block aimed at "
					+ otherMaterial.getClickedPos() + ", expected the cell above the stone eighths");
			require(level.getBlockState(pos).is(eighth),
					"the cell of stone eighths was taken over by " + level.getBlockState(pos));

			// An eighth block may not join the slab of its own material: the click on the seam at the
			// top of a bottom slab would put a piece into the cell the slab is already in.
			level.setBlockAndUpdate(pos, stone.slab().defaultBlockState());

			BlockState refused = eighthPlacement(player, eighth, pos,
					new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP);

			require(refused == null, "an eighth block joined its own slab: " + refused);

			// A click on the side of that slab is not a seam, though: it is an ordinary placement in
			// the cell beyond the face, with the piece touching the face that was clicked.
			BlockState beyond = eighthPlacement(player, eighth, pos,
					new Vec3(pos.getX() + 0.25, pos.getY() + 0.25, pos.getZ()), Direction.NORTH);

			require(beyond != null && MBEighthProperties.mask(beyond) == MBEighthProperties.mask(piece(eighth, 1)),
					"a click on the north side of a bottom slab placed " + beyond
							+ ", expected the lower south west piece of the cell to the north");

			// Breaking a cell gives every piece in it back at once: the drops are read off the state
			// of the block, which has no loot table of its own.
			List<ItemStack> drops = twoPieces(eighth, 0, 5).getDrops(new LootParams.Builder(level));

			require(drops.size() == 2, "a cell with two eighths in it dropped " + drops.size() + " items");
			require(drops.stream().allMatch(drop -> drop.is(Item.BY_BLOCK.get(eighth))),
					"a cell of eighths dropped " + drops);
		});
	}

	/** @return the state of {@code eighth} with the piece at {@code index} and nothing else in it. */
	private static BlockState piece(Block eighth, int index) {
		BlockState state = eighth.defaultBlockState();

		for (int i = 0; i < MBEighthProperties.BITS.length; i++) {
			state = state.setValue(MBEighthProperties.BITS[i], i == index);
		}

		return state;
	}

	/** @return the state of {@code eighth} with the pieces at {@code first} and {@code second} in it. */
	private static BlockState twoPieces(Block eighth, int first, int second) {
		return piece(eighth, first).setValue(MBEighthProperties.bit(second), true);
	}

	/** @return the context vanilla itself would read for a click with {@code eighth} in hand. */
	private static BlockPlaceContext eighthContext(ServerPlayer player, Block eighth, BlockPos pos, Vec3 hit,
			Direction face) {
		ItemStack stack = new ItemStack(eighth.asItem());

		return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
				new BlockHitResult(hit, face, pos, false));
	}

	private static BlockState eighthPlacement(ServerPlayer player, Block eighth, BlockPos pos, Vec3 hit,
			Direction face) {
		return eighth.getStateForPlacement(eighthContext(player, eighth, pos, hit, face));
	}

	/**
	 * Breaks a placed stair and slab and checks that the matching items drop, which covers
	 * "places, breaks and drops itself". The loot tables are datagen output, so this is the
	 * check that a generated table actually matches the block it was generated for.
	 *
	 * <p>The block broken is read back from the world rather than assumed from the scene's
	 * ordering, and the drop is waited for instead of sampled immediately: destroying a block
	 * spawns its loot as an entity on the <em>next</em> tick.
	 */
	private static void verifyDrops(ClientGameTestContext context, TestServerContext server) {
		BlockPos stairsPos = new BlockPos(COLUMN_X0, FIRST_ROW_Y + 2, WALL_Z);
		BlockPos slabPos = new BlockPos(COLUMN_X0 + 1, FIRST_ROW_Y + 1, WALL_Z);

		Block stairsBlock = server.computeOnServer(
				minecraftServer -> minecraftServer.overworld().getBlockState(stairsPos).getBlock());
		Block slabBlock = server.computeOnServer(
				minecraftServer -> minecraftServer.overworld().getBlockState(slabPos).getBlock());

		require(stairsBlock instanceof StairBlock && slabBlock instanceof SlabBlock,
				"expected a stair and a slab at the drop test positions, found "
						+ BuiltInRegistries.BLOCK.getKey(stairsBlock) + " and "
						+ BuiltInRegistries.BLOCK.getKey(slabBlock));

		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();

			level.destroyBlock(stairsPos, true);
			level.destroyBlock(slabPos, true);
		});

		context.waitTicks(4);

		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
					new AABB(stairsPos).minmax(new AABB(slabPos)).inflate(4.0));

			require(drops.stream().anyMatch(drop -> drop.getItem().is(Item.BY_BLOCK.get(stairsBlock))),
					"breaking " + BuiltInRegistries.BLOCK.getKey(stairsBlock) + " dropped " + describe(drops));
			require(drops.stream().anyMatch(drop -> drop.getItem().is(Item.BY_BLOCK.get(slabBlock))),
					"breaking " + BuiltInRegistries.BLOCK.getKey(slabBlock) + " dropped " + describe(drops));
		});
	}

	private static String describe(List<ItemEntity> drops) {
		return drops.isEmpty() ? "nothing" : drops.stream().map(drop -> drop.getItem().toString()).toList().toString();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/**
	 * Photographs the mod's glazed forms against vanilla's own block, turned the way a
	 * player placing them would turn them, with every square's position on screen found by
	 * ray casting so the comparison knows exactly which pixels to compare.
	 *
	 * <p>A glazed pattern has a direction, and the question this run has to answer is
	 * whether the mod's cut forms carry it the way the block they are cut from does. That
	 * is a comparison no assertion in the log can make: the model can be well formed,
	 * complete and correctly named and still show the pattern a quarter turn out. So
	 * vanilla's own block and each of the mod's forms are filled with the same pattern,
	 * placed for the same direction, and photographed one at a time from straight above; the
	 * pictures are then subtracted, and the turn that makes them agree is the error, if any.
	 */
	private static void measureGlazed(ClientGameTestContext context, TestServerContext server) {
		Block base = Blocks.GLAZED_TERRACOTTA.white();
		Block slab = blockWithPath("white_glazed_terracotta_slab");

		record Placed(String label, BlockState state) {
		}
		List<Placed> row = List.of(
				new Placed("pillar-south", withFacing(base.defaultBlockState(), Direction.SOUTH)),
				new Placed("pillar-west", withFacing(base.defaultBlockState(), Direction.WEST)),
				new Placed("pillar-north", withFacing(base.defaultBlockState(), Direction.NORTH)),
				new Placed("pillar-east", withFacing(base.defaultBlockState(), Direction.EAST)),
				new Placed("slab-south", glazed(slab, SlabType.TOP, Direction.SOUTH)),
				new Placed("slab-west", glazed(slab, SlabType.TOP, Direction.WEST)),
				new Placed("slab-north", glazed(slab, SlabType.TOP, Direction.NORTH)),
				new Placed("slab-east", glazed(slab, SlabType.TOP, Direction.EAST)),
				new Placed("stairs-south", glazedStairs(Blocks.GLAZED_TERRACOTTA.white(), Direction.SOUTH)),
				new Placed("stairs-west", glazedStairs(Blocks.GLAZED_TERRACOTTA.white(), Direction.WEST)),
				new Placed("stairs-north", glazedStairs(Blocks.GLAZED_TERRACOTTA.white(), Direction.NORTH)),
				new Placed("stairs-east", glazedStairs(Blocks.GLAZED_TERRACOTTA.white(), Direction.EAST)));

		for (Placed placed : row) {
			shootCell(context, server, placed.state(), MEASURE_SHOT + placed.label() + ".png");
			reportCell(server, placed.label());
		}
	}

	/**
	 * Writes the state the server actually holds at the photographed block.
	 *
	 * <p>Half of the trouble with a picture is knowing what was in it. A state that was built
	 * one call out - a fill written where a vertical slab was meant, a facing left at its
	 * default - produces a picture that looks like a modelling fault and is nothing of the
	 * sort, and the two cannot be told apart from the image alone.
	 */
	private static void reportCell(TestServerContext server, String label) {
		server.runOnServer(minecraftServer -> {
			BlockState state = minecraftServer.overworld()
					.getBlockState(new BlockPos(CELL_X, MEASURE_Y, CELL_Z));

			MoreBuildingBlocks.LOGGER.info("glazed measurement {} held {}", label, state);
		});
	}

	/**
	 * Builds a patch of one block and photographs it from straight above.
	 *
	 * <p>One patch per shot is what makes the comparison exact: with the camera on the
	 * patch's own centre line the pattern sits in the middle of the frame, so the same
	 * pixels of the screenshot hold the same texels of the pattern in every shot, whatever
	 * the pattern's orientation turns out to be. Measuring where a row of patches landed
	 * would mean projecting world points through a camera whose field of view and whose
	 * idea of which way the world runs have to be got exactly right first, and any error
	 * there slides a high frequency pattern out of phase and reads as a wrong orientation.
	 *
	 * <p>The patch is three blocks square so the middle one is seen with a margin of its own
	 * kind around it: one block alone would have the floor at its edge inside the same
	 * window.
	 */
	private static void shootCell(ClientGameTestContext context, TestServerContext server, BlockState state,
			String shot) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();

			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					level.setBlockAndUpdate(new BlockPos(CELL_X, MEASURE_Y, CELL_Z).offset(x, 0, z), state);
				}
			}

			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			player.teleportTo(level, CELL_X + 0.5, MEASURE_Y + MEASURE_EYE_ABOVE, CELL_Z + 0.5, Set.of(), 180.0F, 90.0F,
					false);
		});
		context.waitTicks(6);
		context.takeScreenshot(shot);
	}

	/**
	 * A glazed stair laid the way the player would have laid it: its own {@code facing} is the
	 * way they were looking, which is also the way the pattern points.
	 *
	 * <p>The two are one value in play and two properties in the block, and a measurement that
	 * sets only the pattern measures a stair nobody can place: the step would point one way and
	 * the pattern another, and the picture would be read as a modelling fault.
	 */
	private static BlockState glazedStairs(Block block, Direction facing) {
		return glazedFacing(stairs(block)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, facing), facing);
	}

	/**
	 * Writes vanilla's own {@code facing} onto a full glazed block. The two directions are
	 * both photographed: the default state is north, and north is a quarter turn off south,
	 * so a reference taken from the default alone silently measures every derivative against
	 * the wrong quarter.
	 */
	private static BlockState withFacing(BlockState state, Direction facing) {
		return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				: state;
	}

	/** Writes the pattern's own facing onto a derivative that carries one. */
	private static BlockState glazedFacing(BlockState state, Direction facing) {
		if (!(state.getBlock() instanceof GlazedPattern) || !state.hasProperty(MBBlockStateProperties.GLAZED_FACING)) {
			return state;
		}

		return state.setValue(MBBlockStateProperties.GLAZED_FACING, facing);
	}

	/**
	 * A glazed slab filled to the given half and carrying the given pattern facing. It goes
	 * through {@code withFill}, not through a raw property write, because that is the call
	 * the placement code itself makes: a slab filled through it lies flat the way a placed
	 * one does, with the same half standing above the block's own bottom.
	 */
	private static BlockState glazed(Block block, SlabType type, Direction facing) {
		Fill fill = type == SlabType.TOP ? Fill.positive(Direction.Axis.Y) : Fill.negative(Direction.Axis.Y);
		return glazedFacing(VerticalSlab.withFill(block.defaultBlockState(), fill), facing);
	}

	/**
	 * Puts the camera {@code distance} blocks south of the wall at {@code z}, looking
	 * north at it. Every staircase is built facing north, so looking north puts the
	 * cut corner of each one on the camera's left and lights the whole row the same
	 * way.
	 *
	 * <p>The move is issued on the SERVER, not on the client. Snapping the client
	 * player only sends a movement packet the server rejects with
	 * {@code "moved wrongly!"} and rubber-bands straight back to spawn, which is how
	 * the first few runs ended up photographing the world's own terrain.
	 *
	 * <p>The eye is placed on a level with the wall's middle at {@link #VIEW_DISTANCE}.
	 * Pitch is positive downwards in Minecraft, so the angle is the plain arctangent:
	 * negating it points the camera above the wall and into the sky, which is what the
	 * first composed run photographed. The yaw has to be 180 as well, because the camera
	 * stands at {@code z + VIEW_DISTANCE} to look back at a wall built along {@code -Z},
	 * and a yaw of zero faces the other way.
	 *
	 * <p>The camera is aimed at {@code aimY} from a distance rather than parked at it:
	 * at exactly floor height the eye sits inside the stone platform the showcase is
	 * built on and the near half of the frame fills with that platform's own blocks.
	 * The eye is lifted {@code eyeOffset} above {@code aimY} and the pitch is computed
	 * to point straight back at it, so the target always lands on the crosshair.
	 */
	private static void look(ClientGameTestContext context, TestServerContext server, double x, double aimY,
			double z, double distance, double eyeOffset) {
		// The teleport argument is the player's FEET; the eye sits a bit above it. Aiming
		// wants the eye at the wall's middle, so the feet go that much lower.
		double feetY = aimY + eyeOffset - 1.6;

		// The teleport is a movement packet and the client adopts it a tick or two later,
		// so the pitch cannot simply be computed and trusted. Move first, then READ the
		// eye back from the client and aim from wherever it actually is; the server drops
		// the occasional request, hence the retry. The tolerance is deliberately loose:
		// the player is in free fall between two teleports, and the aim only needs to know
		// roughly where the eye is.
		for (int attempt = 0; attempt < 3; attempt++) {
			server.runOnServer(minecraftServer -> {
				ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);

				player.teleportTo(minecraftServer.overworld(), x, feetY, z + distance, Set.of(), 180.0F, 0.0F, false);
			});
			context.waitTicks(2);

			Vec3 eye = context.computeOnClient(minecraft -> minecraft.player == null ? null
					: minecraft.player.getEyePosition());

			if (eye == null || Math.abs(eye.y - (feetY + 1.6)) > 3.0) {
				continue;
			}

			float pitch = (float) Math.toDegrees(Math.atan2(eye.y - aimY, distance));

			server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().get(0)
					.teleportTo(minecraftServer.overworld(), x, eye.y - 1.6, z + distance, Set.of(), 180.0F, pitch,
							false));
			context.waitTicks(2);
			return;
		}

		MoreBuildingBlocks.LOGGER.warn("Camera never reached ({}, {}, {}) for the screenshot at z={}", x, feetY,
				z + distance, z);
	}

	/** One stair per base block, oriented so the cut corner is visible. */
	private static BlockState stairs(Block block) {
		// The stair of a base block keeps the base block's own path and lives in this
		// mod's namespace; the base block itself is vanilla's.
		Block stairs = blockWithPath(BuiltInRegistries.BLOCK.getKey(block).getPath() + "_stairs");

		if (!(stairs instanceof StairBlock)) {
			return Blocks.AIR.defaultBlockState();
		}

		return stairs.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}

	private static BlockState slab(Block block) {
		Block slab = blockWithPath(BuiltInRegistries.BLOCK.getKey(block).getPath() + "_slab");

		if (!(slab instanceof SlabBlock)) {
			return Blocks.AIR.defaultBlockState();
		}

		return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
	}

	private static BlockState vertical(Block slab, Direction.Axis axis, SlabType type) {
		return VerticalSlab.withVertical(slab.defaultBlockState(), axis, type);
	}

	/**
	 * The 33 vanilla terracotta blocks in vanilla's own order: plain, dyed, glazed, then
	 * sorted by path. These are the blocks the mod cuts its stairs and slabs from; the mod
	 * registers none of them.
	 */
	private static List<Block> familyBlocks() {
		List<Block> blocks = new ArrayList<>();
		blocks.add(Blocks.TERRACOTTA);
		blocks.addAll(coloredTerracotta("terracotta"));
		blocks.addAll(glazedBaseBlocks());
		blocks.sort((left, right) -> BuiltInRegistries.BLOCK.getKey(left).getPath()
				.compareTo(BuiltInRegistries.BLOCK.getKey(right).getPath()));
		return blocks;
	}

	/** The 16 glazed terracotta blocks, in dye order. */
	private static List<Block> glazedBaseBlocks() {
		return coloredTerracotta("glazed_terracotta");
	}

	/** Vanilla's own dyed terracotta blocks for one suffix, in dye order. */
	private static List<Block> coloredTerracotta(String suffix) {
		List<Block> blocks = new ArrayList<>();

		for (String name : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
				"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
			Block block = BuiltInRegistries.BLOCK
					.getOptional(Identifier.fromNamespaceAndPath("minecraft", name + "_" + suffix))
					.orElse(Blocks.AIR);

			if (block != Blocks.AIR) {
				blocks.add(block);
			}
		}

		return blocks;
	}

	/** Every vanilla slab, which the mixin also gives a vertical form. */
	private static List<Block> vanillaSlabs() {
		List<Block> slabs = new ArrayList<>();

		BuiltInRegistries.BLOCK.forEach(block -> {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);

			if (block instanceof SlabBlock && !MoreBuildingBlocks.MOD_ID.equals(id.getNamespace())) {
				slabs.add(block);
			}
		});

		slabs.sort((left, right) -> BuiltInRegistries.BLOCK.getKey(left).getPath()
				.compareTo(BuiltInRegistries.BLOCK.getKey(right).getPath()));
		return slabs;
	}

	private static Block blockWithPath(String path) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, path))
				.orElse(Blocks.AIR);
	}
}
