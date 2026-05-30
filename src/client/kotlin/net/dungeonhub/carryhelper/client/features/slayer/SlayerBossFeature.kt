package net.dungeonhub.carryhelper.client.features.slayer

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import org.slf4j.LoggerFactory

object SlayerBossFeature {
    private val logger = LoggerFactory.getLogger(SlayerBossFeature::class.java)

    private var currentWorld: ClientLevel? = null
    private var slayerBosses = mutableSetOf<SlayerBoss>()

    private var lastClickedEntity: Entity? = null
    private var lastHitCarrierBoss = false

    fun handleLeftClick(hitResult: HitResult) {
        if(hitResult.type == HitResult.Type.ENTITY) {
            handleHitEntity((hitResult as EntityHitResult).entity)
        }
    }

    fun handleHitEntity(entity: Entity) {
        lastClickedEntity = entity
        lastHitCarrierBoss = false

        val slayerBosses = SlayerBoss(entity, getEntityArmorStands(entity))

//        val bossType = foundArmorStands.filter { it.name.string.contains("༕ ☠ Revenant Horror I 500❤") }
        val slayerSpawner = slayerBosses.spawner ?: return

        lastHitCarrierBoss = true
//        lastHitCarrierBoss = CarryTracker.isCustomer(mob.ownerNameOrEmpty)
    }

    fun onSlayerDeath(entity: SlayerBoss) {
        val slayerSpawner = entity.spawner ?: return

        logCompletedSlayerCarry(slayerSpawner)
    }

    fun onTick() {
        if(currentWorld != null && currentWorld != Minecraft.getInstance().level) {
            slayerBosses.clear()
            currentWorld = Minecraft.getInstance().level
            return
        } else if(currentWorld == null) {
            currentWorld = Minecraft.getInstance().level
        }

        val currentWorld = currentWorld ?: return
        val allVisibleEntities = currentWorld.entitiesForRendering().filter { it !is ArmorStand }

        val currentSlayerBosses = mutableSetOf<SlayerBoss>()

        for(entity in allVisibleEntities) {
            val armorStands = getEntityArmorStands(entity)

            if(SlayerBoss.isSlayerBoss(armorStands)) {
                currentSlayerBosses.add(SlayerBoss(entity, armorStands))
            }
        }

        newSlayerBosses(currentSlayerBosses)
    }

    fun newSlayerBosses(newSlayerBosses: Set<SlayerBoss>) {
        for(boss in slayerBosses) {
//            if(!newSlayerBosses.any { it == boss }) {
            if(boss !in newSlayerBosses) {
                if(boss.entity.distanceTo(Minecraft.getInstance().player!!) > 50) continue

                onSlayerDeath(boss)
            }
        }

        slayerBosses.clear()
        slayerBosses.addAll(newSlayerBosses)
    }

    fun getEntityArmorStands(entity: Entity): Set<ArmorStand> {
        var entityId = entity.id
        val world = entity.level()

        val foundArmorStands = mutableSetOf<ArmorStand>()
        while(true) {
            val entity = world.getEntity(++entityId) ?: break

            if(entity !is ArmorStand) break

            foundArmorStands.add(entity)
        }

        return foundArmorStands
    }

    fun logCompletedSlayerCarry(spawner: String) {
        // TODO for testing purposes, we always send a message; once the feature is fully tested, use the debug again
        //logger.sendDebug("[CH] Slayer boss from $spawner was killed!")
        Minecraft.getInstance().execute {
            Minecraft.getInstance().gui.chat.addClientSystemMessage(
                Component.literal("[CH] Slayer boss from $spawner was killed!").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
            )
        }

        // TODO implement logging tickets
        /* TODO open features:
        - a possible way to show the ticket name ingame (the bot loads all channels on a server and writes their current names to the DB)
        - a way for the bot to send a message --> a kind of update queue that simply stores what kind of entity needs updating, an optional data JSON object and a status
        - linking carry difficulty to ingame carry types --> have a really large enum with all possible carry types?
        - logging a carry using the above ingame carry type --> theoretically, this should be easy to do
         */
    }
}