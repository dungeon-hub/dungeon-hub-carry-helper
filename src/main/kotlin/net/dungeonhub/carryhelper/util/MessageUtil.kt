package net.dungeonhub.carryhelper.util

import net.dungeonhub.carryhelper.DhCarryHelper
import net.dungeonhub.carryhelper.config.categories.DevCategory
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.slf4j.Logger

object MessageUtil {
    fun Logger.sendDevError(message: String) {
        if (DhCarryHelper.isDev) {
            throw RuntimeException(message)
        } else if (DevCategory.extendedDebug) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.chat.addClientSystemMessage(
                    Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.RED))
                )
            }
        } else {
            error(message)
        }
    }

    fun Logger.sendDevDebug(message: String) {
        if (DhCarryHelper.isDev || DevCategory.extendedDebug) {
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
        if (DhCarryHelper.isDev || DevCategory.extendedDebug) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.chat.addClientSystemMessage(
                    Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                )
            }
        }

        debug(message)
    }
}