package net.dungeonhub.carryhelper.features

import net.dungeonhub.carryhelper.features.slayer.SlayerBoss
import net.dungeonhub.carryhelper.features.slayer.SlayerBossType
import kotlin.test.Test
import kotlin.test.assertEquals

class SlayerTypeTest {
    @Test
    fun testSlayerType() {
        assertEquals(SlayerBossType.Revenant1, SlayerBoss.findSlayerType("༕ ☠ Revenant Horror I 500❤"))
        assertEquals(SlayerBossType.Voidgloom3, SlayerBoss.findSlayerType("? ☠ Voidgloom Seraph III 165.5M❤"))
        assertEquals(SlayerBossType.Voidgloom4, SlayerBoss.findSlayerType("? ☠ Voidgloom Seraph IV 165.5M❤"))
        assertEquals(SlayerBossType.Voidgloom4, SlayerBoss.findSlayerType("༕ ☠ Voidgloom Seraph IV 89 Hits"))
    }
}