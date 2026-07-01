package net.dungeonhub.carryhelper.features.dungeons

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
import kotlin.collections.joinToString
import kotlin.collections.map

object DungeonsFeature {
    private val logger = LoggerFactory.getLogger(DungeonsFeature::class.java)

    private val floorRegex = Regex("(Master Mode The Catacombs|The Catacombs) - (Entrance|Floor [IV]{1,3})")
    private val scoreRegex = Regex("Team Score: (\\d{1,3}) \\((S\\+|S|A|B|C|D)\\)")
    private val defeatedRegex = Regex("☠ Defeated (The Watcher|Bonzo|Scarf|The Professor|Thorn|Livid|Sadan|Maxor, Storm, Goldor, and Necron) in ")
    // Credit: SkyHanni
    private val dungeonPlayerRegex = Regex("^(?<sbLevel>\\[\\d+]) (?<rank>\\[[^]]+])? ?(?<playerName>\\S+)\\s?(?<symbols>[^(]*) \\((?:(?<className>\\S+) (?<classLevel>[CLXVI0]+)|(?<playerDead>DEAD))\\)\$")

    private var floorLastMessage = false
    private var scoreLastMessage = false

    private var lastType: String? = null
    private var lastFloor: String? = null
    private var lastScore: String? = null

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
        if(scoreLastMessage) {
            scoreLastMessage = false

            handleDefeatedMessage(text)
        }

        if(floorLastMessage) {
            floorLastMessage = false

            handleScoreMessage(text)
        }

        handleFloorMessage(text)
    }

    fun logCompletedCatacombsCarry() {
        if(lastType == null || lastFloor == null || lastScore == null) {
            logger.sendDevError("[CH] Tried to log a carry, but couldn't read the type ($lastType), floor ($lastFloor) or score ($lastScore).")
            return
        }

        val dungeonFloor = DungeonFloor.getFromName(lastType ?: return, lastFloor ?: return)

        if(dungeonFloor == null) {
            logger.sendDevError("[CH] Tried to log a carry, but couldn't read the type ($lastType) or floor ($lastFloor).")
            return
        }

        val carryType = dungeonFloor.getCarryType(lastScore ?: return)

        logger.sendDebug("[CH] Trying to log: $carryType")

        val claimedTickets = TicketService.getClaimedTickets() ?: return

        scheduler.launch {
            val users = findDungeonTeam()

            if(users.isEmpty()) {
                logger.sendDevError("[CH] Couldn't find any users in the dungeon.")
                return@launch
            }

            val ticketIds = claimedTickets.filter { users.contains(it.user.minecraftId) }.map { it.id }

            if(ticketIds.isEmpty()) {
                logger.sendDevDebug("[CH] Couldn't find any related tickets for the following users in the dungeon: ${users.joinToString { MojangService.getPlayerName(it) ?: it.toString() }}")
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

    suspend fun findDungeonTeam(): List<UUID> {
        return ScoreboardUtil.getScoreboard()?.mapNotNull {
            val result = dungeonPlayerRegex.find(it.string) ?: return@mapNotNull null

            result.groups["playerName"]?.value
        }?.map { name ->
            scheduler.async { MojangService.awaitPlayerUuid(name) }
        }?.awaitAll()?.filterNotNull() ?: emptyList()
    }

    fun handleDefeatedMessage(text: String) {
        val result = defeatedRegex.find(text)
        if(result == null) {
            logger.sendDevDebug("[CH] Tried to log a carry, but it seems like the dungeon boss wasn't beaten: $text")
            lastScore = null
            lastType = null
            lastFloor = null

            return
        }

        logger.sendDebug("[CH] The dungeon boss was defeated, trying to log the carry")

        logCompletedCatacombsCarry()
    }

    fun handleScoreMessage(text: String) {
        val result = scoreRegex.find(text)
        if(result == null) {
            logger.sendDevError("[CH] Expected a score message, but the message ($lastType $lastFloor) didn't fit: $text")
            logger.sendDevError("[CH] If you didn't expect this, please report this!")
            lastType = null
            lastFloor = null

            return
        }

        val score = result.groupValues.getOrNull(2)

        scoreLastMessage = true
        lastScore = score

        logger.sendDebug("[CH] Completed the floor with $score")
    }

    fun handleFloorMessage(text: String) {
        val result = floorRegex.matchEntire(text) ?: return

        val type = result.groupValues.getOrNull(1) ?: return
        val floor = result.groupValues.getOrNull(2) ?: return

        floorLastMessage = true
        lastType = type
        lastFloor = floor

        logger.sendDebug("[CH] Completed $type floor $floor!")
    }
}