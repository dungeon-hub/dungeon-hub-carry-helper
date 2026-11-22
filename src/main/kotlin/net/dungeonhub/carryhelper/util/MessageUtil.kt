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
                Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(
                    Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.RED))
                )
            }
        } else {
            error(message)
        }
    }

    fun Logger.sendDevInfo(message: String) {
        if (DhCarryHelper.isDev || DevCategory.extendedDebug) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(
                    Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                )
            }
        } else {
            info(message)
        }
    }

    fun Logger.sendInfo(message: String) {
        if (DhCarryHelper.isDev || DevCategory.extendedDebug) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(
                    Component.literal(message).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                )
            }
        }

        info(message)
    }

    /**
     * Sends a chat message and optionally runs additional logic on the main thread.
     * Both the message display and the additionalLogic callback run inside Minecraft.execute {},
     * ensuring they execute on the main Minecraft thread.
     */
    fun Minecraft.sendMessage(component: Component, additionalLogic: () -> Unit = {}) {
        execute {
            gui.hud.chat.addClientSystemMessage(component)
            additionalLogic()
        }
    }
}