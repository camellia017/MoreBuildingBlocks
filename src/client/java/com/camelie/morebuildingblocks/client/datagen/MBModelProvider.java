package com.camelie.morebuildingblocks.client.datagen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
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
import net.minecraft.world.level.block.state.properties.SlabType;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
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
	 * per-face rotation baked into the geometry so the motif survives the cut corner.
	 *
	 * <p>Every face a player can see takes the sprite of the <em>same world direction</em> of the
	 * full glazed block, rotated exactly as vanilla rotates it on that face. A south-facing upright
	 * slab is therefore filled on the south side, and the face the player sees - the north one -
	 * carries the glazed block's north face.
	 */
	private void generateGlazed(BlockModelGenerators generators, Block base, Block stairs, Block slab) {
		Material pattern = vanillaTexture(base);

		Identifier slabModel = baseModelId(slab);
		Identifier slabTopModel = baseModelId(slab, "top");
		Identifier straight = baseModelId(stairs);
		Identifier inner = baseModelId(stairs, "inner");
		Identifier outer = baseModelId(stairs, "outer");

		// Element for element, box for box, the geometry vanilla's slab, stairs, inner_stairs and
		// outer_stairs templates lay down. Only the face rotations differ, so the pattern keeps
		// pointing the same way it does on the full block.
		this.models.put(slabModel, modelInstance(glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, SLAB_BOX)));
		this.models.put(slabTopModel, modelInstance(glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, TOP_SLAB_BOX)));
		this.models.put(straight, modelInstance(glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, SLAB_BOX, STAIR_BOX)));
		this.models.put(inner, modelInstance(glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, SLAB_BOX, STAIR_BOX, INNER_BOX)));
		this.models.put(outer, modelInstance(glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, SLAB_BOX, OUTER_BOX)));

		writeVerticalModels(slab, glazedVerticalSlab(base));
		generateModSlabState(slab, slabModel, slabTopModel, baseBlockModelId(base));

		this.blockStates.put(stairs, BlockModelGenerators.createStairs(stairs,
				plainVariant(inner), plainVariant(straight), plainVariant(outer)));

		registerItemModel(Item.BY_BLOCK.get(slab), slabModel);
		registerItemModel(Item.BY_BLOCK.get(stairs), straight);
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
	 */
	private void generateModSlabState(Block slab, Identifier bottomModel, Identifier topModel, Identifier fullModel) {
		MultiVariant bottom = plainVariant(bottomModel);
		MultiVariant top = plainVariant(topModel);
		MultiVariant full = plainVariant(fullModel);
		MultiVariant verticalNegativeX = plainVariant(verticalModelId(slab, FILL_NEGATIVE_X));
		MultiVariant verticalPositiveX = plainVariant(verticalModelId(slab, FILL_POSITIVE_X));
		MultiVariant verticalNegativeZ = plainVariant(verticalModelId(slab, FILL_NEGATIVE_Z));
		MultiVariant verticalPositiveZ = plainVariant(verticalModelId(slab, FILL_POSITIVE_Z));

		PropertyDispatch<MultiVariant> dispatch = PropertyDispatch
				.initial(MBBlockStateProperties.VERTICAL, MBBlockStateProperties.AXIS, SlabBlock.TYPE)
				.generate((vertical, axis, type) -> {
					if (vertical && axis != Direction.Axis.Y && type != SlabType.DOUBLE) {
						boolean positive = type == SlabType.TOP;

						if (axis == Direction.Axis.X) {
							return positive ? verticalPositiveX : verticalNegativeX;
						}

						return positive ? verticalPositiveZ : verticalNegativeZ;
					}

					return switch (type) {
						case BOTTOM -> bottom;
						case TOP -> top;
						case DOUBLE -> full;
					};
				});

		this.blockStates.put(slab, MultiVariantGenerator.dispatch(slab).with(dispatch));
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
		geometries.forEach((fill, geometry) -> this.models.put(verticalModelId(slab, fill), modelInstance(geometry)));
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
	 * The four upright geometries of a glazed slab. Unlike a plain slab every face gets the sprite
	 * of the direction it faces, so a half cut out of a glazed block still reads as that block.
	 *
	 * @param base the vanilla glazed block the slab is cut from
	 */
	private static Map<String, JsonObject> glazedVerticalSlab(Block base) {
		Material pattern = vanillaTexture(base);

		Map<String, JsonObject> geometries = new LinkedHashMap<>(4);
		geometries.put(FILL_NEGATIVE_X, glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, NEGATIVE_X_BOX));
		geometries.put(FILL_POSITIVE_X, glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, POSITIVE_X_BOX));
		geometries.put(FILL_NEGATIVE_Z, glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, NEGATIVE_Z_BOX));
		geometries.put(FILL_POSITIVE_Z, glazedCubeGeometry(pattern, pattern, pattern, pattern, pattern, pattern, POSITIVE_Z_BOX));
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
	 * One glazed box: the pattern's rotation is written onto every face, so the motif on a cut
	 * shape continues to point the way it does on the full block.
	 */
	private static JsonObject glazedCubeGeometry(Material down, Material up, Material north, Material south,
			Material west, Material east, Box... boxes) {
		JsonObject root = new JsonObject();
		root.addProperty("parent", BLOCK_PARENT.toString());

		// The faces refer to "#pattern", so the slot has to be defined here. Vanilla's
		// template_glazed_terracotta only names "particle" because it inherits the rest from the
		// per-block model that parents it; these models have no such parent.
		JsonObject textures = new JsonObject();
		textures.addProperty("pattern", down.sprite().toString());
		textures.addProperty("particle", down.sprite().toString());
		root.add("textures", textures);

		JsonArray elements = new JsonArray();

		for (Box box : boxes) {
			JsonObject element = boxElement(box);
			JsonObject faces = new JsonObject();

			faces.add("down", glazedFace(box, "down", down));
			faces.add("up", glazedFace(box, "up", up));
			faces.add("north", glazedFace(box, "north", north));
			faces.add("south", glazedFace(box, "south", south));
			faces.add("west", glazedFace(box, "west", west));
			faces.add("east", glazedFace(box, "east", east));

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
	 * A glazed face: the pattern, rotated the way vanilla rotates it on that direction, cropped to
	 * the part of the block face the quad covers.
	 */
	private static JsonObject glazedFace(Box box, String direction, Material material) {
		JsonObject face = new JsonObject();
		int[] crop = crop(box, direction);
		face.add("uv", uv(crop[0], crop[1], crop[2], crop[3]));
		face.addProperty("texture", "#pattern");

		int rotation = GLAZED_FACE_ROTATION.get(direction);

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
