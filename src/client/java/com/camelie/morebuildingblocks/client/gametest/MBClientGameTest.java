package com.camelie.morebuildingblocks.client.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.VerticalSlab;
import com.camelie.morebuildingblocks.registry.MBBlockFamilies;

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

	/** The world's minimum build height, which is where a flat test world builds. */
	private static final int FLOOR_Y = -63;

	/** Ground level of the showcase, one above the floor. */
	private static final int FIRST_ROW_Y = FLOOR_Y + 1;

	/** How far the camera stands back from the terracotta wall. */
	private static final double VIEW_DISTANCE = 10.0;

	/** The upright wall is a thin line seen edge on, so it is photographed from nearer. */
	private static final double VERTICAL_VIEW_DISTANCE = 8.0;

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

				// A: the whole terracotta family, three rows deep rather than stacked into
				// columns, so the stair in the middle of the frame does not hide the slab
				// behind it. 33 of 101 columns are used.
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

			// All three forms of all 33 blocks: a cube, its slab and its stair, stacked.
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

			// Geometry and loot come last: a failed assertion aborts the run, and the
			// screenshots above are the evidence for the checks a log cannot make.
			verifyShapes(server);
			verifyPlacement(server, family);
			verifyDrops(context, server, family);
		}
	}

	/**
	 * Places a slab through the real {@link BlockPlaceContext} path and checks that the
	 * upright rule chose the intended axis and half.
	 *
	 * <p>This is the one part of the feature a screenshot cannot prove: a slab whose axis
	 * is wrong still renders as a plausible thin wall, and the placement rule reads the
	 * click position rather than the block state. Clicking the eastern half of a block's
	 * top face must give a slab that faces east (thin on X, filled on the east side), and
	 * the northern half one that faces north (thin on Z, filled on the north side).
	 */
	private static void verifyPlacement(TestServerContext server, List<Block> family) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			Block slabBlock = vanillaSlabs().get(0);
			BlockPos pos = new BlockPos(0, FLOOR_Y + 24, 0);

			// Standing east of the block and clicking its top face's eastern half.
			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.9, FLOOR_Y + 25.0, 0.5), Direction.UP,
					Direction.Axis.X, SlabType.TOP, "east half");

			// Standing north of it, clicking the top face's northern half.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.1), Direction.UP,
					Direction.Axis.Z, SlabType.BOTTOM, "north half");

			// A click on a side face uses the same rule, so the slab still faces the player.
			checkPlacement(level, player, slabBlock, pos, new Vec3(0.95, FLOOR_Y + 24.5, 0.5), Direction.EAST,
					Direction.Axis.X, SlabType.TOP, "east side face");

			// A flat floor slab, placed from above at the centre, stays flat: only a
			// horizontal offset makes a slab stand up.
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			BlockState flat = placement(level, player, slabBlock, pos, new Vec3(0.5, FLOOR_Y + 25.0, 0.5), Direction.UP);
			require(flat != null && !flat.getValue(MBBlockStateProperties.VERTICAL),
					"a centre click produced an upright slab: " + flat);
		});
	}

	private static void checkPlacement(ServerLevel level, ServerPlayer player, Block slabBlock, BlockPos pos,
			Vec3 hit, Direction face, Direction.Axis axis, SlabType half, String what) {
		BlockState placed = placement(level, player, slabBlock, pos, hit, face);

		require(placed != null, "no block state was produced for the " + what + " placement");
		require(placed.getValue(MBBlockStateProperties.VERTICAL),
				"the " + what + " placement did not stand the slab up: " + placed);
		require(placed.getValue(MBBlockStateProperties.AXIS) == axis,
				"the " + what + " placement used axis " + placed.getValue(MBBlockStateProperties.AXIS)
						+ ", expected " + axis);
		require(placed.getValue(SlabBlock.TYPE) == half,
				"the " + what + " placement filled the " + placed.getValue(SlabBlock.TYPE)
						+ " half, expected " + half);
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

	/** The 33 terracotta blocks in vanilla's own order: plain, dyed, glazed. */
	private static List<Block> familyBlocks() {
		List<Block> blocks = new ArrayList<>();
		blocks.add(Blocks.TERRACOTTA);
		blocks.addAll(glazedBaseBlocks());
		blocks.addAll(colored("terracotta"));
		blocks.sort((left, right) -> BuiltInRegistries.BLOCK.getKey(left).getPath()
				.compareTo(BuiltInRegistries.BLOCK.getKey(right).getPath()));
		return blocks;
	}

	/** The 16 glazed terracotta blocks, in dye order. */
	private static List<Block> glazedBaseBlocks() {
		return colored("glazed_terracotta");
	}

	/** The mod's own dyed blocks for one suffix, in dye order. */
	private static List<Block> colored(String suffix) {
		List<Block> blocks = new ArrayList<>();

		for (String name : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
				"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
			Block block = blockWithPath(name + "_" + suffix);

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
