package es.kim.story

import java.text.NumberFormat
import java.math.BigInteger
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
        Triple(BigInteger.valueOf(1_000_000_000_000L), "다이아", BigInteger.valueOf(100_000_000L)),
        Triple(BigInteger.valueOf(100_000_000L), "루비", BigInteger.valueOf(10_000L)),
        Triple(BigInteger.valueOf(10_000L), "골드", BigInteger.ONE),
    )
    val selected = units.firstOrNull { magnitude >= it.first }
        ?: return "${formatCurrencyNumber(BigInteger.valueOf(amount))}실버"
    val (primaryDivisor, primaryUnit, secondaryDivisor) = selected
    val primary = magnitude / primaryDivisor
    val secondary = magnitude.mod(primaryDivisor) / secondaryDivisor
    val secondaryUnit = when (primaryUnit) {
        "다이아" -> "루비"
        "루비" -> "골드"
        else -> "실버"
    }
    val sign = if (amount < 0L) "-" else ""
    val primaryText = "$sign${formatCurrencyNumber(primary)}$primaryUnit"
    return if (secondary == BigInteger.ZERO) primaryText
    else "$primaryText ${formatCurrencyNumber(secondary)}$secondaryUnit"
}

private fun formatCurrencyNumber(value: BigInteger): String =
    NumberFormat.getNumberInstance(Locale.KOREA).format(value)
