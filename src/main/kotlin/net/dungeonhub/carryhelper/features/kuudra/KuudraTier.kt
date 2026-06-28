package net.dungeonhub.carryhelper.features.kuudra

import net.dungeonhub.enums.IngameCarryType

enum class KuudraTier(val carryType: IngameCarryType) {
    Basic(IngameCarryType.KuudraBasic),
    Hot(IngameCarryType.KuudraHot),
    Burning(IngameCarryType.KuudraBurning),
    Fiery(IngameCarryType.KuudraFiery),
    Infernal(IngameCarryType.KuudraInfernal);

    companion object {
        fun fromTier(tier: String): KuudraTier? {
            return tier.toIntOrNull()?.let { KuudraTier.entries.getOrNull(it - 1) }
        }
    }
}