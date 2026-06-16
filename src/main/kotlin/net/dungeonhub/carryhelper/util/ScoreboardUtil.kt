package net.dungeonhub.carryhelper.util

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType

object ScoreboardUtil {
    fun getScoreboard(): List<Component>? {
        val player = Minecraft.getInstance().player ?: return null
        val onlinePlayers = player.connection.onlinePlayers.filter {
            it.gameMode != GameType.SPECTATOR
        }.sortedBy { it.team?.name ?: "" }
        return onlinePlayers.map { Minecraft.getInstance().gui.tabList.getNameForDisplay(it) }
    }
}