package net.dungeonhub.carryhelper.util

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType

object ScoreboardUtil {
    fun getScoreboard(): List<Component>? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return null
        val onlinePlayers = player.connection.onlinePlayers.filter {
            it.gameMode != GameType.SPECTATOR
        }.sortedBy { it.team?.name ?: "" }
        return onlinePlayers.map { minecraft.gui.tabList.getNameForDisplay(it) }
    }
}