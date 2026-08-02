package es.kim.story

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.kim.story.data.RpgCharacterEntity
import kotlinx.coroutines.delay

private enum class CampBuilding(val label: String, val icon: String) {
    PORTAL("포탈", "🌀"), TRAINING("훈련소", "🏋️"), SMITHY("대장간", "⚒️"),
    VAULT("금고", "🏦"), REVIVAL("부활소", "⛪"), GUIDE("직업 도감", "📖")
}

@Composable
internal fun RpgView(viewModel: MainViewModel) {
    val state by viewModel.rpgState.collectAsState()
    var building by remember { mutableStateOf(CampBuilding.PORTAL) }
    var selectedSlot by remember { mutableIntStateOf(0) }
    var promotionSlot by remember { mutableStateOf<Int?>(null) }
    var jobChangeSlot by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showAddCharacter by remember { mutableStateOf(false) }
    var newAccountName by remember { mutableStateOf("") }
    var accountCreateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.party) {
        promotionSlot = state.party.firstOrNull { it.level >= 30 && it.jobType() == RpgJob.NOVICE }?.slot
    }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF18261F), Color(0xFFEEE5CF), Color(0xFFF8F3E7))),
        ).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = Color(0xEE20352A), shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCFB86E)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("🏕️ 베이스캠프", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
        PartyStrip(
            party = state.party,
            motionId = state.battle?.motionUnitId,
            selectedSlot = selectedSlot,
            onSelect = { selectedSlot = it },
            onAdd = {
                newAccountName = ""
                accountCreateError = null
                showAddCharacter = true
            },
        )
        state.party.getOrNull(selectedSlot)?.let { character ->
            SelectedCharacterStats(character, onChangeJob = { jobChangeSlot = character.slot })
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(CampBuilding.entries) { item ->
                FilterChip(
                    selected = building == item,
                    onClick = { building = item },
                    label = { Text("${item.icon} ${item.label}", fontWeight = FontWeight.Bold) },
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xF7FFFCF4),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5C7A4)),
        ) {
            Box(Modifier.fillMaxSize().padding(12.dp)) {
                when (building) {
                    CampBuilding.PORTAL -> PortalBuilding(state, viewModel) { notice = it }
                    CampBuilding.TRAINING -> TrainingBuilding(state.party.getOrNull(selectedSlot), state.walletBalance, viewModel) { notice = it }
                    CampBuilding.SMITHY -> SmithyBuilding(state.party.getOrNull(selectedSlot), state, viewModel) { notice = it }
                    CampBuilding.VAULT -> VaultBuilding(state.walletBalance, viewModel) { notice = it }
                    CampBuilding.REVIVAL -> RevivalBuilding(state.party, viewModel) { notice = it }
                    CampBuilding.GUIDE -> JobGuideBuilding()
                }
            }
        }
    }

    promotionSlot?.let { slot ->
        val character = state.party.firstOrNull { it.slot == slot }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("✨ 레벨 30 전직") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${character?.name}의 직업을 선택하세요. 선택 후에는 변경할 수 없습니다.")
                    RpgJob.entries.filter { it != RpgJob.NOVICE }.forEach { job ->
                        OutlinedButton(
                            onClick = { viewModel.chooseRpgJob(slot, job); promotionSlot = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${job.label} · 주 능력치 ${job.primary}") }
                    }
                }
            },
            confirmButton = {},
        )
    }
    jobChangeSlot?.let { slot ->
        val character = state.party.firstOrNull { it.slot == slot }
        if (character != null) {
            AlertDialog(
                onDismissRequest = { jobChangeSlot = null },
                title = { Text("직업 변경") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${character.name}의 직업을 변경하면 레벨이 30으로 초기화됩니다. " +
                                "모든 능력치는 기본값으로 돌아가고 30레벨 성장분인 잔여 스탯 29포인트가 지급됩니다. " +
                                "착용 장비는 인벤토리로 돌아갑니다.",
                        )
                        RpgJob.entries.filter { it != RpgJob.NOVICE && it != character.jobType() }.forEach { job ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.changeRpgJob(slot, job) { success ->
                                        notice = if (success) "${character.name}의 직업이 ${job.label}(으)로 변경되었습니다." else "직업을 변경할 수 없습니다."
                                    }
                                    jobChangeSlot = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${job.label} · 주 능력치 ${job.primary}") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { jobChangeSlot = null }) { Text("취소") } },
            )
        }
    }
    if (showAddCharacter) {
        AlertDialog(
            onDismissRequest = { showAddCharacter = false },
            title = { Text("RPG 캐릭터 추가") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("새 계정을 만들면 같은 이름의 RPG 캐릭터가 파티에 추가됩니다.")
                    OutlinedTextField(
                        value = newAccountName,
                        onValueChange = {
                            newAccountName = it.take(20)
                            accountCreateError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("계정 닉네임") },
                        singleLine = true,
                    )
                    accountCreateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addRpgAccount(newAccountName) { success ->
                            if (success) showAddCharacter = false
                            else accountCreateError = "닉네임을 확인하거나 최대 계정 수를 확인해주세요."
                        }
                    },
                    enabled = newAccountName.isNotBlank(),
                ) { Text("계정 생성") }
            },
            dismissButton = { TextButton(onClick = { showAddCharacter = false }) { Text("취소") } },
        )
    }
    state.battle?.let { BattleDialog(it, viewModel) }
    (notice ?: state.message)?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null; viewModel.clearRpgMessage() },
            title = { Text(if (message.contains("[용사]")) "🎉 마계 정복 축하!" else "RPG 안내") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null; viewModel.clearRpgMessage() }) { Text("확인") } },
        )
    }
}

@Composable
private fun VaultBuilding(walletBalance: Long, viewModel: MainViewModel, notify: (String) -> Unit) {
    val deposits = listOf(
        10_000_000_000L to "100루비",
        50_000_000_000L to "500루비",
        100_000_000_000L to "1,000루비",
        1_000_000_000_000L to "1다이아",
        10_000_000_000_000L to "10다이아",
        100_000_000_000_000L to "100다이아",
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🏦 RPG 공용 금고", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Surface(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("현재 잔액", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatGameCurrency(walletBalance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
        }
        Text("기존 계정 지갑에서 RPG 공용지갑으로만 입금할 수 있습니다. 입금한 재화는 출금할 수 없습니다.")
        deposits.forEach { (amount, label) ->
            Button(
                onClick = {
                    viewModel.depositRpgWallet(amount) {
                        notify(if (it) "$label 입금이 완료되었습니다." else "기존 계정 지갑의 재화가 부족합니다.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("$label 입금") }
        }
        Text(
            "경험치 구매, 장비 구매와 강화 비용은 이 공용지갑에서만 차감됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6D4C41),
        )
    }
}

@Composable
private fun PartyStrip(
    party: List<RpgCharacterEntity>,
    motionId: String?,
    selectedSlot: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        party.forEach { character ->
            Card(
                onClick = { onSelect(character.slot) },
                modifier = Modifier.width(102.dp).height(PARTY_CARD_HEIGHT),
                border = if (selectedSlot == character.slot) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF5D7C3D)) else null,
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RpgCharacterModel(character, Modifier.fillMaxWidth().height(86.dp), motionId == "p${character.slot}")
                    Text(character.name, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text("Lv.${character.level} ${character.jobType().label}", style = MaterialTheme.typography.labelSmall)
                    EquipmentStatus(
                        weaponId = character.weaponId,
                        weaponEnhancement = character.weaponEnhancement,
                        armorId = character.armorId,
                        armorEnhancement = character.armorEnhancement,
                        equipmentUnavailable = character.jobType() == RpgJob.PARROT_ROGUE,
                    )
                    if (character.isDead) Text("사망", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                }
            }
        }
        repeat((3 - party.size).coerceAtLeast(0)) {
            Card(
                onClick = onAdd,
                modifier = Modifier.width(102.dp).height(PARTY_CARD_HEIGHT),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE9E5D8)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9E9E9E)),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("＋", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF546E7A))
                    Text("캐릭터\n추가하기", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("계정 생성", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val PARTY_CARD_HEIGHT = 184.dp

@Composable
private fun EquipmentStatus(
    weaponId: String,
    weaponEnhancement: Int,
    armorId: String,
    armorEnhancement: Int,
    color: Color = Color(0xFF6D4C41),
    equipmentUnavailable: Boolean = false,
) {
    if (equipmentUnavailable) {
        Text("장비 착용 불가능", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), maxLines = 1)
        return
    }
    val weapon = rpgEquipment.firstOrNull { it.id == weaponId }?.name
    val armor = rpgEquipment.firstOrNull { it.id == armorId }?.name
    if (weapon == null && armor == null) {
        Text("장비 없음", style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
        return
    }
    Text(
        listOfNotNull(
            weapon?.let { "⚔ $it +$weaponEnhancement" },
            armor?.let { "🛡 $it +$armorEnhancement" },
        ).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun SelectedCharacterStats(character: RpgCharacterEntity, onChangeJob: () -> Unit) {
    var expanded by remember(character.slot) { mutableStateOf(false) }
    Surface(
        color = Color(0xFFFFFBF0),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB7A77A)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📊 ${character.name} 상세 스탯", fontWeight = FontWeight.Black)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "접기 ▲" else "펼치기 ▼")
                }
            }
            if (expanded) {
                Text("EXP ${character.experience}/${expNeeded(character.level)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "STR ${character.combatStr()}  ·  DEX ${character.dexterity}  ·  " +
                        "INT ${character.combatInt()}  ·  LUK ${character.luck}",
                    fontWeight = FontWeight.Bold,
                )
                Text("HP ${character.combatMaxHp()}  ·  MP ${character.combatMaxMp()}  ·  남은 포인트 ${character.statPoints}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "공격력 ${character.combatAttackPower()}  ·  방어력 ${character.combatDefense()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (character.jobType() == RpgJob.PARROT_ROGUE) "장비 착용 불가능"
                    else "⚔ ${rpgEquipment.firstOrNull { it.id == character.weaponId }?.name ?: "없음"} +${character.weaponEnhancement}  ·  " +
                        "🛡 ${rpgEquipment.firstOrNull { it.id == character.armorId }?.name ?: "없음"} +${character.armorEnhancement}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (character.jobType() == RpgJob.PARROT_ROGUE) Color(0xFFC62828) else Color(0xFF6D4C41),
                    maxLines = 1,
                )
                if (character.jobType() != RpgJob.NOVICE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = onChangeJob, enabled = !character.isDead) {
                            Text("직업 변경")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RpgCharacterModel(character: RpgCharacterEntity, modifier: Modifier, attacking: Boolean = false) {
    CharacterModel(
        avatarIndex = character.avatarIndex,
        novice = character.jobType() == RpgJob.NOVICE,
        dead = character.isDead,
        attacking = attacking,
        breathingEnabled = false,
        modifier = modifier,
    )
}

@Composable
private fun CharacterModel(
    avatarIndex: Int,
    novice: Boolean,
    dead: Boolean,
    attacking: Boolean,
    breathingEnabled: Boolean = true,
    modifier: Modifier,
) {
    val sheet = ImageBitmap.imageResource(if (novice) R.drawable.rpg_novices else R.drawable.rpg_heroes)
    val breathing by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = if (breathingEnabled) 0.985f else 1f,
        targetValue = if (breathingEnabled) 1.015f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breath",
    )
    val attackOffset = if (attacking) 5f else 0f
    Canvas(
        modifier.graphicsLayer {
            scaleY = if (dead) 0.9f else breathing
            translationX = attackOffset
            rotationZ = if (dead) 88f else if (attacking) -5f else 0f
            alpha = if (dead) 0.62f else 1f
            clip = false
        },
    ) {
        val columns = if (novice) 3 else 4
        val srcWidth = sheet.width / columns
        val sourceInset = if (novice) 6 else 12
        val croppedSourceWidth = srcWidth - sourceInset * 2
        val sourceAspect = croppedSourceWidth.toFloat() / sheet.height.toFloat()
        val maxWidth = size.width * .90f
        val maxHeight = size.height * .92f
        var destinationWidth = maxWidth
        var destinationHeight = destinationWidth / sourceAspect
        if (destinationHeight > maxHeight) {
            destinationHeight = maxHeight
            destinationWidth = destinationHeight * sourceAspect
        }
        drawImage(
            image = sheet,
            srcOffset = IntOffset(avatarIndex.coerceIn(0, columns - 1) * srcWidth + sourceInset, 0),
            srcSize = IntSize(croppedSourceWidth, sheet.height),
            dstOffset = IntOffset(
                ((size.width - destinationWidth) / 2f).toInt(),
                ((size.height - destinationHeight) / 2f).toInt(),
            ),
            dstSize = IntSize(destinationWidth.toInt(), destinationHeight.toInt()),
        )
    }
}

@Composable
private fun MonsterModel(index: Int, dead: Boolean, attacking: Boolean, modifier: Modifier) {
    val sheet = ImageBitmap.imageResource(R.drawable.rpg_monsters)
    val breathing by rememberInfiniteTransition(label = "monsterBreathing").animateFloat(
        initialValue = .985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "monsterBreath",
    )
    Canvas(
        modifier.graphicsLayer {
            scaleY = if (dead) .9f else breathing
            translationX = if (attacking) -5f else 0f
            rotationZ = if (dead) -88f else if (attacking) 5f else 0f
            alpha = if (dead) .58f else 1f
            clip = false
        },
    ) {
        val safeIndex = index.coerceIn(0, 9)
        val columns = 5
        val rows = 2
        val sourceWidth = sheet.width / columns
        val sourceHeight = sheet.height / rows
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        val maxWidth = size.width * .94f
        val maxHeight = size.height * .94f
        var destinationWidth = maxWidth
        var destinationHeight = destinationWidth / sourceAspect
        if (destinationHeight > maxHeight) {
            destinationHeight = maxHeight
            destinationWidth = destinationHeight * sourceAspect
        }
        drawImage(
            image = sheet,
            srcOffset = IntOffset((safeIndex % columns) * sourceWidth, (safeIndex / columns) * sourceHeight),
            srcSize = IntSize(sourceWidth, sourceHeight),
            dstOffset = IntOffset(
                ((size.width - destinationWidth) / 2f).toInt(),
                ((size.height - destinationHeight) / 2f).toInt(),
            ),
            dstSize = IntSize(destinationWidth.toInt(), destinationHeight.toInt()),
        )
    }
}

@Composable
private fun PortalBuilding(state: RpgUiState, viewModel: MainViewModel, notify: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        items(rpgStages) { stage ->
            val enabled = stage.number <= state.unlockedStage
            Card(colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFE0E0E0))) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("STAGE ${stage.number} · ${stage.place}", fontWeight = FontWeight.Black)
                        Text(
                            if (stage.number == 9) "적정 Lv.${stage.minLevel}~ · 클리어 EXP ${stage.clearExperience}"
                            else "적정 Lv.${stage.minLevel}~${stage.maxLevel} · 클리어 EXP ${stage.clearExperience}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (stage.description.isNotBlank()) {
                            Text(stage.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = {
                        viewModel.startRpgBattle(stage.number) { if (!it) notify(if (stage.number == 9) "마계는 모든 파티원이 Lv.100 이상이어야 합니다." else "사망한 파티원을 먼저 부활시키거나 이전 스테이지를 클리어하세요.") }
                    }, enabled = enabled) { Text(if (enabled) "이동" else "잠김") }
                }
            }
        }
    }
}

@Composable
private fun TrainingBuilding(character: RpgCharacterEntity?, walletBalance: Long, viewModel: MainViewModel, notify: (String) -> Unit) {
    if (character == null) return
    var experienceInput by remember(character.slot) { mutableStateOf("5") }
    val requestedExperience = experienceInput.toIntOrNull() ?: 0
    val validExperience = requestedExperience > 0 && requestedExperience % RpgManager.EXP_BUNDLE_SIZE == 0
    val purchaseCost = if (validExperience) (requestedExperience / RpgManager.EXP_BUNDLE_SIZE) * RpgManager.EXP_BUNDLE_COST else 0L
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🏋️ ${character.name} 훈련", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text("경험치 ${character.experience}/${expNeeded(character.level)} · 남은 스탯 ${character.statPoints}")
        LinearProgressIndicator(
            progress = { character.experience.toFloat() / expNeeded(character.level) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("경험치 5당 500루비", fontWeight = FontWeight.Bold, color = Color(0xFF6D4C41))
        OutlinedTextField(
            value = experienceInput,
            onValueChange = { value -> experienceInput = value.filter(Char::isDigit).take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("구매할 경험치 (5단위)") },
            supportingText = {
                Text(if (validExperience) "필요 금액 ${formatGameCurrency(purchaseCost)}" else "5, 10, 15처럼 5단위로 입력하세요.")
            },
            isError = experienceInput.isNotBlank() && !validExperience,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Button(
            onClick = { viewModel.buyRpgExperience(character.slot, requestedExperience) { notify(if (it) "경험치 $requestedExperience 구매 완료" else "공용지갑 잔액 또는 입력한 경험치를 확인하세요.") } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !character.isDead && validExperience && purchaseCost <= walletBalance,
        ) { Text("입력한 경험치 구매") }
        OutlinedButton(
            onClick = { viewModel.buyRpgExperience(character.slot, 0, buyMaximum = true) { notify(if (it) "공용지갑으로 구매 가능한 경험치를 모두 구매했습니다." else "구매 가능한 경험치가 없습니다.") } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !character.isDead && walletBalance >= RpgManager.EXP_BUNDLE_COST,
        ) { Text("공용지갑으로 가능한 만큼 모두 성장") }
        HorizontalDivider()
        val stats = listOf(
            RpgStat.STR to character.strength, RpgStat.DEX to character.dexterity,
            RpgStat.INT to character.intelligence, RpgStat.LUK to character.luck,
            RpgStat.HP to character.maxHp, RpgStat.MP to character.maxMp,
        )
        stats.forEach { (stat, value) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${stat.label}  $value", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                HoldToRepeatButton(
                    onAction = { viewModel.allocateRpgStat(character.slot, stat, 1) },
                    modifier = Modifier.weight(0.75f),
                    enabled = character.statPoints > 0 && !character.isDead,
                ) {
                    Text(if (stat == RpgStat.HP || stat == RpgStat.MP) "+50" else "+1")
                }
                HoldToRepeatButton(
                    onAction = { viewModel.allocateRpgStat(character.slot, stat, 10) },
                    modifier = Modifier.weight(0.85f),
                    enabled = character.statPoints > 0 && !character.isDead,
                ) {
                    Text(if (stat == RpgStat.HP || stat == RpgStat.MP) "+500" else "+10")
                }
            }
        }
    }
}

@Composable
private fun SmithyBuilding(character: RpgCharacterEntity?, state: RpgUiState, viewModel: MainViewModel, notify: (String) -> Unit) {
    if (character == null) return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Text("⚒️ ${character.name} 장비", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("성공률 50% · +10부터 실패 시 강화 하락 가능", style = MaterialTheme.typography.bodySmall)
        }
        item { EquippedItem(character, EquipmentSlot.WEAPON, viewModel, notify) }
        item { EquippedItem(character, EquipmentSlot.ARMOR, viewModel, notify) }
        item {
            Text("🎒 공용 장비 아이템창", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("착용하지 않은 장비는 어떤 캐릭터든 조건을 충족하면 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        }
        val availableInventory = state.inventory.filter { it.equippedCharacterSlot == null }
        if (availableInventory.isEmpty()) {
            item { Text("보관 중인 장비가 없습니다.", color = Color.Gray) }
        } else {
            items(availableInventory, key = { it.instanceId }) { owned ->
                val item = rpgEquipment.firstOrNull { it.id == owned.equipmentId }
                if (item != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${item.name} +${owned.enhancement}", fontWeight = FontWeight.Bold)
                                Text(if (item.slot == EquipmentSlot.ARMOR) "HP +${item.baseValue + item.valuePerEnhancement * owned.enhancement} · MP +${item.baseValue + item.valuePerEnhancement * owned.enhancement} · 요구 Lv.${item.requiredLevel}" else "${item.stat.label} +${item.baseValue + item.valuePerEnhancement * owned.enhancement} · 요구 Lv.${item.requiredLevel}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                viewModel.equipRpgInventoryItem(character.slot, owned.instanceId) {
                                    notify(if (it) "${item.name} 장착 완료" else "직업, 레벨 또는 캐릭터 상태를 확인하세요.")
                                }
                            }) { Text("착용") }
                        }
                    }
                }
            }
        }
        item { Text("🛒 장비 상점", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(rpgEquipment) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${item.name} · 요구 Lv.${item.requiredLevel}", fontWeight = FontWeight.Bold)
                        Text(if (item.slot == EquipmentSlot.ARMOR) "HP +${item.baseValue} · MP +${item.baseValue}, 강화당 각각 +${item.valuePerEnhancement}" else "${item.stat.label} +${item.baseValue}, 강화당 +${item.valuePerEnhancement}", style = MaterialTheme.typography.bodySmall)
                        Text("구매 ${formatGameCurrency(item.price)}", color = Color(0xFF6D4C41))
                        Text("1회 강화 ${formatGameCurrency(item.enhancementCost)}", color = Color(0xFF7B1FA2), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { viewModel.purchaseRpgEquipment(character.slot, item.id) { if (!it) notify("착용 제한, 레벨 또는 재화를 확인하세요.") } }) { Text("구매·착용") }
                }
            }
        }
    }
}

@Composable
private fun EquippedItem(character: RpgCharacterEntity, slot: EquipmentSlot, viewModel: MainViewModel, notify: (String) -> Unit) {
    val id = if (slot == EquipmentSlot.WEAPON) character.weaponId else character.armorId
    val level = if (slot == EquipmentSlot.WEAPON) character.weaponEnhancement else character.armorEnhancement
    val item = rpgEquipment.firstOrNull { it.id == id }
    Surface(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${if (slot == EquipmentSlot.WEAPON) "무기" else "갑옷"}: ${item?.name ?: "없음"} ${if (item != null) "+$level" else ""}", fontWeight = FontWeight.Bold)
                if (item != null) {
                    Text("강화 비용 ${formatGameCurrency(item.enhancementCost)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7B1FA2))
                }
            }
            Button(onClick = { viewModel.enhanceRpgEquipment(character.slot, slot) { result -> notify(when (result) {
                EnhancementResult.Success -> "강화 성공! +${level + 1}"
                EnhancementResult.Failed -> "강화 실패. 수치는 유지됩니다."
                EnhancementResult.FailedAndDowngraded -> "강화 실패로 +${(level - 1).coerceAtLeast(0)} 하락했습니다."
                EnhancementResult.NotEnoughMoney -> "강화 재화가 부족합니다."
                EnhancementResult.Unavailable -> "강화할 장비가 없습니다."
            }) } }, enabled = item != null) { Text("강화") }
        }
    }
}

@Composable
private fun RevivalBuilding(party: List<RpgCharacterEntity>, viewModel: MainViewModel, notify: (String) -> Unit) {
    val dead = party.filter { it.isDead }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⛪ 생명의 성소", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text("RPG 공용지갑에서 1루비를 사용해 레벨과 스탯 손실 없이 완전히 부활합니다.")
        if (dead.isEmpty()) {
            Text("쓰러진 캐릭터가 없습니다.", color = Color(0xFF2E7D32))
        } else {
            Button(
                onClick = {
                    viewModel.reviveAllRpgCharacters {
                        notify(if (it) "사망한 캐릭터 ${dead.size}명을 모두 부활시켰습니다." else "전체 부활에는 ${dead.size}루비가 필요합니다.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("전체 부활 · ${dead.size}루비") }
        }
        dead.forEach { character ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RpgCharacterModel(character, Modifier.size(72.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(character.name, fontWeight = FontWeight.Black); Text("Lv.${character.level} ${character.jobType().label}") }
                    Button(onClick = {
                        viewModel.reviveRpgCharacter(character.slot) {
                            notify(if (it) "${character.name}이(가) 완전히 부활했습니다." else "RPG 공용지갑에 1루비가 필요합니다.")
                        }
                    }) { Text("1루비 부활") }
                }
            }
        }
    }
}

private data class JobGuideEntry(
    val icon: String,
    val name: String,
    val primary: String,
    val trait: String,
    val skillOne: String,
    val skillTwo: String,
    val color: Color,
)

@Composable
private fun JobGuideBuilding() {
    val jobs = listOf(
        JobGuideEntry(
            "🐶", "멍멍이기사", "STR",
            "튼튼한 근접 지휘관 · 무기와 방어구 착용 가능",
            "표식 · MP 50\n적 1명이 받는 피해 2배 · 기본 2턴 · STR 30마다 1턴 증가",
            "짖기 · MP 50\n50% 확률로 적 행동 불능 · STR 50마다 대상 1명 증가",
            Color(0xFF8D6E63),
        ),
        JobGuideEntry(
            "🦂", "전갈보병", "DEX",
            "무기 착용 불가 · 방어구 효과 2배 · DEX 1당 방어력 1 증가",
            "도발 · MP 50\n모든 적의 공격을 자신에게 유도",
            "마비 · MP 50\n적을 2턴 행동 불능 · DEX 30마다 대상 1명 증가",
            Color(0xFF546E7A),
        ),
        JobGuideEntry(
            "🐱", "고양이도사", "INT",
            "회복과 공격 강화에 특화 · 평타는 INT 기반 피해",
            "회복 · MP 50\n부상당한 아군을 2턴 회복 · 회복량 50 + INT",
            "버프 · MP 100\n아군 공격력 2턴간 2배 · INT 50마다 배수 +1",
            Color(0xFF7E57C2),
        ),
        JobGuideEntry(
            "🦜", "앵무도적", "LUK",
            "모든 장비 착용 불가 · 빠른 광역 공격 특화",
            "필격 · MP 100\n모든 적에게 레벨 + LUK 피해",
            "필살 · MP 50\n한 턴 준비 후 광역 피해 · LUK 50마다 피해 배수 +1",
            Color(0xFF00897B),
        ),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text("📖 직업 도감", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFF26382E))
            Text("레벨 30에 전직할 수 있습니다. 직업별 핵심 스탯과 전투 역할을 확인하세요.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6D5D46))
        }
        items(jobs) { job ->
            Card(
                colors = CardDefaults.cardColors(containerColor = job.color.copy(alpha = .12f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, job.color.copy(alpha = .55f)),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(job.icon, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = job.color)
                            Text("주 능력치 ${job.primary}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(job.trait, style = MaterialTheme.typography.bodySmall)
                    Surface(color = Color.White.copy(alpha = .72f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(job.skillOne, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(color = Color.White.copy(alpha = .72f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(job.skillTwo, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoBattleDialogLegacy(battle: RpgBattleState, viewModel: MainViewModel) {
    LaunchedEffect(battle.orderIndex, battle.round, battle.finished) {
        if (!battle.finished) { delay(720); viewModel.advanceRpgBattle() }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(8.dp), color = Color(0xFF17202A), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚔️ ${rpgStages[battle.stage - 1].place} · ROUND ${battle.round}", color = Color.White, fontWeight = FontWeight.Black)
                Text("적군", color = Color(0xFFFFCDD2), fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    battle.enemies.forEach { BattleUnitView(it, battle.motionUnitId == it.id) }
                }
                HorizontalDivider(color = Color.Gray)
                Text("아군", color = Color(0xFFC8E6C9), fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    battle.allies.forEach { BattleUnitView(it, battle.motionUnitId == it.id) }
                }
                Surface(color = Color.Black.copy(alpha = .45f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                        battle.log.takeLast(5).forEach { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (battle.finished) {
                    Button(onClick = viewModel::closeRpgBattle, modifier = Modifier.fillMaxWidth()) { Text(if (battle.victory) "승리 확인" else "베이스캠프로 귀환") }
                } else Text("스킬 우선 자동 전투 중…", color = Color(0xFFFFD54F), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RowScope.BattleUnitView(unit: BattleUnit, attacking: Boolean) {
    Column(Modifier.weight(1f).padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (unit.friendly) {
            CharacterModel(unit.avatarIndex, unit.job == RpgJob.NOVICE, !unit.alive, attacking, modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            MonsterModel(unit.monsterIndex, !unit.alive, attacking, Modifier.fillMaxWidth().weight(1f))
        }
        Text(unit.name, color = Color.White, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { unit.hp.toFloat() / unit.maxHp.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFE53935))
        Text("${unit.hp}/${unit.maxHp}", color = Color.White, style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { unit.mp.toFloat() / unit.maxMp.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF1E88E5))
    }
}

@Composable
private fun BattleDialog(battle: RpgBattleState, viewModel: MainViewModel) {
    val currentId = battle.order.getOrNull(battle.orderIndex)
    val currentActor = (battle.allies + battle.enemies).firstOrNull { it.id == currentId }
    var selectedTargetId by remember(battle.stage) {
        mutableStateOf(battle.enemies.firstOrNull { it.alive }?.id)
    }
    var pendingAction by remember(battle.stage, battle.wave) { mutableStateOf<RpgBattleAction?>(null) }
    var autoBattle by remember(battle.stage) { mutableStateOf(false) }
    var showFleeConfirmation by remember(battle.stage) { mutableStateOf(false) }
    val battleLogScrollState = rememberScrollState()
    LaunchedEffect(battle.log.size, battle.wave) {
        delay(60)
        battleLogScrollState.scrollTo(battleLogScrollState.maxValue)
    }
    LaunchedEffect(battle.orderIndex, battle.round, battle.wave, battle.finished, autoBattle) {
        if (!battle.finished && autoBattle) {
            delay(650)
            viewModel.advanceRpgAutoBattle()
        } else if (!battle.finished && (currentActor == null || !currentActor.alive || currentActor.friendly == false)) {
            delay(720)
            viewModel.advanceRpgBattle()
        }
    }
    LaunchedEffect(battle.enemies) {
        if (battle.enemies.none { it.id == selectedTargetId && it.alive }) {
            selectedTargetId = battle.enemies.firstOrNull { it.alive }?.id
        }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize().padding(6.dp),
            color = Color(0xFF17202A),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "⚔️ ${rpgStages[battle.stage - 1].place} · ROUND ${battle.wave}/10",
                            color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { showFleeConfirmation = true },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6D4C41), contentColor = Color.White,
                            ),
                        ) { Text("도망가기") }
                        Spacer(Modifier.width(5.dp))
                    Button(
                        onClick = { autoBattle = !autoBattle; pendingAction = null },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (autoBattle) Color(0xFFC62828) else Color(0xFF2E7D32),
                            contentColor = Color.White,
                        ),
                    ) { Text(if (autoBattle) "자동 중지" else "자동 전투") }
                    }
                    Text(
                        if (currentActor?.friendly == true) "▶ ${currentActor.name}의 차례" else "▶ ${currentActor?.name ?: "적"}의 차례",
                        color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f)
                        .background(stageBattleColor(battle.stage), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF607D6A), RoundedCornerShape(14.dp)),
                ) {
                    StageBattleBackground(battle.stage, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            val livingEnemies = battle.enemies.filter { it.alive }
                            livingEnemies.forEach { unit ->
                                val cardModifier = if (livingEnemies.size == 1) {
                                    Modifier.padding(3.dp).width(122.dp).height(138.dp)
                                } else {
                                    Modifier.padding(3.dp).weight(1f).height(138.dp)
                                }
                                ManualBattleUnitView(unit, battle.motionUnitId == unit.id, selectedTargetId == unit.id,
                                    currentId == unit.id, cardModifier) {
                                    val targetsEnemy = pendingAction == RpgBattleAction.BASIC_ATTACK ||
                                        (currentActor?.job == RpgJob.DOG_KNIGHT && pendingAction == RpgBattleAction.SKILL_ONE)
                                    if (unit.alive && targetsEnemy) {
                                        viewModel.performRpgBattleAction(pendingAction!!, unit.id); pendingAction = null
                                    } else selectedTargetId = unit.id
                                }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            battle.allies.forEach { unit ->
                                ManualBattleUnitView(unit, battle.motionUnitId == unit.id, selectedTargetId == unit.id,
                                    currentId == unit.id, Modifier.padding(3.dp).weight(1f).height(138.dp)) {
                                    val targetsAlly = currentActor?.job == RpgJob.CAT_TAOIST &&
                                        (pendingAction == RpgBattleAction.SKILL_ONE || pendingAction == RpgBattleAction.SKILL_TWO)
                                    if (unit.alive && targetsAlly) {
                                        viewModel.performRpgBattleAction(pendingAction!!, unit.id); pendingAction = null
                                    } else if (unit.alive) selectedTargetId = unit.id
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(172.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(
                        color = Color.Black.copy(alpha = .48f), shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Column(Modifier.padding(8.dp).verticalScroll(battleLogScrollState)) {
                            Text("전투 기록", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                            battle.log.takeLast(7).forEach {
                                Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Surface(
                        color = Color(0xFFF4F1E8), shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF536DFE)),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        if (battle.finished) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Button(onClick = viewModel::closeRpgBattle) {
                                    Text(if (battle.victory) "승리 확인" else "베이스캠프로 귀환")
                                }
                            }
                        } else if (autoBattle) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("자동 전투 진행 중…", fontWeight = FontWeight.Bold)
                            }
                        } else if (currentActor?.friendly == true) {
                            ManualCommandPanel(currentActor, battle.allies, selectedTargetId, pendingAction, viewModel) { action, needsTarget ->
                                if (needsTarget) {
                                    pendingAction = action
                                    selectedTargetId = null
                                } else viewModel.performRpgBattleAction(action, selectedTargetId)
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("적이 행동하고 있습니다…", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    battle.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::clearRpgBattleNotice,
            title = { Text("짖기 결과") }, text = { Text(notice) },
            confirmButton = { Button(onClick = viewModel::clearRpgBattleNotice) { Text("확인") } },
        )
    }
    if (showFleeConfirmation) {
        AlertDialog(
            onDismissRequest = { showFleeConfirmation = false },
            title = { Text("전투에서 도망갈까요?") },
            text = { Text("현재 스테이지 진행 상황은 저장되지 않으며 베이스캠프로 돌아갑니다.") },
            confirmButton = {
                Button(onClick = { autoBattle = false; showFleeConfirmation = false; viewModel.fleeRpgBattle() }) { Text("도망가기") }
            },
            dismissButton = { TextButton(onClick = { showFleeConfirmation = false }) { Text("계속 전투") } },
        )
    }
}

private fun stageBattleColor(stage: Int): Color = listOf(
    Color(0xFF315E4A), Color(0xFF455A64), Color(0xFF6D4C41),
    Color(0xFF4E5D35), Color(0xFF355C7D), Color(0xFF165A72),
    Color(0xFF6B4B3E), Color(0xFF37474F), Color(0xFF4A235A),
).getOrElse(stage - 1) { Color(0xFF263B34) }

@Composable
private fun StageBattleBackground(stage: Int, modifier: Modifier = Modifier) {
    val atlas = ImageBitmap.imageResource(R.drawable.rpg_battle_backgrounds)
    Canvas(modifier) {
        val safeStage = (stage - 1).coerceIn(0, 8)
        val cellWidth = atlas.width / 3
        val cellHeight = atlas.height / 3
        val destinationAspect = if (size.height > 0f) size.width / size.height else 1f
        val cellAspect = cellWidth.toFloat() / cellHeight.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        if (destinationAspect > cellAspect) {
            cropWidth = cellWidth
            cropHeight = (cellWidth / destinationAspect).toInt().coerceAtLeast(1)
        } else {
            cropHeight = cellHeight
            cropWidth = (cellHeight * destinationAspect).toInt().coerceAtLeast(1)
        }
        val cellX = (safeStage % 3) * cellWidth
        val cellY = (safeStage / 3) * cellHeight
        drawImage(
            image = atlas,
            srcOffset = IntOffset(cellX + (cellWidth - cropWidth) / 2, cellY + (cellHeight - cropHeight) / 2),
            srcSize = IntSize(cropWidth, cropHeight),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

@Composable
private fun ManualCommandPanel(
    actor: BattleUnit,
    allies: List<BattleUnit>,
    selectedTargetId: String?,
    pendingAction: RpgBattleAction?,
    viewModel: MainViewModel,
    onAction: (RpgBattleAction, Boolean) -> Unit,
) {
    val skills = when (actor.job) {
        RpgJob.DOG_KNIGHT -> "표식" to "짖기"
        RpgJob.SCORPION_SOLDIER -> "도발" to "마비"
        RpgJob.CAT_TAOIST -> "회복" to "버프"
        RpgJob.PARROT_ROGUE -> "필격" to "필살"
        RpgJob.NOVICE -> "미습득" to "미습득"
    }
    val skillOneCost = when (actor.job) {
        RpgJob.PARROT_ROGUE -> 100
        RpgJob.NOVICE -> Int.MAX_VALUE
        else -> 50
    }
    val skillTwoCost = when (actor.job) {
        RpgJob.CAT_TAOIST -> 100
        RpgJob.NOVICE -> Int.MAX_VALUE
        else -> 50
    }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (pendingAction != null) "대상을 선택하세요" else "${actor.name} 행동 선택", fontWeight = FontWeight.Black, maxLines = 1)
        Text("HP ${actor.hp}/${actor.maxHp} · MP ${actor.mp}/${actor.maxMp}", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(
                onClick = { onAction(RpgBattleAction.BASIC_ATTACK, true) },
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp),
            ) { Text("공격") }
            OutlinedButton(
                onClick = { onAction(RpgBattleAction.REST, false) },
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp),
            ) { Text("휴식 +50") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(
                onClick = { onAction(RpgBattleAction.SKILL_ONE, actor.job == RpgJob.DOG_KNIGHT || actor.job == RpgJob.CAT_TAOIST) },
                enabled = actor.mp >= skillOneCost && (actor.job != RpgJob.CAT_TAOIST || allies.any { it.alive && it.hp < it.maxHp }),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp),
            ) { Text(skills.first, maxLines = 1) }
            Button(
                onClick = { onAction(RpgBattleAction.SKILL_TWO, actor.job == RpgJob.CAT_TAOIST) },
                enabled = actor.mp >= skillTwoCost || (actor.job == RpgJob.PARROT_ROGUE && actor.charging > 0),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp),
            ) { Text(skills.second, maxLines = 1) }
        }
        Text("회복·버프는 아군을, 공격 스킬은 적을 먼저 선택하세요.", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ManualBattleUnitView(
    unit: BattleUnit,
    attacking: Boolean,
    selected: Boolean,
    currentTurn: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isBoss = !unit.friendly && unit.monsterIndex == 9
    val isElite = !unit.friendly && unit.monsterIndex == 8
    val rankColor = when {
        isBoss -> Color(0xFFFFB300)
        isElite -> Color(0xFFCE93D8)
        else -> Color.Gray
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = when {
            selected -> Color(0xFF455A64)
            isBoss -> Color(0xDD4A1515)
            isElite -> Color(0xDD302044)
            else -> Color(0xAA1B252B)
        },
        shape = RoundedCornerShape(if (isBoss) 16.dp else 10.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isBoss || currentTurn || selected) 3.dp else if (isElite) 2.dp else 1.dp,
            when {
                currentTurn -> Color(0xFF00E5FF)
                selected -> Color(0xFFFFD54F)
                isBoss || isElite -> rankColor
                else -> Color.Gray
            },
        ),
    ) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isBoss || isElite) {
                Surface(
                    color = rankColor,
                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                ) {
                    Text(
                        if (isBoss) "👑 BOSS" else "◆ ELITE",
                        Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        color = if (isBoss) Color(0xFF3E1B00) else Color(0xFF311B3F),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (currentTurn) Text("▶ 현재 차례", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            StatusBar("HP", unit.hp, unit.maxHp, Color(0xFFE53935))
            StatusBar("MP", unit.mp, unit.maxMp, Color(0xFF1E88E5))
            if (unit.friendly) {
                CharacterModel(
                    unit.avatarIndex, unit.job == RpgJob.NOVICE, !unit.alive, attacking,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                MonsterModel(unit.monsterIndex, !unit.alive, attacking, Modifier.fillMaxWidth().weight(1f))
            }
            if (unit.weaponId.isNotBlank() || unit.armorId.isNotBlank() || unit.job == RpgJob.PARROT_ROGUE) {
                EquipmentStatus(
                    unit.weaponId,
                    unit.weaponEnhancement,
                    unit.armorId,
                    unit.armorEnhancement,
                    color = Color(0xFFFFE082),
                    equipmentUnavailable = unit.job == RpgJob.PARROT_ROGUE,
                )
            }
            Text("Lv.${unit.level} ${unit.name}", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusBar(label: String, value: Int, maximum: Int, color: Color) {
    Box(Modifier.fillMaxWidth().height(15.dp), contentAlignment = Alignment.Center) {
        LinearProgressIndicator(
            progress = { value.coerceAtLeast(0).toFloat() / maximum.coerceAtLeast(1) },
            modifier = Modifier.fillMaxSize(), color = color, trackColor = Color.Black.copy(alpha = .55f),
        )
        Text("$label $value/$maximum", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}
