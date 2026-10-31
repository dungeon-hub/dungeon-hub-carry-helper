package net.dungeonhub.carryhelper.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.ChangeStreamConnection
import net.dungeonhub.connection.ChangeStreamConnection.ChangeStreamEvent
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.enums.ChangeEventType
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

object TicketService {
    private var claimedTickets: List<TicketModel>? = null
    var carryDifficulties: MutableMap<Long, List<CarryDifficultyModel>> = ConcurrentHashMap()
        private set
    private var lastFetchTime: Instant = Clock.System.now()
    private var isFetching = false
    private var jobs: List<Job> = emptyList()

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    /**
     * Requests to reload the claimed tickets. Requests are conflated, so a request made while the tickets are loaded
     * causes exactly one more reload afterward, and no change is missed.
     */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    /**
     * How often it is checked whether the user logged in or out, which decides whether the change stream is used.
     */
    private val loginCheckInterval = 1.seconds

    fun initialize() {
        if (jobs.isNotEmpty()) return

        jobs = listOf(
            scheduler.launch {
                while (true) {
                    refreshRequests.receive()
                    fetchTickets()
                }
            },
            scheduler.launch {
                listenToTicketChanges()
            }
        )
    }

    fun shutdown() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    /**
     * Reloads the claimed tickets whenever a ticket the user claims (or claimed before) changes.
     * The change stream only sends changes of tickets the user is allowed to see.
     */
    private suspend fun listenToTicketChanges() {
        while (true) {
            if (!AuthenticationHandler.isValid()) {
                delay(loginCheckInterval)
                continue
            }

            coroutineScope {
                val listener = launch {
                    ChangeStreamConnection.authenticated(AuthenticationHandler)
                        .changes(setOf(ChangeEventType.Ticket))
                        .collect { event ->
                            when (event) {
                                // if the stream couldn't be resumed, changes might have been missed
                                is ChangeStreamEvent.Connected -> if (!event.resumed) requestRefresh()
                                is ChangeStreamEvent.Change -> requestRefresh()
                            }
                        }
                }

                // the login state has no events, so it's checked regularly to stop listening after a logout
                while (AuthenticationHandler.isValid()) {
                    delay(loginCheckInterval)
                }

                listener.cancel()
            }
        }
    }

    private fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    fun getClaimedTickets(): List<TicketModel>? = claimedTickets

    fun isFetchingTickets(): Boolean = isFetching

    private suspend fun fetchTickets() {
        if (!AuthenticationHandler.isValid()) return

        isFetching = true
        try {
            val tickets = DiscordUserConnection.authenticated(AuthenticationHandler).getMyClaimedTickets() ?: emptyList()

            for(newTicket in tickets) {
                if(claimedTickets != null && newTicket.id !in claimedTickets!!.map { it.id }) {
                    val uuid = newTicket.user.minecraftId ?: continue
                    if(Minecraft.getInstance().isLocalPlayer(uuid)) continue

                    val ign = MojangService.awaitPlayerName(uuid) ?: continue

                    // A lot of credit and thanks to StrykerAW (383819291687911424) for the suggestion!
                    Minecraft.getInstance().execute {
                        Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(
                            Component.literal("[CH] You claimed a new ticket from $ign! Click here to invite them to a party.").setStyle(
                                Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(ClickEvent.SuggestCommand("/party invite $ign"))
                            )
                        )
                    }
                }
            }

            claimedTickets = tickets
            lastFetchTime = Clock.System.now()
        } catch (e: CancellationException) {
            throw e
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

    fun refresh() {
        claimedTickets = null
        requestRefresh()
    }
}