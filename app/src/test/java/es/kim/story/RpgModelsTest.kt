package es.kim.story

import es.kim.story.data.RpgCharacterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpgModelsTest {
    @Test fun `job change resets character to level 30 with surplus points`() {
        val changed = RpgCharacterEntity(
            "u", 0, "n", 0, job = RpgJob.DOG_KNIGHT.name, level = 250,
            strength = 80, dexterity = 70, intelligence = 60, luck = 50,
            maxHp = 900, maxMp = 800, statPoints = 12, weaponId = "fire_stick", armorId = "plate",
        ).resetForJobChange(RpgJob.CAT_TAOIST)

        assertEquals(30, changed.level)
        assertEquals(0, changed.experience)
        assertEquals(4, changed.intelligence)
        assertEquals(4, changed.strength)
        assertEquals(4, changed.dexterity)
        assertEquals(4, changed.luck)
        assertEquals(29, changed.statPoints)
        assertEquals(100, changed.maxHp)
        assertEquals(100, changed.maxMp)
        assertEquals("", changed.weaponId)
        assertEquals("", changed.armorId)
    }

    @Test fun `nine stages create exactly ninety unique monsters`() {
        assertEquals(9, rpgStages.size)
        assertEquals(90, rpgEnemies.size)
        assertEquals(90, rpgEnemies.map { it.id }.distinct().size)
        assertTrue(rpgStages.all { stage -> rpgEnemies.count { it.id.startsWith("s${stage.number}_") } == 10 })
    }

    @Test fun `demon world uses ten times enemy difficulty`() {
        assertEquals(1, rpgEnemyDifficultyMultiplier(8))
        assertEquals(10, rpgEnemyDifficultyMultiplier(9))
    }

    @Test fun `experience requirement keeps growing beyond level one hundred`() {
        assertEquals(5, expNeeded(1))
        assertEquals(150, expNeeded(30))
        assertEquals(495, expNeeded(99))
        assertEquals(500, expNeeded(100))
        assertEquals(750, expNeeded(150))
    }

    @Test fun `armor increases mana and enhancement increases it further`() {
        val normal = RpgCharacterEntity("u", 0, "n", 0, armorId = "cloth", armorEnhancement = 3)
        val scorpion = normal.copy(job = RpgJob.SCORPION_SOLDIER.name)
        assertEquals(180, normal.combatMaxMp())
        assertEquals(260, scorpion.combatMaxMp())
    }

    @Test fun `scorpion receives double armor hp efficiency`() {
        val normal = RpgCharacterEntity("u", 0, "n", 0, armorId = "cloth", armorEnhancement = 3)
        val scorpion = normal.copy(job = RpgJob.SCORPION_SOLDIER.name)
        assertEquals(180, normal.combatMaxHp())
        assertEquals(260, scorpion.combatMaxHp())
    }

    @Test fun `weapon enhancement contributes to matching primary stat`() {
        val fighter = RpgCharacterEntity("u", 0, "n", 0, strength = 10, weaponId = "stick", weaponEnhancement = 5)
        val mage = fighter.copy(intelligence = 10, weaponId = "orb")
        assertEquals(20, fighter.combatStr())
        assertEquals(20, mage.combatInt())
    }

    @Test fun `dexterity increases defense and attack power follows job primary stat`() {
        val knight = RpgCharacterEntity("u", 0, "n", 0, level = 10, strength = 7, dexterity = 12)
        val taoist = knight.copy(job = RpgJob.CAT_TAOIST.name, intelligence = 20)
        val rogue = knight.copy(job = RpgJob.PARROT_ROGUE.name, luck = 15)
        assertEquals(12, knight.combatDefense())
        assertEquals(17, knight.combatAttackPower())
        assertEquals(30, taoist.combatAttackPower())
        assertEquals(40, rogue.combatAttackPower())
    }

    @Test fun `rpg equipment prices use the reduced economy`() {
        val starter = rpgEquipment.first { it.id == "stick" }
        val middle = rpgEquipment.first { it.id == "iron_stick" }
        val highest = rpgEquipment.first { it.id == "fire_stick" }
        assertEquals(50_000_000L, RpgManager.EXP_BUNDLE_COST)
        assertEquals(
            listOf(50_000_000L, 1_000_000_000L, 10_000_000_000L),
            listOf(starter.price, middle.price, highest.price),
        )
        assertTrue(rpgEquipment.all { it.enhancementCost == it.price / 10L })
    }
}
