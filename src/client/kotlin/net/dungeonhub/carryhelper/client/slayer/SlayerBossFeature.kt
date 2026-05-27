package net.dungeonhub.carryhelper.client.slayer

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

object SlayerBossFeature {
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

        val entityId = entity.id
        val world = entity.level()

        val foundArmorStands = mutableSetOf<ArmorStand>()
        while(true) {
            val entity = world.getEntity(entityId + 1) ?: break

            if(entity !is ArmorStand) break

            foundArmorStands.add(entity)
        }

//        val bossType = foundArmorStands.filter { it.name.string.contains("༕ ☠ Revenant Horror I 500❤") }
        val slayerSpawner = foundArmorStands.firstOrNull { it.name.string.startsWith("Spawned by:") }
            ?.name?.string?.substringAfter("Spawned by: ")?.trim()
            ?: return

        lastHitCarrierBoss = true
//        lastHitCarrierBoss = CarryTracker.isCustomer(mob.ownerNameOrEmpty)




    }
}