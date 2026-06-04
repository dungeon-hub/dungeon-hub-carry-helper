package net.dungeonhub.carryhelper.logging

import com.mojang.brigadier.context.CommandContext
import net.dungeonhub.carryhelper.auth.AuthenticationHandler.requireLogin
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object LogCommand {
    fun executeLogCommand(context: CommandContext<FabricClientCommandSource>): Int {
        if(!context.requireLogin()) {
            return 1
        }

        context.source.sendFeedback(Component.literal("Manual logging isn't implemented yet. Please use /log on discord manually."))
        return 1
    }
}