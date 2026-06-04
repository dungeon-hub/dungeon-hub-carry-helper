package net.dungeonhub.carryhelper.features.slayer

import net.dungeonhub.enums.IngameCarryType

enum class SlayerBossType(val slayerName: String, val ingameCarryType: IngameCarryType) {
    Revenant1("Revenant Horror I", IngameCarryType.Revenant1),
    Revenant2("Revenant Horror II", IngameCarryType.Revenant2),
    Revenant3("Revenant Horror III", IngameCarryType.Revenant3),
    Revenant4("Revenant Horror IV", IngameCarryType.Revenant4),
    Revenant5("Atoned Horror", IngameCarryType.Revenant5),

    Tarantula1("Tarantula Broodfather I", IngameCarryType.Tarantula1),
    Tarantula2("Tarantula Broodfather II", IngameCarryType.Tarantula2),
    Tarantula3("Tarantula Broodfather III", IngameCarryType.Tarantula3),
    Tarantula4("Tarantula Broodfather IV", IngameCarryType.Tarantula4),
    Tarantula5("Conjoined Brood", IngameCarryType.Tarantula5),

    Sven1("Sven Packmaster I", IngameCarryType.Sven1),
    Sven2("Sven Packmaster II", IngameCarryType.Sven2),
    Sven3("Sven Packmaster III", IngameCarryType.Sven3),
    Sven4("Sven Packmaster IV", IngameCarryType.Sven4),

    Voidgloom1("Voidgloom Seraph I", IngameCarryType.Voidgloom1),
    Voidgloom2("Voidgloom Seraph II", IngameCarryType.Voidgloom2),
    Voidgloom3("Voidgloom Seraph III", IngameCarryType.Voidgloom3),
    Voidgloom4("Voidgloom Seraph IV", IngameCarryType.Voidgloom4),

    // TODO the blaze slayer integration has to be tested a bit more --> if the boss despawns for the mini bosses to take over, this could cause issues
    /*Inferno1("Inferno Demonlord I", IngameCarryType.Inferno1),
    Inferno2("Inferno Demonlord II", IngameCarryType.Inferno2),
    Inferno3("Inferno Demonlord III", IngameCarryType.Inferno3),
    Inferno4("Inferno Demonlord IV", IngameCarryType.Inferno4),*/
}