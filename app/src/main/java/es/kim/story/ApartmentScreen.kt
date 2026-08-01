package es.kim.story

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal data class SeoulApartment(
    val district: String,
    val blueChipCost: Long,
    val requiredOwnedCount: Int,
    val tier: Int,
)

internal val seoulApartments = listOf(
    SeoulApartment("도봉구", 100, 0, 1),
    SeoulApartment("강북구", 100, 0, 1),
    SeoulApartment("금천구", 100, 0, 1),
    SeoulApartment("구로구", 110, 0, 1),
    SeoulApartment("노원구", 110, 0, 1),
    SeoulApartment("중랑구", 130, 2, 2),
    SeoulApartment("은평구", 130, 2, 2),
    SeoulApartment("강서구", 140, 2, 2),
    SeoulApartment("관악구", 140, 2, 2),
    SeoulApartment("성북구", 150, 2, 2),
    SeoulApartment("서대문구", 170, 5, 3),
    SeoulApartment("동대문구", 170, 5, 3),
    SeoulApartment("양천구", 180, 5, 3),
    SeoulApartment("영등포구", 190, 5, 3),
    SeoulApartment("동작구", 200, 5, 3),
    SeoulApartment("강동구", 220, 8, 4),
    SeoulApartment("광진구", 230, 8, 4),
    SeoulApartment("마포구", 240, 8, 4),
    SeoulApartment("중구", 250, 8, 4),
    SeoulApartment("종로구", 260, 8, 4),
    SeoulApartment("성동구", 300, 12, 5),
    SeoulApartment("용산구", 320, 12, 5),
    SeoulApartment("송파구", 350, 12, 5),
    SeoulApartment("서초구", 400, 12, 5),
    SeoulApartment("강남구", 450, 12, 5),
)

internal const val APARTMENT_RENT_PER_TIER = 1_000_000L // 100골드

internal fun apartmentHourlyRent(ownedDistricts: Set<String>): Long =
    seoulApartments.filter { it.district in ownedDistricts }
        .sumOf { it.tier.toLong() * APARTMENT_RENT_PER_TIER }

@Composable
internal fun ApartmentView(viewModel: MainViewModel) {
    val user by viewModel.user.collectAsState()
    val owned = remember(user?.ownedApartmentDistricts) {
        user?.ownedApartmentDistricts.orEmpty().split(',').filter(String::isNotBlank).toSet()
    }
    var notice by remember { mutableStateOf<String?>(null) }
    var showMasterTitleDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.checkApartmentRent() }

    Page {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("🏙️ 서울 아파트 컬렉션", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold)
                Text("모든 스토리를 완료해 부동산 컬렉션이 열렸습니다.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("보유 ${owned.size} / ${seoulApartments.size}채", fontWeight = FontWeight.Bold)
                    Text("🔷 ${(user?.blueChips ?: 0L).formattedNumber()}개", fontWeight = FontWeight.Bold)
                }
                Text(
                    "시간당 월세 ${formatGameCurrency(apartmentHourlyRent(owned))}",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(seoulApartments, key = SeoulApartment::district) { apartment ->
                val purchased = apartment.district in owned
                val prerequisiteMet = owned.size >= apartment.requiredOwnedCount
                val affordable = (user?.blueChips ?: 0L) >= apartment.blueChipCost
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (purchased) Color(0xFFE8F5E9) else Color.White,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${apartment.district} 아파트", fontWeight = FontWeight.ExtraBold)
                            Text("등급 ${apartment.tier} · 🔷 ${apartment.blueChipCost}개",
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "월세 ${formatGameCurrency(apartment.tier * APARTMENT_RENT_PER_TIER)}/시간",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!prerequisiteMet) {
                                Text("아파트 ${apartment.requiredOwnedCount}채 보유 후 해금",
                                    color = Color(0xFFC62828), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.purchaseApartment(apartment) { success ->
                                    notice = if (success) {
                                        if (owned.size + 1 == seoulApartments.size) {
                                            showMasterTitleDialog = true
                                        }
                                        "${apartment.district} 아파트를 구매했습니다."
                                    } else {
                                        "구매 조건과 블루칩을 확인하세요."
                                    }
                                }
                            },
                            enabled = !purchased && prerequisiteMet && affordable,
                        ) {
                            Text(if (purchased) "보유 중" else "구매")
                        }
                    }
                }
            }
        }
    }
    if (showMasterTitleDialog) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Text("♛", style = MaterialTheme.typography.displayMedium, color = Color(0xFF00897B)) },
            title = {
                Text(
                    "부동산 마스터 달성!",
                    color = Color(0xFF00695C),
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Text("서울 25개 자치구의 아파트를 모두 모았습니다. 설정에서 '부동산 마스터' 칭호를 선택할 수 있습니다.")
            },
            confirmButton = {
                Button(
                    onClick = { showMasterTitleDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                ) { Text("확인") }
            },
            containerColor = Color(0xFFE0F2F1),
        )
    }
}
