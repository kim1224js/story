package es.kim.story

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 게임 재화의 내부 값은 실버 단위로 유지하고, 화면에서만 상위 단위로 축약한다.
 *
 * 1 골드 = 10,000 실버
 * 1 루비 = 10,000 골드
 * 1 다이아 = 10,000 루비
 */
internal fun formatGameCurrency(amount: Long): String =
    formatGameCurrency(amount.toDouble())

internal fun formatGameCurrency(amount: Double): String {
    val absolute = abs(amount)
    val (divisor, unit) = when {
        absolute >= 1_000_000_000_000.0 -> 1_000_000_000_000.0 to "다이아"
        absolute >= 100_000_000.0 -> 100_000_000.0 to "루비"
        absolute >= 10_000.0 -> 10_000.0 to "골드"
        else -> return "${NumberFormat.getNumberInstance(Locale.KOREA).format(amount.roundToLong())} 실버"
    }

    val scaled = amount / divisor
    val maximumFractionDigits = when {
        abs(scaled) >= 100.0 -> 0
        abs(scaled) >= 10.0 -> 1
        else -> 2
    }
    val formatted = NumberFormat.getNumberInstance(Locale.KOREA).apply {
        minimumFractionDigits = 0
        this.maximumFractionDigits = maximumFractionDigits
    }.format(scaled)
    return "$formatted $unit"
}
