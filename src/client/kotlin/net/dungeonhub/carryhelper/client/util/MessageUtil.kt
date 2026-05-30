package net.dungeonhub.carryhelper.client.util

import net.dungeonhub.carryhelper.client.DhCarryHelperClient
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.slf4j.Logger

object MessageUtil {
    fun Logger.sendDevError(message: String) {
        if (DhCarryHelperClient.isDev) {
            throw RuntimeException(message)
        } else {
            println(message)
        }
    }

    fun Logger.sendDevDebug(message: String) {
        if (DhCarryHelperClient.isDev) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.chat.addClientSystemMessage(
                    Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                )
            }
        } else {
            debug(message)
        }
    }

    fun Logger.sendDebug(message: String) {
        if (DhCarryHelperClient.isDev) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.chat.addClientSystemMessage(
                    Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                )
            }
        }

        debug(message)
    }
}