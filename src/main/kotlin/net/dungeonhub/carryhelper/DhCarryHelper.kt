package net.dungeonhub.carryhelper

import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigScreen
import com.teamresourceful.resourcefulconfig.api.loader.Configurator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dungeonhub.carryhelper.auth.AuthenticationHandler
import net.dungeonhub.carryhelper.commands.TicketCommand
import net.dungeonhub.carryhelper.config.Config
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
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

object DhCarryHelper : ClientModInitializer {
    const val MOD_ID = "dh-carry-helper"

    lateinit var version: String

    val configurator = Configurator(MOD_ID)

    val config = Config.register(configurator)

    val isDev = FabricLoader.getInstance().isDevelopmentEnvironment

    private val supervisor = SupervisorJob()
    private val dispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    private val scheduler = CoroutineScope(supervisor + dispatcher)

    override fun onInitializeClient() {
        version = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")!!

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("carry-helper")
                    .executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().setScreen(ResourcefulConfigScreen.getFactory(MOD_ID).apply(null))
                        }
                        return@executes 1
                    }
            )
        }

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
                        ClientCommands.literal("refresh")
                            .executes(TicketCommand::executeRefresh)
                    )
                    .executes(TicketCommand::executeRefresh)
            )
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            scheduler.launch {
                AuthenticationHandler.setup()
                while(!AuthenticationHandler.isValid()) {
                    delay(100.milliseconds)
                }
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
