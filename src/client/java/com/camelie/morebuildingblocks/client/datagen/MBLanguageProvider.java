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

/**
 * Writes {@code assets/morebuildingblocks/lang/en_us.json} and {@code zh_cn.json}.
 *
 * <p>Every block this mod adds is a stair or a slab cut out of a vanilla terracotta
 * block, so every name is "{@code <colour> terracotta} stairs" and so on - never a
 * name of its own. Both languages are therefore built from the one {@link #BASE_NAMES}
 * table below rather than from 132 hand-written keys.
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
	}

	private static void name(String basePath, String english, String chinese) {
		BASE_NAMES.put(basePath, new String[] { english, chinese });
	}

	private final PackOutput.PathProvider langPath;
	private final Map<String, String> vanillaEnglish = new LinkedHashMap<>();

	public MBLanguageProvider(PackOutput output) {
		// A language file has to live under the namespace it translates, so these are
		// written to this mod's own lang folder and not into minecraft's.
		this.langPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "lang");
		loadVanillaEnglish();
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
			add(json, family, englishBaseName(family), " Stairs", " Slab");
		}

		return json;
	}

	private JsonObject chinese() {
		JsonObject json = new JsonObject();

		for (MBBlockFamilies.Family family : MBBlockFamilies.families()) {
			// Vanilla calls a bottom slab 台阶, and the upright form is the same block, so
			// nothing in this mod is ever called a 竖半砖.
			add(json, family, chineseBaseName(family), "楼梯", "台阶");
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

	/** Vanilla's own name for the base block, falling back to the table above. */
	private String englishBaseName(MBBlockFamilies.Family family) {
		// A family's base path is the base block's own path: "white_terracotta",
		// "blue_glazed_terracotta", and plain "terracotta".
		String vanilla = this.vanillaEnglish.get(family.basePath());

		return vanilla != null ? vanilla : BASE_NAMES.get(family.basePath())[0];
	}

	private String chineseBaseName(MBBlockFamilies.Family family) {
		String[] names = BASE_NAMES.get(family.basePath());

		if (names == null) {
			throw new IllegalStateException("No display name registered for base block " + family.basePath());
		}

		return names[1];
	}

	/**
	 * Reads {@code assets/minecraft/lang/en_us.json} so that a stair or slab cannot
	 * be named differently from the block it is cut from. Vanilla's language file is
	 * optional at data generation time, so a missing one only means the table above
	 * is used as-is.
	 */
	private void loadVanillaEnglish() {
		try (InputStream stream = MBLanguageProvider.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
			if (stream == null) {
				MoreBuildingBlocks.LOGGER.debug("Vanilla en_us.json not readable; using this mod's own English names");

				return;
			}

			JsonObject json = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);

			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (entry.getKey().startsWith("block.minecraft.") && entry.getValue().isJsonPrimitive()) {
					this.vanillaEnglish.put(entry.getKey().substring("block.minecraft.".length()), entry.getValue().getAsString());
				}
			}
		} catch (Exception e) {
			MoreBuildingBlocks.LOGGER.debug("Vanilla en_us.json could not be parsed; using this mod's own English names", e);
		}
	}
}
