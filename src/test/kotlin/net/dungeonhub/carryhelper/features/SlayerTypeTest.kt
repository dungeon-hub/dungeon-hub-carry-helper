package net.dungeonhub.carryhelper.features

import net.dungeonhub.carryhelper.features.slayer.SlayerBoss
import net.dungeonhub.carryhelper.features.slayer.SlayerBossType
import kotlin.test.Test
import kotlin.test.assertEquals

class SlayerTypeTest {
    @Test
    fun testSlayerType() {
        assertEquals(SlayerBossType.Revenant1, SlayerBoss.findSlayerType("[Lv7] \uE084 Revenant Horror I 54/500❤"))
        assertEquals(SlayerBossType.Voidgloom3, SlayerBoss.findSlayerType("[Lv655] \uE078 Voidgloom Seraph III 38.6M/210M❤"))
        assertEquals(SlayerBossType.Voidgloom4, SlayerBoss.findSlayerType("[Lv655] \uE078 Voidgloom Seraph IV 38.6M/210M❤"))
        assertEquals(SlayerBossType.Voidgloom4, SlayerBoss.findSlayerType("[Lv655] \uE078 Voidgloom Seraph IV 52 Hits"))
    }
}