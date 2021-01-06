package com.ruthlessjailer.plugin.onechunk

import com.ruthlessjailer.api.theseus.Chat
import com.ruthlessjailer.api.theseus.PluginBase
import com.ruthlessjailer.api.theseus.multiversion.MinecraftVersion

/**
 * @author RuthlessJailer
 */
class OneChunk : PluginBase() {

	override fun onStart() {
		registerCommands(OCCommand)
		registerEvents(ChunkListener)

		if (MinecraftVersion.lessThan(MinecraftVersion.v1_16)) {
			Chat.warning("Please use 1.16 for best performance.")
		}

		if (MinecraftVersion.lessThan(MinecraftVersion.v1_13)) {
			Chat.warning("Untested legacy (<1.13) version ${MinecraftVersion.CURRENT_VERSION} detected. This plugin may not function properly.")
		}
	}

}