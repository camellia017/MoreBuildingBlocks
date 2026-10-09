package com.camelie.morebuildingblocks.client.datagen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.math.Quadrant;

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
import com.camelie.morebuildingblocks.registry.MBBlocks;

/**
 * Generates every blockstate, block model, block item model and client item
 * definition this mod ships.
 *
 * <p>Plain and dyed terracotta reuse vanilla's {@code slab} and {@code stairs}
 * templates, which already cut the right corner. Glazed terracotta cannot: its
 * texture is a directional pattern whose per-face orientation on a full cube is
 * {@code down 0, up 0, north 90, south 270, west 0, east 180}. Those models are
 * emitted as literal geometry with that rotation written into each face, so the
 * motif still lines up once the corner is cut away instead of inheriting the
 * flat block's UV mapping.
 *
 * <p>Every vanilla slab additionally gains a vertical placement model driven by
 * the {@code vertical} and {@code axis} state properties the slab mixin adds. No
 * vanilla JSON is written: the extra states are supplied at load time by
 * {@code com.camelie.morebuildingblocks.client.MBVanillaSlabModelPlugin}, and the
 * upright model it points at is generated here, per slab, with its own textures.
 */
public class MBModelProvider implements DataProvider {
	/** The namespace every texture this mod references lives in. */
	private static final String VANILLA_NAMESPACE = "minecraft";

	/** Texture slots of the mod's own {@code block/vertical_slab} parent model. */
	private static final TextureSlot SLOT_BOTTOM = TextureSlot.BOTTOM;
	private static final TextureSlot SLOT_TOP = TextureSlot.TOP;
	private static final TextureSlot SLOT_SIDE = TextureSlot.SIDE;

	/** Rotation of a north-facing slab model that produces each vertical axis. */
	private static final Quadrant AXIS_X_ROTATION = Quadrant.R90;
	private static final Quadrant AXIS_Z_ROTATION = Quadrant.R270;

	/** Vanilla's per-face pattern rotation on a full glazed terracotta cube. */
	private static final Map<String, Integer> GLAZED_FACE_ROTATION = Map.of(
			"down", 0,
			"up", 0,
			"north", 90,
			"south", 270,
			"west", 0,
			"east", 180);

	/**
	 * Where an upright slab model lives inside this mod. Kept apart from the flat
	 * models so a path can never collide with one of them.
	 */
	private static final String VERTICAL_DIRECTORY = "vertical_slab/";

	/**
	 * The upright shape: a plain box in the western half of the block, full height and
	 * full depth, with every face taking its UVs from a whole texture face. The
	 * blockstate rotates it a quarter turn per axis, so one model serves both.
	 */
	private static final Box VERTICAL_BOX = new Box(0, 0, 0, 8, 16, 16);

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

		Map<Block, Block> slabBases = new LinkedHashMap<>();
		MBBlockFamilies.families().forEach(family -> slabBases.put(family.slab(), family.base()));

		generateBaseBlocks();
		generateTerracotta(generators);
		generateModSlabs(slabBases);
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

	/**
	 * The cube model and blockstate of each of the 33 blocks this mod adds.
	 *
	 * <p>The mod ships no texture of its own, so a base block would otherwise have
	 * nothing to render with: the slab and stairs models only reference vanilla's
	 * {@code slab}, {@code stairs} and base-block models, never a model for the base
	 * block itself. Each cube therefore points straight at the vanilla texture of the
	 * vanilla block it mirrors, its blockstate maps every state to that cube, and its
	 * block item renders that same cube.
	 */
	private void generateBaseBlocks() {
		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			Block base = family.base();
			Identifier model = baseModelId(base);
			MultiVariant variant = plainVariant(model);

			this.models.put(model, modelInstance(cubeGeometry(vanillaTextureId(base))));
			this.blockStates.put(base, MultiVariantGenerator.dispatch(base, variant));
			registerItemModel(Item.BY_BLOCK.get(base), model);
		}
	}

	/** Stairs and slabs for plain, dyed and glazed terracotta. */
	private void generateTerracotta(BlockModelGenerators generators) {
		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			Block base = family.base();
			Block stairs = family.stairs();
			Block slab = family.slab();

			if (MBBlocks.GLAZED_TERRACOTTA.asList().contains(base)) {
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

		Identifier texture = vanillaTextureId(base);
		this.models.put(verticalModelId(slab),
				modelInstance(verticalGeometry(texture, texture, texture)));

		this.blockStates.put(slab, verticalSlabDispatch(slab, slabBottom, slabTop, baseBlockModelId(base), verticalModelId(slab)));
		this.blockStates.put(stairs, BlockModelGenerators.createStairs(stairs,
				plainVariant(inner), plainVariant(straight), plainVariant(outer)));
	}

	/**
	 * Glazed terracotta: the same shapes as vanilla stairs and slabs, but with the
	 * pattern's per-face rotation baked into the geometry so the motif survives the
	 * cut corner.
	 */
	private void generateGlazed(BlockModelGenerators generators, Block base, Block stairs, Block slab) {
		Identifier pattern = vanillaTextureId(base);

		Identifier slabModel = baseModelId(slab);
		Identifier slabTopModel = baseModelId(slab, "top");
		Identifier straight = baseModelId(stairs);
		Identifier inner = baseModelId(stairs, "inner");
		Identifier outer = baseModelId(stairs, "outer");

		// Element for element, box for box, the geometry vanilla's slab, stairs,
		// inner_stairs and outer_stairs templates lay down. Only the face rotations
		// differ, so the pattern keeps pointing the same way it does on the full
		// block.
		this.models.put(slabModel, modelInstance(glazedGeometry(pattern, SLAB_BOX)));
		this.models.put(slabTopModel, modelInstance(glazedGeometry(pattern, TOP_SLAB_BOX)));
		this.models.put(straight, modelInstance(glazedGeometry(pattern, SLAB_BOX, STAIR_BOX)));
		this.models.put(inner, modelInstance(glazedGeometry(pattern, SLAB_BOX, STAIR_BOX, INNER_BOX)));
		this.models.put(outer, modelInstance(glazedGeometry(pattern, SLAB_BOX, OUTER_BOX)));

		this.blockStates.put(slab, verticalSlabDispatch(slab, slabModel, slabTopModel, baseBlockModelId(base), verticalModelId(slab)));
		this.blockStates.put(stairs, BlockModelGenerators.createStairs(stairs,
				plainVariant(inner), plainVariant(straight), plainVariant(outer)));

		this.models.put(verticalModelId(slab), modelInstance(verticalGeometry(pattern, pattern, pattern)));

		registerItemModel(Item.BY_BLOCK.get(slab), slabModel);
		registerItemModel(Item.BY_BLOCK.get(stairs), straight);
	}

	/** The vanilla texture of a block, i.e. {@code minecraft:block/<its id>}. */
	private static Identifier vanillaTextureId(Block block) {
		return Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/" + vanillaPath(block));
	}

	// ---------------------------------------------------------- vertical slabs

	/**
	 * A vertical model for every vanilla slab, so the whole vanilla half-slab
	 * catalogue can also stand upright.
	 *
	 * <p>Vanilla's own slab blockstates and models are left exactly as they are: the
	 * extra states are supplied at load time by
	 * {@code com.camelie.morebuildingblocks.client.MBVanillaSlabModelPlugin}. Only the
	 * upright shape is written here, and each one is self-contained.
	 *
	 * <p>A model with no {@code parent} has to name the sprites it draws, so the
	 * three textures come from {@link VanillaSlabTextures} rather than being left as
	 * {@code #bottom}/{@code #top}/{@code #side} for an inherited map to fill in. That
	 * map is never inherited: these models are reached from a vanilla blockstate
	 * through a resolver, so there is no child model in the stack to define them.
	 */
	private void generateVanillaSlabs() {
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof SlabBlock)) {
				continue;
			}

			Identifier id = BuiltInRegistries.BLOCK.getKey(block);

			// This mod's own slabs already have an upright model from
			// generateModSlabs, built from their terracotta texture.
			if (!VANILLA_NAMESPACE.equals(id.getNamespace())) {
				continue;
			}

			VanillaSlabTextures.Textures textures = VanillaSlabTextures.bySlab(id.getPath());

			if (textures == null) {
				// Not a vanilla slab this provider knows: leave it to the block's own model.
				continue;
			}

			this.models.put(verticalModelId(block), modelInstance(verticalGeometry(
					vanillaTexture(textures.bottom()), vanillaTexture(textures.top()),
					vanillaTexture(textures.side()))));
		}
	}

	// ---------------------------------------------------------- vertical slabs

	/**
	 * A slab blockstate with the flat states plus both vertical orientations, which
	 * the mixin selects through the {@code vertical} and {@code axis} properties.
	 *
	 * <p>Half of this table is unreachable: the mixin only ever turns on
	 * {@code vertical} for a horizontal {@code axis}, and only ever leaves {@code
	 * axis} at its flat default when {@code vertical} is off. {@link PropertyDispatch}
	 * still demands a definition for every combination of the properties it declares,
	 * so the impossible states reuse the shapes they would have had as flat slabs.
	 *
	 * @param verticalModel the upright model, rotated per axis
	 */
	private static BlockModelDefinitionGenerator verticalSlabDispatch(Block slab, Identifier bottomModel,
			Identifier topModel, Identifier fullModel, Identifier verticalModel) {
		MultiVariant bottom = plainVariant(bottomModel);
		MultiVariant top = plainVariant(topModel);
		MultiVariant full = plainVariant(fullModel);
		MultiVariant verticalX = rotated(verticalModel, AXIS_X_ROTATION);
		MultiVariant verticalZ = rotated(verticalModel, AXIS_Z_ROTATION);

		PropertyDispatch<MultiVariant> dispatch = PropertyDispatch
				.initial(MBBlockStateProperties.VERTICAL, MBBlockStateProperties.AXIS, SlabBlock.TYPE)
				.generate((vertical, axis, type) -> switch (type) {
					case BOTTOM -> vertical && axis != Direction.Axis.Y
							? (axis == Direction.Axis.X ? verticalX : verticalZ)
							: bottom;
					case TOP -> vertical && axis != Direction.Axis.Y
							? (axis == Direction.Axis.X ? verticalX : verticalZ)
							: top;
					case DOUBLE -> vertical && axis != Direction.Axis.Y
							? (axis == Direction.Axis.X ? verticalX : verticalZ)
							: full;
				});

		return MultiVariantGenerator.dispatch(slab).with(dispatch);
	}

	/**
	 * Blockstates for every slab this mod registered that {@link #generateTerracotta}
	 * did not already cover, so each one gains a vertical form too.
	 *
	 * <p>Only this mod's own namespace is written. Vanilla slabs are ours to extend
	 * but not ours to restate: their models, including the three whose double form is
	 * not the base block's model, stay in vanilla's own files, and the extra states
	 * are supplied at load time by
	 * {@code com.camelie.morebuildingblocks.client.MBVanillaSlabModelPlugin}.
	 *
	 * @param bases the block each slab is made of, i.e. its double model
	 */
	private void generateModSlabs(Map<Block, Block> bases) {
		for (Map.Entry<Block, Block> entry : bases.entrySet()) {
			Block slab = entry.getKey();

			if (this.blockStates.containsKey(slab)) {
				continue;
			}

			this.blockStates.put(slab, verticalSlabDispatch(slab,
					baseModelId(slab), baseModelId(slab, "top"), baseBlockModelId(entry.getValue()),
					verticalModelId(slab)));
		}
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
			this.itemDefinitions.put(item, new ClientItem(ItemModelUtils.plainModel(model), ClientItem.Properties.DEFAULT));
		}
	}

	/**
	 * Any block item this mod registered without an explicit model falls back to its
	 * block's model. Only this mod's namespace is touched, so no vanilla item
	 * definition is ever emitted.
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

	/** One box of the model, in vanilla's own coordinates. */
	private record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
	}

	/**
	 * A full cube using one texture on all six faces, i.e. what vanilla's
	 * {@code block/cube_all} template produces: a single 0..16 box whose faces all
	 * sample the whole texture.
	 */
	private static JsonObject cubeGeometry(Identifier texture) {
		JsonObject root = new JsonObject();
		JsonObject textures = new JsonObject();
		textures.addProperty("all", texture.toString());
		textures.addProperty("particle", texture.toString());
		root.add("textures", textures);

		JsonObject element = new JsonObject();
		element.add("from", vector(0, 0, 0));
		element.add("to", vector(16, 16, 16));

		JsonObject faces = new JsonObject();

		for (String direction : new String[] { "down", "up", "north", "south", "west", "east" }) {
			JsonObject face = new JsonObject();
			face.add("uv", uv(0, 0, 16, 16));
			face.addProperty("texture", "#all");
			face.addProperty("cullface", direction);
			faces.add(direction, face);
		}

		element.add("faces", faces);
		JsonArray elements = new JsonArray();
		elements.add(element);
		root.add("elements", elements);

		return root;
	}

	/** One glazed model: literal geometry plus the pattern's per-face rotations. */
	private static JsonObject glazedGeometry(Identifier pattern, Box... boxes) {
		JsonObject root = new JsonObject();
		JsonObject textures = new JsonObject();

		// The faces refer to "#pattern", so the slot has to be defined here. Vanilla's
		// template_glazed_terracotta only names "particle" because it inherits the rest
		// from the per-block model that parents it; these models have no parent, so both
		// slots are written out.
		textures.addProperty("pattern", pattern.toString());
		textures.addProperty("particle", pattern.toString());
		root.add("textures", textures);

		JsonArray elements = new JsonArray();

		for (Box box : boxes) {
			elements.add(box(box));
		}

		root.add("elements", elements);
		return root;
	}

	/**
	 * One axis-aligned box. Horizontal faces map u/v to x/z and vertical faces map u
	 * to the global horizontal coordinate with v on y - exactly the mapping vanilla's
	 * own slab and stair boxes use - so the pattern rotation table below applies to
	 * them the same way it does to a full cube.
	 */
	private static JsonObject box(Box box) {
		JsonObject element = new JsonObject();
		element.add("from", vector(box.x1(), box.y1(), box.z1()));
		element.add("to", vector(box.x2(), box.y2(), box.z2()));

		JsonObject faces = new JsonObject();
		faces.add("down", face(box.x1(), box.z1(), box.x2(), box.z2(), "down", box));
		faces.add("up", face(box.x1(), box.z1(), box.x2(), box.z2(), "up", box));
		faces.add("north", face(box.x1(), box.y1(), box.x2(), box.y2(), "north", box));
		faces.add("south", face(box.x1(), box.y1(), box.x2(), box.y2(), "south", box));
		faces.add("west", face(box.z1(), box.y1(), box.z2(), box.y2(), "west", box));
		faces.add("east", face(box.z1(), box.y1(), box.z2(), box.y2(), "east", box));
		element.add("faces", faces);

		return element;
	}

	/** A single face, carrying the glazed pattern rotation for that direction. */
	private static JsonObject face(int minU, int minV, int maxU, int maxV, String direction, Box box) {
		JsonObject face = new JsonObject();
		face.add("uv", uv(minU, minV, maxU, maxV));
		face.addProperty("texture", "#pattern");

		int rotation = GLAZED_FACE_ROTATION.get(direction);

		if (rotation != 0) {
			face.addProperty("rotation", rotation);
		}

		// Only a face that covers the whole 16x16 face of the block and lies on its
		// boundary may cull, otherwise the two boxes of a stair would hide each other.
		if (maxU - minU == 16 && maxV - minV == 16 && isOnBoundary(box, direction)) {
			face.addProperty("cullface", direction);
		}

		return face;
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

	private static MultiVariant rotated(Identifier model, Quadrant yRotation) {
		return new MultiVariant(WeightedList.of(new Variant(model,
				new Variant.SimpleModelState(Quadrant.R0, yRotation, Quadrant.R0, false))));
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
	 * {@code morebuildingblocks:block/<id>}, or {@code <id>_<suffix>} when the same
	 * block needs more than one model.
	 *
	 * <p>Used for slabs and stairs, whose registry ids already end in {@code _slab}
	 * and {@code _stairs}: appending the form name again would produce
	 * {@code white_terracotta_slab_slab}.
	 */
	private static Identifier formModelId(Block block, String suffix) {
		return suffix.isEmpty() ? baseModelId(block) : baseModelId(block, suffix);
	}

	/**
	 * The model of the vanilla block a slab or stairs is made of, which is also its
	 * double-slab model, e.g. {@code minecraft:block/white_terracotta}.
	 */
	private static Identifier baseBlockModelId(Block block) {
		return Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/" + vanillaPath(block));
	}

	private static Identifier modelId(Identifier path) {
		return Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, "block/" + path.getPath());
	}

	/**
	 * {@code morebuildingblocks:vertical_slab/<block path>}.
	 *
	 * <p>Note the missing {@code block/}: {@code PackOutput.PathProvider} prefixes the
	 * kind directory itself ({@code this.kind + "/" + path + ".json"}), so the id of a
	 * model under {@code assets/<ns>/models/} is the file path <em>below</em> {@code
	 * models/}. Every other model here starts with {@code block/} for the same reason;
	 * this one deliberately starts with its own folder instead.
	 */
	private static Identifier verticalModelId(Block block) {
		return Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID,
				"vertical_slab/" + BuiltInRegistries.BLOCK.getKey(block).getPath());
	}

	/**
	 * An upright slab model that carries its own textures.
	 *
	 * <p>Written out in full rather than parented to a shared template, because a model
	 * reached from a vanilla block's blockstate has nothing above it to define
	 * {@code #bottom}/{@code #top}/{@code #side}: the model the resolver names is the
	 * one that has to name its sprites.
	 */
	private static JsonObject verticalGeometry(Identifier bottom, Identifier top, Identifier side) {
		JsonObject root = new JsonObject();
		JsonObject textures = new JsonObject();
		textures.addProperty("bottom", bottom.toString());
		textures.addProperty("top", top.toString());
		textures.addProperty("side", side.toString());
		textures.addProperty("particle", side.toString());
		root.add("textures", textures);

		JsonArray elements = new JsonArray();
		elements.add(verticalBox());
		root.add("elements", elements);
		return root;
	}

	/**
	 * The single box of an upright slab, UV-mapped face by face.
	 *
	 * <p>The two long faces are the ones the player actually reads: {@code west} is the
	 * full 16x16 face of a north-facing slab, and {@code north}/{@code south} are the
	 * thin 8x16 edges. The {@code down}/{@code up} strips take the same {@code #side}
	 * sprite because a slab's top and bottom faces are covered by the full slab form
	 * anyway, and using {@code #bottom}/{@code #top} there would put a sliver of the
	 * block's top texture on a vertical edge.
	 */
	private static JsonObject verticalBox() {
		Box box = VERTICAL_BOX;
		JsonObject element = new JsonObject();
		element.add("from", vector(box.x1(), box.y1(), box.z1()));
		element.add("to", vector(box.x2(), box.y2(), box.z2()));

		JsonObject faces = new JsonObject();
		faces.add("down", verticalFace(0, 0, 8, 16, "#side", box, "down"));
		faces.add("up", verticalFace(0, 0, 8, 16, "#side", box, "up"));
		faces.add("north", verticalFace(0, 0, 8, 16, "#side", box, "north"));
		faces.add("south", verticalFace(8, 0, 16, 16, "#side", box, "south"));
		faces.add("west", verticalFace(0, 0, 16, 16, "#side", box, "west"));
		faces.add("east", verticalFace(0, 0, 16, 16, "#side", box, "east"));
		element.add("faces", faces);
		return element;
	}

	private static JsonObject verticalFace(int minU, int minV, int maxU, int maxV, String texture, Box box,
			String direction) {
		JsonObject face = new JsonObject();
		face.addProperty("texture", texture);
		face.add("uv", uv(minU, minV, maxU, maxV));

		if (isOnBoundary(box, direction)) {
			face.addProperty("cullface", direction);
		}

		return face;
	}

	/**
	 * One texture on all three slots.
	 *
	 * <p>The sprite is the vanilla texture of the vanilla block this one is built on,
	 * not {@code morebuildingblocks:block/<this block>}: this mod deliberately ships
	 * no texture files, so a reference in its own namespace would resolve to nothing.
	 * Every one of the 33 base blocks has a vanilla counterpart providing the texture.
	 */
	private static TextureMapping singleTextureMapping(Block block) {
		TextureMapping mapping = new TextureMapping();
		Material texture = vanillaTexture(block);
		mapping.put(SLOT_BOTTOM, texture);
		mapping.put(SLOT_TOP, texture);
		mapping.put(SLOT_SIDE, texture);
		return mapping;
	}

	/** {@code minecraft:block/<the vanilla block with the same name>}. */
	private static Material vanillaTexture(Block block) {
		return new Material(Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/" + vanillaPath(block)));
	}

	/**
	 * The path of the vanilla block this mod block copies.
	 *
	 * <p>All 33 base blocks keep vanilla's block name —{@code white_terracotta},
	 * {@code light_blue_glazed_terracotta}, yes even {@code terracotta} —so the path
	 * is already right and only the namespace has to be swapped.
	 */
	private static String vanillaPath(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	/** A vanilla sprite from an already-{@code block/}-prefixed path. */
	private static Identifier vanillaTexture(String path) {
		return Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, path);
	}

	@Override
	public String getName() {
		return "MoreBuildingBlocks Models";
	}
}
