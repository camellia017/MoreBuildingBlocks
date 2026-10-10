package com.camelie.morebuildingblocks.client.datagen;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import com.camelie.morebuildingblocks.MoreBuildingBlocks;
import com.camelie.morebuildingblocks.registry.MBBlockFamilies;
import com.camelie.morebuildingblocks.registry.MBEighths;

/**
 * Writes {@code assets/morebuildingblocks/lang/en_us.json} and {@code zh_cn.json}.
 *
 * <p>Every block this mod adds is a stair, a slab or an eighth block cut out of a block
 * vanilla already has, so every name is "{@code <block>} stairs" or
 * "{@code Eighth <block>}" - never a name of its own. Both languages are therefore read
 * from vanilla's own language files, with the {@link #BASE_NAMES} table below covering
 * the 33 terracotta blocks this mod supplies the names for.
 *
 * <p>Slabs are called slabs, not "vertical slabs": the {@code vertical} block state
 * is an extra way to place the same block, not a second block.
 */
public class MBLanguageProvider implements DataProvider {
	/** Locale file name of each language written, in the order they are reported. */
	private static final String[] LOCALES = { "en_us", "zh_cn" };

	/**
	 * Display name of each family's base block, indexed by the base block's path
	 * (which is also the prefix of both ids in that family).
	 *
	 * <p>The English column is only a fallback: when vanilla's own {@code en_us.json}
	 * can be read, its name for the base block wins, so the stairs and slabs are
	 * guaranteed to be named exactly like the block they are cut from.
	 */
	private static final Map<String, String[]> BASE_NAMES = new LinkedHashMap<>();

	static {
		name("terracotta", "Terracotta", "陶瓦");
		name("white_terracotta", "White Terracotta", "白色陶瓦");
		name("light_gray_terracotta", "Light Gray Terracotta", "淡灰色陶瓦");
		name("gray_terracotta", "Gray Terracotta", "灰色陶瓦");
		name("black_terracotta", "Black Terracotta", "黑色陶瓦");
		name("brown_terracotta", "Brown Terracotta", "棕色陶瓦");
		name("red_terracotta", "Red Terracotta", "红色陶瓦");
		name("orange_terracotta", "Orange Terracotta", "橙色陶瓦");
		name("yellow_terracotta", "Yellow Terracotta", "黄色陶瓦");
		name("lime_terracotta", "Lime Terracotta", "黄绿色陶瓦");
		name("green_terracotta", "Green Terracotta", "绿色陶瓦");
		name("cyan_terracotta", "Cyan Terracotta", "青色陶瓦");
		name("light_blue_terracotta", "Light Blue Terracotta", "淡蓝色陶瓦");
		name("blue_terracotta", "Blue Terracotta", "蓝色陶瓦");
		name("purple_terracotta", "Purple Terracotta", "紫色陶瓦");
		name("magenta_terracotta", "Magenta Terracotta", "品红色陶瓦");
		name("pink_terracotta", "Pink Terracotta", "粉红色陶瓦");

		name("white_glazed_terracotta", "White Glazed Terracotta", "白色带釉陶瓦");
		name("light_gray_glazed_terracotta", "Light Gray Glazed Terracotta", "淡灰色带釉陶瓦");
		name("gray_glazed_terracotta", "Gray Glazed Terracotta", "灰色带釉陶瓦");
		name("black_glazed_terracotta", "Black Glazed Terracotta", "黑色带釉陶瓦");
		name("brown_glazed_terracotta", "Brown Glazed Terracotta", "棕色带釉陶瓦");
		name("red_glazed_terracotta", "Red Glazed Terracotta", "红色带釉陶瓦");
		name("orange_glazed_terracotta", "Orange Glazed Terracotta", "橙色带釉陶瓦");
		name("yellow_glazed_terracotta", "Yellow Glazed Terracotta", "黄色带釉陶瓦");
		name("lime_glazed_terracotta", "Lime Glazed Terracotta", "黄绿色带釉陶瓦");
		name("green_glazed_terracotta", "Green Glazed Terracotta", "绿色带釉陶瓦");
		name("cyan_glazed_terracotta", "Cyan Glazed Terracotta", "青色带釉陶瓦");
		name("light_blue_glazed_terracotta", "Light Blue Glazed Terracotta", "淡蓝色带釉陶瓦");
		name("blue_glazed_terracotta", "Blue Glazed Terracotta", "蓝色带釉陶瓦");
		name("purple_glazed_terracotta", "Purple Glazed Terracotta", "紫色带釉陶瓦");
		name("magenta_glazed_terracotta", "Magenta Glazed Terracotta", "品红色带釉陶瓦");
		name("pink_glazed_terracotta", "Pink Glazed Terracotta", "粉红色带釉陶瓦");

		// The 100 vanilla materials the eighth blocks are cut from. Vanilla's en_us.json is
		// on the data generator's classpath and is read below, but its zh_cn.json is not:
		// the 26.3 client jar only ships en_us.json, and the other languages live in the
		// asset index. The Chinese column is therefore written out here, spelled exactly
		// like vanilla's own translations, and the loader above stays as the English
		// source. Regenerate with build/mcsrc/write_vanilla_names.ps1.
		// GENERATED-VANILLA-NAMES-BEGIN
		name("acacia_planks", "Acacia Planks", "金合欢木板");
		name("andesite", "Andesite", "安山岩");
		name("bamboo_mosaic", "Bamboo Mosaic", "竹马赛克");
		name("bamboo_planks", "Bamboo Planks", "竹板");
		name("birch_planks", "Birch Planks", "白桦木板");
		name("black_concrete", "Black Concrete", "黑色混凝土");
		name("black_wool", "Black Wool", "黑色羊毛");
		name("blackstone", "Blackstone", "黑石");
		name("blue_concrete", "Blue Concrete", "蓝色混凝土");
		name("blue_wool", "Blue Wool", "蓝色羊毛");
		name("bricks", "Bricks", "红砖块");
		name("brown_concrete", "Brown Concrete", "棕色混凝土");
		name("brown_wool", "Brown Wool", "棕色羊毛");
		name("cherry_planks", "Cherry Planks", "樱花木板");
		name("cinnabar", "Cinnabar", "朱砂");
		name("cinnabar_bricks", "Cinnabar Bricks", "朱砂砖");
		name("cobbled_deepslate", "Cobbled Deepslate", "深板岩圆石");
		name("cobblestone", "Cobblestone", "圆石");
		name("crimson_planks", "Crimson Planks", "绯红木板");
		name("cut_copper", "Cut Copper", "切制铜块");
		name("cut_red_sandstone", "Cut Red Sandstone", "切制红砂岩");
		name("cut_sandstone", "Cut Sandstone", "切制砂岩");
		name("cyan_concrete", "Cyan Concrete", "青色混凝土");
		name("cyan_wool", "Cyan Wool", "青色羊毛");
		name("dark_oak_planks", "Dark Oak Planks", "深色橡木木板");
		name("dark_prismarine", "Dark Prismarine", "暗海晶石");
		name("deepslate_bricks", "Deepslate Bricks", "深板岩砖");
		name("deepslate_tiles", "Deepslate Tiles", "深板岩瓦");
		name("diorite", "Diorite", "闪长岩");
		name("end_stone_bricks", "End Stone Bricks", "末地石砖");
		name("exposed_cut_copper", "Exposed Cut Copper", "斑驳的切制铜块");
		name("granite", "Granite", "花岗岩");
		name("gray_concrete", "Gray Concrete", "灰色混凝土");
		name("gray_wool", "Gray Wool", "灰色羊毛");
		name("green_concrete", "Green Concrete", "绿色混凝土");
		name("green_wool", "Green Wool", "绿色羊毛");
		name("jungle_planks", "Jungle Planks", "丛林木板");
		name("light_blue_concrete", "Light Blue Concrete", "淡蓝色混凝土");
		name("light_blue_wool", "Light Blue Wool", "淡蓝色羊毛");
		name("light_gray_concrete", "Light Gray Concrete", "淡灰色混凝土");
		name("light_gray_wool", "Light Gray Wool", "淡灰色羊毛");
		name("lime_concrete", "Lime Concrete", "黄绿色混凝土");
		name("lime_wool", "Lime Wool", "黄绿色羊毛");
		name("magenta_concrete", "Magenta Concrete", "品红色混凝土");
		name("magenta_wool", "Magenta Wool", "品红色羊毛");
		name("mangrove_planks", "Mangrove Planks", "红树木板");
		name("mossy_cobblestone", "Mossy Cobblestone", "苔石");
		name("mossy_stone_bricks", "Mossy Stone Bricks", "苔石砖");
		name("mud_bricks", "Mud Bricks", "泥砖");
		name("nether_bricks", "Nether Bricks", "下界砖块");
		name("oak_planks", "Oak Planks", "橡木木板");
		name("orange_concrete", "Orange Concrete", "橙色混凝土");
		name("orange_wool", "Orange Wool", "橙色羊毛");
		name("oxidized_cut_copper", "Oxidized Cut Copper", "氧化的切制铜块");
		name("pale_oak_planks", "Pale Oak Planks", "苍白橡木木板");
		name("pink_concrete", "Pink Concrete", "粉红色混凝土");
		name("pink_wool", "Pink Wool", "粉红色羊毛");
		name("polished_andesite", "Polished Andesite", "磨制安山岩");
		name("polished_blackstone", "Polished Blackstone", "磨制黑石");
		name("polished_blackstone_bricks", "Polished Blackstone Bricks", "磨制黑石砖");
		name("polished_cinnabar", "Polished Cinnabar", "磨制朱砂");
		name("polished_deepslate", "Polished Deepslate", "磨制深板岩");
		name("polished_diorite", "Polished Diorite", "磨制闪长岩");
		name("polished_granite", "Polished Granite", "磨制花岗岩");
		name("polished_sulfur", "Polished Sulfur", "磨制硫黄");
		name("polished_tuff", "Polished Tuff", "磨制凝灰岩");
		name("poplar_planks", "Poplar Planks", "杨木木板");
		name("prismarine", "Prismarine", "海晶石");
		name("prismarine_bricks", "Prismarine Bricks", "海晶石砖");
		name("purple_concrete", "Purple Concrete", "紫色混凝土");
		name("purple_wool", "Purple Wool", "紫色羊毛");
		name("purpur_block", "Purpur Block", "紫珀块");
		name("quartz_block", "Block of Quartz", "石英块");
		name("red_concrete", "Red Concrete", "红色混凝土");
		name("red_nether_bricks", "Red Nether Bricks", "红色下界砖块");
		name("red_sandstone", "Red Sandstone", "红砂岩");
		name("red_wool", "Red Wool", "红色羊毛");
		name("resin_bricks", "Resin Bricks", "树脂砖块");
		name("sandstone", "Sandstone", "砂岩");
		name("smooth_quartz", "Smooth Quartz Block", "平滑石英块");
		name("smooth_red_sandstone", "Smooth Red Sandstone", "平滑红砂岩");
		name("smooth_sandstone", "Smooth Sandstone", "平滑砂岩");
		name("smooth_stone", "Smooth Stone", "平滑石头");
		name("spruce_planks", "Spruce Planks", "云杉木板");
		name("stone", "Stone", "石头");
		name("stone_bricks", "Stone Bricks", "石砖");
		name("sulfur", "Sulfur", "硫黄");
		name("sulfur_bricks", "Sulfur Bricks", "硫黄砖");
		name("tuff", "Tuff", "凝灰岩");
		name("tuff_bricks", "Tuff Bricks", "凝灰岩砖");
		name("warped_planks", "Warped Planks", "诡异木板");
		name("waxed_cut_copper", "Waxed Cut Copper", "涂蜡的切制铜块");
		name("waxed_exposed_cut_copper", "Waxed Exposed Cut Copper", "涂蜡的斑驳切制铜块");
		name("waxed_oxidized_cut_copper", "Waxed Oxidized Cut Copper", "涂蜡的氧化切制铜块");
		name("waxed_weathered_cut_copper", "Waxed Weathered Cut Copper", "涂蜡的锈蚀切制铜块");
		name("weathered_cut_copper", "Weathered Cut Copper", "锈蚀的切制铜块");
		name("white_concrete", "White Concrete", "白色混凝土");
		name("white_wool", "White Wool", "白色羊毛");
		name("yellow_concrete", "Yellow Concrete", "黄色混凝土");
		name("yellow_wool", "Yellow Wool", "黄色羊毛");
		// GENERATED-VANILLA-NAMES-END
	}

	private static void name(String basePath, String english, String chinese) {
		BASE_NAMES.put(basePath, new String[] { english, chinese });
	}

	private final PackOutput.PathProvider langPath;
	private final Map<String, String> vanillaEnglish = new LinkedHashMap<>();
	private final Map<String, String> vanillaChinese = new LinkedHashMap<>();

	public MBLanguageProvider(PackOutput output) {
		// A language file has to live under the namespace it translates, so these are
		// written to this mod's own lang folder and not into minecraft's.
		this.langPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "lang");
		loadVanillaNames("en_us", this.vanillaEnglish);
		loadVanillaNames("zh_cn", this.vanillaChinese);
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		return CompletableFuture.allOf(
				DataProvider.saveStable(output, english(), this.langPath.json(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, LOCALES[0]))),
				DataProvider.saveStable(output, chinese(), this.langPath.json(Identifier.fromNamespaceAndPath(MoreBuildingBlocks.MOD_ID, LOCALES[1]))));
	}

	@Override
	public String getName() {
		return "MoreBuildingBlocks Languages";
	}

	private JsonObject english() {
		JsonObject json = new JsonObject();

		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			add(json, family, englishBaseName(family.basePath()), " Stairs", " Slab");
		}

		for (MBEighths.Material material : MBEighths.materials()) {
			json.addProperty(eighthKey(material.basePath()), "Eighth " + englishBaseName(material.basePath()));
		}

		return json;
	}

	private JsonObject chinese() {
		JsonObject json = new JsonObject();

		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			// Vanilla calls a bottom slab 台阶, and the upright form is the same block, so
			// nothing in this mod is ever called a 竖半砖.
			add(json, family, chineseBaseName(family.basePath()), "楼梯", "台阶");
		}

		for (MBEighths.Material material : MBEighths.materials()) {
			json.addProperty(eighthKey(material.basePath()), chineseBaseName(material.basePath()) + "八分块");
		}

		return json;
	}

	private void add(JsonObject json, MBBlockFamilies.Family family, String base, String stairsSuffix, String slabSuffix) {
		json.addProperty(key(family.basePath(), "stairs"), base + stairsSuffix);
		json.addProperty(key(family.basePath(), "slab"), base + slabSuffix);
	}

	private static String key(String basePath, String form) {
		return "block." + MoreBuildingBlocks.MOD_ID + "." + basePath + "_" + form;
	}

	/** The key of an eighth block, whose id puts the form in front of the material. */
	private static String eighthKey(String basePath) {
		return "block." + MoreBuildingBlocks.MOD_ID + "." + MBEighths.PREFIX + basePath;
	}

	/** Vanilla's own name for the base block, falling back to the table above. */
	private String englishBaseName(String basePath) {
		// A material's base path is the base block's own path: "oak_planks", "white_terracotta",
		// "blue_glazed_terracotta", and plain "terracotta".
		String vanilla = this.vanillaEnglish.get(basePath);

		return vanilla != null ? vanilla : names(basePath)[0];
	}

	/** Vanilla's own Chinese name for the base block, falling back to the table above. */
	private String chineseBaseName(String basePath) {
		String vanilla = this.vanillaChinese.get(basePath);

		return vanilla != null ? vanilla : names(basePath)[1];
	}

	private static String[] names(String basePath) {
		String[] names = BASE_NAMES.get(basePath);

		if (names == null) {
			throw new IllegalStateException("No display name registered for base block " + basePath);
		}

		return names;
	}

	/**
	 * Reads {@code assets/minecraft/lang/<locale>.json} so that a stair, a slab or an
	 * eighth block cannot be named differently from the block it is cut from. Vanilla's
	 * language file is optional at data generation time, so a missing one only means the
	 * table above is used as-is.
	 */
	private void loadVanillaNames(String locale, Map<String, String> names) {
		String path = "/assets/minecraft/lang/" + locale + ".json";

		try (InputStream stream = MBLanguageProvider.class.getResourceAsStream(path)) {
			if (stream == null) {
				MoreBuildingBlocks.LOGGER.debug("Vanilla {} not readable; using this mod's own names", path);

				return;
			}

			JsonObject json = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);

			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (entry.getKey().startsWith("block.minecraft.") && entry.getValue().isJsonPrimitive()) {
					names.put(entry.getKey().substring("block.minecraft.".length()), entry.getValue().getAsString());
				}
			}
		} catch (Exception e) {
			MoreBuildingBlocks.LOGGER.debug("Vanilla {} could not be parsed; using this mod's own names", path, e);
		}
	}
}
