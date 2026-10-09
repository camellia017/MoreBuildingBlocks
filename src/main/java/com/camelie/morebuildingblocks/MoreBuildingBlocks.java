package com.camelie.morebuildingblocks;

import com.camelie.morebuildingblocks.registry.MBBlocks;
import com.camelie.morebuildingblocks.registry.MBItems;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + dedicated server) entrypoint.
 *
 * <p>All block and item registration happens here so a dedicated server ends up with exactly the
 * same registry contents as a client. Anything that touches rendering lives in the {@code client}
 * source set instead.</p>
 */
public class MoreBuildingBlocks implements ModInitializer {
	public static final String MOD_ID = "morebuildingblocks";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MBBlocks.bootstrap();
		MBItems.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
