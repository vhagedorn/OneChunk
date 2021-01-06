package com.ruthlessjailer.plugin.onechunk

import com.ruthlessjailer.api.theseus.task.manager.TaskManager
import com.ruthlessjailer.plugin.onechunk.ChunkUtil.air
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * @author RuthlessJailer
 */
object ChunkListener : Listener {

	@EventHandler
	fun onTeleport(event: PlayerTeleportEvent) {
		if (event.to == null || event.from.chunk == event.to!!.chunk) {
			return
		}
		TaskManager.async.later {
			ChunkUtil.handleChunks(event.player, event.to!!, false)
			ChunkUtil.forceUpdateEntities(event.player)
		}
	}

	@EventHandler
	private fun onMove(event: PlayerMoveEvent) {
		if (event.to == null || event.from.chunk == event.to!!.chunk) {
			return
		}

		TaskManager.async.later { ChunkUtil.handleChunks(event.player, event.to!!, false) }
	}

	@EventHandler
	private fun onBreak(event: BlockBreakEvent) {
		val entities = event.player.world.getNearbyEntities(event.player.location, 200.0, 125.0, 200.0) { e -> e is Player }

		val broken = event.block.location

		TaskManager.async.delay({
									entities.forEach { ent ->
										if (ChunkUtil.PLAYERS.contains(ent.uniqueId)) {
											if (broken.chunk != ent.location.chunk) {
												(ent as Player).sendBlockChange(broken, ChunkUtil.AIR_BLOCKDATA)
											}
										}
									}
								}, 3)
	}

	@EventHandler
	private fun onPlace(event: BlockPlaceEvent) {
		if (event.blockPlaced.type.air()) {
			return
		}

		val entities = event.player.world.getNearbyEntities(event.player.location, 200.0, 125.0, 200.0) { e -> e is Player }

		val placed = event.blockPlaced.location
		val against = event.blockAgainst.location

		TaskManager.async.delay({
									entities.forEach { ent ->
										if (ChunkUtil.PLAYERS.contains(ent.uniqueId)) {
											if (against.chunk != ent.location.chunk) {
												(ent as Player).sendBlockChange(against, ChunkUtil.BARRIER_BLOCKDATA)
											}

											if (placed.chunk != ent.location.chunk) {
												(ent as Player).sendBlockChange(placed, ChunkUtil.BARRIER_BLOCKDATA)
											}
										}
									}
								}, 2)
	}

	@EventHandler
	private fun onInteract(event: PlayerInteractEvent) {
		if (event.clickedBlock == null || event.clickedBlock!!.type.air() || event.action == Action.LEFT_CLICK_BLOCK) {//let them break the block
			return
		}

		val entities = event.player.world.getNearbyEntities(event.player.location, 200.0, 125.0, 200.0) { e -> e is Player }

		val clicked = event.clickedBlock!!.location

		TaskManager.async.delay({
									entities.forEach { ent ->
										if (ChunkUtil.PLAYERS.contains(ent.uniqueId)) {
											if (clicked.chunk != ent.location.chunk) {
												(ent as Player).sendBlockChange(clicked, ChunkUtil.BARRIER_BLOCKDATA)
											}
										}
									}
								}, 2)
	}

//	@EventHandler
//	private fun onUpdate(event: BlockPhysicsEvent) {
//		if (ChunkUtil.PLAYERS.isEmpty()) {
//			return
//		}
//
//		val type = event.changedType
//		val src = event.sourceBlock
//
//		TaskManager.async.later {
//			ChunkUtil.PLAYERS.forEach {
//				val player = Bukkit.getPlayer(it)
//				if (player?.location?.chunk != src.chunk) {
//					player?.sendBlockChange(src.location, if (type.isSolid) ChunkUtil.BARRIER_BLOCKDATA else ChunkUtil.AIR_BLOCKDATA)
//				}
//			}
//		}
//	}

}