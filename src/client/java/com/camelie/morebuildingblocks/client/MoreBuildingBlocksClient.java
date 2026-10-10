package com.camelie.morebuildingblocks.client;

import net.fabricmc.api.ClientModInitializer;

public class MoreBuildingBlocksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The upright slab states of vanilla slabs are not in vanilla's blockstate
		// files, so they are resolved at load time instead of rewritten into them.
		new MBVanillaSlabModelPlugin().onInitializeClient();

		// Eighth blocks also show up in vanilla's building blocks and coloured blocks tabs.
		MBEighthTabPlugin.onInitializeClient();
	}
}
