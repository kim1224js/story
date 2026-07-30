package es.kim.story

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToLong
import javax.inject.Inject
import javax.inject.Singleton

data class StockDefinition(
    val id: String,
    val name: String,
    val category: String,
    val priceRatio: Double,
    val volatility: Double,
)

data class StockQuote(
    val stock: StockDefinition,
    val price: Long,
    val previousPrice: Long,
    val changePercent: Double,
)

data class StockHolding(
    val stockId: String,
    val quantity: Int,
    val averagePrice: Long,
)

data class StockSaleResult(
    val stockId: String,
    val stockName: String,
    val purchasePrice: Long,
    val salePrice: Long,
    val quantity: Int,
    val saleAmount: Long,
    val realizedProfit: Long,
)
data class StockNews(
    val stockId: String,
    val stockName: String,
    val title: String,
    val summary: String,
    val changePercent: Double,
    val breaking: Boolean,
    val slot: Long,
)

data class StockState(
    val quotes: List<StockQuote> = emptyList(),
    val holdings: List<StockHolding> = emptyList(),
    val news: List<StockNews> = emptyList(),
    val pendingBreakingNews: List<StockNews> = emptyList(),
    val realizedProfitByStock: Map<String, Long> = emptyMap(),
    val marketOpen: Boolean = false,
    val lastUpdatedText: String = "",
)

val virtualStocks = listOf(
    StockDefinition("MST001", "오성전자", "전자", 0.050, 0.8),
    StockDefinition("FRT002", "과일팡 스튜디오", "게임", 0.018, 1.8),
    StockDefinition("DOG003", "까미펫케어", "반려동물", 0.027, 1.2),
    StockDefinition("BLU004", "블루칩 은행", "금융", 0.085, 0.5),
    StockDefinition("ICE005", "춘식식품", "식품", 0.014, 1.0),
    StockDefinition("MAZ006", "카스건설", "건설", 0.041, 1.5),
    StockDefinition("WRK007", "영자네커피", "외식", 0.010, 1.3),
    StockDefinition("HLT008", "파이헬스", "헬스케어", 0.033, 1.4),
    StockDefinition("MOL009", "황금두더지 광업", "자원", 0.066, 2.2),
    StockDefinition("STR010", "루팡기행", "콘텐츠", 0.024, 1.6),
)

@Singleton
class StockManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("virtual_stock_market", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(StockState())
    val state = _state.asStateFlow()
    private var accountId = ""
    private var chapter = 1

    fun selectAccount(userId: String, chapter: Int) {
        accountId = userId
        this.chapter = chapter.coerceAtLeast(1)
        refresh(checkBreaking = true)
    }

    fun refresh(checkBreaking: Boolean = false, now: LocalDateTime = LocalDateTime.now()) {
        if (accountId.isBlank()) return
        chapter = chapter.coerceAtLeast(1)
        val slot = marketSlot(now)
        val quotes = virtualStocks.map { quoteFor(it, slot, chapter) }
        val holdings = loadHoldings()
        val news = quotes.map { newsFor(it, slot) }.sortedByDescending(StockNews::slot)
        val acknowledged = prefs.getLong(key("breaking_ack_slot"), slot - 192L)
        val pending = if (checkBreaking && holdings.isNotEmpty()) {
            val owned = holdings.filter { it.quantity > 0 }.map(StockHolding::stockId).toSet()
            ((acknowledged + 1)..slot).takeLast(192).mapNotNull { candidateSlot ->
                virtualStocks.asSequence().filter { it.id in owned }
                    .map { newsFor(quoteFor(it, candidateSlot, chapter), candidateSlot) }
                    .firstOrNull(StockNews::breaking)
            }.takeLast(5)
        } else {
            _state.value.pendingBreakingNews
        }
        val marketOpen = isMarketOpen(now)
        _state.value = StockState(
            quotes = quotes,
            holdings = holdings,
            news = news,
            pendingBreakingNews = pending,
            realizedProfitByStock = loadRealizedProfits(),
            marketOpen = marketOpen,
            lastUpdatedText = slotText(now, marketOpen),
        )
    }

    fun buy(stockId: String, expectedPrice: Long, quantity: Int): Boolean {
        if (quantity <= 0) return false
        val quote = _state.value.quotes.firstOrNull { it.stock.id == stockId } ?: return false
        if (!isMarketOpen(LocalDateTime.now()) || quote.price != expectedPrice) return false
        val holdings = loadHoldings().toMutableList()
        val index = holdings.indexOfFirst { it.stockId == stockId }
        if (index < 0) holdings += StockHolding(stockId, quantity, quote.price)
        else {
            val old = holdings[index]
            val newQuantity = old.quantity + quantity
            val total = old.averagePrice * old.quantity.toLong() + quote.price * quantity.toLong()
            holdings[index] = old.copy(
                quantity = newQuantity,
                averagePrice = total / newQuantity,
            )
        }
        saveHoldings(holdings)
        refresh()
        return true
    }
    fun sell(stockId: String, sellAll: Boolean): StockSaleResult? {
        if (!isMarketOpen(LocalDateTime.now())) return null
        val quote = _state.value.quotes.firstOrNull { it.stock.id == stockId } ?: return null
        val holdings = loadHoldings().toMutableList()
        val index = holdings.indexOfFirst { it.stockId == stockId && it.quantity > 0 }
        if (index < 0) return null
        val old = holdings[index]
        val quantity = if (sellAll) old.quantity else 1
        val saleAmount = quote.price * quantity.toLong()
        val realizedProfit = (quote.price - old.averagePrice) * quantity.toLong()
        if (quantity == old.quantity) holdings.removeAt(index)
        else holdings[index] = old.copy(quantity = old.quantity - quantity)
        saveHoldings(holdings)
        val profitKey = key("realized_profit_$stockId")
        prefs.edit().putLong(
            profitKey,
            prefs.getLong(profitKey, 0L) + realizedProfit,
        ).apply()
        refresh()
        return StockSaleResult(
            stockId = stockId,
            stockName = quote.stock.name,
            purchasePrice = old.averagePrice,
            salePrice = quote.price,
            quantity = quantity,
            saleAmount = saleAmount,
            realizedProfit = realizedProfit,
        )
    }
    fun acknowledgeBreakingNews() {
        val latest = _state.value.pendingBreakingNews.maxOfOrNull(StockNews::slot) ?: marketSlot(LocalDateTime.now())
        prefs.edit().putLong(key("breaking_ack_slot"), latest).apply()
        _state.value = _state.value.copy(pendingBreakingNews = emptyList())
    }

    private fun loadHoldings(): List<StockHolding> = virtualStocks.mapNotNull { stock ->
        val quantity = prefs.getInt(key("holding_${stock.id}_quantity"), 0)
        if (quantity <= 0) null else StockHolding(
            stockId = stock.id,
            quantity = quantity,
            averagePrice = prefs.getLong(key("holding_${stock.id}_average"), 0L),
        )
    }

    private fun loadRealizedProfits(): Map<String, Long> =
        virtualStocks.associate { stock ->
            stock.id to prefs.getLong(key("realized_profit_${stock.id}"), 0L)
        }
    private fun saveHoldings(holdings: List<StockHolding>) {
        val editor = prefs.edit()
        virtualStocks.forEach { stock ->
            val holding = holdings.firstOrNull { it.stockId == stock.id }
            editor.putInt(key("holding_${stock.id}_quantity"), holding?.quantity ?: 0)
            editor.putLong(key("holding_${stock.id}_average"), holding?.averagePrice ?: 0L)
        }
        editor.apply()
    }

    private fun key(name: String) = "$accountId|$name"
}

private fun quoteFor(stock: StockDefinition, slot: Long, chapter: Int): StockQuote {
    val stageValue = stageClearCost(chapter).coerceAtLeast(10_000L)
    val base = (stageValue * stock.priceRatio).roundToLong().coerceIn(1_000L, 8_000_000L)
    val price = stockPriceForSlot(stock, slot, base)
    val previousPrice = stockPriceForSlot(stock, slot - 1, base)
    val change = (price - previousPrice) * 100.0 / previousPrice
    return StockQuote(stock, price, previousPrice, change)
}

private fun stockPriceForSlot(stock: StockDefinition, slot: Long, base: Long): Long {
    fun regularPrice(targetSlot: Long): Long {
        val dailyBias = seededUnit(stock.id.hashCode().toLong(), targetSlot / 192L) * 4.0 - 2.0
        return (base * (1.0 + (dailyBias + stockMovement(stock, targetSlot)) / 100.0))
            .roundToLong().coerceAtLeast(100L)
    }
    val circuit = circuitDirection(stock.id, slot)
    return if (circuit == 0.0) regularPrice(slot) else {
        (regularPrice(slot - 1) * (1.0 + circuit / 100.0)).roundToLong().coerceAtLeast(100L)
    }
}
private fun stockMovement(stock: StockDefinition, slot: Long): Double {
    val random = seededUnit(stock.id.hashCode().toLong(), slot)
    val normal = (random * 2.0 - 1.0) * stock.volatility
    val breaking = breakingDirection(stock.id, slot)
    return (normal + breaking).coerceIn(-7.0, 7.0)
}

private data class StockNewsProfile(
    val positiveEvents: List<String>,
    val negativeEvents: List<String>,
)

private val stockNewsProfiles = mapOf(
    "MST001" to StockNewsProfile(
        positiveEvents = listOf("신형 도깨비폰 예약 판매 호조", "차세대 반도체 생산 수율 개선", "해외 전자상가 공급 계약 체결", "구름 배터리 안전 인증 통과", "스마트 지팡이 특허 등록"),
        negativeEvents = listOf("신제품 배터리 긴급 점검", "반도체 부품 공급 지연", "해외 판매 목표 하향", "생산 설비 일시 정지", "스마트 지팡이 출시 연기"),
    ),
    "FRT002" to StockNewsProfile(
        positiveEvents = listOf("신규 퍼즐 이용자 급증", "무지개 과일 이벤트 흥행", "글로벌 게임 서비스 계약", "과일 팡 최고 매출 경신", "신작 그림 팡 사전예약 호조"),
        negativeEvents = listOf("업데이트 후 접속 지연", "퍼즐 점수 오류 신고 증가", "신작 출시 일정 연기", "이용자 결제 감소", "게임 서버 임시 점검 확대"),
    ),
    "DOG003" to StockNewsProfile(
        positiveEvents = listOf("강아지 산책 서비스 전국 확대", "반려동물 건강검진 제휴", "프리미엄 간식 판매 호조", "스마트 목줄 예약 주문 증가", "유기견 보호 캠페인 흥행"),
        negativeEvents = listOf("반려동물 간식 공급 지연", "스마트 목줄 배터리 점검", "산책 서비스 예약 취소 증가", "신규 매장 개장 연기", "원료 가격 상승 부담"),
    ),
    "BLU004" to StockNewsProfile(
        positiveEvents = listOf("신규 예금 가입자 증가", "블루칩 교환 이용량 확대", "모바일 금융 서비스 호평", "대출 연체율 개선", "무릉 상점 결제 제휴 체결"),
        negativeEvents = listOf("전산 점검 지연", "예금 가입 증가세 둔화", "대출 연체율 상승", "모바일 송금 오류 발생", "금융 보안 비용 증가"),
    ),
    "ICE005" to StockNewsProfile(
        positiveEvents = listOf("얼음 간식 판매량 급증", "빙하수 신제품 완판", "여름 축제 납품 계약", "냉동 물류 효율 개선", "펭귄 캐릭터 상품 흥행"),
        negativeEvents = listOf("원재료 가격 상승", "냉동 창고 점검 확대", "겨울 상품 재고 증가", "축제 납품 일정 지연", "빙하수 생산량 감소"),
    ),
    "MAZ006" to StockNewsProfile(
        positiveEvents = listOf("무릉 신도시 사업 수주", "미로 공원 조기 완공", "친환경 건축 인증 획득", "대형 교량 공사 계약", "건설 원가 절감 성공"),
        negativeEvents = listOf("미로 공사 일정 지연", "건설 자재 가격 상승", "신도시 인허가 보완 요청", "현장 안전 점검 확대", "대형 교량 계약 재검토"),
    ),
    "WRK007" to StockNewsProfile(
        positiveEvents = listOf("복숭아라떼 판매 호조", "신규 카페 지점 개장", "원두 직거래 계약 체결", "아침 메뉴 주문 증가", "무릉 맛집 평가 1위"),
        negativeEvents = listOf("커피 원두 가격 상승", "신규 지점 개장 연기", "배달 주문 감소", "제빙기 긴급 점검", "아침 메뉴 재료 부족"),
    ),
    "HLT008" to StockNewsProfile(
        positiveEvents = listOf("만 보 챌린지 참여자 급증", "걸음 측정 정확도 개선", "헬스케어 기기 제휴", "건강 포인트 이용 확대", "산책 분석 서비스 호평"),
        negativeEvents = listOf("걸음 수 측정 오류 증가", "헬스 데이터 동기화 지연", "신규 기기 연동 연기", "챌린지 참여율 감소", "서버 유지 비용 상승"),
    ),
    "MOL009" to StockNewsProfile(
        positiveEvents = listOf("대규모 금빛 광맥 발견", "채굴 효율 신기록 달성", "황금 두더지 탐사 성공", "광물 수출 계약 체결", "신형 굴착 장비 도입"),
        negativeEvents = listOf("채굴 지역 두더지 파업", "광맥 매장량 전망 하향", "굴착 장비 고장 발생", "광물 운송로 통제", "채굴 안전 비용 증가"),
    ),
    "STR010" to StockNewsProfile(
        positiveEvents = listOf("신규 스토리 챕터 흥행", "캐릭터 상품 예약 판매 호조", "웹툰 제작 계약 체결", "해외 콘텐츠 수출 확대", "무릉 세계관 전시회 매진"),
        negativeEvents = listOf("다음 챕터 공개 연기", "캐릭터 상품 배송 지연", "웹툰 제작 일정 재조정", "해외 서비스 심사 지연", "전시회 방문객 감소"),
    ),
)

private fun newsFor(quote: StockQuote, slot: Long): StockNews {
    val circuit = circuitDirection(quote.stock.id, slot)
    val breaking = circuit != 0.0 || breakingDirection(quote.stock.id, slot) != 0.0
    val up = quote.changePercent >= 0.0
    val profile = stockNewsProfiles.getValue(quote.stock.id)
    val events = if (up) profile.positiveEvents else profile.negativeEvents
    val eventIndex = (seededUnit(quote.stock.id.hashCode().toLong() xor 0x21A5L, slot) * events.size)
        .toInt().coerceIn(events.indices)
    val style = (seededUnit(quote.stock.id.hashCode().toLong() xor 0x7C31L, slot) * 5)
        .toInt().coerceIn(0, 4)
    val event = events[eventIndex]
    val titleBody = if (up) {
        when (style) {
            0 -> "$event, 성장 기대감 확대"
            1 -> "$event… 이용자 관심 집중"
            2 -> "$event 발표에 거래 활발"
            3 -> "$event 효과 본격화"
            else -> "$event 소식에 시장 반색"
        }
    } else {
        when (style) {
            0 -> "$event, 실적 우려 확대"
            1 -> "$event… 투자 심리 위축"
            2 -> "$event 소식에 거래 신중"
            3 -> "$event 영향 장기화 우려"
            else -> "$event 부담에 시장 긴장"
        }
    }
    val summary = if (up) {
        when (style) {
            0 -> "${quote.stock.name}의 $event 소식이 전해지며 성장 기대가 높아지고 있습니다."
            1 -> "$event 영향으로 신규 이용자와 매출이 함께 늘어날 것이라는 전망입니다."
            2 -> "시장에서는 $event 성과가 다음 실적에 긍정적으로 반영될 것으로 보고 있습니다."
            3 -> "$event 효과가 예상보다 빠르게 나타나며 매수세가 유입됐습니다."
            else -> "${quote.stock.name}이 $event 소식을 발표하자 향후 사업 확대에 관심이 모였습니다."
        }
    } else {
        when (style) {
            0 -> "${quote.stock.name}의 $event 소식이 전해지며 단기 실적에 대한 우려가 커졌습니다."
            1 -> "$event 영향으로 비용 부담과 일정 차질 가능성이 제기됐습니다."
            2 -> "시장에서는 $event 문제가 해결될 때까지 보수적인 흐름이 이어질 것으로 보고 있습니다."
            3 -> "$event 영향이 예상보다 길어질 수 있다는 전망에 매도세가 나타났습니다."
            else -> "${quote.stock.name}이 $event 상황을 알리자 투자자들의 경계감이 높아졌습니다."
        }
    }
    if (circuit != 0.0) {
        val rising = circuit > 0.0
        return StockNews(
            stockId = quote.stock.id,
            stockName = quote.stock.name,
            title = "[속보][${if (rising) "상승서킷" else "하락서킷"}] ${quote.stock.name}",
            summary = if (rising) {
                "희귀 급등 이벤트가 발생해 5분 전 가격보다 약 20% 상승했습니다."
            } else {
                "희귀 급락 이벤트가 발생해 5분 전 가격보다 약 20% 하락했습니다."
            },
            changePercent = quote.changePercent,
            breaking = true,
            slot = slot,
        )
    }

    return StockNews(
        stockId = quote.stock.id,
        stockName = quote.stock.name,
        title = "${if (breaking) "[속보] " else ""}$titleBody",
        summary = summary,
        changePercent = quote.changePercent,
        breaking = breaking,
        slot = slot,
    )
}
private fun circuitDirection(stockId: String, slot: Long): Double {
    val chance = (seededUnit(stockId.hashCode().toLong() xor 0x19C7_20L, slot) * 10_000).toInt()
    if (chance >= 5) return 0.0
    val rising = seededUnit(stockId.hashCode().toLong() xor 0x72A1_55L, slot) >= 0.5
    return if (rising) 20.0 else -20.0
}
private fun breakingDirection(stockId: String, slot: Long): Double {
    val chance = (seededUnit(stockId.hashCode().toLong() xor 0x5A17L, slot) * 1_000).toInt()
    if (chance >= 10) return 0.0
    return if (chance % 2 == 0) 3.0 + chance / 10.0 else -3.0 - chance / 10.0
}

private fun seededUnit(seed: Long, slot: Long): Double {
    var value = seed xor (slot * -7046029254386353131L)
    value = (value xor (value ushr 30)) * -4658895280553007687L
    value = (value xor (value ushr 27)) * -7723592293110705685L
    value = value xor (value ushr 31)
    return (value ushr 11).toDouble() / (1L shl 53).toDouble()
}

private fun marketSlot(now: LocalDateTime): Long {
    val date = if (now.toLocalTime() < LocalTime.of(8, 0)) now.toLocalDate().minusDays(1) else now.toLocalDate()
    val minute = when {
        now.toLocalTime() < LocalTime.of(8, 0) -> 955
        else -> (now.hour - 8) * 60 + now.minute
    }
    return date.toEpochDay() * 192L + (minute / 5).coerceIn(0, 191)
}

private fun isMarketOpen(now: LocalDateTime): Boolean =
    !now.toLocalTime().isBefore(LocalTime.of(8, 0))

private fun slotText(now: LocalDateTime, open: Boolean): String {
    if (!open) return "장 마감 · 매일 08:00~24:00"
    val minute = now.minute / 5 * 5
    return "${now.toLocalDate()} %02d:%02d 갱신".format(now.hour, minute)
}

private fun LongRange.takeLast(count: Int): LongRange {
    if (isEmpty()) return LongRange.EMPTY
    return maxOf(first, last - count + 1)..last
}