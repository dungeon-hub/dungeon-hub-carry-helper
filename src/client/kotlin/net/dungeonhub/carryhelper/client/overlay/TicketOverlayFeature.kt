package net.dungeonhub.carryhelper.client.overlay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dungeonhub.carryhelper.client.auth.AuthenticationHandler
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.model.ticket.TicketModel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

object TicketOverlayFeature {
    private var claimedTickets: List<TicketModel>? = null
    private var lastFetchTime: Instant = Clock.System.now()
    private var isFetching = false
    var isEnabled = true

    private const val FETCH_INTERVAL = 15
    private const val OVERLAY_X = 10
    private const val OVERLAY_Y = 10
    private const val LINE_HEIGHT = 10
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val BACKGROUND_COLOR = 0x80000000.toInt()

    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val usernameCache = ConcurrentHashMap<UUID, String>()
    private val fetchingUsernames = ConcurrentHashMap<UUID, Boolean>()

    fun render(guiGraphics: GuiGraphicsExtractor) {
        if (!isEnabled) return
        if (!AuthenticationHandler.isValid()) return

        // Auto-fetch if needed
        val currentTime = Clock.System.now()
        if (claimedTickets == null || (currentTime - lastFetchTime > FETCH_INTERVAL.seconds)) {
            fetchTickets()
        }

        val tickets = claimedTickets?.filter { it.ticketPanel.relatedCarryTier != null }
        if (tickets == null) {
            if (isFetching) {
                renderText(guiGraphics, "Loading tickets...", OVERLAY_X, OVERLAY_Y)
            }
            return
        }

        if (tickets.isEmpty()) {
            renderText(guiGraphics, "No claimed carry tickets", OVERLAY_X, OVERLAY_Y)
            return
        }

        // Render header
        var yOffset = OVERLAY_Y

        renderText(guiGraphics, "Claimed Tickets (${tickets.size}):", OVERLAY_X, yOffset)
        yOffset += LINE_HEIGHT + 2

        // Render each ticket
        tickets.forEach { ticket ->
            val displayText = "-> " + formatTicketName(ticket)
            renderText(guiGraphics, displayText, OVERLAY_X, yOffset)
            yOffset += LINE_HEIGHT + 2
        }
    }

    private fun renderText(guiGraphics: GuiGraphicsExtractor, text: String, x: Int, y: Int) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val textWidth = font.width(text)

        // Draw background
        guiGraphics.fill(x - 2, y - 2, x + textWidth + 2, y + LINE_HEIGHT, BACKGROUND_COLOR)

        // Draw text
        guiGraphics.text(font, text, x, y, TEXT_COLOR)
    }

    private fun formatTicketName(ticket: TicketModel): String {
        val playerName = getPlayerName(ticket.user.minecraftId)
        val amount = ticket.formResponses.firstOrNull { it.customId == "carry-amount" }?.value ?: "?"
        val carryDifficulty = ticket.formResponses.firstOrNull { it.customId == "carry-difficulty" }?.value // TODO maybe make it possible to load the carry difficulty from the ticket instead --> nullable field?
            ?: ticket.ticketPanel.relatedCarryDifficulty?.displayName
            ?: "?"
        val carryTier = ticket.ticketPanel.relatedCarryTier?.descriptiveName ?: "Unknown"

        return "$playerName: $amount $carryTier - $carryDifficulty"
    }

    private fun getPlayerName(uuid: UUID?): String {
        if (uuid == null) return "Unknown"

        // Check cache first
        usernameCache[uuid]?.let { return it }

        // Trigger async fetch if not already fetching
        if (fetchingUsernames.putIfAbsent(uuid, true) == null) {
            fetchUsername(uuid)
        }

        // Return truncated UUID while loading
        return uuid.toString().substring(0, 8)
    }

    private fun fetchUsername(uuid: UUID) {
        AuthenticationHandler.scheduler.launch {
            try {
                val uuidString = uuid.toString().replace("-", "")
                val url = "https://api.minecraftservices.com/minecraft/profile/lookup/$uuidString"

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }

                if (response.statusCode() == 200) {
                    val profile = json.decodeFromString<MojangProfile>(response.body())
                    usernameCache[uuid] = profile.name
                } else {
                    usernameCache[uuid] = uuid.toString().substring(0, 8)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                usernameCache[uuid] = uuid.toString().substring(0, 8)
            } finally {
                fetchingUsernames.remove(uuid)
            }
        }
    }

    @Serializable
    data class MojangProfile(
        val id: String,
        val name: String
    )

    fun fetchTickets() {
        if (isFetching) return
        if (!AuthenticationHandler.isValid()) return

        isFetching = true
        AuthenticationHandler.scheduler.launch {
            try {
                val tickets = DiscordUserConnection.authenticated(AuthenticationHandler).getMyClaimedTickets()
                claimedTickets = tickets
                lastFetchTime = Clock.System.now()
            } catch (e: Exception) {
                e.printStackTrace()
                claimedTickets = emptyList()
            } finally {
                isFetching = false
            }
        }
    }

    fun toggle() {
        isEnabled = !isEnabled
    }

    fun refresh() {
        claimedTickets = null
        lastFetchTime = Clock.System.now()
        fetchTickets()
    }
}
