package net.dungeonhub.carryhelper.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.model.carry_difficulty.CarryDifficultyModel
import net.dungeonhub.model.ticket.TicketModel
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// TODO use carryDifficultyConnection.findCarryDifficultyByString(value) or even better carryDifficultyConnection.getByIdentifier(value) to load the carry difficulty and use the proper display name instead of just the identifier
object TicketService {
    private var claimedTickets: List<TicketModel>? = null
    var carryDifficulties: MutableMap<Long, List<CarryDifficultyModel>> = ConcurrentHashMap()
        private set
    private var lastFetchTime: Instant = Clock.System.now()
    private var isFetching = false
    private var isRunning = false

    private const val FETCH_INTERVAL = 15

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    fun initialize() {
        if (isRunning) return
        isRunning = true

        scheduler.launch {
            while (isRunning) {
                if (AuthenticationHandler.isValid()) {
                    fetchTickets()
                }

                delay(FETCH_INTERVAL.seconds)
            }
        }
    }

    fun shutdown() {
        isRunning = false
    }

    fun getClaimedTickets(): List<TicketModel>? = claimedTickets

    fun isFetchingTickets(): Boolean = isFetching

    private fun fetchTickets() {
        if (isFetching) return
        if (!AuthenticationHandler.isValid()) return

        isFetching = true
        scheduler.launch {
            try {
                val tickets = DiscordUserConnection.authenticated(AuthenticationHandler).getMyClaimedTickets() ?: emptyList()

                for(newTicket in tickets) {
                    if(claimedTickets != null && newTicket.id !in claimedTickets!!.map { it.id }) {
                        val ign = newTicket.user.minecraftId?.let {
                            MojangService.getPlayerName(it)
                        } ?: continue

                        // A lot of credit and thanks to StrykerAW (383819291687911424) for the suggestion!
                        Minecraft.getInstance().execute {
                            Minecraft.getInstance().gui.chat.addClientSystemMessage(
                                Component.literal("[CH] You claimed a new ticket from $ign! Click here to invite them to a party.").setStyle(
                                    Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent.SuggestCommand("/party invite $ign"))
                                )
                            )
                        }
                    }
                }

                claimedTickets = tickets
                lastFetchTime = Clock.System.now()
            } catch (e: Exception) {
                e.printStackTrace()
                claimedTickets = emptyList()
            } finally {
                isFetching = false
            }

            for(ticket in claimedTickets ?: emptyList()) {
                val carryTier = ticket.ticketPanel.relatedCarryTier ?: continue

                if(!carryDifficulties.containsKey(carryTier.id)) {
                    val loadedCarryDifficulties = CarryDifficultyConnection.Companion[carryTier].authenticated(
                        AuthenticationHandler
                    ).getAllCarryDifficulties() ?: continue
                    carryDifficulties[carryTier.id] = loadedCarryDifficulties
                }
            }
        }
    }

    fun refresh() {
        claimedTickets = null
        fetchTickets()
    }
}