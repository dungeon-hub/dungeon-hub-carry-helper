package net.dungeonhub.carryhelper.features.dungeons

import net.dungeonhub.enums.IngameCarryType

enum class DungeonFloor(val completionCarryType: IngameCarryType, val sCarryType: IngameCarryType, val sPlusCarryType: IngameCarryType) {
    Floor1(IngameCarryType.Floor1Completion, IngameCarryType.Floor1S, IngameCarryType.Floor1SPlus),
    Floor2(IngameCarryType.Floor2Completion, IngameCarryType.Floor2S, IngameCarryType.Floor2SPlus),
    Floor3(IngameCarryType.Floor3Completion, IngameCarryType.Floor3S, IngameCarryType.Floor3SPlus),
    Floor4(IngameCarryType.Floor4Completion, IngameCarryType.Floor4S, IngameCarryType.Floor4SPlus),
    Floor5(IngameCarryType.Floor5Completion, IngameCarryType.Floor5S, IngameCarryType.Floor5SPlus),
    Floor6(IngameCarryType.Floor6Completion, IngameCarryType.Floor6S, IngameCarryType.Floor6SPlus),
    Floor7(IngameCarryType.Floor7Completion, IngameCarryType.Floor7S, IngameCarryType.Floor7SPlus),
    MasterMode1(IngameCarryType.MasterMode1Completion, IngameCarryType.MasterMode1S, IngameCarryType.MasterMode1SPlus),
    MasterMode2(IngameCarryType.MasterMode2Completion, IngameCarryType.MasterMode2S, IngameCarryType.MasterMode2SPlus),
    MasterMode3(IngameCarryType.MasterMode3Completion, IngameCarryType.MasterMode3S, IngameCarryType.MasterMode3SPlus),
    MasterMode4(IngameCarryType.MasterMode4Completion, IngameCarryType.MasterMode4S, IngameCarryType.MasterMode4SPlus),
    MasterMode5(IngameCarryType.MasterMode5Completion, IngameCarryType.MasterMode5S, IngameCarryType.MasterMode5SPlus),
    MasterMode6(IngameCarryType.MasterMode6Completion, IngameCarryType.MasterMode6S, IngameCarryType.MasterMode6SPlus),
    MasterMode7(IngameCarryType.MasterMode7Completion, IngameCarryType.MasterMode7S, IngameCarryType.MasterMode7SPlus);

    fun getCarryType(score: String): IngameCarryType {
        if(score == "S") return sCarryType
        if(score == "S+") return sPlusCarryType
        return completionCarryType
    }

    companion object {
        fun getFromName(type: String, floor: String): DungeonFloor? {
            if(type == "The Catacombs") {
                return when(floor) {
                    "Entrance" -> null
                    "Floor I" -> Floor1
                    "Floor II" -> Floor2
                    "Floor III" -> Floor3
                    "Floor IV" -> Floor4
                    "Floor V" -> Floor5
                    "Floor VI" -> Floor6
                    "Floor VII" -> Floor7
                    else -> null
                }
            } else if(type == "Master Mode The Catacombs") {
                return when(floor) {
                    "Floor I" -> MasterMode1
                    "Floor II" -> MasterMode2
                    "Floor III" -> MasterMode3
                    "Floor IV" -> MasterMode4
                    "Floor V" -> MasterMode5
                    "Floor VI" -> MasterMode6
                    "Floor VII" -> MasterMode7
                    else -> null
                }
            }

            return null
        }
    }
}