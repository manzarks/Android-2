package com.dicewars.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dicewars.game.R
import com.dicewars.game.data.LocalSettings
import com.dicewars.game.data.SaveSummary
import com.dicewars.game.domain.BoardSize
import com.dicewars.game.domain.Difficulty
import com.dicewars.game.domain.GameEngine
import com.dicewars.game.domain.GameState
import com.dicewars.game.domain.MatchPhase
import com.dicewars.game.ui.AppScreen.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val pageBackground = Color(0xFF111915)
private val mutedText = Color(0xFFAAB9AF)
private val accentMint = Color(0xFF71E2B0)
private val panelColor = Color(0xFF1A2720)

@Composable
fun DiceWarsApp(model: GameViewModel = viewModel()) {
    val ui by model.ui.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.onForeground()
                Lifecycle.Event.ON_STOP -> model.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = ui.screen != MENU || ui.confirmNewGame) {
        if (ui.confirmNewGame) model.cancelStartNewGame() else when (ui.screen) {
            MENU -> Unit
            SETUP -> model.goMenu()
            PREVIEW -> model.backToSetup()
            GAME -> model.goMenu()
            SAVES -> model.backFromSaves()
            else -> model.goMenu()
        }
    }
    Surface(color = pageBackground, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (ui.screen) {
                MENU -> MenuScreen(ui, model)
                SETUP -> SetupScreen(ui, model)
                PREVIEW -> PreviewScreen(ui, model)
                GAME -> GameScreen(ui, model)
                SAVES -> SavesScreen(ui, model)
                CREATIVE -> CreativeScreen(ui, model)
                MAPS -> MapsScreen(ui, model)
                SETTINGS -> SettingsScreen(ui, model)
                STATS -> StatsScreen(ui, model)
                HELP -> HelpScreen(ui, model)
            }
            if (ui.confirmNewGame) {
                AlertDialog(
                    onDismissRequest = model::cancelStartNewGame,
                    title = { Text("Начать новую партию?") },
                    text = { Text("Автосохранение текущей партии будет заменено.") },
                    confirmButton = { TextButton(onClick = model::confirmStartNewGame) { Text("Новая партия") } },
                    dismissButton = { TextButton(onClick = model::cancelStartNewGame) { Text("Отмена") } }
                )
            }
        }
    }
}

@Composable
private fun MenuScreen(ui: AppUiState, model: GameViewModel) {
    Page { 
        Spacer(Modifier.height(22.dp))
        Text("DICE WARS", color = accentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_name), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.tagline), color = mutedText, fontSize = 15.sp)
        Spacer(Modifier.height(22.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20342A)), shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Территории ждут", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Оффлайн-стратегия на костях", color = mutedText, fontSize = 13.sp)
                }
                Text("⚄  ⚂", color = Color(0xFFFFC857), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        ActionButton(stringResource(R.string.new_game), "⚔", onClick = model::openSetup)
        if (ui.autoSaveAvailable) {
            SecondaryButton(stringResource(R.string.continue_game), onClick = model::continueGame)
        }
        SecondaryButton(stringResource(R.string.load_game), onClick = model::openSaves)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.creative), Modifier.weight(1f), onClick = { model.openCreative() })
            SecondaryButton(stringResource(R.string.my_maps), Modifier.weight(1f), onClick = model::openMaps)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.settings), Modifier.weight(1f), onClick = model::openSettings)
            SecondaryButton(stringResource(R.string.statistics), Modifier.weight(1f), onClick = model::openStats)
        }
        TextButton(onClick = model::openHelp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.how_to_play), color = mutedText)
        }
        ui.message?.let { MessageCard(it) }
    }
}

@Composable
private fun SetupScreen(ui: AppUiState, model: GameViewModel) {
    Page(title = "Новая партия", onBack = model::goMenu) {
        SectionTitle("Участники")
        Text("Один человек и ${ui.config.playerCount - 1} ${pluralBots(ui.config.playerCount - 1)}", color = mutedText)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (3..8).forEach { count ->
                FilterChip(selected = ui.config.playerCount == count, onClick = {
                    model.updateConfig { it.copy(playerCount = count) }
                }, label = { Text(count.toString()) })
            }
        }
        SectionTitle(stringResource(R.string.difficulty))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                FilterChip(selected = ui.config.difficulty == difficulty, onClick = {
                    model.updateConfig { it.copy(difficulty = difficulty) }
                }, label = { Text(difficultyName(difficulty)) })
            }
        }
        SectionTitle(stringResource(R.string.map_size))
        BoardSize.entries.forEach { size ->
            val selected = ui.config.boardSize == size
            Card(
                onClick = { model.updateConfig { it.copy(boardSize = size) } },
                colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF244634) else panelColor),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(sizeName(size), color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${size.territoryCount} территорий · ${size.radius} hex-радиус", color = mutedText, fontSize = 13.sp)
                    }
                    if (selected) Text("✓", color = accentMint, fontSize = 21.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (ui.busy) Text("Готовим карту…", color = mutedText)
        ActionButton(if (ui.busy) "Создание…" else stringResource(R.string.next), onClick = model::createPreview, enabled = !ui.busy)
        ui.message?.let { MessageCard(it) }
    }
}

@Composable
private fun PreviewScreen(ui: AppUiState, model: GameViewModel) {
    val preview = ui.preview
    Page(title = "Предпросмотр карты", onBack = model::backToSetup) {
        if (preview == null) {
            Text("Карта ещё не готова", color = mutedText)
        } else {
            Text("${preview.players.size} участника · ${preview.territories.size} территорий · ${difficultyName(preview.difficulty)} ИИ",
                color = mutedText)
            Spacer(Modifier.height(14.dp))
            HexBoard(preview, height = 360.dp, onTerritoryTap = {})
            Spacer(Modifier.height(12.dp))
            PreviewRoster(preview)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.regenerate), onClick = model::regeneratePreview)
            ActionButton(stringResource(R.string.start), "⚔", onClick = model::startPreviewGame, enabled = !ui.busy)
        }
        ui.message?.let { MessageCard(it) }
    }
}

@Composable
private fun GameScreen(ui: AppUiState, model: GameViewModel) {
    val game = ui.game
    if (game == null) {
        Page(title = "Партия", onBack = model::goMenu) { Text("Партия не загружена", color = mutedText) }
        return
    }
    Page(title = "Раунд ${game.round}", onBack = model::goMenu) {
        val player = game.players.firstOrNull { it.id == game.currentPlayerId }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (game.phase == MatchPhase.FINISHED) "Партия завершена" else player?.name.orEmpty(),
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (game.currentPlayerId == 0 && game.phase == MatchPhase.PLAYING) stringResource(R.string.your_turn)
                    else if (ui.botThinking) "ИИ выбирает ход…" else "${difficultyName(game.difficulty)} ИИ",
                    color = if (game.currentPlayerId == 0) accentMint else mutedText)
            }
            Text("${game.territories.count { it.ownerId == 0}} / ${game.territories.size}", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        HexBoard(
            state = game,
            height = 375.dp,
            selectedId = ui.selectedTerritoryId,
            highlightedIds = ui.selectedTerritoryId?.let { GameEngine.legalTargets(game, it) }.orEmpty(),
            onTerritoryTap = model::tapGameTerritory
        )
        Spacer(Modifier.height(8.dp))
        if (game.phase == MatchPhase.FINISHED) {
            val winner = game.players.firstOrNull { it.id == game.winnerId }?.name ?: "Участник"
            Panel {
                Text(if (game.winnerId == 0) stringResource(R.string.victory) else stringResource(R.string.defeat),
                    color = if (game.winnerId == 0) accentMint else Color(0xFFFFC857), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Победитель: $winner", color = Color.White)
                Text("Атак: ${game.attacks} · захвачено территорий: ${game.ownerChanges}", color = mutedText)
                ActionButton(stringResource(R.string.new_game), onClick = model::openSetup)
                SecondaryButton(stringResource(R.string.menu), onClick = model::goMenu)
            }
        } else {
            BattleCard(game)
            Text(
                if (ui.selectedTerritoryId == null) stringResource(R.string.select_source) else stringResource(R.string.select_target),
                color = mutedText, fontSize = 13.sp
            )
            if (ui.botThinking) {
                MessageCard("${player?.name ?: "Бот"} думает…")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(stringResource(R.string.end_turn), Modifier.weight(1f), onClick = model::endTurn,
                        enabled = game.currentPlayerId == 0 && !ui.busy)
                    SecondaryButton(stringResource(R.string.save), Modifier.weight(1f), onClick = model::openSaves,
                        enabled = !ui.busy)
                }
                SecondaryButton(stringResource(R.string.menu), onClick = model::goMenu, enabled = !ui.busy)
            }
        }
        ui.message?.let { MessageCard(it) }
    }
}

@Composable
private fun BattleCard(game: GameState) {
    val battle = game.lastBattle ?: return
    Panel {
        Text(stringResource(R.string.attack_result), color = Color.White, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("${stringResource(R.string.attacker)} · ${battle.attackDice.size}", color = accentMint, fontSize = 13.sp)
                Text(diceText(battle.attackDice), color = Color.White, fontSize = 16.sp)
                Text("Сумма ${battle.attackSum}", color = mutedText)
            }
            Column(Modifier.weight(1f)) {
                Text("${stringResource(R.string.defender)} · ${battle.defenseDice.size}", color = Color(0xFFFFC857), fontSize = 13.sp)
                Text(diceText(battle.defenseDice), color = Color.White, fontSize = 16.sp)
                Text("Сумма ${battle.defenseSum}", color = mutedText)
            }
        }
        Text(if (battle.captured) stringResource(R.string.captured) else stringResource(R.string.held),
            color = if (battle.captured) accentMint else Color(0xFFFFC857), fontWeight = FontWeight.Bold)
        battle.eliminatedPlayerId?.let { id ->
            Text("${game.players.firstOrNull { it.id == id }?.name ?: "Игрок"} выбыл", color = Color.White)
        }
    }
}

@Composable
private fun PreviewRoster(game: GameState) {
    Panel {
        game.players.forEach { player ->
            val owned = game.territories.filter { it.ownerId == player.id }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(player.name, color = if (player.id == 0) accentMint else Color.White)
                Text("${owned.size} территорий · ${owned.sumOf { it.dice }} куб.", color = mutedText)
            }
        }
    }
}

@Composable
private fun SavesScreen(ui: AppUiState, model: GameViewModel) {
    var pendingOverwrite by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    Page(title = "Сохранения", onBack = model::backFromSaves) {
        ui.saveSlots.forEach { slot ->
            SaveSlotCard(slot = slot, onLoad = { if (slot.exists) model.loadSlot(slot.slot) },
                canSave = ui.savesReturnTo == GAME,
                onSave = { if (slot.exists) pendingOverwrite = slot.slot else model.saveManual(slot.slot) },
                onDelete = { pendingDelete = slot.slot })
        }
        ui.message?.let { MessageCard(it) }
    }
    pendingOverwrite?.let { slot ->
        AlertDialog(
            onDismissRequest = { pendingOverwrite = null },
            title = { Text("Перезаписать слот $slot?") },
            text = { Text("Предыдущее сохранение в этом слоте будет заменено.") },
            confirmButton = { TextButton(onClick = { model.saveManual(slot); pendingOverwrite = null }) { Text("Перезаписать") } },
            dismissButton = { TextButton(onClick = { pendingOverwrite = null }) { Text(stringResource(R.string.back)) } }
        )
    }
    pendingDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить слот $slot?") },
            text = { Text("Сохранение будет удалено с устройства.") },
            confirmButton = { TextButton(onClick = { model.deleteSlot(slot); pendingDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.back)) } }
        )
    }
}

@Composable
private fun SaveSlotCard(slot: SaveSummary, canSave: Boolean, onLoad: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (slot.slot == 0) "Автосохранение" else "Слот ${slot.slot}", color = Color.White, fontWeight = FontWeight.Bold)
                if (slot.exists) {
                    Text("${slot.mapName} · раунд ${slot.round} · ${slot.playerCount} игроков", color = mutedText, fontSize = 13.sp)
                    Text("Ходит ${slot.currentPlayer} · ${formatDate(slot.changedAt)}", color = mutedText, fontSize = 12.sp)
                } else Text(stringResource(R.string.empty_slot), color = mutedText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (slot.exists) {
                SecondaryButton("Загрузить", Modifier.weight(1f), onClick = onLoad)
                if (slot.slot > 0 && canSave) SecondaryButton("Сохранить сюда", Modifier.weight(1f), onClick = onSave)
                if (slot.slot > 0) TextButton(onClick = onDelete) { Text("Удалить", color = Color(0xFFFF9B8E)) }
            } else if (slot.slot > 0 && canSave) ActionButton("Сохранить в слот", Modifier.weight(1f), onClick = onSave)
        }
    }
}

@Composable
private fun CreativeScreen(ui: AppUiState, model: GameViewModel) {
    var showValidation by remember { mutableStateOf(false) }
    val game = ui.game
    Page(title = "Креатив · редактор", onBack = model::goMenu) {
        if (game == null) return@Page
        Text(stringResource(R.string.creative_hint), color = mutedText, fontSize = 13.sp)
        TextField(value = ui.editorName, onValueChange = model::setEditorName, label = { Text("Название карты") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        HexBoard(game, height = 360.dp, selectedId = ui.editorTerritoryId,
            onTerritoryTap = model::selectEditorTerritory)
        val selected = game.territories.firstOrNull { it.id == ui.editorTerritoryId }
        if (selected != null) {
            Panel {
                Text("Территория ${selected.id}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Владелец: ${game.players.firstOrNull { it.id == selected.ownerId }?.name}", color = mutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("◀ Владелец", Modifier.weight(1f), onClick = { model.cycleEditorOwner(-1) })
                    SecondaryButton("Владелец ▶", Modifier.weight(1f), onClick = { model.cycleEditorOwner(1) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton("−", Modifier.weight(1f), onClick = { model.changeEditorDice(-1) })
                    Text("${selected.dice} куб.", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    SecondaryButton("+", Modifier.weight(1f), onClick = { model.changeEditorDice(1) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Отменить · ${ui.editorUndoCount}", Modifier.weight(1f), onClick = model::undoEditor,
                enabled = ui.editorUndoCount > 0)
            SecondaryButton("Повторить · ${ui.editorRedoCount}", Modifier.weight(1f), onClick = model::redoEditor,
                enabled = ui.editorRedoCount > 0)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Случайная основа", Modifier.weight(1f), onClick = model::randomEditorMap)
            SecondaryButton("Проверить", Modifier.weight(1f), onClick = { showValidation = true })
        }
        SecondaryButton("Сохранить карту", onClick = model::saveEditedMap)
        ActionButton("Играть на карте", "⚔", onClick = model::playEditedMap)
        ui.message?.let { MessageCard(it) }
    }
    if (showValidation && game != null) {
        val errors = GameEngine.validationErrors(game)
        AlertDialog(
            onDismissRequest = { showValidation = false },
            title = { Text(if (errors.isEmpty()) "Карта готова" else "Найдены проблемы") },
            text = { Text(errors.firstOrNull() ?: "Связная карта, владельцы и армии в допустимых пределах.") },
            confirmButton = { TextButton(onClick = { showValidation = false }) { Text("Понятно") } }
        )
    }
}

@Composable
private fun MapsScreen(ui: AppUiState, model: GameViewModel) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    Page(title = "Мои карты", onBack = model::goMenu) {
        if (ui.maps.isEmpty()) Text("Сохранённых карт пока нет. Создайте карту в режиме «Креатив».", color = mutedText)
        ui.maps.forEach { map ->
            Panel {
                Text(map.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${map.territoryCount} территорий · ${formatDate(map.changedAt)}", color = mutedText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Играть", Modifier.weight(1f), onClick = { model.playMap(map.mapId) })
                    SecondaryButton("Изменить", Modifier.weight(1f), onClick = { model.openCreative(map.mapId) })
                    TextButton(onClick = { pendingDelete = map.mapId }) { Text("Удалить", color = Color(0xFFFF9B8E)) }
                }
            }
        }
        ActionButton("Создать карту", onClick = { model.openCreative() })
        ui.message?.let { MessageCard(it) }
    }
    pendingDelete?.let { id ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Удалить карту?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = { TextButton(onClick = { model.deleteMap(id); pendingDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.back)) } })
    }
}

@Composable
private fun SettingsScreen(ui: AppUiState, model: GameViewModel) {
    val current = ui.settings
    Page(title = stringResource(R.string.settings), onBack = model::goMenu) {
        SettingRow(stringResource(R.string.music), current.music) { model.updateSettings(current.copy(music = it)) }
        SettingRow(stringResource(R.string.effects), current.effects) { model.updateSettings(current.copy(effects = it)) }
        SettingRow(stringResource(R.string.motion), current.reduceMotion) { model.updateSettings(current.copy(reduceMotion = it)) }
        Panel {
            Text("Громкость эффектов · ${current.effectVolume}%", color = Color.White)
            Slider(value = current.effectVolume.toFloat(), onValueChange = { model.updateSettings(current.copy(effectVolume = it.toInt())) }, valueRange = 0f..100f)
            Text("Громкость музыки · ${current.musicVolume}%", color = Color.White)
            Slider(value = current.musicVolume.toFloat(), onValueChange = { model.updateSettings(current.copy(musicVolume = it.toInt())) }, valueRange = 0f..100f)
        }
        SecondaryButton(stringResource(R.string.tutorial), onClick = model::openHelp)
        Text(stringResource(R.string.app_version), color = mutedText, modifier = Modifier.padding(top = 12.dp))
        Text("Оффлайн · без рекламы и сетевых разрешений", color = mutedText, fontSize = 13.sp)
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun StatsScreen(ui: AppUiState, model: GameViewModel) {
    val stats = ui.stats
    Page(title = stringResource(R.string.statistics), onBack = model::goMenu) {
        StatPanel("Сыграно партий", stats.games.toString())
        StatPanel("Победы", "${stats.wins} · ${stats.winPercent}%")
        StatPanel("Атаки", stats.attacks.toString())
        StatPanel("Захвачено территорий", stats.captured.toString())
        Text("Статистика хранится только на этом устройстве.", color = mutedText, fontSize = 13.sp)
    }
}

@Composable
private fun HelpScreen(ui: AppUiState, model: GameViewModel) {
    Page(title = "Коротко о правилах", onBack = model::goMenu) {
        listOf(
            "1. В начале партии вы ходите первым. Нажмите свою территорию с двумя или более кубиками.",
            "2. Подсвеченные соседние территории — законные цели. Сумма атаки должна быть строго больше защиты.",
            "3. При захвате атакующая армия оставляет один кубик на исходной территории, а цель получает армию атаки минус один.",
            "4. При поражении или ничьей исходная территория остаётся с одним кубиком, защитник не меняется.",
            "5. Завершите ход, чтобы получить подкрепление по размеру крупнейшей связанной группы ваших территорий.",
            "6. Захватите все территории соперников. Игра полностью работает без сети."
        ).forEach { step ->
            Panel { Text(step, color = Color.White) }
        }
    }
}

@Composable
private fun StatPanel(title: String, value: String) {
    Panel {
        Text(title, color = mutedText, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Page(title: String? = null, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(pageBackground).systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            if (title != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                        Text("‹", color = accentMint, fontSize = 32.sp)
                    }
                    Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
            }
            content()
            Spacer(Modifier.height(14.dp))
        }
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = panelColor), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ActionButton(text: String, icon: String? = null, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
        Text(listOfNotNull(icon, text).joinToString("  "), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, color = if (enabled) Color.White else mutedText)
    }
}

@Composable
private fun MessageCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF26382D)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Color.White, modifier = Modifier.padding(14.dp), fontSize = 14.sp)
    }
}

private fun difficultyName(value: Difficulty) = when (value) {
    Difficulty.EASY -> "Лёгкая"
    Difficulty.NORMAL -> "Средняя"
    Difficulty.HARD -> "Сложная"
}

private fun sizeName(value: BoardSize) = when (value) {
    BoardSize.SMALL -> "Маленькая"
    BoardSize.MEDIUM -> "Средняя"
    BoardSize.LARGE -> "Большая"
}

private fun pluralBots(count: Int) = when (count) {
    1 -> "бот"
    2, 3, 4 -> "бота"
    else -> "ботов"
}

private fun diceText(values: List<Int>): String = values.joinToString("  ") { "⚀⚁⚂⚃⚄⚅".getOrNull(it - 1)?.toString() ?: it.toString() }

private fun formatDate(epochMillis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
}.getOrDefault("—")

