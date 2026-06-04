package net.dungeonhub.carryhelper.features.slayer

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

class SlayerBoss(
    val entity: Entity,
    val slayerBossType: SlayerBossType,
    val armorStands: Set<ArmorStand>
) {
    var hasHelped = false
    var area: EndermanSlayerArea? = null

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
        fun findSlayerType(foundArmorStands: Set<ArmorStand>): SlayerBossType? {
            for(armorStand in foundArmorStands) {
                val name = armorStand.name.string

                return findSlayerType(name) ?: continue
            }

            return null
        }

        fun findSlayerType(name: String): SlayerBossType? {
            // The format is e.g.:
            // ༕ ☠ Revenant Horror I 500❤
            // ? ☠ Voidgloom Seraph III 165.5M❤
            // ? ☠ Voidgloom Seraph IV 165.5M❤
            // ༕ ☠ Voidgloom Seraph IV 89 Hits
            if(!name.contains("❤") && !name.contains(" Hit")) {
                return null
            }

            return SlayerBossType.entries.firstOrNull {
                name.contains("${it.slayerName} ")
            }
        }
    }
}