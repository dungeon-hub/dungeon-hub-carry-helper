package net.dungeonhub.carryhelper.util

import com.google.common.collect.ComparisonChain
import com.google.common.collect.Ordering
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import kotlin.text.contains

object ScoreboardUtil {
    // Credit: SkyHanni
    private val playerOrdering = Ordering.from(TabPlayerComparator())
    internal class TabPlayerComparator : Comparator<PlayerInfo> {
        override fun compare(o1: PlayerInfo, o2: PlayerInfo): Int = ComparisonChain.start()
            .compareTrueFirst(o1.gameMode != GameType.SPECTATOR, o2.gameMode != GameType.SPECTATOR)
            .compare(o1.team?.name.orEmpty(), o2.team?.name.orEmpty())
            .compare(o1.profile.name, o2.profile.name).result()
    }

    fun getOnlinePlayers(): List<PlayerInfo>? {
        val player = Minecraft.getInstance().player ?: return null

        return player.connection.listedOnlinePlayers.filter {
            it.gameMode != GameType.SPECTATOR
        }.sortedBy { it.team?.name ?: "" }.filter { it.profile.id.version() == 4 }
    }

    fun getTabList(): List<Component>? {
        val player = Minecraft.getInstance().player ?: return null

        val result = playerOrdering.sortedCopy(player.connection.onlinePlayers).map {
            Minecraft.getInstance().gui.tabList.getNameForDisplay(it)
        }

        return if (result.size < 80) result
        else result.subList(0, 80)
    }

    fun getOnlinePlayersDisplayNames(): List<Component>? {
        return getOnlinePlayers()?.map { Minecraft.getInstance().gui.tabList.getNameForDisplay(it) }
    }

    fun getScoreboardLines(): Collection<PlayerScoreEntry>? {
        val scoreboard = Minecraft.getInstance().level?.scoreboard ?: return null
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null

        return scoreboard.listPlayerScores(objective)
    }

    fun getAreaLine(): String? {
        val scoreboard = Minecraft.getInstance().level?.scoreboard ?: return null

        val scoreboardLines = getScoreboardLines()?.mapNotNull {
            val team = scoreboard.getPlayersTeam(it.owner) ?: return@mapNotNull null

            team.playerPrefix.string + it.owner + team.playerSuffix.string
        }

        // The format looks like:
        //  §7⏣ §dThe End
        return scoreboardLines?.firstOrNull { it.contains("\uE067") }
    }
}