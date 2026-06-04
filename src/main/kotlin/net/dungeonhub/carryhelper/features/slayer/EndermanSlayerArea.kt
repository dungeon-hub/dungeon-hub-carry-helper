package net.dungeonhub.carryhelper.features.slayer

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import kotlin.text.contains

enum class EndermanSlayerArea(val areaName: String) {
    Sepulture("Void Sepulture"),
    Bruiser("Zealot Bruiser Hideout");

    companion object {
        fun getArea(): EndermanSlayerArea? {
            val scoreboard = Minecraft.getInstance().level?.scoreboard ?: return null
            val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null

            val scores = scoreboard.listPlayerScores(objective)

            val scoreboardLines = scores.map {
                val team = scoreboard.getPlayersTeam(it.owner) ?: return null

                team.playerPrefix.string + team.playerSuffix.string
            }

            // The format looks like:
            //  §7⏣ §dThe End
            val areaLine = scoreboardLines.firstOrNull { it.contains("⏣") } ?: return null

            for(possibleArea in EndermanSlayerArea.entries) {
                if(areaLine.endsWith(possibleArea.areaName)) {
                    return possibleArea
                }
            }

            return null
        }
    }
}