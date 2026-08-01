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

data class StockReconnectChange(
    val stockId: String,
    val stockName: String,
    val quantity: Int,
    val previousPrice: Long,
    val currentPrice: Long,
) {
    val valueChange: Long
        get() = (currentPrice - previousPrice) * quantity.toLong()

    val changePercent: Double
        get() = if (previousPrice > 0L) {
            (currentPrice - previousPrice) * 100.0 / previousPrice
        } else {
            0.0
        }
}

data class StockState(
    val quotes: List<StockQuote> = emptyList(),
    val holdings: List<StockHolding> = emptyList(),
    val news: List<StockNews> = emptyList(),
    val pendingBreakingNews: List<StockNews> = emptyList(),
    val realizedProfitByStock: Map<String, Long> = emptyMap(),
    val reconnectChanges: List<StockReconnectChange> = emptyList(),
    val elapsedMarketSlots: Long = 0L,
    val marketTrendDirection: Int = 0,
    val marketTrendRemainingMinutes: Int = 0,
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
    StockDefinition("MLL011", "구마네 말랑이", "생활", 0.016, 1.7),
    StockDefinition("STL012", "불새 철강", "철강", 0.058, 1.9),
    StockDefinition("FUN013", "누피 장례식", "장례", 0.022, 1.1),
    StockDefinition("LAN014", "환영 랜선", "통신", 0.037, 1.5),
    StockDefinition("NUT015", "네모 호두", "식품", 0.019, 1.4),
)

@Singleton
class StockManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("virtual_stock_market", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(StockState())
    val state = _state.asStateFlow()
    private var accountId = ""
    private var chapter = 1

    fun selectAccount(userId: String, chapter: Int) {
        if (accountId != userId) {
            _state.value = StockState()
        }
        accountId = userId
        this.chapter = chapter.coerceAtLeast(1)
        refresh(checkBreaking = true, captureReconnectChanges = true)
    }

    @Synchronized
    fun refresh(
        checkBreaking: Boolean = false,
        captureReconnectChanges: Boolean = false,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        if (accountId.isBlank()) return
        chapter = chapter.coerceAtLeast(1)
        val slot = marketSlot(now)
        val holdings = loadHoldings()
        val priceUpdate = updatePersistedPrices(slot, holdings, captureReconnectChanges)
        val quotes = priceUpdate.quotes
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
        val marketTrend = marketTrendDirection(slot)
        _state.value = StockState(
            quotes = quotes,
            holdings = holdings,
            news = news,
            pendingBreakingNews = pending,
            realizedProfitByStock = loadRealizedProfits(),
            reconnectChanges = if (captureReconnectChanges && priceUpdate.elapsedSlots > 0L) {
                priceUpdate.reconnectChanges
            } else {
                _state.value.reconnectChanges
            },
            elapsedMarketSlots = if (captureReconnectChanges && priceUpdate.elapsedSlots > 0L) {
                priceUpdate.elapsedSlots
            } else {
                _state.value.elapsedMarketSlots
            },
            marketTrendDirection = marketTrend,
            marketTrendRemainingMinutes = if (marketTrend == 0) {
                0
            } else {
                ((12L - slot % 12L) * 5L).toInt()
            },
            marketOpen = marketOpen,
            lastUpdatedText = slotText(now, marketOpen),
        )
    }

    fun buy(stockId: String, expectedPrice: Long, quantity: Int): Boolean {
        if (quantity <= 0) return false
        refresh()
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
        refresh()
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

    fun acknowledgeReconnectChanges() {
        _state.value = _state.value.copy(
            reconnectChanges = emptyList(),
            elapsedMarketSlots = 0L,
        )
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

    private fun updatePersistedPrices(
        targetSlot: Long,
        holdings: List<StockHolding>,
        captureReconnectChanges: Boolean,
    ): PersistedPriceUpdate {
        val lastSlotKey = key("price_last_slot")
        val savedSlot = prefs.getLong(lastSlotKey, Long.MIN_VALUE)

        if (savedSlot == Long.MIN_VALUE) {
            val quotes = virtualStocks.map { quoteFor(it, targetSlot, chapter) }
            val editor = prefs.edit().putLong(lastSlotKey, targetSlot)
            quotes.forEach { quote ->
                editor.putLong(key("price_${quote.stock.id}"), quote.price)
                editor.putLong(key("previous_price_${quote.stock.id}"), quote.previousPrice)
            }
            editor.commit()
            return PersistedPriceUpdate(quotes, emptyList(), 0L)
        }

        val savedPrices = virtualStocks.associateWith { stock ->
            prefs.getLong(
                key("price_${stock.id}"),
                quoteFor(stock, savedSlot, chapter).price,
            )
        }
        val savedPreviousPrices = virtualStocks.associateWith { stock ->
            prefs.getLong(
                key("previous_price_${stock.id}"),
                quoteFor(stock, savedSlot - 1, chapter).price,
            )
        }

        if (targetSlot <= savedSlot) {
            return PersistedPriceUpdate(
                quotes = virtualStocks.map { stock ->
                    persistedQuote(stock, savedPrices.getValue(stock), savedPreviousPrices.getValue(stock))
                },
                reconnectChanges = emptyList(),
                elapsedSlots = 0L,
            )
        }

        val elapsedSlots = targetSlot - savedSlot
        val currentPrices = savedPrices.toMutableMap()
        val previousPrices = savedPreviousPrices.toMutableMap()
        for (candidateSlot in (savedSlot + 1)..targetSlot) {
            virtualStocks.forEach { stock ->
                val oldPrice = currentPrices.getValue(stock)
                previousPrices[stock] = oldPrice
                currentPrices[stock] = nextStockPrice(
                    stock = stock,
                    previousPrice = oldPrice,
                    slot = candidateSlot,
                    chapter = chapter,
                    accountSeed = accountId.hashCode().toLong(),
                )
            }
        }

        val editor = prefs.edit().putLong(lastSlotKey, targetSlot)
        virtualStocks.forEach { stock ->
            editor.putLong(key("price_${stock.id}"), currentPrices.getValue(stock))
            editor.putLong(key("previous_price_${stock.id}"), previousPrices.getValue(stock))
        }
        editor.commit()

        val changes = if (captureReconnectChanges) {
            holdings.mapNotNull { holding ->
                val stock = virtualStocks.firstOrNull { it.id == holding.stockId } ?: return@mapNotNull null
                val before = savedPrices.getValue(stock)
                val after = currentPrices.getValue(stock)
                if (before == after) null else StockReconnectChange(
                    stockId = stock.id,
                    stockName = stock.name,
                    quantity = holding.quantity,
                    previousPrice = before,
                    currentPrice = after,
                )
            }
        } else {
            emptyList()
        }

        return PersistedPriceUpdate(
            quotes = virtualStocks.map { stock ->
                persistedQuote(stock, currentPrices.getValue(stock), previousPrices.getValue(stock))
            },
            reconnectChanges = changes,
            elapsedSlots = elapsedSlots,
        )
    }

    private fun key(name: String) = "$accountId|$name"
}

private data class PersistedPriceUpdate(
    val quotes: List<StockQuote>,
    val reconnectChanges: List<StockReconnectChange>,
    val elapsedSlots: Long,
)

private fun persistedQuote(stock: StockDefinition, price: Long, previousPrice: Long): StockQuote {
    val change = if (previousPrice > 0L) {
        (price - previousPrice) * 100.0 / previousPrice
    } else {
        0.0
    }
    return StockQuote(stock, price, previousPrice, change)
}

private fun nextStockPrice(
    stock: StockDefinition,
    previousPrice: Long,
    slot: Long,
    chapter: Int,
    accountSeed: Long,
): Long {
    val stageValue = stageClearCost(chapter).coerceAtLeast(10_000L)
    val base = (stageValue * stock.priceRatio).roundToLong().coerceIn(1_000L, 8_000_000L)
    val meanReversion = ((base - previousPrice) * 100.0 / previousPrice)
        .coerceIn(-0.25, 0.25)
    val circuit = circuitDirection(stock.id, slot)
    val marketTrend = marketTrendDirection(slot)
    val movement = when {
        marketTrend != 0 -> {
            val strength = 0.25 +
                seededUnit(stock.id.hashCode().toLong() xor 0x4D41_524BL, slot) * 0.55
            marketTrend * strength
        }
        circuit != 0.0 -> circuit
        else -> stockMovement(stock, slot, accountSeed) + meanReversion
    }
    return (previousPrice * (1.0 + movement / 100.0))
        .roundToLong()
        .coerceAtLeast(100L)
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
private fun stockMovement(stock: StockDefinition, slot: Long, accountSeed: Long = 0L): Double {
    val seed = stock.id.hashCode().toLong() xor accountSeed
    val rising = seededUnit(seed, slot) < 0.55
    val magnitude = (0.15 + seededUnit(seed xor 0x5550_3435L, slot) * 0.85) *
        stock.volatility
    val normal = if (rising) magnitude else -magnitude
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
    "MLL011" to StockNewsProfile(
        positiveEvents = listOf("신형 말랑이 완구 완판", "촉감 소재 안전 인증 획득", "어린이 체험 매장 흥행", "해외 캐릭터 상품 공급 계약", "한정판 말랑이 예약 주문 급증"),
        negativeEvents = listOf("말랑이 원료 공급 지연", "일부 제품 품질 점검", "신제품 출시 일정 연기", "완구 매장 주문 감소", "포장재 가격 상승 부담"),
    ),
    "STL012" to StockNewsProfile(
        positiveEvents = listOf("대형 조선사 후판 공급 계약", "친환경 제철 설비 가동", "철강 수출 물량 확대", "생산 원가 절감 성공", "고강도 특수강 개발 완료"),
        negativeEvents = listOf("철광석 가격 급등", "제철소 정기 보수 연장", "해외 철강 수요 둔화", "후판 공급 계약 재협상", "생산 설비 가동률 하락"),
    ),
    "FUN013" to StockNewsProfile(
        positiveEvents = listOf("전국 추모 시설 제휴 확대", "온라인 추모 서비스 이용 증가", "친환경 장례 상품 출시", "상조 서비스 만족도 1위", "신규 추모관 운영 계약"),
        negativeEvents = listOf("신규 추모관 개장 지연", "장례용품 공급 비용 상승", "상조 서비스 해지 증가", "시설 보수 일정 연장", "온라인 추모 서비스 장애"),
    ),
    "LAN014" to StockNewsProfile(
        positiveEvents = listOf("초고속 랜선 전국 공급 계약", "게임 전용망 이용자 급증", "해저 케이블 사업 수주", "통신 지연 개선 기술 특허", "기업용 네트워크 매출 신기록"),
        negativeEvents = listOf("일부 지역 통신 장애", "해저 케이블 공사 지연", "망 유지 비용 증가", "기업용 회선 계약 감소", "신규 장비 인증 일정 연기"),
    ),
    "NUT015" to StockNewsProfile(
        positiveEvents = listOf("네모 호두 과자 품절 대란", "대형 마트 납품 계약", "호두 가공 특허 등록", "건강 간식 수출 확대", "신규 농장 장기 공급 계약"),
        negativeEvents = listOf("호두 원물 가격 상승", "일부 제품 출하 지연", "농장 수확량 전망 하향", "대형 마트 판촉 축소", "가공 공장 정기 점검 연장"),
    ),
)

private val majorBadEvents = listOf(
    "대규모 유상증자 결정",
    "공매도 물량 급증",
    "오너 리스크 확산",
    "회계 감사 의견 거절 우려",
    "핵심 사업 계약 전격 해지",
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
        val majorBadEvent = majorBadEvents[
            (seededUnit(quote.stock.id.hashCode().toLong() xor 0xBAD_30L, slot) * majorBadEvents.size)
                .toInt().coerceIn(majorBadEvents.indices)
        ]
        return StockNews(
            stockId = quote.stock.id,
            stockName = quote.stock.name,
            title = if (rising) {
                "[속보][대형 호재] $titleBody"
            } else {
                "[속보][대형 악재] $majorBadEvent"
            },
            summary = if (rising) {
                "$summary 대형 호재에 매수세가 집중되며 5분 전 가격보다 약 20% 상승했습니다."
            } else {
                "${quote.stock.name}에 $majorBadEvent 소식이 전해지며 투매가 집중돼 5분 전 가격보다 약 30% 하락했습니다."
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
    val seed = stockId.hashCode().toLong()
    val risingChance = seededUnit(seed xor 0x19C7_20L, slot)
    if (risingChance < 0.05) return 20.0

    val fallingChance = seededUnit(seed xor 0x72A1_55L, slot)
    return if (fallingChance < 0.025) -30.0 else 0.0
}

private fun marketTrendDirection(slot: Long): Int {
    val hourBlock = slot / 12L
    val chance = (seededUnit(0x4D41_524B_4554L, hourBlock) * 1_000).toInt()
    return when (chance) {
        in 0..19 -> 1
        in 20..39 -> -1
        else -> 0
    }
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
