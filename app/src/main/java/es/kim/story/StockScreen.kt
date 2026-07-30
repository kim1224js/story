package es.kim.story

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale

@Composable
internal fun StockView(viewModel: MainViewModel) {
    val state by viewModel.stockState.collectAsState()
    val user by viewModel.user.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var saleResult by remember { mutableStateOf<StockSaleResult?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshStocks()
            val now = LocalDateTime.now()
            val secondsUntilNextSlot = (5 - now.minute % 5) * 60L - now.second
            delay(secondsUntilNextSlot.coerceAtLeast(5L) * 1_000L)
        }
    }

    Page {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (state.marketOpen) "● 장 운영 중" else "● 장 마감",
                        color = if (state.marketOpen) Color(0xFF2E7D32) else Color(0xFF757575),
                        fontWeight = FontWeight.ExtraBold)
                    Text("보유 재화 ${stockWon(user?.money ?: 0L)}", fontWeight = FontWeight.Bold)
                }
                Text("매일 08:00~24:00 · 주말 운영 · 5분마다 가격 변경",
                    style = MaterialTheme.typography.labelMedium)
                Text(state.lastUpdatedText, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("내 주식목록") })
            Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("호가") })
            Tab(selectedTab == 2, { selectedTab = 2 }, text = { Text("뉴스") })
            Tab(selectedTab == 3, { selectedTab = 3 }, text = { Text("통계") })
        }
        Spacer(Modifier.height(8.dp))
        notice?.let {
            Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
        }
        when (selectedTab) {
            0 -> PortfolioList(state, Modifier.fillMaxWidth().weight(1f))
            1 -> QuoteList(
                state = state,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onBuy = { quote, quantity ->
                    viewModel.buyStock(quote.stock.id, quote.price, quantity) { success ->
                        notice = if (success) "${quote.stock.name} ${quantity}주를 매수했습니다."
                        else "매수할 수 없습니다. 재화와 장 운영 시간을 확인하세요."
                    }
                },
                onSell = { quote, sellAll ->
                    viewModel.sellStock(quote.stock.id, sellAll) { result ->
                        if (result == null) {
                            notice = "매도할 수 없습니다. 보유 수량과 장 운영 시간을 확인하세요."
                        } else {
                            notice = null
                            saleResult = result
                        }
                    }
                },
            )
            2 -> NewsList(state.news, Modifier.fillMaxWidth().weight(1f))
            else -> StockStatistics(state, Modifier.fillMaxWidth().weight(1f))
        }
    }
    saleResult?.let { sale ->
        val profitRate = if (sale.purchasePrice > 0L) {
            sale.realizedProfit * 100.0 / (sale.purchasePrice * sale.quantity.toLong())
        } else 0.0
        AlertDialog(
            onDismissRequest = { saleResult = null },
            icon = { Text(if (sale.realizedProfit >= 0L) "📈" else "📉") },
            title = { Text("${sale.stockName} 매도 완료", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("매도 수량")
                        Text("${sale.quantity}주", fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("평균 매수가")
                        Text(stockWon(sale.purchasePrice), fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("매도가")
                        Text(stockWon(sale.salePrice), fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("총 매도금액")
                        Text(stockWon(sale.saleAmount), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("실현 손익", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "${if (sale.realizedProfit > 0L) "+" else ""}${stockWon(sale.realizedProfit)} " +
                                "(${"%+.2f".format(Locale.KOREAN, profitRate)}%)",
                            color = stockChangeColor(sale.realizedProfit.toDouble()),
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { saleResult = null }) { Text("확인") } },
        )
    }}

@Composable
private fun PortfolioList(state: StockState, modifier: Modifier = Modifier) {
    val holdings = state.holdings.filter { it.quantity > 0 }
    if (holdings.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("보유 중인 주식이 없습니다.\n호가 탭에서 1주씩 매수할 수 있어요.", textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(holdings, key = StockHolding::stockId) { holding ->
            val quote = state.quotes.firstOrNull { it.stock.id == holding.stockId } ?: return@items
            val value = quote.price * holding.quantity
            val cost = holding.averagePrice * holding.quantity
            val profit = value - cost
            val profitPercent = if (cost > 0L) profit * 100.0 / cost else 0.0
            StockCard {
                Text(quote.stock.name, fontWeight = FontWeight.ExtraBold)
                Text("${holding.quantity}주 · 평균 ${stockWon(holding.averagePrice)}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("평가 ${stockWon(value)}", fontWeight = FontWeight.Bold)
                    Text(
                        "${if (profit >= 0) "+" else ""}${stockWon(profit)} " +
                            "(${"%+.2f".format(Locale.KOREAN, profitPercent)}%)",
                        color = stockChangeColor(profit.toDouble()),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteList(
    state: StockState,
    modifier: Modifier = Modifier,
    onBuy: (StockQuote, Int) -> Unit,
    onSell: (StockQuote, Boolean) -> Unit,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.quotes, key = { it.stock.id }) { quote ->
            val held = state.holdings.firstOrNull { it.stockId == quote.stock.id }?.quantity ?: 0
            StockCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(quote.stock.name, fontWeight = FontWeight.ExtraBold)
                        Text("${quote.stock.id} · ${quote.stock.category}", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stockWon(quote.price), fontWeight = FontWeight.Black)
                        Text(
                            "%+.2f%%".format(Locale.KOREAN, quote.changePercent),
                            color = stockChangeColor(quote.changePercent),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onBuy(quote, 1) },
                        enabled = state.marketOpen,
                        modifier = Modifier.weight(1f),
                    ) { Text("1주 매수") }
                    Button(
                        onClick = { onBuy(quote, 10) },
                        enabled = state.marketOpen,
                        modifier = Modifier.weight(1f),
                    ) { Text("10주 매수") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSell(quote, false) },
                        enabled = state.marketOpen && held > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("1주 매도") }
                    OutlinedButton(
                        onClick = { onSell(quote, true) },
                        enabled = state.marketOpen && held > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("전량 매도${if (held > 0) " ($held)" else ""}") }
                }
            }
        }
    }
}

@Composable
private fun StockStatistics(state: StockState, modifier: Modifier = Modifier) {
    val totalProfit = state.realizedProfitByStock.values.sum()
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StockCard(containerColor = Color(0xFFE3F2FD)) {
                Text("누적 실현 손익", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold)
                Text(
                    "${if (totalProfit > 0L) "+" else ""}${stockWon(totalProfit)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = stockChangeColor(totalProfit.toDouble()),
                    fontWeight = FontWeight.Black,
                )
                Text("실제로 매도 완료된 거래만 집계합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(virtualStocks, key = StockDefinition::id) { stock ->
            val profit = state.realizedProfitByStock[stock.id] ?: 0L
            StockCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stock.name, fontWeight = FontWeight.ExtraBold)
                        Text(stock.id, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        "${if (profit > 0L) "+" else ""}${stockWon(profit)}",
                        color = stockChangeColor(profit.toDouble()),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}
@Composable
private fun NewsList(news: List<StockNews>, modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(news, key = { "${it.stockId}_${it.slot}" }) { item ->
            StockCard(containerColor = if (item.breaking) Color(0xFFFFEBEE) else Color.White) {
                Text(
                    "${item.stockName} · ${item.stockId}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(item.title, fontWeight = FontWeight.ExtraBold,
                    color = if (item.breaking) Color(0xFFC62828) else Color.Unspecified)
                Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "5분 변동 ${"%+.2f".format(Locale.KOREAN, item.changePercent)}%",
                    color = stockChangeColor(item.changePercent),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StockCard(
    containerColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

internal fun stockWon(value: Long): String =
    formatGameCurrency(value)

private fun stockChangeColor(value: Double): Color = when {
    value > 0 -> Color(0xFFD32F2F)
    value < 0 -> Color(0xFF1976D2)
    else -> Color(0xFF616161)
}