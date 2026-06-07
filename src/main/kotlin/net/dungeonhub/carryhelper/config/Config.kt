package net.dungeonhub.carryhelper.config

import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.ConfigKt
import net.dungeonhub.carryhelper.DhCarryHelper
import net.dungeonhub.carryhelper.DhCarryHelper.MOD_ID
import net.dungeonhub.carryhelper.config.categories.DevCategory
import net.dungeonhub.carryhelper.config.categories.TicketCategory
import net.minecraft.util.Util

object Config : ConfigKt("$MOD_ID/config") {
    override val name: TranslatableValue
        get() = Literal("DH Carry Helper ${DhCarryHelper.version}")

    init {
        button {
            title = "GitHub"
            description = "This is open source!"
            text = "Open"
            onClick {
                Util.getPlatform().openUri("https://github.com/dungeon-hub/")
            }
        }

        button {
            title = "Connect with us"
            description = "For questions and support, check out our discord"
            text = "Join"
            onClick {
                Util.getPlatform().openUri("https://discord.dungeon-hub.net/")
            }
        }

        button {
            title = "Support us"
            description = "Support our development costs and keep the servers running"
            text = "Patreon"
            onClick {
                Util.getPlatform().openUri("https://www.patreon.com/dungeon_hub/")
            }
        }

        button {
            title = "Website"
            description = "Check out our website to learn more about the bot and how to configure it"
            text = "Explore"
            onClick {
                Util.getPlatform().openUri("https://dungeon-hub.net/")
            }
        }
    }

    var developer by boolean("developer", false) {
        name = Literal("Developer Mode")
        description = Literal("Reopen the config after updating this value.")
    }

    init {
        category(TicketCategory)
        category(DevCategory)
    }
}