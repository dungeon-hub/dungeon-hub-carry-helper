package net.dungeonhub.carryhelper.config.categories

import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.CategoryKt
import net.dungeonhub.carryhelper.DhCarryHelper.MOD_ID

object TicketCategory : CategoryKt("$MOD_ID/tickets") {
    override val name: TranslatableValue
        get() = Literal("Tickets")

    var showClaimedTicketOverlay by boolean("show_claimed_tickets", true) {
        name = Literal("Claimed Ticket Overlay")
        description = Literal("Shows an overlay of your currently claimed tickets.")
    }
}