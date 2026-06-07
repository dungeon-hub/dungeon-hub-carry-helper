package net.dungeonhub.carryhelper.config.categories

import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.CategoryKt
import net.dungeonhub.carryhelper.DhCarryHelper.MOD_ID

object TicketCategory : CategoryKt("$MOD_ID/tickets") {
    val ticketRefreshCooldownRange = 5..60

    override val name: TranslatableValue
        get() = Literal("Tickets")

    var showClaimedTicketOverlay by boolean("show_claimed_tickets", true) {
        name = Literal("Claimed Ticket Overlay")
        description = Literal("Shows an overlay of your currently claimed tickets.")
    }

    var ticketRefreshCooldown by int("ticket_refresh_cooldown", 15) {
        name = Literal("Ticket refresh cooldown")
        description = Literal("Change after how many seconds the tickets are reloaded from discord. A higher value means that you use more bandwidth, but you will also see updates slower.")
        range = ticketRefreshCooldownRange
        slider = true
    }
}