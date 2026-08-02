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
    fun calculatesHourlyRentAsOneThousandGoldPerOwnedTier() {
        assertEquals(0L, apartmentHourlyRent(emptySet()))
        assertEquals(
            60_000_000L,
            apartmentHourlyRent(setOf("도봉구", "강남구")),
        )
        assertEquals(
            30_000_000L,
            apartmentHourlyRent(listOf("도봉구", "도봉구", "도봉구")),
        )
    }

    @Test
    fun blueChipExchangeRateMatchesOneHundredMillionGold() {
        assertEquals(100_000_000L, es.kim.story.data.UserRepository.BLUE_CHIP_EXCHANGE_COST)
    }

    @Test
    fun calculatesOnlyCompletedHoursForAvailableRent() {
        assertEquals(30_000_000L, availableApartmentRent(10_000_000L, 1_000L, 1_000L + 3 * APARTMENT_RENT_HOUR_MILLIS + 500L))
    }
}
