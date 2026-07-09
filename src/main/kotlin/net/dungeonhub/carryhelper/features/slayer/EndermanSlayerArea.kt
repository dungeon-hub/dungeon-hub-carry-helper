package net.dungeonhub.carryhelper.features.slayer

import net.dungeonhub.carryhelper.util.ScoreboardUtil

enum class EndermanSlayerArea(val areaName: String) {
    Sepulture("Void Sepulture"),
    Bruiser("Zealot Bruiser Hideout");

    companion object {
        fun getArea(): EndermanSlayerArea? {
            val areaLine = ScoreboardUtil.getAreaLine() ?: return null

            for(possibleArea in EndermanSlayerArea.entries) {
                if(areaLine.endsWith(possibleArea.areaName)) {
                    return possibleArea
                }
            }

            return null
        }
    }
}