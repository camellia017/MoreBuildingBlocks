package com.camelie.morebuildingblocks.client.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.GlazedPattern;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.camelie.morebuildingblocks.block.VerticalSlab.Fill;
import com.camelie.morebuildingblocks.registry.MBBlockFamilies;
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
			verifyPlacement(server, family);
			verifyUprightStacking(server);
			verifyWallContinuation(server);
			verifyDoubleSlabClicks(server);
			verifyGlazedSlabs(server);
			verifyDrops(context, server, family);
			verifyCreativeTab(server);
		}

		// The singleplayer world has to be closed first: the client can only be in one
		// world at a time, and this join is the multiplayer half of the acceptance run.
		verifyDedicatedServer(context);
	}

	/**
	 * Checks that the mod's creative tab exists and holds every block the mod adds, in
	 * the order the tab is built from.
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

			for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
				expected.add(family.stairs());
				expected.add(family.slab());
			}

			require(displayOrder.size() == expected.size(),
					"the tab lists " + displayOrder.size() + " blocks, expected " + expected.size());
			require(displayOrder.containsAll(expected),
					"the tab is missing " + expected.stream().filter(b -> !displayOrder.contains(b)).toList());

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

			dedicatedServer.runOnServer(minecraftServer -> {
				Bootstrap.validate();
				ServerLevel level = minecraftServer.overworld();

				int serverBlocks = 0;
				Map<Identifier, Block> serverById = new HashMap<>();

				for (Block block : MBItems.displayOrder()) {
					if (block instanceof StairBlock || block instanceof SlabBlock) {
						serverById.put(BuiltInRegistries.BLOCK.getKey(block), block);
						serverBlocks++;
					}
				}

				// 33 stair/slab pairs and nothing else: the terracotta blocks they are
				// cut from are vanilla's own and are not registered by this mod.
				require(serverBlocks == 66, "the dedicated server registered " + serverBlocks
						+ " stair/slab blocks, expected 66");
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

				require(clientById.size() == 66,
						"the client knows " + clientById.size() + " mod items, expected 66");
				require(clientById.containsKey(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta_slab")),
						"the client is missing morebuildingblocks:terracotta_slab");

				BlockState received = minecraft.level.getBlockState(pos);
				require(received.is(clientById.get(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "terracotta_slab"))),
						"the block the server placed did not arrive at the client: " + received);
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
	private static void verifyPlacement(TestServerContext server, List<Block> family) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(0, FLOOR_Y + 24, 0);

			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());

			// The top face is read with east to the right and north upwards, so each band fills the
			// half on its own side of the block: north band the northern half, south band the
			// southern, west band the western half and east band the eastern one.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.negative(Direction.Axis.Y), "top face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.1), Direction.UP,
					Fill.negative(Direction.Axis.Z), "top face north band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.9), Direction.UP,
					Fill.positive(Direction.Axis.Z), "top face south band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.negative(Direction.Axis.X), "top face west band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.positive(Direction.Axis.X), "top face east band");

			// A side face that looks north carries the same two axes: east to the right and down the
			// face, so its bands name the same halves as the top face's do. The centre fills the half
			// behind the face - the southern half - which carries a wall on straight.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.positive(Direction.Axis.Z), "north face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.1, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Y), "north face bottom band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.9, 0.05), Direction.NORTH,
					Fill.positive(Direction.Axis.Y), "north face top band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.positive(Direction.Axis.X), "north face east band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.X), "north face west band");

			// A face that looks east is read with south to the right and down the face, so its north
			// band fills the northern half and its south band the southern one. Its right band fills
			// the southern half as well, both being the half on the side those two edges sit on.
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.5), Direction.EAST,
					Fill.negative(Direction.Axis.X), "east face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.1), Direction.EAST,
					Fill.negative(Direction.Axis.Z), "east face north band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.9), Direction.EAST,
					Fill.positive(Direction.Axis.Z), "east face south band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.1, 0.5), Direction.EAST,
					Fill.negative(Direction.Axis.Y), "east face bottom band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.9, 0.5), Direction.EAST,
					Fill.positive(Direction.Axis.Y), "east face top band");

			// The bottom face is read the same way as the top one, mirrored: east is still to the
			// right, but its second axis runs north as well, so the band against its northern edge
			// fills the northern half all the same.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.positive(Direction.Axis.Y), "bottom face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.1), Direction.DOWN,
					Fill.negative(Direction.Axis.Z), "bottom face north band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.9), Direction.DOWN,
					Fill.positive(Direction.Axis.Z), "bottom face south band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.negative(Direction.Axis.X), "bottom face west band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
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
				checkPlacement(level, player, slabBlock, pos, new Vec3(band.x(), band.y(), band.z()), band.face(),
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

		checkPlacement(level, player, slabBlock, pos, new Vec3(pos.getX() + 0.5, FLOOR_Y + 25.0, pos.getZ() + 0.5),
				Direction.UP, Fill.negative(Direction.Axis.Y), "top face centre far from the origin");
		checkPlacement(level, player, slabBlock, pos, new Vec3(pos.getX() + 0.5, FLOOR_Y + 25.0, pos.getZ() + 0.9),
				Direction.UP, Fill.positive(Direction.Axis.Z), "top face north band far from the origin");
		checkPlacement(level, player, slabBlock, pos, new Vec3(pos.getX() + 0.5, FLOOR_Y + 24.5, pos.getZ()),
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
	 * direction is not part of that comparison but is checked alongside it, against the player's
	 * own facing rather than a fixed direction: where the player stands may not change the shape,
	 * and must change the pattern.
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
				Identifier id = BuiltInRegistries.BLOCK.getKey(glazed);

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

		require(isVertical(plainState) == isVertical(glazedState),
				"clicking " + what + " with " + id + " stood the glazed slab "
						+ (isVertical(glazedState) ? "up" : "flat") + " where a plain slab went "
						+ (isVertical(plainState) ? "up" : "flat") + ": " + glazedState);

		Direction expected = player.getDirection().getOpposite();
		Direction turned = glazedState.getValue(MBBlockStateProperties.GLAZED_FACING);

		require(turned == expected,
				"clicking " + what + " with " + id + " turned the pattern to " + turned + " for a player looking "
						+ player.getDirection() + ", expected " + expected);
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

			BlockPlaceContext completion = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
					new ItemStack(slabBlock.asItem()),
					new BlockHitResult(centre.add(open.getUnitVec3().scale(0.5)), open, pos, false));

			require(completion.getClickedPos().equals(pos),
					"clicking the open face of an upright slab aimed at " + completion.getClickedPos()
							+ ", expected the clicked block itself at " + pos);
			BlockState completed = slabBlock.getStateForPlacement(completion);

			require(completed != null, "no block state was produced for a click on an upright slab's open face");
			require(SlabType.DOUBLE == completed.getValue(SlabBlock.TYPE),
					"the click on the open face of an upright slab produced " + completed
							+ ", expected a double slab completing the same block");

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
			checkAgainstWall(level, player, slabBlock, pos, wall, Direction.EAST,
					new Vec3(pos.getX() + 1.0, FLOOR_Y + 30.5, pos.getZ() + 0.5), pos.east());
			checkAgainstWall(level, player, slabBlock, pos, wall, Direction.WEST,
					new Vec3(pos.getX(), FLOOR_Y + 30.5, pos.getZ() + 0.5), pos.west());

			// The boundary the filled half runs up to - the far side of the block's own interior - is
			// the wall seen from its side, a whole face of the block like any face of a plain block,
			// so the click is read by its position instead of being handed the clicked slab's half.
			// At the centre the region names the half lying against this wall, which carries the wall
			// on in its own plane; a band names the half its own edge sits on instead.
			checkAgainstBoundary(level, player, slabBlock, pos, Direction.SOUTH,
					new Vec3(pos.getX() + 0.5, FLOOR_Y + 30.5, pos.getZ() + 1.0),
					Fill.opposite(Direction.SOUTH), "centre of the far boundary");
			checkAgainstBoundary(level, player, slabBlock, pos, Direction.SOUTH,
					new Vec3(pos.getX() + 0.5, FLOOR_Y + 30.8, pos.getZ() + 1.0),
					Fill.positive(Direction.Axis.Y), "top band of the far boundary");
			checkAgainstBoundary(level, player, slabBlock, pos, Direction.SOUTH,
					new Vec3(pos.getX() + 0.1, FLOOR_Y + 30.5, pos.getZ() + 1.0),
					Fill.negative(Direction.Axis.X), "west band of the far boundary");

			// The open face is the one face that finishes the block instead.
			checkAgainstOpenFace(player, slabBlock, pos, wall);

			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
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
	private static void checkAgainstWall(ServerLevel level, ServerPlayer player, Block slabBlock, BlockPos pos,
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
	 * Clicks a point on a whole face of an upright slab - the boundary its filled half runs up to -
	 * and checks the placement starts the next block in the cell beyond, filling the half the click
	 * position names rather than the clicked slab's own half.
	 *
	 * <p>This is the face a player uses to build against the side of a wall, so it is read the way
	 * any plain block's face is: a click at its centre carries the wall on in the same plane, and a
	 * click in one of its bands names the half that band's own edge sits on.
	 */
	private static void checkAgainstBoundary(ServerLevel level, ServerPlayer player, Block slabBlock, BlockPos pos,
			Direction face, Vec3 hit, Fill expected, String what) {
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
				new ItemStack(slabBlock.asItem()), new BlockHitResult(hit, face, pos, false));

		require(context.getClickedPos().equals(pos.relative(face)),
				"clicking the " + what + " of an upright slab aimed at " + context.getClickedPos()
						+ ", expected the cell beyond it at " + pos.relative(face));

		BlockState placed = slabBlock.getStateForPlacement(context);

		require(placed != null, "no block state was produced for a click on the " + what + " of an upright slab");
		require(!VerticalSlab.isDouble(placed),
				"clicking the " + what + " of an upright slab doubled the slab up: " + placed);
		require(Fill.of(placed).equals(expected),
				"clicking the " + what + " of an upright slab filled " + Fill.of(placed) + ", expected "
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
				"clicking the open face of an upright slab aimed at " + context.getClickedPos()
						+ ", expected the clicked block itself at " + pos);

		BlockState doubled = slabBlock.getStateForPlacement(context);

		require(doubled != null, "no block state was produced for a click on the open face of an upright slab");
		require(SlabType.DOUBLE == doubled.getValue(SlabBlock.TYPE),
				"the click on the open face of an upright slab produced " + doubled
						+ ", expected a double slab completing the same block");
	}

	private static void checkPlacement(ServerLevel level, ServerPlayer player, Block slabBlock, BlockPos pos,
			Vec3 hit, Direction face, Fill expected, String what) {
		BlockState placed = placement(level, player, slabBlock, pos, hit, face);

		require(placed != null, "no block state was produced for the " + what + " placement");
		require(SlabType.DOUBLE != placed.getValue(SlabBlock.TYPE),
				"the " + what + " placement doubled the slab: " + placed);
		require(Fill.of(placed).equals(expected),
				"the " + what + " placement filled " + Fill.of(placed) + ", expected " + expected
						+ " (clicked " + face + " at " + hit + "): " + placed);
	}

	private static BlockState placement(ServerLevel level, ServerPlayer player, Block slabBlock, BlockPos pos,
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
	 * Breaks a placed stair and slab and checks that the matching items drop, which covers
	 * "places, breaks and drops itself". The loot tables are datagen output, so this is the
	 * check that a generated table actually matches the block it was generated for.
	 *
	 * <p>The block broken is read back from the world rather than assumed from the scene's
	 * ordering, and the drop is waited for instead of sampled immediately: destroying a block
	 * spawns its loot as an entity on the <em>next</em> tick.
	 */
	private static void verifyDrops(ClientGameTestContext context, TestServerContext server, List<Block> family) {
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
		Block stairs = blockWithPath("white_glazed_terracotta_stairs");

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

	private static void look(ClientGameTestContext context, TestServerContext server, double x, double aimY,
			double z) {
		look(context, server, x, aimY, z, VIEW_DISTANCE, 0.5);
	}

	private static void look(ClientGameTestContext context, TestServerContext server, double x, double aimY,
			double z, double distance) {
		look(context, server, x, aimY, z, distance, 0.5);
	}

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

	/** Keeps the imported property reference honest if the mixin renames it. */
	@SuppressWarnings("unused")
	private static boolean isVertical(BlockState state) {
		return state.hasProperty(MBBlockStateProperties.VERTICAL) && state.getValue(MBBlockStateProperties.VERTICAL);
	}
}
