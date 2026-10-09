package com.camelie.morebuildingblocks.client.datagen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelOutput;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.VerticalSlab.Fill;
import com.camelie.morebuildingblocks.registry.MBBlockFamilies;

/**
 * Generates every blockstate, block model, block item model and client item definition this mod
 * ships.
 *
 * <p>Plain and dyed terracotta reuse vanilla's {@code slab} and {@code stairs} templates, so they
 * look exactly like the vanilla shapes cut out of the same texture. Glazed terracotta cannot: its
 * texture is a directional pattern that vanilla rotates per face ({@code down 0, up 0, north 90,
 * south 270, west 0, east 180}), so those models are emitted as literal geometry carrying that
 * rotation on every face.
 *
 * <p>An upright slab is not a rotated copy of one model. It is one of four boxes - the western,
 * eastern, northern or southern half of the block - and the blockstate picks the box that matches
 * the {@code axis} and {@code type} the mixin set. Rotating a single box instead would send two of
 * the four directions to the wrong half of the block, which is what an earlier version did.
 *
 * <p>Every vanilla slab additionally gains those four upright models, driven by the {@code
 * vertical} and {@code axis} state properties the slab mixin adds. No vanilla JSON is written: the
 * extra states are supplied at load time by
 * {@code com.camelie.morebuildingblocks.client.MBVanillaSlabModelPlugin}, and the boxes it points
 * at are generated here, per slab, with their own textures.
 */
public class MBModelProvider implements DataProvider {
	/** The namespace every texture this mod references lives in. */
	private static final String VANILLA_NAMESPACE = "minecraft";

	/** Texture slots of the mod's own {@code block/vertical_slab} parent model. */
	private static final TextureSlot SLOT_BOTTOM = TextureSlot.BOTTOM;
	private static final TextureSlot SLOT_TOP = TextureSlot.TOP;
	private static final TextureSlot SLOT_SIDE = TextureSlot.SIDE;

	/**
	 * Vanilla's own block model, whose only job is to give block models the standard GUI, ground
	 * and hand transforms. A model that draws raw geometry has no parent otherwise, and an item
	 * that inherits no display transform is rendered flat against the screen - which is why the
	 * icons of the literal-geometry blocks came out wrong.
	 */
	private static final Identifier BLOCK_PARENT = Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/block");

	/** Vanilla's per-face pattern rotation on a full glazed terracotta cube. */
	private static final Map<String, Integer> GLAZED_FACE_ROTATION = Map.of(
			"down", 0,
			"up", 0,
			"north", 90,
			"south", 270,
			"west", 0,
			"east", 180);

	/**
	 * Where an upright slab model lives inside this mod. Kept apart from the flat models so a path
	 * can never collide with one of them.
	 */
	private static final String VERTICAL_DIRECTORY = "vertical_slab/";

	/** The four halves of the block an upright slab can fill. */
	private static final String FILL_NEGATIVE_X = "neg_x";
	private static final String FILL_POSITIVE_X = "pos_x";
	private static final String FILL_NEGATIVE_Z = "neg_z";
	private static final String FILL_POSITIVE_Z = "pos_z";

	/**
	 * How many quarter turns a glazed model is written in. Vanilla gives a glazed block one model
	 * per {@code facing}, and these shapes have to follow the block they are cut from.
	 */
	private static final int TURNS = 4;

	/** The six faces of a box, in the order a model file lists them. */
	private static final String[] FACE_DIRECTIONS = { "down", "up", "north", "south", "west", "east" };

	private final PackOutput.PathProvider blockStatePathProvider;
	private final PackOutput.PathProvider itemPathProvider;
	private final PackOutput.PathProvider modelPathProvider;

	private final Map<Block, BlockModelDefinitionGenerator> blockStates = new LinkedHashMap<>();
	private final Map<Identifier, ModelInstance> models = new LinkedHashMap<>();
	private final Map<Item, ClientItem> itemDefinitions = new LinkedHashMap<>();

	public MBModelProvider(PackOutput output) {
		this.blockStatePathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
		this.itemPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
		this.modelPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		BlockModelGenerators generators = new BlockModelGenerators(
				generator -> this.blockStates.put(generator.block(), generator),
				new CollectingItemModelOutput(),
				(id, model) -> this.models.put(id, model));

		generateTerracotta(generators);
		generateVanillaSlabs();
		finalizeItemModels();

		Map<Block, BlockStateModelDispatcher> dispatchers = new LinkedHashMap<>();
		this.blockStates.forEach((block, generator) -> dispatchers.put(block, generator.create()));

		return CompletableFuture.allOf(
				DataProvider.saveAll(cache, BlockStateModelDispatcher.CODEC,
						block -> this.blockStatePathProvider.json(BuiltInRegistries.BLOCK.getKey(block)),
						dispatchers),
				DataProvider.saveAll(cache, supplier -> supplier.get(),
						this.modelPathProvider::json, Map.copyOf(this.models)),
				DataProvider.saveAll(cache, ClientItem.CODEC,
						item -> this.itemPathProvider.json(BuiltInRegistries.ITEM.getKey(item)),
						Map.copyOf(this.itemDefinitions)));
	}

	// ------------------------------------------------------------- terracotta

	/** Stairs and slabs for plain, dyed and glazed terracotta. */
	private void generateTerracotta(BlockModelGenerators generators) {
		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			Block base = family.base();
			Block stairs = family.stairs();
			Block slab = family.slab();

			if (family.glazed()) {
				generateGlazed(generators, base, stairs, slab);
			} else {
				generatePlain(generators, base, stairs, slab);
			}
		}
	}

	/** Plain and dyed terracotta: vanilla templates, one texture on every slot. */
	private void generatePlain(BlockModelGenerators generators, Block base, Block stairs, Block slab) {
		TextureMapping mapping = singleTextureMapping(base);

		Identifier slabBottom = templateModel(generators, ModelTemplates.SLAB_BOTTOM, slab, "", mapping);
		Identifier slabTop = templateModel(generators, ModelTemplates.SLAB_TOP, slab, "top", mapping);
		Identifier straight = templateModel(generators, ModelTemplates.STAIRS_STRAIGHT, stairs, "", mapping);
		Identifier inner = templateModel(generators, ModelTemplates.STAIRS_INNER, stairs, "inner", mapping);
		Identifier outer = templateModel(generators, ModelTemplates.STAIRS_OUTER, stairs, "outer", mapping);

		writeVerticalModels(slab, plainVerticalSlab(base, null));
		generateModSlabState(slab, slabBottom, slabTop, baseBlockModelId(base));
		this.blockStates.put(stairs, BlockModelGenerators.createStairs(stairs,
				plainVariant(inner), plainVariant(straight), plainVariant(outer)));
	}

	/**
	 * Glazed terracotta: the same shapes as vanilla stairs and slabs, but with the pattern's
	 * per-face rotation baked into the geometry so the motif survives the cut corner, and with the
	 * whole model turned by the {@code facing} the player was looking when they placed it.
	 *
	 * <p>Vanilla gives a glazed block one model per {@code facing}, rotated by a quarter turn at a
	 * time, so a player looking west gets a pattern a quarter turn round from one looking south.
	 * The stairs and the slab cut out of that block have to follow it, or a staircase made of them
	 * runs against the wall behind it. A blockstate {@code y} rotation cannot do it here - it would
	 * turn the cut corner along with the texture - so each of the four facings gets its own model.
	 *
	 * <p>Every face takes the sprite of the <em>same world direction</em> of the full glazed block,
	 * rotated exactly as vanilla rotates it on that face, and then turned with the rest of the model.
	 */
	private void generateGlazed(BlockModelGenerators generators, Block base, Block stairs, Block slab) {
		Material pattern = vanillaTexture(base);
		Material[] all = { pattern, pattern, pattern, pattern, pattern, pattern };

		// Element for element, box for box, the geometry vanilla's slab, stairs, inner_stairs and
		// outer_stairs templates lay down. Only the face rotations differ, so the pattern keeps
		// pointing the same way it does on the full block.
		Map<String, JsonObject> upright = glazedVerticalSlab(base);

		for (int turn = 0; turn < TURNS; turn++) {
			// formModelId, not baseModelId: the unturned copy has an empty suffix and must keep
			// the block's own model id. baseModelId would append "_" and emit a model named
			// "…_stairs_" that nothing - least of all the item definition - ever asks for.
			this.models.put(formModelId(slab, suffix("", turn)),
				modelInstance(turned(glazedCubeGeometry(all, SLAB_BOX), turn)));
			this.models.put(formModelId(slab, suffix("top", turn)),
				modelInstance(turned(glazedCubeGeometry(all, TOP_SLAB_BOX), turn)));
			this.models.put(formModelId(stairs, suffix("", turn)),
				modelInstance(turned(glazedCubeGeometry(all, SLAB_BOX, STAIR_BOX), turn)));
			this.models.put(formModelId(stairs, suffix("inner", turn)),
				modelInstance(turned(glazedCubeGeometry(all, SLAB_BOX, STAIR_BOX, INNER_BOX), turn)));
			this.models.put(formModelId(stairs, suffix("outer", turn)),
				modelInstance(turned(glazedCubeGeometry(all, SLAB_BOX, OUTER_BOX), turn)));

			writeVerticalModels(slab, upright, turn);
		}

		generateModSlabState(slab, baseModelId(slab), baseModelId(slab, "top"), baseBlockModelId(base), true);

		this.blockStates.put(stairs, glazedStairsState(stairs));

		registerItemModel(Item.BY_BLOCK.get(slab), baseModelId(slab));
		registerItemModel(Item.BY_BLOCK.get(stairs), baseModelId(stairs));
	}

	/** The vanilla texture of a block, i.e. {@code minecraft:block/<its id>}. */
	private static Material vanillaTexture(Block block) {
		return new Material(Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/" + vanillaPath(block)));
	}

	// ---------------------------------------------------------- vertical slabs

	/**
	 * A slab blockstate covering the flat states plus all four upright halves, which the mixin
	 * selects through the {@code vertical} and {@code axis} properties.
	 *
	 * <p>Half of this table is unreachable: the mixin only turns on {@code vertical} for a
	 * horizontal {@code axis}, and only leaves {@code axis} at its flat default when {@code
	 * vertical} is off. {@link PropertyDispatch} still demands a definition for every combination
	 * of the properties it declares, so the impossible states reuse the flat shapes.
	 *
	 * @param glazed {@code true} for a glazed slab, whose model is turned by its placement direction
	 *               and therefore needs one model per quarter turn. Only a glazed slab declares the
	 *               property that holds it, so the table has to be built two ways.
	 */
	private void generateModSlabState(Block slab, Identifier bottomModel, Identifier topModel, Identifier fullModel) {
		generateModSlabState(slab, bottomModel, topModel, fullModel, false);
	}

	private void generateModSlabState(Block slab, Identifier bottomModel, Identifier topModel, Identifier fullModel,
			final boolean glazed) {
		MultiVariant full = plainVariant(fullModel);

		if (!glazed) {
			PropertyDispatch<MultiVariant> dispatch = PropertyDispatch
					.initial(MBBlockStateProperties.VERTICAL, MBBlockStateProperties.AXIS, SlabBlock.TYPE)
					.generate((vertical, axis, type) -> {
						if (vertical && axis != Direction.Axis.Y && type != SlabType.DOUBLE) {
							return plainVariant(verticalModelId(slab, fillName(axis, type)));
						}

						return switch (type) {
							case BOTTOM -> plainVariant(bottomModel);
							case TOP -> plainVariant(topModel);
							case DOUBLE -> full;
						};
					});

			this.blockStates.put(slab, MultiVariantGenerator.dispatch(slab).with(dispatch));
			return;
		}

		PropertyDispatch<MultiVariant> dispatch = PropertyDispatch
				.initial(MBBlockStateProperties.VERTICAL, MBBlockStateProperties.AXIS, SlabBlock.TYPE, MBBlockStateProperties.GLAZED_FACING)
				.generate((vertical, axis, type, facing) -> {
					int turn = MBBlockStateProperties.glazedTurns(facing);

					if (vertical && axis != Direction.Axis.Y && type != SlabType.DOUBLE) {
						return plainVariant(verticalModelId(slab, fillName(axis, type), turn));
					}

					return switch (type) {
						case BOTTOM -> plainVariant(formModelId(slab, suffix("", turn)));
						case TOP -> plainVariant(formModelId(slab, suffix("top", turn)));
						case DOUBLE -> full;
					};
				});

		this.blockStates.put(slab, MultiVariantGenerator.dispatch(slab).with(dispatch));
	}

	/**
	 * The name of the upright model half that holds {@code type} of the {@code axis} pair.
	 *
	 * <p>{@link SlabType} names a side of the block, not a side of an axis: upright it is the half
	 * showing its face towards the <em>negative</em> end, which is the opposite of what it means on
	 * the Y axis. {@link VerticalSlab.Fill#positive()} is where that translation lives; asking
	 * whether the blockstate would be {@code bottom} when the half is the positive one is the same
	 * question.
	 */
	private static String fillName(final Direction.Axis axis, final SlabType type) {
		boolean positive = type == Fill.positive(axis).type();

		if (axis == Direction.Axis.X) {
			return positive ? FILL_POSITIVE_X : FILL_NEGATIVE_X;
		}

		return positive ? FILL_POSITIVE_Z : FILL_NEGATIVE_Z;
	}

	/**
	 * The stair blockstate of a glazed family: vanilla's own stair table, with every variant chosen
	 * by the {@code facing} the player was looking when they placed it.
	 */
	private BlockModelDefinitionGenerator glazedStairsState(Block stairs) {
		return MultiVariantGenerator.dispatch(stairs).with(PropertyDispatch
				.initial(StairBlock.FACING, StairBlock.HALF, StairBlock.SHAPE, MBBlockStateProperties.GLAZED_FACING)
				.generate((facing, half, shape, placed) -> {
					int turn = MBBlockStateProperties.glazedTurns(placed);

					return plainVariant(switch (shape) {
						case STRAIGHT -> formModelId(stairs, suffix("", turn));
						case INNER_LEFT, INNER_RIGHT -> formModelId(stairs, suffix("inner", turn));
						case OUTER_LEFT, OUTER_RIGHT -> formModelId(stairs, suffix("outer", turn));
					});
				}));
	}

	/**
	 * A vertical model for every vanilla slab, so the whole vanilla half-slab catalogue can also
	 * stand upright.
	 *
	 * <p>Vanilla's own slab blockstates and models are left exactly as they are: the extra states
	 * are supplied at load time by
	 * {@code com.camelie.morebuildingblocks.client.MBVanillaSlabModelPlugin}. Only the upright
	 * shapes are written here, and each one is self-contained.
	 */
	private void generateVanillaSlabs() {
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof SlabBlock)) {
				continue;
			}

			Identifier id = BuiltInRegistries.BLOCK.getKey(block);

			// This mod's own slabs already have upright models from generateTerracotta.
			if (!VANILLA_NAMESPACE.equals(id.getNamespace())) {
				continue;
			}

			VanillaSlabTextures.Textures textures = VanillaSlabTextures.bySlab(id.getPath());

			if (textures == null) {
				// Not a vanilla slab this provider knows: leave it to the block's own model.
				continue;
			}

			writeVerticalModels(block, plainVerticalSlab(block, textures));
		}
	}

	/** Writes the four upright boxes of {@code slab}, one per half it can fill. */
	private void writeVerticalModels(Block slab, Map<String, JsonObject> geometries) {
		geometries.forEach((fill, geometry) ->
			this.models.put(verticalModelId(slab, fill), modelInstance(geometry)));
	}

	/**
	 * Writes the upright boxes of a glazed {@code slab} as they look once turned by {@code turn}
	 * quarter turns.
	 *
	 * <p>The boxes themselves never move - only the pattern on them does - so this is one copy of
	 * each box per turn, and the {@code glazed_facing} the player placed the slab with selects
	 * between them.
	 */
	private void writeVerticalModels(Block slab, Map<String, JsonObject> geometries, final int turn) {
		geometries.forEach((fill, geometry) ->
			this.models.put(verticalModelId(slab, fill, turn), modelInstance(turned(geometry, turn))));
	}

	/**
	 * The four upright geometries of a plain slab, each drawing everything from its side sprite:
	 * the whole 16x16 sprite on the two big faces, and the matching half or strip of it on the thin
	 * ones - the same cropping vanilla's own slab template applies, so an upright slab is cut out of
	 * the same texture as the flat one.
	 *
	 * @param base     the vanilla block whose sprite the slab is cut from
	 * @param textures the three vanilla sprites of a vanilla slab, or {@code null} to use
	 *                 {@code base}'s own sprite on every slot
	 */
	private static Map<String, JsonObject> plainVerticalSlab(Block base, VanillaSlabTextures.Textures textures) {
		Material bottom = textures != null ? vanillaTexture(textures.bottom()) : vanillaTexture(base);
		Material top = textures != null ? vanillaTexture(textures.top()) : vanillaTexture(base);
		Material side = textures != null ? vanillaTexture(textures.side()) : vanillaTexture(base);

		Map<String, JsonObject> geometries = new LinkedHashMap<>(4);
		geometries.put(FILL_NEGATIVE_X, geometry(bottom, top, side, side, side, side, NEGATIVE_X_BOX));
		geometries.put(FILL_POSITIVE_X, geometry(bottom, top, side, side, side, side, POSITIVE_X_BOX));
		geometries.put(FILL_NEGATIVE_Z, geometry(bottom, top, side, side, side, side, NEGATIVE_Z_BOX));
		geometries.put(FILL_POSITIVE_Z, geometry(bottom, top, side, side, side, side, POSITIVE_Z_BOX));
		return geometries;
	}

	/**
	 * The four upright geometries of a glazed slab, each turned the way the glazed block the player
	 * placed alongside it would have been. Unlike a plain slab every face gets the sprite of the
	 * direction it faces, so a half cut out of a glazed block still reads as that block.
	 *
	 * @param base the vanilla glazed block the slab is cut from
	 */
	private static Map<String, JsonObject> glazedVerticalSlab(Block base) {
		Material pattern = vanillaTexture(base);
		Material[] all = { pattern, pattern, pattern, pattern, pattern, pattern };

		Map<String, JsonObject> geometries = new LinkedHashMap<>(4);
		geometries.put(FILL_NEGATIVE_X, glazedCubeGeometry(all, NEGATIVE_X_BOX));
		geometries.put(FILL_POSITIVE_X, glazedCubeGeometry(all, POSITIVE_X_BOX));
		geometries.put(FILL_NEGATIVE_Z, glazedCubeGeometry(all, NEGATIVE_Z_BOX));
		geometries.put(FILL_POSITIVE_Z, glazedCubeGeometry(all, POSITIVE_Z_BOX));
		return geometries;
	}

	// -------------------------------------------------------------- item models

	/** Collects item models, mirroring vanilla's {@code ModelProvider.ItemInfoCollector}. */
	private final class CollectingItemModelOutput implements ItemModelOutput {
		@Override
		public void accept(Item item, ItemModel.Unbaked model, ClientItem.Properties properties) {
			itemDefinitions.put(item, new ClientItem(model, properties));
		}

		@Override
		public void copy(Item donor, Item acceptor) {
			ClientItem donorInfo = itemDefinitions.get(donor);

			if (donorInfo != null) {
				itemDefinitions.put(acceptor, donorInfo);
			}
		}
	}

	private void registerItemModel(Item item, Identifier model) {
		if (item != null) {
			this.itemDefinitions.put(item, new ClientItem(
					ItemModelUtils.plainModel(model), ClientItem.Properties.DEFAULT));
		}
	}

	/**
	 * Any block item this mod registered without an explicit model falls back to its block's model.
	 * Only this mod's namespace is touched, so no vanilla item definition is ever emitted.
	 */
	private void finalizeItemModels() {
		BuiltInRegistries.ITEM.forEach(item -> {
			if (!(item instanceof BlockItem blockItem) || this.itemDefinitions.containsKey(item)) {
				return;
			}

			Identifier id = BuiltInRegistries.ITEM.getKey(item);

			if (MoreBuildingBlocks.MOD_ID.equals(id.getNamespace())) {
				registerItemModel(item, ModelLocationUtils.getModelLocation(blockItem.getBlock()));
			}
		});
	}

	// ------------------------------------------------------------------ geometry

	/** Vanilla {@code block/slab}: the bottom half of the cube. */
	private static final Box SLAB_BOX = new Box(0, 0, 0, 16, 8, 16);
	/** Vanilla {@code block/slab_top}: the top half. */
	private static final Box TOP_SLAB_BOX = new Box(0, 8, 0, 16, 16, 16);
	/** Vanilla {@code block/stairs}: the slab plus the raised right half at the back. */
	private static final Box STAIR_BOX = new Box(8, 8, 0, 16, 16, 16);
	/** Vanilla {@code block/inner_stairs}: adds the raised left strip. */
	private static final Box INNER_BOX = new Box(0, 8, 8, 8, 16, 16);
	/** Vanilla {@code block/outer_stairs}: the raised quarter sits diagonally. */
	private static final Box OUTER_BOX = new Box(8, 8, 8, 16, 16, 16);

	/** Upright slab filled on the west side, i.e. its visible face points east. */
	private static final Box NEGATIVE_X_BOX = new Box(0, 0, 0, 8, 16, 16);
	/** Upright slab filled on the east side. */
	private static final Box POSITIVE_X_BOX = new Box(8, 0, 0, 16, 16, 16);
	/** Upright slab filled on the north side. */
	private static final Box NEGATIVE_Z_BOX = new Box(0, 0, 0, 16, 16, 8);
	/** Upright slab filled on the south side. */
	private static final Box POSITIVE_Z_BOX = new Box(0, 0, 8, 16, 16, 16);

	/** One box of the model, in vanilla's own coordinates. */
	private record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
	}

	/**
	 * A plain model: vanilla's block parent for the display transforms, one box, one sprite per
	 * texture slot.
	 */
	private static JsonObject geometry(Material bottom, Material top, Material side, Material side2,
			Material side3, Material side4, Box box) {
		JsonObject root = new JsonObject();
		root.addProperty("parent", BLOCK_PARENT.toString());

		JsonObject textures = new JsonObject();
		textures.addProperty("bottom", bottom.sprite().toString());
		textures.addProperty("top", top.sprite().toString());
		textures.addProperty("side", side.sprite().toString());
		textures.addProperty("particle", side.sprite().toString());
		root.add("textures", textures);

		JsonArray elements = new JsonArray();
		elements.add(plainBox(box));
		root.add("elements", elements);

		return root;
	}

	/**
	 * One box whose faces crop their sprite the way vanilla's own slab and stair models do.
	 *
	 * <p>Each face samples exactly the part of the texture its quad covers, so the top half of a
	 * flat slab shows the top half of the side texture and the edge of an upright slab shows the
	 * matching half of the block's own face.
	 */
	private static JsonObject plainBox(Box box) {
		JsonObject element = boxElement(box);
		JsonObject faces = new JsonObject();

		faces.add("down", plainFace(crop(box, "down"), "#bottom", "down", box));
		faces.add("up", plainFace(crop(box, "up"), "#top", "up", box));
		faces.add("north", plainFace(crop(box, "north"), "#side", "north", box));
		faces.add("south", plainFace(crop(box, "south"), "#side", "south", box));
		faces.add("west", plainFace(crop(box, "west"), "#side", "west", box));
		faces.add("east", plainFace(crop(box, "east"), "#side", "east", box));

		element.add("faces", faces);
		return element;
	}

	/**
	 * One quarter turn of a glazed model, as seen from above.
	 *
	 * <p>A model is turned the way a blockstate's {@code y} would turn it - clockwise looking down -
	 * but a slab and a stair cannot use one, because it would carry the cut corner round with the
	 * pattern. Turning the geometry a quarter at a time by hand reproduces it exactly: the boxes move
	 * by {@code (x, z) -> (z, 16 - x)}, the six face directions move with them, and the pattern's own
	 * per-face rotation gains the same quarter turn.
	 *
	 * <p>{@link #direction} maps a face of the <em>turned</em> model back to the face of the
	 * unturned one it was made from, which is both where its sprite was cropped from and the value
	 * it keeps.
	 *
	 * <p>That value is deliberately <em>not</em> adjusted by the turn. A blockstate {@code y}
	 * rotation turns the model's geometry and nothing else: {@code FaceBakery.bakeQuad} leaves a
	 * face's sprite alone whenever the model state's UV transform is the identity, which it is for an
	 * ordinary variant without {@code uvlock}, and the texture on a turned face comes out rotated by
	 * exactly the turn because the quad under it turned. Vanilla shows this outright - one model,
	 * {@code template_glazed_terracotta}, with fixed per-face rotations serves all four {@code
	 * facing} values of a glazed terracotta block through nothing but a {@code y} rotation. Adding
	 * the turn here as well would rotate the pattern twice.
	 */
	private record Turn(int count) {
		/** The world direction {@code direction} of the model this one was turned from. */
		private static String reverse(final String direction) {
			return switch (direction) {
				case "north" -> "west";
				case "west" -> "south";
				case "south" -> "east";
				case "east" -> "north";
				default -> direction;
			};
		}

		private String direction(final String direction) {
			String turned = direction;

			for (int i = 0; i < this.count; i++) {
				turned = reverse(turned);
			}

			return turned;
		}

		/** The box {@code box} occupies once this turn has been applied to it. */
		private Box box(final Box box) {
			return switch (this.count) {
				case 0 -> box;
				case 1 -> new Box(box.z1(), box.y1(), 16 - box.x2(), box.z2(), box.y2(), 16 - box.x1());
				case 2 -> new Box(16 - box.x2(), box.y1(), 16 - box.z2(), 16 - box.x1(), box.y2(), 16 - box.z1());
				default -> new Box(16 - box.z2(), box.y1(), box.x1(), 16 - box.z1(), box.y2(), box.x2());
			};
		}
	}

	/**
	 * A glazed model turned {@code turn} quarter turns clockwise, as seen from above.
	 *
	 * <p>This is what a blockstate {@code y} rotation would do to the model, and it is the reason
	 * one cannot be used here: it would carry the cut corner of a slab or a stair round with the
	 * pattern, so a staircase would no longer line up with the wall behind it. Turning the geometry
	 * face by face applies the turn to the pattern while leaving the shape where it belongs.
	 *
	 * <p>The texture slot is a reference, so it is copied through untouched.
	 */
	private static JsonObject turned(JsonObject geometry, final int turn) {
		if (turn == 0) {
			return geometry;
		}

		Turn rotation = new Turn(turn);
		JsonObject root = new JsonObject();
		geometry.entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));

		JsonArray elements = new JsonArray();

		for (JsonElement element : root.getAsJsonArray("elements")) {
			JsonObject box = element.getAsJsonObject();
			JsonObject turnedBox = boxElement(rotation.box(boxOf(box)));
			JsonObject faces = new JsonObject();

			for (String direction : FACE_DIRECTIONS) {
				faces.add(direction, turnedFace(box.getAsJsonObject("faces"), direction, rotation));
			}

			turnedBox.add("faces", faces);
			elements.add(turnedBox);
		}

		root.add("elements", elements);
		return root;
	}

	/** The box a written element covers, read back out of its {@code from}/{@code to} arrays. */
	private static Box boxOf(JsonObject element) {
		JsonArray from = element.getAsJsonArray("from");
		JsonArray to = element.getAsJsonArray("to");
		return new Box(
				from.get(0).getAsInt(), from.get(1).getAsInt(), from.get(2).getAsInt(),
				to.get(0).getAsInt(), to.get(1).getAsInt(), to.get(2).getAsInt());
	}

	/**
	 * One face of a turned box: a verbatim copy of the face that was turned onto it, so the crop and
	 * the rotation stay exactly as the unturned model wrote them. The turn itself is already in the
	 * geometry, and a blockstate {@code y} rotation would not have altered these values either.
	 */
	private static JsonObject turnedFace(JsonObject faces, final String direction, final Turn turn) {
		JsonObject original = faces.getAsJsonObject(turn.direction(direction));
		JsonObject turned = new JsonObject();
		original.entrySet().forEach(entry -> turned.add(entry.getKey(), entry.getValue()));
		return turned;
	}

	/**
	 * One glazed box: the pattern's rotation is written onto every face, so the motif on a cut
	 * shape continues to point the way it does on the full block.
	 */
	private static JsonObject glazedCubeGeometry(final Material[] materials, final Box... boxes) {
		JsonObject root = new JsonObject();
		root.addProperty("parent", BLOCK_PARENT.toString());

		// The faces refer to "#pattern", so the slot has to be defined here. Vanilla's
		// template_glazed_terracotta only names "particle" because it inherits the rest from the
		// per-block model that parents it; these models have no such parent.
		JsonObject textures = new JsonObject();
		textures.addProperty("pattern", materials[0].sprite().toString());
		textures.addProperty("particle", materials[0].sprite().toString());
		root.add("textures", textures);

		JsonArray elements = new JsonArray();

		for (Box box : boxes) {
			JsonObject element = boxElement(box);
			JsonObject faces = new JsonObject();

			for (String direction : FACE_DIRECTIONS) {
				faces.add(direction, glazedFace(box, direction, new Turn(0)));
			}

			element.add("faces", faces);
			elements.add(element);
		}

		root.add("elements", elements);
		return root;
	}

	private static JsonObject boxElement(Box box) {
		JsonObject element = new JsonObject();
		element.add("from", vector(box.x1(), box.y1(), box.z1()));
		element.add("to", vector(box.x2(), box.y2(), box.z2()));
		return element;
	}

	/** A face that crops its sprite to the part of the block face its quad covers. */
	private static JsonObject plainFace(int[] crop, String texture, String direction, Box box) {
		JsonObject face = new JsonObject();
		face.add("uv", uv(crop[0], crop[1], crop[2], crop[3]));
		face.addProperty("texture", texture);

		if (canCull(crop, box, direction)) {
			face.addProperty("cullface", direction);
		}

		return face;
	}

	/**
	 * A glazed face: the pattern of the face it shows, with vanilla's own rotation for that face,
	 * cropped to the part of the block face the quad covers.
	 *
	 * @param turn the turn that carried this face to where it is, which decides where its pattern
	 *             was cropped from
	 */
	private static JsonObject glazedFace(Box box, String direction, Turn turn) {
		JsonObject face = new JsonObject();
		int[] crop = crop(box, direction);
		face.add("uv", uv(crop[0], crop[1], crop[2], crop[3]));
		face.addProperty("texture", "#pattern");

		int rotation = GLAZED_FACE_ROTATION.get(turn.direction(direction));

		if (rotation != 0) {
			face.addProperty("rotation", rotation);
		}

		if (canCull(crop, box, direction)) {
			face.addProperty("cullface", direction);
		}

		return face;
	}

	/**
	 * Only a face that covers the whole block face it lies on, and lies on the block's outer
	 * boundary, may cull; otherwise the two boxes of a stair would hide each other.
	 */
	private static boolean canCull(int[] crop, Box box, String direction) {
		return crop[2] - crop[0] == 16 && crop[3] - crop[1] == 16 && isOnBoundary(box, direction);
	}

	/** @return whether {@code box} touches the outer face of the block in {@code direction}. */
	private static boolean isOnBoundary(Box box, String direction) {
		return switch (direction) {
			case "down" -> box.y1() == 0;
			case "up" -> box.y2() == 16;
			case "north" -> box.z1() == 0;
			case "south" -> box.z2() == 16;
			case "west" -> box.x1() == 0;
			case "east" -> box.x2() == 16;
			default -> false;
		};
	}

	/**
	 * The part of a full block face that the given face of {@code box} covers, in texture
	 * coordinates.
	 *
	 * <p>Minecraft maps a face's {@code uv} rectangle onto the block's own face, so a quad that
	 * spans only half of the block's face has to ask for only that half of the sprite.
	 */
	private static int[] crop(Box box, String direction) {
		return switch (direction) {
			case "down" -> new int[] { box.x1(), box.z1(), box.x2(), box.z2() };
			case "up" -> new int[] { box.x1(), 16 - box.z2(), box.x2(), 16 - box.z1() };
			case "north" -> new int[] { box.x1(), 16 - box.y2(), box.x2(), 16 - box.y1() };
			case "south" -> new int[] { 16 - box.x2(), 16 - box.y2(), 16 - box.x1(), 16 - box.y1() };
			case "west" -> new int[] { box.z1(), 16 - box.y2(), box.z2(), 16 - box.y1() };
			case "east" -> new int[] { 16 - box.z2(), 16 - box.y2(), 16 - box.z1(), 16 - box.y1() };
			default -> new int[] { 0, 0, 16, 16 };
		};
	}

	private static JsonArray vector(int x, int y, int z) {
		JsonArray array = new JsonArray();
		array.add(x);
		array.add(y);
		array.add(z);
		return array;
	}

	private static JsonArray uv(int minU, int minV, int maxU, int maxV) {
		JsonArray array = new JsonArray();
		array.add(minU);
		array.add(minV);
		array.add(maxU);
		array.add(maxV);
		return array;
	}

	// ------------------------------------------------------------------ plumbing

	private static ModelInstance modelInstance(JsonObject json) {
		return () -> json;
	}

	private static MultiVariant plainVariant(Identifier model) {
		return new MultiVariant(WeightedList.of(new Variant(model)));
	}

	private Identifier templateModel(BlockModelGenerators generators, ModelTemplate template, Block block,
			String suffix, TextureMapping mapping) {
		return template.create(formModelId(block, suffix), mapping, generators.modelOutput);
	}

	/** {@code morebuildingblocks:block/<block path>}. */
	private static Identifier baseModelId(Block block) {
		return modelId(BuiltInRegistries.BLOCK.getKey(block));
	}

	/** {@code morebuildingblocks:block/<block path>_<suffix>}. */
	private static Identifier baseModelId(Block block, String suffix) {
		return modelId(BuiltInRegistries.BLOCK.getKey(block).withSuffix("_" + suffix));
	}

	/**
	 * The suffix of the model for one quarter turn: the plain shape keeps its name, and the three
	 * turned copies are told apart by the {@code facing} they belong to.
	 */
	private static String suffix(final String shape, final int turn) {
		if (turn == 0) {
			return shape;
		}

		return shape.isEmpty() ? "facing" + turn : shape + "_facing" + turn;
	}

	/**
	 * {@code morebuildingblocks:block/<id>}, or {@code <id>_<suffix>} when the same block needs more
	 * than one model.
	 *
	 * <p>Used for slabs and stairs, whose registry ids already end in {@code _slab} and {@code
	 * _stairs}: appending the form name again would produce {@code white_terracotta_slab_slab}.
	 */
	private static Identifier formModelId(Block block, String suffix) {
		return suffix.isEmpty() ? baseModelId(block) : baseModelId(block, suffix);
	}

	/**
	 * The model of the vanilla block a slab or stairs is made of, which is also its double-slab
	 * model, e.g. {@code minecraft:block/white_terracotta}.
	 */
	private static Identifier baseBlockModelId(Block block) {
		return Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/" + vanillaPath(block));
	}

	private static Identifier modelId(Identifier path) {
		return Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "block/" + path.getPath());
	}

	/**
	 * {@code morebuildingblocks:vertical_slab/<block path>/<fill>}.
	 *
	 * <p>Note the missing {@code block/}: {@code PackOutput.PathProvider} prefixes the kind
	 * directory itself ({@code this.kind + "/" + path + ".json"}), so the id of a model under
	 * {@code assets/<ns>/models/} is the file path <em>below</em> {@code models/}. Every other model
	 * here starts with {@code block/} for the same reason; these deliberately start with their own
	 * folder instead.
	 */
	private static Identifier verticalModelId(Block block, String fill) {
		return Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID,
				VERTICAL_DIRECTORY + BuiltInRegistries.BLOCK.getKey(block).getPath() + "/" + fill);
	}

	/**
	 * {@code morebuildingblocks:vertical_slab/<block path>/<fill>_facing<turn>}, for a glazed slab
	 * whose pattern is turned by the {@code facing} the player placed it with.
	 */
	private static Identifier verticalModelId(Block block, String fill, final int turn) {
		return turn == 0 ? verticalModelId(block, fill)
				: verticalModelId(block, fill + "_facing" + turn);
	}

	/**
	 * One texture on all three slots.
	 *
	 * <p>The sprite is the vanilla texture of the vanilla block this one is built on, not {@code
	 * morebuildingblocks:block/<this block>}: this mod deliberately ships no texture files, so a
	 * reference in its own namespace would resolve to nothing. Every one of the 33 base blocks has a
	 * vanilla counterpart providing the texture.
	 */
	private static TextureMapping singleTextureMapping(Block block) {
		TextureMapping mapping = new TextureMapping();
		Material texture = vanillaTexture(block);
		mapping.put(SLOT_BOTTOM, texture);
		mapping.put(SLOT_TOP, texture);
		mapping.put(SLOT_SIDE, texture);
		return mapping;
	}

	/**
	 * The path of the vanilla block a mod block copies.
	 *
	 * <p>Every slab and stair keeps its base block's name - {@code white_terracotta},
	 * {@code light_blue_glazed_terracotta}, yes even {@code terracotta} - so the path is already
	 * right and only the namespace has to be swapped.
	 */
	private static String vanillaPath(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	/** A vanilla sprite from an already-{@code block/}-prefixed path. */
	private static Material vanillaTexture(String path) {
		return new Material(Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, path));
	}

	@Override
	public String getName() {
		return "MoreBuildingBlocks Models";
	}
}
