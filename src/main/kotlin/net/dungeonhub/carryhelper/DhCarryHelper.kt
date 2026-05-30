package net.dungeonhub.carryhelper

import com.teamresourceful.resourcefulconfig.api.loader.Configurator
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.carryhelper.commands.TicketCommand
import net.dungeonhub.carryhelper.config.AuthConfig
import net.dungeonhub.carryhelper.logging.LogCommand
import net.dungeonhub.carryhelper.service.TicketService
import net.dungeonhub.carryhelper.features.slayer.SlayerBossFeature
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader

object DhCarryHelper : ClientModInitializer {
    const val MOD_ID = "dh-carry-helper"

    lateinit var version: String

    val configurator = Configurator(MOD_ID)

    val authConfig = AuthConfig.register(configurator)

    val isDev = FabricLoader.getInstance().isDevelopmentEnvironment

    override fun onInitializeClient() {
        version = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")!!

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
                TicketService.initialize()
            }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            TicketService.shutdown()
            AuthenticationHandler.shutdown()
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            SlayerBossFeature.onTick()
        }
    }
}
