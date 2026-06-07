package net.dungeonhub.carryhelper.features.overlay

import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.carryhelper.config.categories.TicketCategory
import net.dungeonhub.carryhelper.service.MojangService
import net.dungeonhub.carryhelper.service.TicketService
import net.dungeonhub.model.ticket.TicketModel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object TicketOverlayFeature {
    private const val OVERLAY_X = 10
    private const val OVERLAY_Y = 10
    private const val LINE_HEIGHT = 10
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val BACKGROUND_COLOR = 0x80000000.toInt()

    fun render(guiGraphics: GuiGraphicsExtractor) {
        if (Minecraft.getInstance().options.hideGui) return
        if (!TicketCategory.showClaimedTicketOverlay) return
        if (!AuthenticationHandler.isValid()) return

        val tickets = TicketService.getClaimedTickets()?.filter { it.ticketPanel.relatedCarryTier != null }
        if (tickets == null) {
            if (TicketService.isFetchingTickets()) {
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
        val channelName = ticket.channel?.name ?: "unknown"

        val carryTier = ticket.ticketPanel.relatedCarryTier!!
        val carryTierName = ticket.ticketPanel.relatedCarryTier?.descriptiveName ?: "Unknown"

        val playerUuid = ticket.user.minecraftId
        val playerName = if(playerUuid != null) {
            MojangService.getPlayerName(playerUuid) ?: playerUuid.toString().substring(0, 8)
        } else {
            "Unknown"
        }
        val amount = ticket.formResponses.firstOrNull { it.customId == "carry-amount" }?.value ?: "?"

        val carryDifficulty = ticket.formResponses.firstOrNull { formResponse ->
            formResponse.customId == "carry-difficulty"
        }?.value?.let { carryDifficultyIdentifier ->
            TicketService.carryDifficulties.getOrDefault(carryTier.id, emptyList()).firstOrNull {
                it.identifier == carryDifficultyIdentifier
            }?.displayName ?: carryDifficultyIdentifier
        } ?: ticket.ticketPanel.relatedCarryDifficulty?.displayName
        ?: "?"

        return "$channelName: $playerName wants $amount $carryTierName - $carryDifficulty"
    }

    fun refresh() {
        TicketService.refresh()
    }
}