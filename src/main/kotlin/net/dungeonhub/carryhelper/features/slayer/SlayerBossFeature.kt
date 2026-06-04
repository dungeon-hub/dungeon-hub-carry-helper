package net.dungeonhub.carryhelper.features.slayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.carryhelper.features.slayer.SlayerBoss.Companion.findSlayerType
import net.dungeonhub.carryhelper.service.MojangService
import net.dungeonhub.carryhelper.service.TicketService
import net.dungeonhub.carryhelper.util.MessageUtil.sendDebug
import net.dungeonhub.carryhelper.util.MessageUtil.sendDevDebug
import net.dungeonhub.carryhelper.util.MessageUtil.sendDevError
import net.dungeonhub.connection.QueueConnection
import net.dungeonhub.enums.IngameCarryType
import net.dungeonhub.model.carry_queue.IngameQueueCreationModel
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

object SlayerBossFeature {
    private val logger = LoggerFactory.getLogger(SlayerBossFeature::class.java)

    private var currentWorld: ClientLevel? = null
    private var slayerBosses = mutableSetOf<SlayerBoss>()

    private var lastClickedEntity: Entity? = null
    private var lastHitCarrierBoss = false

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    fun handleLeftClick(hitResult: HitResult) {
        if(hitResult.type == HitResult.Type.ENTITY) {
            handleHitEntity((hitResult as EntityHitResult).entity)
        }
    }

    fun handleHitEntity(entity: Entity) {
        lastClickedEntity = entity
        lastHitCarrierBoss = false

        val armorStands = getEntityArmorStands(entity)
        val bossType = findSlayerType(armorStands) ?: return

        val slayerBosses = SlayerBoss(entity, bossType, armorStands)

//        val bossType = foundArmorStands.filter { it.name.string.contains("༕ ☠ Revenant Horror I 500❤") }
        val slayerSpawner = slayerBosses.spawner ?: return

        lastHitCarrierBoss = true
//        lastHitCarrierBoss = CarryTracker.isCustomer(mob.ownerNameOrEmpty)
    }

    fun onSlayerDeath(slayerBoss: SlayerBoss) {
        if(slayerBoss.spawner == null) return

        logCompletedSlayerCarry(slayerBoss)
    }

    fun onTick() {
        if(currentWorld != null && currentWorld != Minecraft.getInstance().level) {
            slayerBosses.clear()
            currentWorld = Minecraft.getInstance().level
            return
        } else if(currentWorld == null) {
            currentWorld = Minecraft.getInstance().level
        }

        val currentWorld = currentWorld ?: return
        val allVisibleEntities = currentWorld.entitiesForRendering().filter { it !is ArmorStand }

        val currentSlayerBosses = mutableSetOf<SlayerBoss>()

        for(entity in allVisibleEntities) {
            val armorStands = getEntityArmorStands(entity)

            val slayerBossType = findSlayerType(armorStands) ?: continue

            currentSlayerBosses.add(SlayerBoss(entity, slayerBossType, armorStands))
        }

        val area = EndermanSlayerArea.getArea()

        if(area != null) {
            currentSlayerBosses.forEach { it.area = area }
        }

        newSlayerBosses(currentSlayerBosses)
    }

    fun newSlayerBosses(newSlayerBosses: Set<SlayerBoss>) {
        for(boss in slayerBosses) {
//            if(!newSlayerBosses.any { it == boss }) {
            if(boss !in newSlayerBosses) {
                if(boss.entity.distanceTo(Minecraft.getInstance().player!!) > 50) continue

                onSlayerDeath(boss)
            }
        }

        slayerBosses.clear()
        slayerBosses.addAll(newSlayerBosses)
    }

    fun getEntityArmorStands(entity: Entity): Set<ArmorStand> {
        var entityId = entity.id
        val world = entity.level()

        val foundArmorStands = mutableSetOf<ArmorStand>()
        while(true) {
            val entity = world.getEntity(++entityId) ?: break

            if(entity !is ArmorStand) break

            foundArmorStands.add(entity)
        }

        return foundArmorStands
    }

    fun resolveIngameCarryType(slayerBoss: SlayerBoss): IngameCarryType {
        if (slayerBoss.slayerBossType == SlayerBossType.Voidgloom3) {
            if (slayerBoss.area == EndermanSlayerArea.Sepulture) {
                return IngameCarryType.Voidgloom3Sepulture
            } else if (slayerBoss.area == EndermanSlayerArea.Bruiser) {
                return IngameCarryType.Voidgloom3Bruiser
            }
        } else if (slayerBoss.slayerBossType == SlayerBossType.Voidgloom4) {
            if (slayerBoss.area == EndermanSlayerArea.Sepulture) {
                return IngameCarryType.Voidgloom4Sepulture
            } else if (slayerBoss.area == EndermanSlayerArea.Bruiser) {
                return IngameCarryType.Voidgloom4Bruiser
            }
        }

        return slayerBoss.slayerBossType.ingameCarryType
    }

    fun logCompletedSlayerCarry(slayerBoss: SlayerBoss) {
        val spawner = slayerBoss.spawner

        val ingameCarryType = resolveIngameCarryType(slayerBoss)

        if(spawner == null) {
            logger.sendDevDebug("[CH] Slayer with no spawner has been tried to log!")
            return
        }

        logger.sendDebug("[CH] Slayer boss from ${slayerBoss.spawner} was killed!")

        val claimedTickets = TicketService.getClaimedTickets() ?: return

        scheduler.launch {
            val carriedUser = MojangService.getPlayerUuid(spawner)

            if(carriedUser == null) {
                logger.sendDevError("Couldn't load user for slayer spawner: $spawner")
                return@launch
            }

            val ticketIds = TicketService.getClaimedTickets()?.let {
                claimedTickets.filter { carriedUser == it.user.minecraftId }.map { it.id }
            } ?: return@launch

            if(ticketIds.isEmpty()) {
                logger.sendDevDebug("[CH] Couldn't find a related ticket for killed slayer from $spawner!")
                return@launch
            }

            val createdQueues = QueueConnection.authenticated(AuthenticationHandler).logIngame(IngameQueueCreationModel(
                ingameCarryType,
                ticketIds
            ))

            if(createdQueues == null) {
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().gui.chat.addClientSystemMessage(
                        Component.literal("[CH] Unable to automatically log this carry! Please make sure that the ticket is still valid and was setup properly.").setStyle(Style.EMPTY.withColor(ChatFormatting.RED))
                    )
                }

                logger.sendDevDebug("[CH] Unable to log type ${slayerBoss.slayerBossType} for tickets $ticketIds with spawner $spawner and entity ${slayerBoss.entity}")
                return@launch
            }

            if(createdQueues.size != ticketIds.size) {
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().gui.chat.addClientSystemMessage(
                        Component.literal("[CH] Only ${createdQueues.size} of the following $ticketIds carries were logged! Please check the related tickets manually.").setStyle(Style.EMPTY.withColor(ChatFormatting.RED))
                    )
                }

                logger.sendDevDebug("[CH] For the ticket $ticketIds, only the following carry queues were added:${
                    createdQueues.map { "#${it.id}: ${it.amount} ${it.carryDifficulty.displayName} (${it.carryDifficulty.identifier} #${it.carryDifficulty.id}) related to ${it.relationId}" }
                }")
            } else {
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().gui.chat.addClientSystemMessage(
                        Component.literal("[CH] Logged ${createdQueues.size} ${if(createdQueues.size == 1) "carry" else "carries"} for ${
                            createdQueues.joinToString(", ") {
                                it.player.minecraftId?.let {
                                    MojangService.getPlayerName(it)
                                } ?: "Unknown"
                            }
                        } automatically!").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))
                    )
                }
            }
        }
    }
}