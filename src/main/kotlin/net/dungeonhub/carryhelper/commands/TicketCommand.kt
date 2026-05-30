package net.dungeonhub.carryhelper.commands

import com.mojang.brigadier.context.CommandContext
import net.dungeonhub.carryhelper.auth.AuthenticationHandler.requireLogin
import net.dungeonhub.carryhelper.features.overlay.TicketOverlayFeature
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object TicketCommand {
    fun executeToggle(context: CommandContext<FabricClientCommandSource>): Int {
        if (!context.requireLogin()) return 0

        TicketOverlayFeature.toggle()

        val message = if (TicketOverlayFeature.isEnabled) {
            "[CH] Ticket overlay enabled"
        } else {
            "[CH] Ticket overlay disabled"
        }

        context.source.sendFeedback(Component.literal(message))
        return 1
    }

    fun executeRefresh(context: CommandContext<FabricClientCommandSource>): Int {
        if (!context.requireLogin()) return 0

        TicketOverlayFeature.refresh()
        context.source.sendFeedback(Component.literal("[CH] Refreshing tickets..."))
        return 1
    }
}