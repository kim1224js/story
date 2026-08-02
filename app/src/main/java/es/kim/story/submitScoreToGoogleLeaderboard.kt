package es.kim.story

import android.app.Activity
import android.content.Context
import com.google.android.gms.games.PlayGames
import es.kim.story.data.UserEntity

fun submitScoreToGoogleLeaderboard(context: Context, totalScore: Long) {
    val leaderboardId = context.getString(R.string.leaderboard_id)

    PlayGames.getLeaderboardsClient(context as Activity)
        .submitScore(leaderboardId, totalScore)
}

fun calculateTotalScore(user: UserEntity): Long {
    val moneyScore = user.money // 보유 돈
    val chipScore = user.blueChips * 10000L // 블루칩 점수

    // 부동산 보유 개수 계산 (쉼표 갯수로 개수 파악)
    val apartmentCount = if (user.ownedApartmentDistricts.isBlank()) 0
    else user.ownedApartmentDistricts.split(",").size
    val realEstateScore = apartmentCount * 500000L // 아파트 1개당 50만점
    val totalScore = moneyScore + chipScore + realEstateScore
    // 최종 종합 점수
    return totalScore
}
