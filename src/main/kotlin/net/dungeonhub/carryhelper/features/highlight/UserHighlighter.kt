package net.dungeonhub.carryhelper.features.highlight

import net.azureaaron.renderchest.api.CustomGlowCallback
import net.azureaaron.renderchest.api.GlowConstants
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

object UserHighlighter {
    val highlightedUsers = ConcurrentHashMap<String, Int>()

    fun initialize() {
        CustomGlowCallback.EVENT.register { entity, _ ->
            when(entity) {
                is Player -> {
                    entity.profile.name().getOrNull()?.let {
                        highlightedUsers[it]
                    } ?: GlowConstants.NO_GLOW
                }
                else -> GlowConstants.NO_GLOW
            }
        }
    }
}