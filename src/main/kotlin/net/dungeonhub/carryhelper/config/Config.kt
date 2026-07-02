package net.dungeonhub.carryhelper.config

import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigSeparatorElement
import com.teamresourceful.resourcefulconfig.api.types.options.TranslatableValue
import com.teamresourceful.resourcefulconfigkt.api.ConfigKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.DhCarryHelper
import net.dungeonhub.carryhelper.DhCarryHelper.MOD_ID
import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.carryhelper.config.categories.DevCategory
import net.dungeonhub.carryhelper.config.categories.TicketCategory
import net.dungeonhub.carryhelper.util.MessageUtil.sendMessage
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.Util
import java.util.concurrent.Executors

object Config : ConfigKt("$MOD_ID/config") {
    override val name: TranslatableValue
        get() = Literal("DH Carry Helper ${DhCarryHelper.version}")

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    init {
        element(
            object : ResourcefulConfigSeparatorElement {
                override fun title(): TranslatableValue = TranslatableValue("Thanks for using the Dungeon Hub Carry Helper!")
                override fun description(): TranslatableValue = TranslatableValue(
                    if(AuthenticationHandler.isValid()) {
                        "You're currently logged in as ${AuthenticationHandler.username}"
                    } else {
                        "Please login below to start using the mod."
                    }
                )
            }
        )

        button {
            title = "Login"
            description = "Log in with your Dungeon Hub account"
            text = "Login"
            condition = { !AuthenticationHandler.isValid() }
            onClick {
                val result = AuthenticationHandler.promptLogin()

                if(result == null) {
                    Minecraft.getInstance().sendMessage(
                        Component.literal("[CH] You're already logged in!").withStyle(
                            Style.EMPTY
                                .withColor(ChatFormatting.GREEN)
                        )
                    ) { Minecraft.getInstance().setScreen(null) }
                    return@onClick
                }

                scheduler.launch {
                    Minecraft.getInstance().execute {
                        Minecraft.getInstance().setScreen(null)
                    }
                    result.collect {
                        Minecraft.getInstance().execute {
                            Minecraft.getInstance().gui.chat.addClientSystemMessage(it)
                        }
                    }
                }
            }
        }

        button {
            title = "Logout"
            description = "Log out of your Dungeon Hub account"
            text = "Log out"
            condition = { AuthenticationHandler.isValid() }
            onClick {
                AuthenticationHandler.logout()
                Minecraft.getInstance().sendMessage(
                    Component.literal("[CH] Successfully logged out!").withStyle(
                        Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                    )
                ) { Minecraft.getInstance().setScreen(null) }
            }
        }

        separator {
            title = "Links"
        }

        button {
            title = "GitHub"
            description = "This is open source!"
            text = "Open"
            onClick {
                Util.getPlatform().openUri("https://github.com/dungeon-hub/dungeon-hub-carry-helper/")
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