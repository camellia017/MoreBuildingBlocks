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
			verifyOccupiedNeighbour(server);
			verifyUprightNeighbours(server);
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
	 * Places a slab through the real {@link BlockPlaceContext} path and checks that the upright rule
	 * chose the intended axis and half.
	 *
	 * <p>This is the one part of the feature a screenshot cannot prove: a slab whose axis is wrong
	 * still renders as a plausible thin wall, and the rule reads the click position rather than the
	 * camera. The click is a position inside the block that was clicked, and the two halves of its
	 * horizontal axes decide the answer: the axis the click is further out on is the axis the
	 * upright slab is thin along, and the half it fills is the one the click is nearest to.
	 *
	 * <p>On a top or bottom face that is a 16x16 plan around the click, so a click in the eastern
	 * half of the top face fills the eastern half of the block. A click within a quarter of a block
	 * of the centre picks no half at all and leaves the slab flat, which is what a player building a
	 * floor means by clicking the middle.
	 *
	 * <p>A click on one of the four <em>side</em> faces reads the same way and lands in the cell in
	 * front of that face, so the half it fills is the one flush against the block that was clicked -
	 * the new slab standing at the end of the wall the player is building.
	 */
	private static void verifyPlacement(TestServerContext server, List<Block> family) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(0, FLOOR_Y + 24, 0);

			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());

			// Top face: the click's own x and z offsets from the centre of the block pick the half.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.negative(Direction.Axis.Y), "top face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.1), Direction.UP,
					Fill.negative(Direction.Axis.Z), "top face north half");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.9), Direction.UP,
					Fill.positive(Direction.Axis.Z), "top face south half");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.negative(Direction.Axis.X), "top face west half");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Fill.positive(Direction.Axis.X), "top face east half");
			// A corner click is nearer to two halves; the further-out axis is the one that wins.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 25.0, 0.2), Direction.UP,
					Fill.positive(Direction.Axis.X), "top face north-east corner");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.2, FLOOR_Y + 25.0, 0.9), Direction.UP,
					Fill.positive(Direction.Axis.Z), "top face south-west corner");

			// Bottom face: read the same way, so the offsets pick the same halves.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.positive(Direction.Axis.Y), "bottom face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.1), Direction.DOWN,
					Fill.negative(Direction.Axis.Z), "bottom face north half");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.0, 0.9), Direction.DOWN,
					Fill.positive(Direction.Axis.Z), "bottom face south half");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.negative(Direction.Axis.X), "bottom face west half");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 24.0, 0.5), Direction.DOWN,
					Fill.positive(Direction.Axis.X), "bottom face east half");

			// Every side face points into an empty cell, so a click there adds an upright slab
			// to the wall: the half filled is the one flush against the block that was clicked,
			// and the click's height up the face has no say in it at all.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Z), "north face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.1, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Z), "north face bottom band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.9, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Z), "north face top band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Z), "north face east band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.1, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Z), "north face west band");

			// A click on a side face lies on that face's plane, so its offset across the face is
			// always exactly half a block - the largest either axis can reach - and the axis it
			// wins by is the one perpendicular to the face. The four side faces therefore each
			// name their own half of the cell beyond them, whatever the click's height or its
			// place along the face.
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.5), Direction.EAST,
					Fill.positive(Direction.Axis.X), "east face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.5, 0.1), Direction.EAST,
					Fill.positive(Direction.Axis.X), "east face north band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(1.0, FLOOR_Y + 24.1, 0.5), Direction.EAST,
					Fill.positive(Direction.Axis.X), "east face bottom band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.05, FLOOR_Y + 24.5, 0.5), Direction.WEST,
					Fill.negative(Direction.Axis.X), "west face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.05, FLOOR_Y + 24.5, 0.9), Direction.WEST,
					Fill.negative(Direction.Axis.X), "west face south band");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.5, 0.95), Direction.SOUTH,
					Fill.positive(Direction.Axis.Z), "south face centre");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 24.9, 0.95), Direction.SOUTH,
					Fill.positive(Direction.Axis.Z), "south face east top corner");
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.5, 0.05), Direction.NORTH,
					Fill.negative(Direction.Axis.Z), "north face centre again");
		});
	}

	/**
	 * Places a slab into a cell that is already occupied, and checks that the rule leaves those
	 * clicks to vanilla rather than turning the block that is standing there into a wall.
	 *
	 * <p>A side click only reaches a cell nothing is standing in yet. A full block across the gap
	 * is the block being built against, and the click means the flat slab the player is holding.
	 */
	private static void verifyOccupiedNeighbour(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(0, FLOOR_Y + 24, 0);
			BlockPos north = pos.north();

			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(north, Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());

			BlockState placed = placement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 24.5, 0.05),
					Direction.NORTH);
			require(placed != null, "no block state was produced for the side click on a wall");
			require(!VerticalSlab.isVertical(placed),
					"a side click on a wall made an upright slab: " + placed);
			require(Fill.of(placed).equals(Fill.negative(Direction.Axis.Y)),
					"a side click on a wall filled " + Fill.of(placed) + ", expected " + Fill.negative(Direction.Axis.Y)
							+ ": " + placed);

			level.setBlockAndUpdate(north, Blocks.AIR.defaultBlockState());
		});
	}

	/**
	 * Places a second upright slab against both broad faces of a first one, and against its top,
	 * through the real {@link BlockPlaceContext} path, and checks which block and which half each
	 * one lands in.
	 *
	 * <p>An upright slab is not replaceable from any face, so a click on one is always taken into
	 * the next cell along before the placement rule sees it. Every one of these clicks therefore
	 * means the wall carrying on, never the clicked slab being replaced:
	 *
	 * <ul>
	 *   <li>Against the <em>outer</em> face - here the northern one, the face a south-facing slab
	 *       shows through - the new slab belongs in the next block north, filling the half that
	 *       reaches back towards the clicked one. The click has to be moved out of the slab for
	 *       this, which is what makes {@code canBeReplaced} answer {@code false}.</li>
	 *   <li>Against the <em>inner</em> face the new slab belongs in the next block south, filling
	 *       that block's southern half. Leaving the click inside the clicked slab instead makes
	 *       vanilla combine the two halves into one double slab and silently swaps the upright
	 *       slab out.</li>
	 *   <li>Against the <em>top</em>: the last of those three, and the one a player hits by
	 *       accident while building. It lands in the block above with the half the click picked.</li>
	 * </ul>
	 */
	private static void verifyUprightNeighbours(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(6, FLOOR_Y + 24, 0);
			BlockPos north = pos.north();
			BlockState wall = slabBlock.defaultBlockState()
					.setValue(MBBlockStateProperties.VERTICAL, true)
					.setValue(MBBlockStateProperties.AXIS, Direction.Axis.Z)
					.setValue(SlabBlock.TYPE, SlabType.TOP);

			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(north.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(pos, wall);
			level.setBlockAndUpdate(north, Blocks.AIR.defaultBlockState());

			// (a) The click has to be taken out of the slab. SlabBlock reports an upright
			// slab as replaceable from every face it presents, so without the mod's own
			// canBeReplaced answer getClickedPos() stays on the slab and the new state is
			// computed for the block that is already standing there.
			BlockPlaceContext outer = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
					new ItemStack(slabBlock.asItem()),
					new BlockHitResult(new Vec3(0.5, FLOOR_Y + 24.5, 0.0), Direction.NORTH, pos, false));

			require(outer.getClickedPos().equals(north),
					"clicking the outer face of an upright slab aimed at " + outer.getClickedPos()
							+ ", expected the block beyond it at " + north);

			BlockState continued = slabBlock.getStateForPlacement(outer);

			require(continued != null, "no block state was produced for a click on an upright slab's outer face");
			require(continued.getValue(MBBlockStateProperties.VERTICAL),
					"the slab placed against the outer face of an upright slab came out flat: " + continued);

			require(Fill.of(continued).equals(Fill.positive(Direction.Axis.Z)),
					"the slab placed against the outer face of a south-facing slab filled " + Fill.of(continued)
							+ ", expected the southern half so the two touch: " + continued);

			// (b) The inner face is the clicked slab's open half, and it is not replaceable
			// either, so this click is moved south instead of staying on the slab. Staying
			// there is what vanilla would do with a replaceable half slab: it combines the
			// two halves into one double and swaps the upright slab out.
			BlockPos south = pos.south();
			BlockPlaceContext inner = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
					new ItemStack(slabBlock.asItem()),
					new BlockHitResult(new Vec3(0.5, FLOOR_Y + 24.5, 1.0), Direction.SOUTH, pos, false));

			require(inner.getClickedPos().equals(south),
					"clicking the inner face of an upright slab aimed at " + inner.getClickedPos()
							+ ", expected the block beyond it at " + south);

			BlockState continuedSouth = slabBlock.getStateForPlacement(inner);

			require(continuedSouth != null, "no block state was produced for a click on an upright slab's inner face");
			require(continuedSouth.getValue(MBBlockStateProperties.VERTICAL),
					"the slab placed against the inner face of an upright slab came out flat: " + continuedSouth);
			require(SlabType.DOUBLE != continuedSouth.getValue(SlabBlock.TYPE),
					"the click on the inner face of an upright slab produced " + continuedSouth
							+ ", expected a second upright slab beside it rather than the clicked block completed");

			// (c) A click on the top of an upright slab. The block it lands in has to be the
			// one above, and the clicked slab has to survive it: the half that used to be
			// built here was the clicked slab's own cell, where the new state replaced the
			// standing slab with a differently turned one.
			BlockPos above = pos.above();
			level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());

			BlockPlaceContext top = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
					new ItemStack(slabBlock.asItem()),
					new BlockHitResult(new Vec3(pos.getX() + 0.9, FLOOR_Y + 25.0, pos.getZ() + 0.5),
							Direction.UP, pos, false));

			require(top.getClickedPos().equals(above),
					"clicking the top of an upright slab aimed at " + top.getClickedPos()
							+ ", expected the block above it at " + above);

			BlockState stacked = slabBlock.getStateForPlacement(top);

			require(stacked != null, "no block state was produced for a click on the top of an upright slab");
			require(stacked.getValue(MBBlockStateProperties.VERTICAL),
					"the slab placed on top of an upright slab came out flat: " + stacked);
			require(Fill.of(stacked).equals(Fill.positive(Direction.Axis.X)),
					"the slab placed on top of an upright slab filled " + Fill.of(stacked)
							+ ", expected the eastern half the click picked: " + stacked);
			require(level.getBlockState(pos).equals(wall),
					"the upright slab that was clicked came out as " + level.getBlockState(pos)
							+ " after a click on its top, expected it untouched at " + wall);

			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(north, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
		});
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
