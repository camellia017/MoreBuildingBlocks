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
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
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
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.block.GlazedPattern;
import com.camelie.morebuildingblocks.block.MBBlockStateProperties;
import com.camelie.morebuildingblocks.block.MBEighthProperties;
import com.camelie.morebuildingblocks.block.VerticalSlab.Fill;
import com.camelie.morebuildingblocks.registry.MBBlockFamilies;
import com.camelie.morebuildingblocks.registry.MBEighths;

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

	/**
	 * Vanilla's own stair models. A stair is not shown the way a plain block is: vanilla tilts its
	 * icon a quarter turn further round so the step is read from the side, and that tilt is written
	 * on the stair models themselves rather than on {@link #BLOCK_PARENT}. A glazed stair draws its
	 * own geometry, so it has to name the same parent to be shown the same way; the inner and outer
	 * shapes take vanilla's too, which carry no display at all, they being shapes never held.
	 */
	private static final Identifier STAIRS_PARENT = Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/stairs");
	private static final Identifier INNER_STAIRS_PARENT =
			Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/inner_stairs");
	private static final Identifier OUTER_STAIRS_PARENT =
			Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, "block/outer_stairs");

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
		generateEighths();
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

	// ---------------------------------------------------------------- eighths

	/**
	 * The eight models, the blockstate and the item model of every eighth block.
	 *
	 * <p>An eighth block is one cell cut into eight pieces, so one model per piece is enough.
	 * Whether a piece is turned with the cell is decided by the material: a block whose own texture
	 * is laid out around a facing - in this mod only the glazed terracotta, the way vanilla turns
	 * its own - gets the four turns, so that the eighths of a cell all follow the pattern direction
	 * of the first one placed. Every other material shows the piece where it is and keeps the
	 * texture the way the block it was cut from shows it: turning planks, sandstone or copper
	 * would turn their grain against the block next to them.
	 *
	 * <p>The item is one eighth of the block, not the block it is cut from: the icon is the piece
	 * in the x-, y- and z- corner, unrotated, so it shows an eighth at its own size with the part
	 * of the sprite that piece covers. Squeezing the whole sprite onto the small cube would stretch
	 * the texture, which is what this avoids.
	 */
	private void generateEighths() {
		for (MBEighths.Material material : MBEighths.materials()) {
			Block eighth = material.eighth();
			boolean pattern = material.slab() instanceof GlazedPattern;

			for (int index = 0; index < MBEighthProperties.BITS.length; index++) {
				this.models.put(eighthModelId(eighth, index), modelInstance(eighthGeometry(material, index)));
			}

			this.blockStates.put(eighth, eighthState(eighth, pattern));
			this.models.put(eighthIconModelId(eighth), modelInstance(eighthIconGeometry(material)));

			registerItemModel(Item.BY_BLOCK.get(eighth), eighthIconModelId(eighth));
		}
	}

	/**
	 * One part of the blockstate for every piece - and, for a patterned material, every facing.
	 *
	 * <p>A plain material shows every piece where it is: a {@code y} rotation would turn its
	 * texture with it, and vanilla never turns it either. A patterned one does get the four
	 * turns, so the eighths of a cell follow the pattern direction of the first one placed: for
	 * every piece and every {@code facing} the part picks the model of the piece that ends up
	 * there once the cell has been turned that way, and turns it by the same amount.
	 */
	private static MultiPartGenerator eighthState(final Block eighth, final boolean pattern) {
		MultiPartGenerator generator = MultiPartGenerator.multiPart(eighth);

		for (int index = 0; index < MBEighthProperties.BITS.length; index++) {
			if (!pattern) {
				generator.with(new ConditionBuilder().term(MBEighthProperties.BITS[index], true),
						plainVariant(eighthModelId(eighth, index)));

				continue;
			}

			for (int turn = 0; turn < TURNS; turn++) {
				int turned = MBEighthProperties.turned(index, (TURNS - turn) % TURNS);
				ConditionBuilder condition = new ConditionBuilder()
						.term(MBEighthProperties.BITS[index], true)
						.term(MBEighthProperties.FACING, MBEighthProperties.facing(turn));

				generator.with(condition, plainVariant(eighthModelId(eighth, turned)).with(yRotation(turn)));
			}
		}

		return generator;
	}

	/** {@code morebuildingblocks:block/eighth_<base>_icon}, the model the item shows. */
	private static Identifier eighthIconModelId(final Block eighth) {
		return baseModelId(eighth, "icon");
	}

	/**
	 * The item's model: the piece in the x-, y- and z- corner of the block, sprite and all, moved a
	 * quarter of a block along x and z so that it sits on the middle line of the slot instead of in
	 * the corner of the block it was cut from.
	 *
	 * <p>The icon is drawn a fifth larger than the piece it shows and lifted a little, because an
	 * eighth is small enough to be mistaken for a full block in a full tab. Both are done to the
	 * geometry rather than to the GUI transform: the model is an icon and nothing else, so it can
	 * carry a size of its own without touching the display transforms the item inherits - the ones
	 * that decide how it is held, shown in a frame and dropped.
	 *
	 * <p>It is an icon, not a block: it keeps the sprite of that corner piece - the icon of a
	 * copper or plank eighth may not show a stretched or turned texture - and drops the culling a
	 * block model puts on the faces that sit on a block boundary.
	 */
	private static JsonObject eighthIconGeometry(final MBEighths.Material material) {
		final double scale = 1.2;
		final double lift = 3.0;
		double half = 4 * scale;
		double bottom = 4 - half + lift;
		double top = 4 + half + lift;

		JsonObject geometry = eighthGeometry(material, MBEighthProperties.index(false, false, false));
		JsonObject element = geometry.getAsJsonArray("elements").get(0).getAsJsonObject();

		element.add("from", vector(8 - half, bottom, 8 - half));
		element.add("to", vector(8 + half, top, 8 + half));

		JsonObject faces = element.getAsJsonObject("faces");

		for (String direction : FACE_DIRECTIONS) {
			faces.getAsJsonObject(direction).remove("cullface");
		}

		return geometry;
	}

	/**
	 * One eighth of a block: the box of that piece, textured with the part of the parent's sprite
	 * the piece covers, the way a slab or a stair is.
	 *
	 * <p>The three sprites are the ones the block's own slab uses - the mod ships no textures, and
	 * vanilla's slab is already textured like the block it is cut from - falling back to the block's
	 * texture on all three slots for a material whose forms this mod made itself, whose terracotta
	 * is one texture on every face anyway.
	 */
	private static JsonObject eighthGeometry(final MBEighths.Material material, final int index) {
		Box box = eighthBox(index);

		if (material.slab() instanceof GlazedPattern) {
			return glazedCubeGeometry(BLOCK_PARENT, vanillaTexture(material.base()), box);
		}

		VanillaSlabTextures.Textures textures = material.slab() == null ? null
				: VanillaSlabTextures.bySlab(BuiltInRegistries.BLOCK.getKey(material.slab()).getPath());

		if (textures == null) {
			Material all = vanillaTexture(material.base());

			return geometry(all, all, all, box);
		}

		return geometry(vanillaTexture(textures.bottom()), vanillaTexture(textures.top()),
				vanillaTexture(textures.side()), box);
	}

	/** The box of one piece, in the model's own coordinates. */
	private static Box eighthBox(final int index) {
		int x = MBEighthProperties.isEast(index) ? 8 : 0;
		int y = MBEighthProperties.isUpper(index) ? 8 : 0;
		int z = MBEighthProperties.isSouth(index) ? 8 : 0;

		return new Box(x, y, z, x + 8, y + 8, z + 8);
	}

	/** {@code morebuildingblocks:block/eighth_<base>_<piece>}, one per piece of one material. */
	private static Identifier eighthModelId(final Block eighth, final int index) {
		return baseModelId(eighth, MBEighthProperties.bit(index).getName());
	}

	// ------------------------------------------------------------- terracotta

	/** Stairs and slabs for plain, dyed and glazed terracotta. */
	private void generateTerracotta(BlockModelGenerators generators) {
		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			Block base = family.base();
			Block stairs = family.stairs();
			Block slab = family.slab();

			if (family.glazed()) {
				generateGlazed(base, stairs, slab);
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
		generateModSlabState(slab, slabBottom, slabTop, baseBlockModelId(base), false);
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
	private void generateGlazed(Block base, Block stairs, Block slab) {
		Material pattern = vanillaTexture(base);

		// Element for element, box for box, the geometry vanilla's slab, stairs, inner_stairs and
		// outer_stairs templates lay down. Only the face rotations differ, so the pattern keeps
		// pointing the same way it does on the full block.
		Map<String, JsonObject> upright = glazedVerticalSlab(base);

		for (int turn = 0; turn < TURNS; turn++) {
			// formModelId, not baseModelId: the unturned copy has an empty suffix and must keep
			// the block's own model id. baseModelId would append "_" and emit a model named
			// "…_stairs_" that nothing - least of all the item definition - ever asks for.
			this.models.put(formModelId(slab, suffix("", turn)),
				modelInstance(turned(glazedCubeGeometry(BLOCK_PARENT, pattern, SLAB_BOX), turn)));
			this.models.put(formModelId(slab, suffix("top", turn)),
				modelInstance(turned(glazedCubeGeometry(BLOCK_PARENT, pattern, TOP_SLAB_BOX), turn)));
			// The stair boxes stay put: the stair's own facing is carried by the blockstate's y,
			// and a box turned here as well would turn twice. Only the pattern moves.
			this.models.put(formModelId(stairs, suffix("", turn)), modelInstance(turned(
					glazedCubeGeometry(STAIRS_PARENT, pattern, SLAB_BOX, STAIR_BOX), turn, false)));
			this.models.put(formModelId(stairs, suffix("inner", turn)), modelInstance(turned(
					glazedCubeGeometry(INNER_STAIRS_PARENT, pattern, SLAB_BOX, STAIR_BOX, INNER_BOX), turn, false)));
			this.models.put(formModelId(stairs, suffix("outer", turn)), modelInstance(turned(
					glazedCubeGeometry(OUTER_STAIRS_PARENT, pattern, SLAB_BOX, OUTER_BOX), turn, false)));

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
						// A completed double slab is the full glazed block, so it turns the way that
						// block turns: vanilla's own blockstate gives it y = 90 * glazedTurns(facing).
						case DOUBLE -> full.with(yRotation(turn));
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
	 * The stair blockstate of a glazed family: vanilla's own stair table, with the pattern turned
	 * to the quarter the player placed the block with.
	 *
	 * <p>Two rotations are in play and they are not the same one. The stair's own {@code facing}
	 * turns the geometry, exactly as it does for an ordinary stair, and a blockstate {@code y}
	 * carries that turn out; the table is vanilla's {@code BlockModelGenerators.createStairs},
	 * {@code half} and both hands included. The glazed pattern is a second, independent rotation:
	 * it is baked into the model, one model per quarter turn of {@code glazed_facing}.
	 *
	 * <p>They stay independent because every variant is {@code uvlock}ed, exactly as vanilla's own
	 * stair variants are. A locked texture does not turn with the geometry - the model is sampled
	 * as though the stair still stood in the corner it was drawn in, and the geometry turns through
	 * it - so a face shows the texels of the full glazed block at the same world place, and the
	 * pattern keeps the alignment the block has at its own facing wherever the step ends up.
	 *
	 * <p>Without the lock the {@code y} would carry the pattern along with the step and every shape
	 * would need its own correction: one quarter less for the {@code *_left} shapes, whose entries
	 * in the {@code y} table already start a quarter earlier than the right-handed ones they share
	 * a model with, and one more for {@code half=top}, whose {@code x} flip would turn the pattern
	 * upside down.
	 */
	private BlockModelDefinitionGenerator glazedStairsState(Block stairs) {
		return MultiVariantGenerator.dispatch(stairs).with(PropertyDispatch
				.initial(StairBlock.FACING, StairBlock.HALF, StairBlock.SHAPE, MBBlockStateProperties.GLAZED_FACING)
				.generate((facing, half, shape, placed) -> {
					int turn = MBBlockStateProperties.glazedTurns(placed);

					MultiVariant variant = plainVariant(switch (shape) {
						case STRAIGHT -> formModelId(stairs, suffix("", turn));
						case INNER_LEFT, INNER_RIGHT -> formModelId(stairs, suffix("inner", turn));
						case OUTER_LEFT, OUTER_RIGHT -> formModelId(stairs, suffix("outer", turn));
					});

					return variant.with(rotation(shape, facing, half)).with(upsideDown(half))
							.with(BlockModelGenerators.UV_LOCK);
				}));
	}

	/**
	 * The rotation that stands a stair on its head, which vanilla gives every {@code half=top}
	 * variant.
	 *
	 * <p>Without it an upside-down stair is drawn the right way up: {@code half} is carried by the
	 * blockstate alone, and a stair is the one family here whose two halves are not the same shape
	 * seen from the other end.
	 */
	private static VariantMutator upsideDown(final Half half) {
		return half == Half.TOP ? BlockModelGenerators.X_ROT_180 : BlockModelGenerators.NOP;
	}

	/**
	 * The {@code y} rotation vanilla gives a stair of this shape, facing {@code facing}, half
	 * {@code half}.
	 *
	 * <p>{@code straight}, {@code inner_right} and {@code outer_right} share one table and the two
	 * {@code *_left} shapes share the other, exactly as {@code BlockModelGenerators.createStairs}
	 * lays them out: east 0, south 90, west 180, north 270 for the first, and east 270, south 0,
	 * west 90, north 180 for the second. The difference is real geometry, not a preference - the
	 * left templates are the mirror of the right ones, so the same facing needs a quarter turn less.
	 *
	 * <p>{@code half=top} needs one quarter turn more for the {@code inner} and {@code outer}
	 * shapes, which is vanilla's table as well: a stair turned end over end swaps its two hands, so
	 * the left template lands where the right one was. {@code straight} is its own mirror and keeps
	 * its quarter.
	 */
	private static VariantMutator rotation(final StairsShape shape, final Direction facing, final Half half) {
		boolean left = shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT;

		int quarters = switch (facing) {
			case SOUTH -> left ? 0 : 1;
			case WEST -> left ? 1 : 2;
			case NORTH -> left ? 2 : 3;
			case EAST -> left ? 3 : 0;
			default -> 0;
		};

		if (half == Half.TOP && shape != StairsShape.STRAIGHT) {
			quarters++;
		}

		return yRotation(quarters);
	}

	/**
	 * The blockstate {@code y} that turns a model {@code quarters} quarter turns clockwise seen
	 * from above, which is the direction vanilla's {@code y} counts in.
	 */
	private static VariantMutator yRotation(final int quarters) {
		return switch (Mth.positiveModulo(quarters, TURNS)) {
			case 1 -> BlockModelGenerators.Y_ROT_90;
			case 2 -> BlockModelGenerators.Y_ROT_180;
			case 3 -> BlockModelGenerators.Y_ROT_270;
			default -> BlockModelGenerators.NOP;
		};
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
			this.models.put(verticalModelId(slab, fill, turn), modelInstance(turned(geometry, turn, false))));
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
		geometries.put(FILL_NEGATIVE_X, geometry(bottom, top, side, NEGATIVE_X_BOX));
		geometries.put(FILL_POSITIVE_X, geometry(bottom, top, side, POSITIVE_X_BOX));
		geometries.put(FILL_NEGATIVE_Z, geometry(bottom, top, side, NEGATIVE_Z_BOX));
		geometries.put(FILL_POSITIVE_Z, geometry(bottom, top, side, POSITIVE_Z_BOX));
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

		Map<String, JsonObject> geometries = new LinkedHashMap<>(4);
		geometries.put(FILL_NEGATIVE_X, glazedCubeGeometry(BLOCK_PARENT, pattern, NEGATIVE_X_BOX));
		geometries.put(FILL_POSITIVE_X, glazedCubeGeometry(BLOCK_PARENT, pattern, POSITIVE_X_BOX));
		geometries.put(FILL_NEGATIVE_Z, glazedCubeGeometry(BLOCK_PARENT, pattern, NEGATIVE_Z_BOX));
		geometries.put(FILL_POSITIVE_Z, glazedCubeGeometry(BLOCK_PARENT, pattern, POSITIVE_Z_BOX));
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
	private static JsonObject geometry(Material bottom, Material top, Material side, Box box) {
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
	 * One quarter turn of a glazed model, the way a blockstate's {@code y} turns one.
	 *
	 * <p>{@code y} rotations are quarter turns of a block seen from above, and a point
	 * {@code (x, z)} goes to {@code (16 - z, x)}: north to east, east to south, and so on. That is
	 * what {@code Quadrant.R90} resolves to - {@code OctahedralGroup.ROT_90_Y_NEG}, which swaps the
	 * two axes and then negates x - and the glazed blockstates lean on it: one model,
	 * {@code template_glazed_terracotta}, serves all four {@code facing} values of a glazed block
	 * through nothing but a {@code y} rotation, 90 degrees a step.
	 *
	 * <p>A slab or a stair cannot use one for the pattern, because it would carry the cut corner
	 * round with it and the shape would no longer sit against the wall it was placed against.
	 * Turning the model a quarter at a time by hand reproduces it exactly: the boxes move by that
	 * same map, the six face directions move with them, and the pattern follows.
	 *
	 * <p>{@link #direction} maps a face of the <em>turned</em> model back to the face of the
	 * unturned one it was made from - under this map the old west face is the new north one - which
	 * is where its sprite was cropped from and the value it keeps.
	 *
	 * <p>Not every face keeps that value as it is. A blockstate {@code y} rotation turns a quad and
	 * leaves its {@code uv} and {@code rotation} alone, so the sprite goes round with the quad; here
	 * the box is rebuilt instead, and the corners of its faces are not the old ones in the same
	 * places. The four sides come out of it unchanged - the turn carries the chart of the face it
	 * came from onto the chart of the face it lands on, so the same rectangle still names the same
	 * part of the block face - but up and down turn within their own plane, and the pattern on them
	 * has to turn with them: {@link #drift} is what each one gains, and up gains what down loses.
	 */
	private record Turn(int count) {
		/**
		 * The world direction of the unturned model that {@code direction} of this one was turned
		 * from.
		 *
		 * <p>This is the <em>inverse</em> of the turn - read as "which old face became this one" -
		 * because a face of the turned box has to be found in the unturned model to know what its
		 * sprite was: the new north face is the old west one. Written the other way round, each
		 * direction mapped onto its own image, the labels would turn twice as far as the geometry
		 * they describe.
		 */
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
				case 1 -> new Box(16 - box.z2(), box.y1(), box.x1(), 16 - box.z1(), box.y2(), box.x2());
				case 2 -> new Box(16 - box.x2(), box.y1(), 16 - box.z2(), 16 - box.x1(), box.y2(), 16 - box.z1());
				default -> new Box(box.z1(), box.y1(), 16 - box.x2(), box.z2(), box.y2(), 16 - box.x1());
			};
		}

		/**
		 * How many quarter turns the pattern on {@code direction} gains on top of the turn itself.
		 *
		 * <p>Up and down are the two faces the turn leaves facing the same way, so their quads turn
		 * where they lie instead of travelling to another side, and the pattern on them turns with
		 * them. The two run opposite ways because a sprite's v runs down the block as seen from
		 * above and up it as seen from below - the same reversal {@link #crop} writes for down - so
		 * the up face is traversed the way the turn goes and the down face against it.
		 */
		private int drift(final String direction) {
			return switch (direction) {
				case "up" -> 1;
				case "down" -> -1;
				default -> 0;
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
		return turned(geometry, turn, true);
	}

	/**
	 * A glazed model turned {@code turn} quarter turns clockwise, with its boxes either carried
	 * round or left where they are.
	 *
	 * <p>A shape that stays where it was put still shows the turned pattern. The boxes of a
	 * vertical slab are chosen by the axis it was placed against and the half of the block it
	 * fills, so they must not move with the pattern; instead the pattern turns over them where they
	 * lie, which is the same pattern on the same half of the block as a turned slab would show.
	 */
	private static JsonObject turned(JsonObject geometry, final int turn, final boolean move) {
		if (turn == 0) {
			return geometry;
		}

		Turn rotation = new Turn(turn);
		JsonObject root = new JsonObject();
		geometry.entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));

		JsonArray elements = new JsonArray();

		for (JsonElement element : root.getAsJsonArray("elements")) {
			JsonObject box = element.getAsJsonObject();
			Box placed = boxOf(box);
			Box moved = move ? rotation.box(placed) : placed;
			JsonObject turnedBox = boxElement(moved);
			JsonObject faces = new JsonObject();

			for (String direction : FACE_DIRECTIONS) {
				faces.add(direction, turnedFace(box.getAsJsonObject("faces"), moved, direction, rotation));
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
	 * One face of a turned model: the face that was turned onto it, that face's crop taken again
	 * from where the box now sits, and the pattern turned as far as the turn carries it.
	 *
	 * <p>The quad is read from {@link Turn#direction} - the face of the unturned model that ends up
	 * here, the old west face becoming the new north one, say. Sides pass their pattern on whole: a
	 * quarter turn about the vertical axis does not spin the pattern on a wall, it hands it to the
	 * next wall, so the rotation that face had is the rotation this one keeps. Up and down are the
	 * two faces the turn leaves facing the same way, so their pattern does spin, by a quarter turn
	 * each, and in opposite directions - {@link Turn#drift}.
	 *
	 * <p>The crop is a fact about where the quad sits in the block, not about the quad: it is the
	 * slice of block face under it, so a quad that has moved to another part of the block face asks
	 * for another slice of the pattern. Copying the old numbers through would slide the pattern
	 * across the shape, which on a stair reads as a seam: its half-width step would go on showing
	 * the strip of pattern it was cut from, wherever the turn has carried it. A shape whose box
	 * stayed put crops what it cropped before.
	 *
	 * <p>Those two numbers only mean anything together, so the pair written here is the same one
	 * {@link #glazedFace} writes: the pattern's rectangle turned back by its own rotation, and the
	 * rotation itself. A cullface, on the other hand, names the neighbour the quad hides against
	 * once it has arrived, so it is written from the face it is on now.
	 */
	private static JsonObject turnedFace(JsonObject faces, final Box box, final String direction, final Turn turn) {
		JsonObject original = faces.getAsJsonObject(turn.direction(direction));
		JsonObject turned = new JsonObject();
		original.entrySet().forEach(entry -> turned.add(entry.getKey(), entry.getValue()));

		int[] crop = crop(box, direction);
		int rotation = Math.floorMod(
				(original.has("rotation") ? original.get("rotation").getAsInt() : 0)
						+ 90 * turn.drift(direction) * turn.count(), 360);
		int[] pattern = patternUv(crop, rotation);
		turned.add("uv", uv(pattern[0], pattern[1], pattern[2], pattern[3]));

		if (rotation != 0) {
			turned.addProperty("rotation", rotation);
		} else {
			turned.remove("rotation");
		}

		if (canCull(crop, box, direction)) {
			turned.addProperty("cullface", direction);
		} else {
			turned.remove("cullface");
		}

		return turned;
	}

	/**
	 * One glazed box: the pattern's rotation is written onto every face, so the motif on a cut
	 * shape continues to point the way it does on the full block.
	 */
	private static JsonObject glazedCubeGeometry(final Identifier parent, final Material pattern, final Box... boxes) {
		JsonObject root = new JsonObject();
		root.addProperty("parent", parent.toString());

		// The faces refer to "#pattern", so the slot has to be defined here. Vanilla's
		// template_glazed_terracotta only names "particle" because it inherits the rest from the
		// per-block model that parents it; these models have no such parent.
		JsonObject textures = new JsonObject();
		textures.addProperty("pattern", pattern.sprite().toString());
		textures.addProperty("particle", pattern.sprite().toString());
		root.add("textures", textures);

		JsonArray elements = new JsonArray();

		for (Box box : boxes) {
			JsonObject element = boxElement(box);
			JsonObject faces = new JsonObject();

			for (String direction : FACE_DIRECTIONS) {
				faces.add(direction, glazedFace(box, direction));
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
	 * A glazed face: the pattern of the face it shows, turned vanilla's way for that face and then
	 * cropped to the part of the block face the quad covers.
	 *
	 * <p>{@code template_glazed_terracotta}, which every vanilla glazed block parents, gives each
	 * of its six faces its own {@code rotation} - 90 on north, 180 on east, 270 on south, none on
	 * west, up or down - so the same sprite reads as a woven block from every side. A half or a
	 * stair cut out of one has to ask for the part of that turned pattern its quad shows, not for
	 * the same part of the unturned sprite, which is what {@link #patternUv} works out.
	 *
	 * <p>This writes the unturned model. A turn is written afterwards by {@link #turnedFace}, which
	 * is the only place that knows how far the shape has been carried round.
	 */
	private static JsonObject glazedFace(Box box, String direction) {
		JsonObject face = new JsonObject();
		int[] crop = crop(box, direction);
		int rotation = GLAZED_FACE_ROTATION.get(direction);
		int[] pattern = patternUv(crop, rotation);

		face.add("uv", uv(pattern[0], pattern[1], pattern[2], pattern[3]));
		face.addProperty("texture", "#pattern");

		if (rotation != 0) {
			face.addProperty("rotation", rotation);
		}

		if (canCull(crop, box, direction)) {
			face.addProperty("cullface", direction);
		}

		return face;
	}

	/**
	 * Whether a face may be hidden when the block behind it is solid.
	 *
	 * <p>Two things are needed. The face has to lie on the block's own outer boundary, or it is
	 * an internal seam and culling it would punch a hole in the model. And it has to fill the
	 * whole block face along at least one axis, because {@code cullface} hides the entire block
	 * face, not the part of it the quad covers - a half-height upright slab's down face is a
	 * full 16x16 in plan even though the slab is only half as tall, so it may cull; the up face
	 * of a stair's bottom box, which the step only partly covers, may not.
	 *
	 * <p>Vanilla's {@code block/slab} culls its four sides from a box that stops at y=8, and
	 * {@code block/stairs} culls its step's up face from a box that starts at (8,8,0), so
	 * "on the boundary plus full along one axis" is exactly the rule vanilla follows.
	 */
	private static boolean canCull(int[] crop, Box box, String direction) {
		boolean fillsAnAxis = (crop[0] == 0 && crop[2] == 16) || (crop[1] == 0 && crop[3] == 16);
		return fillsAnAxis && isOnBoundary(box, direction);
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
	 *
	 * <p>Which half is not simply the box's own extent. A sprite's u runs with the world axis it
	 * stands for - down and up read x, north and south read x, west and east read z - but the
	 * quad's first vertices take the sprite's <em>left</em> column, and on the faces that are read
	 * from behind that column lies at the far end of the axis. On a north face, seen from the
	 * north, +x is to the left, so the sprite's left column belongs at the largest x and the
	 * extent has to be reversed; the same goes for east. South and west are read from in front and
	 * take the extent as it is.
	 *
	 * <p>Up and down read their v the same way round only when seen from the same side: looking
	 * down at a block, +z runs down the sprite, and looking up at it from below, +z runs up the
	 * sprite. So up takes the box's z as it stands and down takes it reversed, which is what
	 * {@code FaceBakery.defaultFaceUV} writes and what makes the pair asymmetric: a box cropped
	 * along z shows the near half of the sprite on one of them and the far half on the other.
	 *
	 * <p>Vanilla's own {@code block/stairs} proves it: its step box {@code [8,8,0]-[16,16,16]}
	 * crops {@code [0,0,8,8]} on north - the box's x is 8 to 16, the sprite's u 0 to 8 - and
	 * {@code [0,0,16,8]} on east, where the box's own z of 0 to 8 is taken as it stands. Its
	 * bottom box, spanning the whole block, crops the whole sprite on both, which is why the
	 * reversal only shows on a shape that covers part of a face. {@code block/slab} crops the whole
	 * sprite on down and up for the same reason - its box spans the block in z - and an upright
	 * slab's box, which does not, is where the two conventions part company.
	 */
	private static int[] crop(Box box, String direction) {
		return switch (direction) {
			case "down" -> new int[] { box.x1(), 16 - box.z2(), box.x2(), 16 - box.z1() };
			case "up" -> new int[] { box.x1(), box.z1(), box.x2(), box.z2() };
			case "north" -> new int[] { 16 - box.x2(), 16 - box.y2(), 16 - box.x1(), 16 - box.y1() };
			case "south" -> new int[] { box.x1(), 16 - box.y2(), box.x2(), 16 - box.y1() };
			case "west" -> new int[] { box.z1(), 16 - box.y2(), box.z2(), 16 - box.y1() };
			case "east" -> new int[] { 16 - box.z2(), 16 - box.y2(), 16 - box.z1(), 16 - box.y1() };
			default -> new int[] { 0, 0, 16, 16 };
		};
	}

	/**
	 * The rectangle a face has to name when the pattern on it is turned by {@code rotation} degrees
	 * and the quad covers only the part of the block face that {@code crop} describes.
	 *
	 * <p>A model's {@code rotation} does not turn a rectangle: {@code CuboidFace.getU} and
	 * {@code getV} hand the rectangle's four corners to the quad's four vertices in a different
	 * order and leave the rectangle's own bounds alone. So "crop the sprite, then say it is turned"
	 * gives a rectangle of the wrong shape whenever the crop is not a square, and Minecraft
	 * stretches it to fit. The rectangle has to be the one that <em>becomes</em> the crop once the
	 * pattern is turned - the pattern is turned first and the piece is then cut out of it, which is
	 * exactly the order the block itself is built in.
	 *
	 * <p>Turning the sprite a quarter clockwise sends a point {@code (u, v)} to {@code (v, 16 - u)},
	 * so the crop is carried through that same map, once per quarter turn, taking the two corners it
	 * moves with it as it goes.
	 */
	private static int[] patternUv(int[] crop, int rotation) {
		int minU = crop[0];
		int minV = crop[1];
		int maxU = crop[2];
		int maxV = crop[3];

		for (int i = 0; i < Math.floorMod(rotation / 90, 4); i++) {
			int nextMinU = minV;
			int nextMinV = 16 - maxU;
			int nextMaxU = maxV;
			int nextMaxV = 16 - minU;
			minU = nextMinU;
			minV = nextMinV;
			maxU = nextMaxU;
			maxV = nextMaxV;
		}

		return new int[] { minU, minV, maxU, maxV };
	}

	private static JsonArray vector(int x, int y, int z) {
		JsonArray array = new JsonArray();
		array.add(x);
		array.add(y);
		array.add(z);
		return array;
	}

	/** A corner that does not land on a sixteenth of the block, such as an icon's. */
	private static JsonArray vector(double x, double y, double z) {
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
