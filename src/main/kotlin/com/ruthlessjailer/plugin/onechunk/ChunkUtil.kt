package com.ruthlessjailer.plugin.onechunk

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.nesaak.noreflection.NoReflection.shared
import com.ruthlessjailer.api.theseus.ReflectUtil.*
import com.ruthlessjailer.api.theseus.multiversion.MinecraftVersion.*
import com.ruthlessjailer.api.theseus.task.handler.FutureHandler
import com.ruthlessjailer.api.theseus.task.manager.TaskManager
import lombok.SneakyThrows
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * @author RuthlessJailer
 */
object ChunkUtil {

	const val REL_CHUNK = 15
	const val CHUNK_COORD = 4
	const val CHUNK_SECTION = 240
	const val FULL_CHUNK = 65535

	private val CraftBlockData_getState = shared().get(getMethod(getOBCClass("block.data.CraftBlockData"), "getState"))
	private val CraftWorld_getHandle = shared().get(getMethod(getOBCClass("CraftWorld"), "getHandle"))
	private val CraftChunk_getHandle = shared().get(getMethod(getOBCClass("CraftChunk"), "getHandle"))
	private val CraftPlayer_getHandle = shared().get(getMethod(getOBCClass("entity.CraftPlayer"), "getHandle"))
	private val CraftEntity_getHandle = shared().get(getMethod(getOBCClass("entity.CraftEntity"), "getHandle"))
	private val CraftLivingEntity_getHandle = shared().get(getMethod(getOBCClass("entity.CraftLivingEntity"), "getHandle"))
	private val CraftHumanEntity_getHandle = shared().get(getMethod(getOBCClass("entity.CraftHumanEntity"), "getHandle"))

	private val Chunk = shared().get(getConstructor(getNMSClass("Chunk"), getNMSClass("World"), getNMSClass("ProtoChunk")))
	private val Chunk_p = shared().get(getMethod(getNMSClass("Chunk"), "p"))
	private val Chunk_e = shared().get(getMethod(getNMSClass("Chunk"), "e"))
	private val Chunk_getChunkStatus = shared().get(getMethod(getNMSClass("Chunk"), "getChunkStatus"))
	private val Chunk_getSections = shared().get(getMethod(getNMSClass("Chunk"), "getSections"))
	private val Chunk_getType = shared().get(getMethod(getNMSClass("Chunk"), "getType", getNMSClass("BlockPosition")))
	private val Chunk_tileEntities = shared().get(getField(getNMSClass("Chunk"), "tileEntities"))

	private val ChunkCoordIntPair = shared().get(getConstructor(getNMSClass("ChunkCoordIntPair"), Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))

	private val BlockPosition = shared().get(getConstructor(getNMSClass("BlockPosition"), Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))

	private val ProtoChunk = shared().get(getConstructor(getNMSClass("ProtoChunk"), getNMSClass("ChunkCoordIntPair"), getNMSClass("ChunkConverter")))
	private val ProtoChunk_a_ChunkStatus = shared().get(getMethod(getNMSClass("ProtoChunk"), "a", getNMSClass("ChunkStatus")))
	private val ProtoChunk_a_LightEngine = shared().get(getMethod(getNMSClass("ProtoChunk"), "a", getNMSClass("LightEngine")))
	private val ProtoChunk_setType = shared().get(getMethod(getNMSClass("ProtoChunk"), "setType", getNMSClass("BlockPosition"), getNMSClass("IBlockData"), Boolean::class.javaPrimitiveType!!))
	private val ProtoChunk_setTileEntity = shared().get(getMethod(getNMSClass("ProtoChunk"), "setTileEntity", getNMSClass("BlockPosition"), getNMSClass("TileEntity")))

	private val ChunkSection = shared().get(getConstructor(getNMSClass("ChunkSection"), Int::class.javaPrimitiveType))
	private val ChunkSection_c = shared().get(getMethod(getNMSClass("ChunkSection"), "c"))
	private val ChunkSection_getYPosition = shared().get(getMethod(getNMSClass("ChunkSection"), "getYPosition"))
	private val ChunkSection_setType = shared().get(getMethod(getNMSClass("ChunkSection"), "setType", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, getNMSClass("IBlockData")))
	private val ChunkSection_getType = shared().get(getMethod(getNMSClass("ChunkSection"), "getType", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))

	private val Entity_yaw = shared().get(getField(getNMSClass("Entity"), "yaw"))
	private val Entity_getDataWatcher = shared().get(getMethod(getNMSClass("Entity"), "getDataWatcher"))

	private val EntityLiving_getItemInMainHand = shared().get(getMethod(getNMSClass("EntityLiving"), "getItemInMainHand"))
	private val EntityLiving_getItemInOffHand = shared().get(getMethod(getNMSClass("EntityLiving"), "getItemInOffHand"))

	private val EntityHuman_getArmorItems = shared().get(getMethod(getNMSClass("EntityHuman"), "getArmorItems"))

	private val EntityPlayer_playerConnection = shared().get(getField(getNMSClass("EntityPlayer"), "playerConnection"))

	private val PlayerConnection_sendPacket = shared().get(getMethod(getNMSClass("PlayerConnection"), "sendPacket", getNMSClass("Packet")))

	private val PacketPlayOutMapChunk = shared().get(getConstructor(getNMSClass("PacketPlayOutMapChunk"), getNMSClass("Chunk"), Int::class.javaPrimitiveType))
	private val PacketPlayOutEntityDestroy = shared().get(getConstructor(getNMSClass("PacketPlayOutEntityDestroy"), IntArray::class.java))
	private val PacketPlayOutNamedEntitySpawn = shared().get(getConstructor(getNMSClass("PacketPlayOutNamedEntitySpawn"), getNMSClass("EntityHuman")))
	private val PacketPlayOutSpawnEntityLiving = shared().get(getConstructor(getNMSClass("PacketPlayOutSpawnEntityLiving"), getNMSClass("EntityLiving")))
	private val PacketPlayOutSpawnEntity = shared().get(getConstructor(getNMSClass("PacketPlayOutSpawnEntity"), getNMSClass("Entity")))
	private val PacketPlayOutEntityHeadRotation = shared().get(getConstructor(getNMSClass("PacketPlayOutEntityHeadRotation"), getNMSClass("Entity"), Byte::class.javaPrimitiveType))
	private val PacketPlayOutEntityMetadata = shared().get(getConstructor(getNMSClass("PacketPlayOutEntityMetadata"), Int::class.javaPrimitiveType, getNMSClass("DataWatcher"), Boolean::class.javaPrimitiveType))
	private val PacketPlayOutEntityEquipment = when {
		atLeast(v1_16) -> {
			shared().get(getConstructor(getNMSClass("PacketPlayOutEntityEquipment"), Int::class.javaPrimitiveType, List::class.java))
		}
		atLeast(v1_13) -> {
			shared().get(getConstructor(getNMSClass("PacketPlayOutEntityEquipment"), Int::class.javaPrimitiveType, getNMSClass("EnumItemSlot"), getNMSClass("ItemStack")))
		}
		else           -> {
			//TODO check 1.12 and below compatibility
			shared().get(getConstructor(getNMSClass("PacketPlayOutEntityEquipment"), Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, getNMSClass("ItemStack")))
		}
	}

	private val Pair_of = shared().get(getMethod(getClass("com.mojang.datafixers.util.Pair"), "of", Any::class.java, Any::class.java))

	val BARRIER_BLOCKDATA = Material.BARRIER.createBlockData()
	val AIR_BLOCKDATA = Material.AIR.createBlockData()
	private val BARRIER_IBLOCKDATA = CraftBlockData_getState.call(BARRIER_BLOCKDATA)
	private val AIR_IBLOCKDATA = CraftBlockData_getState.call(AIR_BLOCKDATA)
	private val MAINHAND = getEnum(getNMSClass("EnumItemSlot") as Class<Enum<*>>, "MAINHAND")
	private val OFFHAND = getEnum(getNMSClass("EnumItemSlot") as Class<Enum<*>>, "OFFHAND")
	private val ENUMITEMSLOT_VALUES: Array<*> = invokeMethod(getNMSClass("EnumItemSlot"), "values", null)

	val MATERIAL_CACHE: Cache<String, Material> = CacheBuilder.newBuilder().build()
	val PLAYERS: MutableSet<UUID> = HashSet()
	val PLAYER_TASKS: MutableMap<UUID, BukkitTask> = HashMap()
	val PLAYER_ENTITIES: MutableMap<UUID, MutableMap<UUID, Pair<Int, Int>>> = HashMap()

	fun Material.air(): Boolean = this == Material.AIR || name.endsWith("_AIR")

	fun handleChunks(player: Player, to: Location, reset: Boolean) {
		if (!PLAYERS.contains(player.uniqueId) && !reset) {
			return
		}
//		TaskManager.async.delay({ forceUpdateEntities(player) }, 2)//do this after showing the blocks
		forceUpdateEntities(player)
		val toChunk = to.chunk
		val world = player.world
		for (x in -2..2) {
			for (z in -2..2) {
				val chunk = world.getChunkAt(toChunk.x + x, toChunk.z + z)
//				val snapshot: ChunkSnapshot? = if (atLeast(v1_16)) chunk.chunkSnapshot else null
				val snapshot: ChunkSnapshot? = chunk.chunkSnapshot
				if (reset) { //show all chunks
					TaskManager.async.later { multiBlockChange(snapshot, chunk, player, false) }
//					multiBlockChange(chunk, player, false)
					continue
				}

				if (chunk == toChunk) {
//					multiBlockChange(chunk, player, false)
					TaskManager.async.later { multiBlockChange(snapshot, chunk, player, false) }
				} else {
//					multiBlockChange(chunk, player, true)
					TaskManager.async.later { multiBlockChange(snapshot, chunk, player, true) }
				}
			}
		}
	}

	private fun getBitmask(nmsChunk: Any): Int {
		var bitmask = 0
		for (section in (Chunk_getSections.call(nmsChunk) as Array<*>)) {/*ogNmsChunk.getSections()*/
			if (section != null && !(ChunkSection_c.call(section) as Boolean)) { /*!section.c()*///empty section
				bitmask += 1 shl ((ChunkSection_getYPosition.call(section) as Int) shr 4)/*section.getYPosition()*/
			}
		}

		return bitmask
	}

	private fun generateNMSChunk(ogNMSChunk: Any, chunk: Chunk): Any {
		//create a new protochunk and fill it will all the information from the original chunk
		val proto = ProtoChunk.call(ChunkCoordIntPair.call(chunk.x, chunk.z), Chunk_p.call(ogNMSChunk))
		/*val proto = ProtoChunk(ChunkCoordIntPair(snapshot.x, snapshot.z), ogNmsChunk.p())*/

		//fill it with tile entities and crap if we're sending it to the player

		//chunk status
		ProtoChunk_a_ChunkStatus.call(proto, Chunk_getChunkStatus.call(ogNMSChunk))
		/*proto.a(ogNmsChunk.getChunkStatus())*/

		//light engine
		ProtoChunk_a_LightEngine.call(proto, Chunk_e.call(ogNMSChunk))
		/*proto.a(ogNmsChunk.e())*/

//			proto.b(ogNmsChunk.v());    //

		(Chunk_tileEntities.get(ogNMSChunk) as Map<*, *>).forEach { (bp, tile) ->  //handle tile entities
			ProtoChunk_setType.call(proto, bp, Chunk_getType.call(ogNMSChunk, bp), false)
			/*proto.setType(bp, ogNmsChunk.getType(bp), false)*/

			ProtoChunk_setTileEntity.call(proto, bp, tile)
			/*proto.setTileEntity(bp, tile)*/
		}

		//create a new nmschunk with the manually shallow-cloned protochunk
		return Chunk.call(CraftWorld_getHandle.call(chunk.world), proto)/*Chunk((chunk.world as CraftWorld).getHandle(), proto)*/
	}

	private fun convertNMSChunk(snapshot: ChunkSnapshot?, nmsChunk: Any, ogNMSChunk: Any) {
		for (x in 0..15) {
			for (z in 0..15) {
				for (y in 0..255) {
//					val type = chunk.getBlock(x, y, z).type
//					val type =

					val type = snapshot?.getBlockType(x, y, z) ?: let {
						val name = getType(ogNMSChunk, x, y, z).toString().split("minecraft:")[1].split("}")[0]
						MATERIAL_CACHE[name, { Material.getMaterial(name.toUpperCase()) }]//Block{minecraft:material}...
					}

					if (type.isSolid) { //solid -> barrier
						setBlock(nmsChunk, BARRIER_IBLOCKDATA, x, y, z)
					} else if (!type.air()) { //liquids, grass, and other misc blocks -> air explicitly
						setBlock(nmsChunk, AIR_IBLOCKDATA, x, y, z)
					}
				}
			}
		}
	}

	@SneakyThrows
	private fun multiBlockChange(snapshot: ChunkSnapshot?, chunk: Chunk, player: Player, hide: Boolean) {
		val ogNMSChunk = CraftChunk_getHandle.call(chunk)

		val bitmask = getBitmask(ogNMSChunk)

		if (!hide) {//just map the chunk if we're sending it to the player
			sendPacket(PacketPlayOutMapChunk.call(ogNMSChunk, if (chunk.isLoaded) bitmask else FULL_CHUNK), player)
			return
		}

		val nmsChunk = generateNMSChunk(ogNMSChunk, chunk)

		convertNMSChunk(snapshot, nmsChunk, ogNMSChunk)

		sendPacket(PacketPlayOutMapChunk.call(nmsChunk, if (chunk.isLoaded) bitmask else FULL_CHUNK), player)
	}

	fun getChunkSection(y: Int): Any {
		return ChunkSection.call(y and CHUNK_SECTION)
	}

	@SneakyThrows
	fun setBlock(nmsChunk: Any, iBlockData: Any, x: Int, y: Int, z: Int) {
		var chunkSection = (Chunk_getSections.call(nmsChunk) as Array<*>)[y shr CHUNK_COORD]
		if ( /*chunkSection == a.call(nmsChunk) ||*/chunkSection == null) {
			chunkSection = getChunkSection(y and CHUNK_SECTION)
			NoReflectionUtil.setMethodArrayOutput(y shr CHUNK_COORD, chunkSection, Chunk_getSections, nmsChunk)
//			(Chunk_getSections.call(nmsChunk) as Array<*>)[y shr CHUNK_COORD] = chunkSection//kotlin not letting me
		}
		// 0000 [0000] last 4 are used to determine chunk-relative location
		// >> 4 shifts them out and gets chunk location (x and z); & 15 (1111) masks them for when you set it in the chunk
		ChunkSection_setType.call(chunkSection, x and REL_CHUNK, y and REL_CHUNK, z and REL_CHUNK, iBlockData)
	}


	fun forceUpdateEntity(entity: Entity, player: Player) {
		val packet: MutableList<Any> = ArrayList()
		if (entity.location.chunk != player.location.chunk && PLAYERS.contains(player.uniqueId)) {
			packet.add(PacketPlayOutEntityDestroy.call(intArrayOf(entity.entityId)))
		} else {
			when (entity) {
				is HumanEntity -> {
					packet.add(PacketPlayOutNamedEntitySpawn.call(CraftHumanEntity_getHandle.call(entity)))
				}
				is LivingEntity -> {
					packet.add(PacketPlayOutSpawnEntityLiving.call(CraftLivingEntity_getHandle.call(entity)))
				}
				else            -> {
					packet.add(PacketPlayOutSpawnEntity.call(CraftEntity_getHandle.call(entity)))
				}
			}
			if (entity is LivingEntity) {
				val nmsEntity = CraftLivingEntity_getHandle.call(entity)

				when {
					atLeast(v1_16) -> {
						val equipment: MutableList<Any> = mutableListOf()
						/*val equipment: MutableList<Pair<EnumItemSlot, ItemStack>> = ArrayList<Pair<EnumItemSlot, ItemStack>>()*/

						if (entity is HumanEntity) {//add armor
							var e = 2
							for (item in (EntityHuman_getArmorItems.call(CraftHumanEntity_getHandle.call(entity)) as Iterable<*>)) {
								equipment.add(Pair_of.call(ENUMITEMSLOT_VALUES[e], item))
								e++
							}
						}

						equipment.add(Pair_of.call(MAINHAND, EntityLiving_getItemInMainHand.call(nmsEntity)))
						equipment.add(Pair_of.call(OFFHAND, EntityLiving_getItemInOffHand.call(nmsEntity)))
						/*equipment.add(Pair.of(EnumItemSlot.MAINHAND, entityPlayer.getItemInMainHand()))*/
						/*equipment.add(Pair.of(EnumItemSlot.OFFHAND, entityPlayer.getItemInOffHand()))*/

						packet.add(PacketPlayOutEntityEquipment.call(entity.entityId, equipment))
					}
					atLeast(v1_13) -> {
						packet.add(PacketPlayOutEntityEquipment.call(entity.entityId, MAINHAND, EntityLiving_getItemInMainHand.call(nmsEntity)))
						packet.add(PacketPlayOutEntityEquipment.call(entity.entityId, OFFHAND, EntityLiving_getItemInOffHand.call(nmsEntity)))

						if (entity is HumanEntity) {//add armor
							var e = 2
							for (item in (EntityHuman_getArmorItems.call(CraftHumanEntity_getHandle.call(entity)) as Iterable<*>)) {
								packet.add(PacketPlayOutEntityEquipment.call(entity.entityId, ENUMITEMSLOT_VALUES[e], item))
								e++
							}
						}
					}
					else           -> {
						//TODO check 1.12 and below compatibility
//						packet.add(PacketPlayOutEntityEquipment.call(entity.entityId, 0/*mainhand*/, EntityLiving_getItemInMainHand.call(nmsEntity)))
//
//						if (entity is HumanEntity) {//add armor
//							var e = 1
//							for (item in (EntityHuman_getArmorItems.call(CraftHumanEntity_getHandle.call(entity)) as Iterable<*>)) {
//								packet.add(PacketPlayOutEntityEquipment.call(entity.entityId, ENUMITEMSLOT_VALUES[e], item))
//								e++
//							}
//						}
					}
				}
				/*packet.add(PacketPlayOutEntityEquipment(entityPlayer.getId(), equipment))*/
			}

			packet.add(PacketPlayOutEntityHeadRotation.call(CraftEntity_getHandle.call(entity), ((Entity_yaw.get(CraftEntity_getHandle.call(entity)) as Float) * 256 / 360).toInt().toByte()))
			packet.add(PacketPlayOutEntityMetadata.call(entity.entityId, Entity_getDataWatcher.call(CraftEntity_getHandle.call(entity)), true))
		}
		packet.forEach { p: Any -> sendPacket(p, player) }
	}

	@SneakyThrows
	fun forceUpdateEntities(player: Player) {
		val entities = FutureHandler.sync.supply {
			player.world.getNearbyEntities(player.location, 200.0, 125.0, 200.0, null)
		}.get()
		for (entity in entities) {
			if (entity == player || player.vehicle == entity || entity.passengers.contains(player)) {//skip for player or vehicle
				continue
			}
			forceUpdateEntity(entity, player)
		}
	}

	@SneakyThrows
	fun updateEntities(player: Player) {
		val entities = FutureHandler.sync.supply {
			player.world.getNearbyEntities(player.location, 200.0, 125.0, 200.0, null)
		}.get()
		for (entity in entities) {
			if (entity == player || player.vehicle == entity || entity.passengers.contains(player)) {//skip for player or vehicle
				continue
			}
			val pair: Pair<Int, Int>? = PLAYER_ENTITIES.getOrPut(player.uniqueId, { HashMap() })
					.put(entity.uniqueId, Pair(entity.location.chunk.x, entity.location.chunk.z))
			if (pair?.first != entity.location.chunk.x || pair.second != entity.location.chunk.z) { //chunk has not changed
				forceUpdateEntity(entity, player)
			}
		}
	}

	fun getType(nmsChunk: Any, x: Int, y: Int, z: Int): Any {
		val chunkSection = (Chunk_getSections.call(nmsChunk) as Array<*>)[y shr CHUNK_COORD] ?: return AIR_IBLOCKDATA
		// 0000 [0000] last 4 are used to determine chunk-relative location
		// >> 4 shifts them out and gets chunk location (x and z); & 15 (1111) masks them for when you set it in the chunk
		return ChunkSection_getType.call(chunkSection, x and REL_CHUNK, y and REL_CHUNK, z and REL_CHUNK)
	}

	fun sendPacket(packet: Any, player: Player) {
		val nmsPlayer = CraftPlayer_getHandle.call(player)
		val connection = EntityPlayer_playerConnection.get(nmsPlayer)
		PlayerConnection_sendPacket.call(connection, packet)
		/*((CraftPlayer) player).getHandle().playerConnection.sendPacket((Packet<?>) packet);*/
	}

}