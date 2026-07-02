package net.dungeonhub.carryhelper.features.kuudra

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.carryhelper.service.MojangService
import net.dungeonhub.carryhelper.service.TicketService
import net.dungeonhub.carryhelper.util.MessageUtil.sendDebug
import net.dungeonhub.carryhelper.util.MessageUtil.sendDevDebug
import net.dungeonhub.carryhelper.util.MessageUtil.sendDevError
import net.dungeonhub.carryhelper.util.ScoreboardUtil
import net.dungeonhub.connection.QueueConnection
import net.dungeonhub.model.carry_queue.IngameQueueCreationModel
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.collections.map

object KuudraFeature {
    private val logger = LoggerFactory.getLogger(KuudraFeature::class.java)

    private val endRegex = Regex("^KUUDRA DOWN!")
    private val tierRegex = Regex(" ⏣ Kuudra's Hollow \\(T(?<tier>\\d+)\\)")

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    fun initialize() {
        ClientReceiveMessageEvents.GAME.register { message, _ ->
            val text = message.string.trim()

            if(text.isEmpty()) return@register

            handleMessage(text)
        }
    }

    fun handleMessage(text: String) {
        endRegex.find(text) ?: return

        val tierText = ScoreboardUtil.getAreaLine()?.let {
            val result = tierRegex.find(it) ?: return@let null

            result.groups["tier"]?.value
        }

        val tier = tierText?.let { tier ->
            KuudraTier.fromTier(tier) ?: return@let null
        }

        if (tier == null) {
            logger.sendDevError("[CH] Tried to log a carry, but couldn't read the tier ($tierText).")
            return
        }

        logger.sendDebug("[CH] Completed Kuudra $tier!")

        logCompletedKuudraCarry(tier)
    }

    fun logCompletedKuudraCarry(tier: KuudraTier) {
        val carryType = tier.carryType

        logger.sendDebug("[CH] Trying to log: $carryType")

        val claimedTickets = TicketService.getClaimedTickets() ?: return

        scheduler.launch {
            val users = findKuudraTeam()

            if(users.isEmpty()) {
                logger.sendDevError("[CH] Couldn't find any users in the Kuudra run.")
                return@launch
            }

            val ticketIds = claimedTickets.filter { users.contains(it.user.minecraftId) }.map { it.id }

            if(ticketIds.isEmpty()) {
                logger.sendDevDebug("[CH] Couldn't find any related tickets for the following users in the Kuudra run: $users")
                return@launch
            }

            val createdQueues = QueueConnection.authenticated(AuthenticationHandler).logIngame(IngameQueueCreationModel(
                carryType,
                ticketIds
            ))

            if(createdQueues == null) {
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().gui.chat.addClientSystemMessage(
                        Component.literal("[CH] Unable to automatically log this carry! Please make sure that the ticket is still valid and was setup properly.").setStyle(Style.EMPTY.withColor(ChatFormatting.RED))
                    )
                }

                logger.sendDevDebug("[CH] Unable to log type $carryType for tickets $ticketIds with users: $users")
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
                val message = Component.literal("[CH] Logged ${createdQueues.size} ${if(createdQueues.size == 1) "carry" else "carries"} for ${
                    createdQueues.map {
                        it.player.minecraftId?.let {
                            MojangService.awaitPlayerName(it)
                        } ?: "Unknown"
                    }.joinToString(", ")
                } automatically!").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))

                Minecraft.getInstance().execute {
                    Minecraft.getInstance().gui.chat.addClientSystemMessage(message)
                }
            }
        }
    }

    suspend fun findKuudraTeam(): List<UUID> {
        return ScoreboardUtil.getOnlinePlayers()?.mapNotNull {
            it.profile.name
        }?.map { name ->
            scheduler.async { MojangService.awaitPlayerUuid(name) }
        }?.awaitAll()?.filterNotNull() ?: emptyList()
    }
}