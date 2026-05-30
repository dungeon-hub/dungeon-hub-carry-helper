package net.dungeonhub.carryhelper.features.slayer

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

class SlayerBoss(
    val entity: Entity,
    val armorStands: Set<ArmorStand>
) {
    var hasHelped = false

    val spawner: String?
        get() = armorStands.firstOrNull { it.name.string.startsWith("Spawned by:") }
            ?.name?.string?.substringAfter("Spawned by: ")?.trim()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SlayerBoss) return false
        return entity == other.entity
    }

    override fun hashCode(): Int {
        return entity.hashCode()
    }

    companion object {
        private val allSlayerNames = listOf(
            "Revenant Horror",
            "Atoned Horror",
            "Tarantula Broodfather",
            "Sven Packmaster",
            "Voidgloom Seraph",
            "Riftstalker Bloodfiend",
            "Inferno Demonlord"
        )

        fun isSlayerBoss(foundArmorStands: Set<ArmorStand>): Boolean {
            return foundArmorStands.any { armorStand ->
                val name = armorStand.name.string

                // The format is e.g. "༕ ☠ Revenant Horror I 500❤"
                name.contains("❤") && allSlayerNames.any {
                    name.contains(it)
                }
            }
        }
    }
}