package net.dungeonhub.carryhelper.client

import com.teamresourceful.resourcefulconfig.api.loader.Configurator
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.client.auth.AuthenticationHandler
import net.dungeonhub.carryhelper.client.commands.TicketCommand
import net.dungeonhub.carryhelper.client.config.AuthConfig
import net.dungeonhub.carryhelper.client.logging.LogCommand
import net.dungeonhub.connection.DiscordUserConnection
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader

class DhCarryHelperClient : ClientModInitializer {
    override fun onInitializeClient() {
        configurator.register(AuthConfig.javaClass)

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("carrylog")
                    .executes(LogCommand::executeLogCommand)
            )
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("clog")
                    .executes(LogCommand::executeLogCommand)
            )
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("tickets")
                    .then(
                        ClientCommands.literal("toggle")
                            .executes(TicketCommand::executeToggle)
                    )
                    .then(
                        ClientCommands.literal("refresh")
                            .executes(TicketCommand::executeRefresh)
                    )
                    .executes(TicketCommand::executeToggle)
            )
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            AuthenticationHandler.scheduler.launch {
                AuthenticationHandler.setup()
            }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            AuthenticationHandler.shutdown()
        }
    }

    companion object {
        val configurator = Configurator("dh-carry-helper")

        fun saveConfig() {
            configurator.saveConfig(AuthConfig.javaClass)
        }

        val isDev = FabricLoader.getInstance().isDevelopmentEnvironment
    }
}
