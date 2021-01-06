package com.ruthlessjailer.plugin.onechunk

import com.ruthlessjailer.api.theseus.Chat
import com.ruthlessjailer.api.theseus.command.CommandBase
import com.ruthlessjailer.api.theseus.command.SubCommand
import com.ruthlessjailer.api.theseus.command.SuperiorCommand
import com.ruthlessjailer.api.theseus.task.manager.TaskManager
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender

/**
 * @author RuthlessJailer
 */
object OCCommand : CommandBase("onechunk|oc"), SuperiorCommand {

	override fun runCommand(sender: CommandSender, args: Array<out String>?, label: String) {

	}

	@SubCommand(inputArgs = "toggle")
	fun toggle(sender: CommandSender, args: Array<out String>) {
		if (args.size > 1) {
			return
		}

		toggle(sender, args, getPlayer(sender))
	}

	@SubCommand(inputArgs = "toggle %p")
	fun toggle(sender: CommandSender, args: Array<out String>, player: OfflinePlayer) {
		if (!player.isOnline) {
			return
		}

		val uuid = player.uniqueId
		val pl = Bukkit.getPlayer(uuid)!!

		if (ChunkUtil.PLAYERS.contains(uuid)) {
			ChunkUtil.PLAYERS.remove(uuid)
			ChunkUtil.PLAYER_TASKS[uuid]?.cancel()
			ChunkUtil.PLAYER_TASKS.remove(uuid)
			TaskManager.async.later { ChunkUtil.handleChunks(pl, pl.location, true) }
			Chat.send("&b${pl.name}&2, you are now spectating.", if (pl.player == sender) listOf(pl) else listOf(pl, sender))
		} else {
			ChunkUtil.PLAYERS.add(uuid)
			ChunkUtil.PLAYER_TASKS[uuid] = TaskManager.async.repeat({ ChunkUtil.updateEntities(pl) }, 1)
			TaskManager.async.later { ChunkUtil.handleChunks(pl, pl.location, false) }
			Chat.send("&b${pl.name}&4, you're no longer spectating.", if (pl.player == sender) listOf(pl) else listOf(pl, sender))
		}
	}

}