package es.kim.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulApartmentTest {
    @Test
    fun containsAllSeoulDistrictsExactlyOnce() {
        assertEquals(25, seoulApartments.size)
        assertEquals(25, seoulApartments.map { it.district }.distinct().size)
    }

    @Test
    fun usesProgressiveGameRequirementsStartingAtOneHundredBlueChips() {
        assertEquals(100L, seoulApartments.minOf { it.blueChipCost })
        assertTrue(seoulApartments.maxOf { it.blueChipCost } > 100L)
        assertTrue(seoulApartments.any { it.requiredOwnedCount > 0 })
        assertTrue(seoulApartments.zipWithNext().all { (first, second) ->
            first.blueChipCost <= second.blueChipCost
        })
    }

    @Test
    fun calculatesHourlyRentAsOneHundredGoldPerOwnedTier() {
        assertEquals(0L, apartmentHourlyRent(emptySet()))
        assertEquals(
            6_000_000L,
            apartmentHourlyRent(setOf("도봉구", "강남구")),
        )
    }
}
