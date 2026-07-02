package net.dungeonhub.carryhelper.util

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import kotlin.text.contains

object ScoreboardUtil {
    fun getOnlinePlayers(): List<PlayerInfo>? {
        val player = Minecraft.getInstance().player ?: return null

        return player.connection.listedOnlinePlayers.filter {
            it.gameMode != GameType.SPECTATOR
        }.sortedBy { it.team?.name ?: "" }.filter { it.profile.id.version() == 4 }
    }

    fun getTabPlayersDisplayNames(): List<Component>? {
        return getOnlinePlayers()?.map { Minecraft.getInstance().gui.tabList.getNameForDisplay(it) }
    }

    fun getScoreboardLines(): Collection<PlayerScoreEntry>? {
        val scoreboard = Minecraft.getInstance().level?.scoreboard ?: return null
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null

        return scoreboard.listPlayerScores(objective)
    }

    fun getAreaLine(): String? {
        val scoreboard = Minecraft.getInstance().level?.scoreboard ?: return null

        val scoreboardLines = getScoreboardLines()?.map {
            val team = scoreboard.getPlayersTeam(it.owner) ?: return null

            team.playerPrefix.string + team.playerSuffix.string
        }

        // The format looks like:
        //  §7⏣ §dThe End
        return scoreboardLines?.firstOrNull { it.contains("⏣") }
    }
}