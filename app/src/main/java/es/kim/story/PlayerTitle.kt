package es.kim.story

import es.kim.story.data.UserEntity

internal const val TITLE_NONE = ""
internal const val TITLE_GAME_MASTER = "game_master"
internal const val TITLE_REAL_ESTATE_MASTER = "real_estate_master"
internal const val TITLE_HERO = "hero"

internal data class PlayerTitleDefinition(
    val id: String,
    val label: String,
    val icon: String,
)

internal val playerTitles = listOf(
    PlayerTitleDefinition(TITLE_NONE, "칭호 없음", "○"),
    PlayerTitleDefinition(TITLE_GAME_MASTER, "게임 마스터", "♠"),
    PlayerTitleDefinition(TITLE_REAL_ESTATE_MASTER, "부동산 마스터", "♛"),
    PlayerTitleDefinition(TITLE_HERO, "용사", "⚔️"),
)

internal fun ownedPlayerTitleIds(user: UserEntity?): Set<String> = buildSet {
    add(TITLE_NONE)
    if (user?.premiumIdColor == true) add(TITLE_GAME_MASTER)
    val apartmentCount = user?.ownedApartmentDistricts.orEmpty()
        .split(',').filter(String::isNotBlank).toSet().size
    if (apartmentCount >= seoulApartments.size) add(TITLE_REAL_ESTATE_MASTER)
    if (user?.heroTitleUnlocked == true) add(TITLE_HERO)
}
