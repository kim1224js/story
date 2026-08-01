package es.kim.story

import es.kim.story.data.UserEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTitleTest {
    @Test
    fun gameMasterIsOwnedAfterPremiumPurchaseFlag() {
        val owned = ownedPlayerTitleIds(UserEntity(userId = "test", premiumIdColor = true))
        assertTrue(TITLE_GAME_MASTER in owned)
        assertTrue(TITLE_REAL_ESTATE_MASTER !in owned)
    }

    @Test
    fun realEstateMasterRequiresAllTwentyFiveDistricts() {
        val districts = seoulApartments.joinToString(",") { it.district }
        val owned = ownedPlayerTitleIds(
            UserEntity(userId = "test", ownedApartmentDistricts = districts),
        )
        assertEquals(
            setOf(TITLE_NONE, TITLE_REAL_ESTATE_MASTER),
            owned,
        )
    }
}
