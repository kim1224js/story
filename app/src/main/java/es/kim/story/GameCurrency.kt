package es.kim.story

import java.text.NumberFormat
import java.text.DecimalFormat
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 게임 재화의 내부 값은 실버 단위로 유지하고, 화면에서만 상위 단위로 축약한다.
 *
 * 1 골드 = 10,000 실버
 * 1 루비 = 10,000 골드
 * 1 다이아 = 10,000 루비
 */
internal fun formatGameCurrency(amount: Long): String =
    formatGameCurrencyUnits(amount.coerceAtLeast(0L))

internal fun formatGameCurrency(amount: Double): String =
    formatGameCurrencyUnits(amount.coerceAtLeast(0.0).roundToLong())

internal fun formatSignedGameCurrency(amount: Long): String =
    formatGameCurrencyUnits(amount)

private fun formatGameCurrencyUnits(amount: Long): String {
    val magnitude = BigInteger.valueOf(amount).abs()
    val units = listOf(
        BigInteger.valueOf(1_000_000_000_000L) to "다이아",
        BigInteger.valueOf(100_000_000L) to "루비",
        BigInteger.valueOf(10_000L) to "골드",
    )
    val selected = units.firstOrNull { magnitude >= it.first }
        ?: return "${formatCurrencyNumber(BigInteger.valueOf(amount))}실버"
    val (primaryDivisor, primaryUnit) = selected
    val primary = BigDecimal(magnitude).divide(BigDecimal(primaryDivisor), 1, RoundingMode.HALF_UP)
    val sign = if (amount < 0L) "-" else ""
    val formatter = (NumberFormat.getNumberInstance(Locale.KOREA) as DecimalFormat).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }
    return "$sign${formatter.format(primary)}$primaryUnit"
}

private fun formatCurrencyNumber(value: BigInteger): String =
    NumberFormat.getNumberInstance(Locale.KOREA).format(value)
