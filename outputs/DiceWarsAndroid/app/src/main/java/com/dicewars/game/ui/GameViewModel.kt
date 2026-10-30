package com.dicewars.game.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dicewars.game.ai.BotPolicy
import com.dicewars.game.data.AudioController
import com.dicewars.game.data.GameRepository
import com.dicewars.game.data.LocalSettings
import com.dicewars.game.data.LocalStats
import com.dicewars.game.data.SaveSummary
import com.dicewars.game.data.SavedMapSummary
import com.dicewars.game.domain.BoardSize
import com.dicewars.game.domain.Difficulty
import com.dicewars.game.domain.EngineResult
import com.dicewars.game.domain.GameConfig
import com.dicewars.game.domain.GameEngine
import com.dicewars.game.domain.GameState
import com.dicewars.game.domain.MatchPhase
import com.dicewars.game.generation.BoardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class AppScreen { MENU, SETUP, PREVIEW, GAME, SAVES, CREATIVE, MAPS, SETTINGS, STATS, HELP }

data class AppUiState(
    val screen: AppScreen = AppScreen.MENU,
    val savesReturnTo: AppScreen = AppScreen.MENU,
    val config: GameConfig = GameConfig(),
    val preview: GameState? = null,
    val game: GameState? = null,
    val selectedTerritoryId: String? = null,
    val editorTerritoryId: String? = null,
    val editorName: String = "Моя карта",
    val message: String? = null,
    val busy: Boolean = false,
    val botThinking: Boolean = false,
    val confirmNewGame: Boolean = false,
    val autoSaveAvailable: Boolean = false,
    val initialized: Boolean = false,
    val saveSlots: List<SaveSummary> = emptyList(),
    val maps: List<SavedMapSummary> = emptyList(),
    val editorUndoCount: Int = 0,
    val editorRedoCount: Int = 0,
    val settings: LocalSettings = LocalSettings(),
    val stats: LocalStats = LocalStats()
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    private val audio = AudioController(application)
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()
    private var botJob: Job? = null
    private var appForeground = true
    private val editorUndo = ArrayDeque<GameState>()
    private val editorRedo = ArrayDeque<GameState>()

    init {
        AudioController.register(audio)
        audio.apply(repository.settings())
        viewModelScope.launch {
            val auto = withContext(Dispatchers.IO) { repository.load(0) }
            val settings = withContext(Dispatchers.IO) { repository.settings() }
            val stats = withContext(Dispatchers.IO) { repository.stats() }
            val slots = withContext(Dispatchers.IO) { repository.saveSummaries() }
            _ui.value = _ui.value.copy(
                autoSaveAvailable = auto != null,
                settings = settings,
                stats = stats,
                saveSlots = slots,
                initialized = true
            )
        }
    }

    fun openSetup() {
        if (!_ui.value.initialized) {
            viewModelScope.launch {
                val hasSave = withContext(Dispatchers.IO) { repository.load(0) != null }
                _ui.value = _ui.value.copy(autoSaveAvailable = hasSave, initialized = true)
                if (hasSave) _ui.value = _ui.value.copy(confirmNewGame = true) else beginSetup()
            }
            return
        }
        if (_ui.value.autoSaveAvailable) {
            _ui.value = _ui.value.copy(confirmNewGame = true, message = null)
        } else beginSetup()
    }

    fun confirmStartNewGame() {
        _ui.value = _ui.value.copy(confirmNewGame = false)
        beginSetup()
    }

    fun cancelStartNewGame() { _ui.value = _ui.value.copy(confirmNewGame = false) }

    private fun beginSetup() {
        stopBots()
        _ui.value = _ui.value.copy(screen = AppScreen.SETUP, config = GameConfig(seed = System.currentTimeMillis()), message = null)
    }

    fun updateConfig(transform: (GameConfig) -> GameConfig) {
        _ui.value = _ui.value.copy(config = transform(_ui.value.config))
    }

    fun createPreview() {
        val config = _ui.value.config.copy(seed = System.currentTimeMillis())
        _ui.value = _ui.value.copy(config = config, busy = true, message = null)
        viewModelScope.launch {
            val game = runCatching { withContext(Dispatchers.Default) { BoardGenerator.generate(config) } }.getOrNull()
            _ui.value = _ui.value.copy(preview = game, screen = if (game == null) AppScreen.SETUP else AppScreen.PREVIEW,
                busy = false, message = if (game == null) "Не удалось создать карту. Попробуйте ещё раз." else null)
        }
    }

    fun regeneratePreview() {
        val config = _ui.value.config.copy(seed = System.currentTimeMillis() xor System.nanoTime())
        _ui.value = _ui.value.copy(config = config, busy = true)
        viewModelScope.launch {
            val game = runCatching { withContext(Dispatchers.Default) { BoardGenerator.generate(config) } }.getOrNull()
            _ui.value = _ui.value.copy(preview = game, busy = false, message = if (game == null) "Ошибка генерации карты" else null)
        }
    }

    fun startPreviewGame() {
        val game = _ui.value.preview ?: return
        startMatch(game.copy(lastBattle = null))
    }

    fun backToSetup() { _ui.value = _ui.value.copy(screen = AppScreen.SETUP, message = null) }

    private fun startMatch(game: GameState) {
        stopBots()
        _ui.value = _ui.value.copy(screen = AppScreen.GAME, game = game, selectedTerritoryId = null,
            preview = null, busy = false, botThinking = false, message = null)
        viewModelScope.launch {
            val saved = persist(game)
            _ui.value = _ui.value.copy(game = saved, autoSaveAvailable = true)
            startBotTurnsIfNeeded()
        }
    }

    fun continueGame() {
        stopBots()
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.load(0) }
            if (loaded == null) {
                _ui.value = _ui.value.copy(message = "Нет сохранённой партии")
            } else {
                _ui.value = _ui.value.copy(screen = AppScreen.GAME, game = loaded, selectedTerritoryId = null,
                    busy = false, botThinking = false, message = null)
                startBotTurnsIfNeeded()
            }
        }
    }

    fun leaveGame() {
        stopBots()
        val current = _ui.value.game
        viewModelScope.launch {
            if (current != null) withContext(Dispatchers.IO) { repository.save(current, 0) }
            val slots = withContext(Dispatchers.IO) { repository.saveSummaries() }
            _ui.value = _ui.value.copy(screen = AppScreen.MENU, selectedTerritoryId = null,
                busy = false, botThinking = false, autoSaveAvailable = current != null, saveSlots = slots)
        }
    }

    fun tapGameTerritory(id: String) {
        val uiState = _ui.value
        val game = uiState.game ?: return
        if (uiState.screen != AppScreen.GAME || uiState.busy || uiState.botThinking || game.currentPlayerId != 0 || game.phase != MatchPhase.PLAYING) return
        val selected = uiState.selectedTerritoryId
        val tapped = game.territories.firstOrNull { it.id == id } ?: return
        if (selected == null || tapped.ownerId == 0) {
            _ui.value = uiState.copy(selectedTerritoryId = if (selected == id) null else id.takeIf { tapped.ownerId == 0 }, message = null)
            return
        }
        if (id == selected) {
            _ui.value = uiState.copy(selectedTerritoryId = null)
            return
        }
        if (id !in GameEngine.legalTargets(game, selected)) {
            _ui.value = uiState.copy(selectedTerritoryId = if (tapped.ownerId == 0) id else selected,
                message = "Эта территория не является доступной целью")
            return
        }
        _ui.value = uiState.copy(busy = true, message = null)
        viewModelScope.launch {
            when (val result = GameEngine.attack(game, selected, id)) {
                is EngineResult.Rejected -> _ui.value = _ui.value.copy(busy = false, message = result.reason)
                is EngineResult.Applied -> {
                    audio.playEffect(result.battle?.captured)
                    val committed = persist(result.state)
                    _ui.value = _ui.value.copy(game = committed, selectedTerritoryId = null,
                        busy = true, autoSaveAvailable = true)
                    delay(if (_ui.value.settings.reduceMotion) 160 else 780)
                    _ui.value = _ui.value.copy(busy = false)
                    if (committed.phase != MatchPhase.FINISHED) startBotTurnsIfNeeded()
                }
            }
        }
    }

    fun endTurn() {
        val state = _ui.value
        val game = state.game ?: return
        if (state.busy || state.botThinking || game.currentPlayerId != 0 || game.phase != MatchPhase.PLAYING) return
        when (val result = GameEngine.endTurn(game)) {
            is EngineResult.Rejected -> _ui.value = state.copy(message = result.reason)
            is EngineResult.Applied -> {
                val reinforcement = result.reinforcement
                val note = reinforcement?.let {
                    "Подкрепление: +${it.added}" + if (it.lost > 0) ", сгорело ${it.lost}" else ""
                }
                _ui.value = state.copy(game = result.state, selectedTerritoryId = null, message = note)
                viewModelScope.launch {
                    val saved = persist(result.state)
                    _ui.value = _ui.value.copy(game = saved, autoSaveAvailable = true)
                    startBotTurnsIfNeeded()
                }
            }
        }
    }

    fun openSaves() {
        stopBots()
        val returnTo = if (_ui.value.screen == AppScreen.GAME) AppScreen.GAME else AppScreen.MENU
        viewModelScope.launch {
            val slots = withContext(Dispatchers.IO) { repository.saveSummaries() }
            _ui.value = _ui.value.copy(screen = AppScreen.SAVES, savesReturnTo = returnTo, saveSlots = slots, message = null)
        }
    }

    fun backFromSaves() { _ui.value = _ui.value.copy(screen = _ui.value.savesReturnTo, message = null) }

    fun saveManual(slot: Int) {
        val game = _ui.value.game ?: return
        if (slot !in 1..5 || _ui.value.busy) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.save(game, slot) }
                val slots = withContext(Dispatchers.IO) { repository.saveSummaries() }
                _ui.value = _ui.value.copy(saveSlots = slots, message = "Партия сохранена в слот $slot")
            }.onFailure { _ui.value = _ui.value.copy(message = "Не удалось сохранить слот: ${it.message ?: "ошибка записи"}") }
        }
    }

    fun loadSlot(slot: Int) {
        stopBots()
        viewModelScope.launch {
            val game = withContext(Dispatchers.IO) { repository.load(slot) }
            if (game == null) _ui.value = _ui.value.copy(message = "Слот пуст или повреждён")
            else {
                _ui.value = _ui.value.copy(screen = AppScreen.GAME, game = game, selectedTerritoryId = null,
                    busy = false, botThinking = false, message = null)
                startBotTurnsIfNeeded()
            }
        }
    }

    fun deleteSlot(slot: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(slot) }
            _ui.value = _ui.value.copy(saveSlots = withContext(Dispatchers.IO) { repository.saveSummaries() }, message = "Слот удалён")
        }
    }

    fun openCreative(mapId: String? = null) {
        stopBots()
        editorUndo.clear()
        editorRedo.clear()
        _ui.value = _ui.value.copy(screen = AppScreen.CREATIVE, busy = true, editorTerritoryId = null, message = null)
        viewModelScope.launch {
            val editor = withContext(Dispatchers.IO) { mapId?.let(repository::loadMap) }
                ?: withContext(Dispatchers.Default) {
                    BoardGenerator.generate(GameConfig(4, Difficulty.NORMAL, BoardSize.SMALL, System.currentTimeMillis()))
                }
            _ui.value = _ui.value.copy(game = editor, screen = AppScreen.CREATIVE, busy = false,
                editorUndoCount = 0, editorRedoCount = 0,
                editorName = if (mapId == null) "Моя карта" else _ui.value.maps.firstOrNull { it.mapId == mapId }?.name ?: "Моя карта")
        }
    }

    fun selectEditorTerritory(id: String) {
        _ui.value = _ui.value.copy(editorTerritoryId = if (_ui.value.editorTerritoryId == id) null else id, message = null)
    }

    fun setEditorName(name: String) { _ui.value = _ui.value.copy(editorName = name.take(40)) }

    fun cycleEditorOwner(delta: Int) {
        val state = _ui.value
        val game = state.game ?: return
        val id = state.editorTerritoryId ?: return
        val count = game.players.size
        val changed = game.territories.map { if (it.id == id) it.copy(ownerId = (it.ownerId + delta + count) % count) else it }
        pushEditorChange(game.copy(territories = changed), state)
    }

    fun changeEditorDice(delta: Int) {
        val state = _ui.value
        val game = state.game ?: return
        val id = state.editorTerritoryId ?: return
        val changed = game.territories.map { if (it.id == id) it.copy(dice = (it.dice + delta).coerceIn(1, 8)) else it }
        pushEditorChange(game.copy(territories = changed), state)
    }

    fun undoEditor() {
        if (editorUndo.isEmpty()) return
        val current = _ui.value.game ?: return
        editorRedo.addLast(current)
        val previous = editorUndo.removeLast()
        _ui.value = _ui.value.copy(game = previous, editorUndoCount = editorUndo.size, editorRedoCount = editorRedo.size, message = null)
    }

    fun redoEditor() {
        if (editorRedo.isEmpty()) return
        val current = _ui.value.game ?: return
        editorUndo.addLast(current)
        val next = editorRedo.removeLast()
        _ui.value = _ui.value.copy(game = next, editorUndoCount = editorUndo.size, editorRedoCount = editorRedo.size, message = null)
    }

    private fun pushEditorChange(next: GameState, before: AppUiState) {
        val old = before.game ?: return
        if (old.territories == next.territories) return
        editorUndo.addLast(old)
        while (editorUndo.size > 80) editorUndo.removeFirst()
        editorRedo.clear()
        _ui.value = before.copy(game = next, editorUndoCount = editorUndo.size, editorRedoCount = 0, message = null)
    }

    fun randomEditorMap() {
        val current = _ui.value.game ?: return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            val new = withContext(Dispatchers.Default) {
                BoardGenerator.generate(GameConfig(current.players.size, current.difficulty, current.boardSize,
                    System.currentTimeMillis() xor System.nanoTime()))
            }
            pushEditorChange(new, _ui.value.copy(busy = false, editorTerritoryId = null, message = "Создана новая основа"))
            _ui.value = _ui.value.copy(busy = false, editorTerritoryId = null, message = "Создана новая основа")
        }
    }

    fun saveEditedMap() {
        val game = _ui.value.game ?: return
        val errors = GameEngine.validationErrors(game)
        if (errors.isNotEmpty()) {
            _ui.value = _ui.value.copy(message = errors.first())
            return
        }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { repository.saveMap(game, _ui.value.editorName) }
            val maps = withContext(Dispatchers.IO) { repository.maps() }
            _ui.value = _ui.value.copy(maps = maps, message = "Карта «${saved.name}» сохранена")
        }
    }

    fun playEditedMap() {
        val game = _ui.value.game ?: return
        val errors = GameEngine.validationErrors(game)
        if (errors.isNotEmpty()) {
            _ui.value = _ui.value.copy(message = errors.first())
            return
        }
        startMatch(game.copy(matchId = com.dicewars.game.domain.newId(game.seed xor System.nanoTime(), "custom-match"),
            currentPlayerId = 0, round = 1, phase = MatchPhase.PLAYING, winnerId = null, attacks = 0,
            ownerChanges = 0, staleRounds = 0, ownerChangesAtRoundStart = 0, lastBattle = null, resultRecorded = false))
    }

    fun openMaps() {
        stopBots()
        viewModelScope.launch {
            val maps = withContext(Dispatchers.IO) { repository.maps() }
            _ui.value = _ui.value.copy(screen = AppScreen.MAPS, maps = maps, message = null)
        }
    }

    fun playMap(mapId: String) {
        stopBots()
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { repository.loadMap(mapId) }
            if (state == null) {
                _ui.value = _ui.value.copy(message = "Не удалось открыть карту")
            } else {
                val ready = state.copy(
                    matchId = com.dicewars.game.domain.newId(state.seed xor System.nanoTime(), "map-match"),
                    currentPlayerId = 0,
                    round = 1,
                    phase = MatchPhase.PLAYING,
                    winnerId = null,
                    attacks = 0,
                    ownerChanges = 0,
                    staleRounds = 0,
                    ownerChangesAtRoundStart = 0,
                    lastBattle = null,
                    resultRecorded = false
                )
                startMatch(ready)
            }
        }
    }

    fun deleteMap(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteMap(id) }
            _ui.value = _ui.value.copy(maps = withContext(Dispatchers.IO) { repository.maps() }, message = "Карта удалена")
        }
    }

    fun openSettings() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { repository.settings() }
            _ui.value = _ui.value.copy(screen = AppScreen.SETTINGS, settings = settings, message = null)
        }
    }

    fun updateSettings(settings: LocalSettings) {
        _ui.value = _ui.value.copy(settings = settings)
        audio.apply(settings)
        viewModelScope.launch(Dispatchers.IO) { repository.saveSettings(settings) }
    }

    fun audioForeground(value: Boolean) { audio.setForeground(value) }

    fun onBackground() {
        appForeground = false
        stopBots()
        audio.setForeground(false)
        val current = _ui.value.game ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.save(current, 0) }
        }
    }

    fun onForeground() {
        appForeground = true
        audio.setForeground(true)
        startBotTurnsIfNeeded()
    }

    fun openStats() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(screen = AppScreen.STATS, stats = withContext(Dispatchers.IO) { repository.stats() }, message = null)
        }
    }

    fun openHelp() { _ui.value = _ui.value.copy(screen = AppScreen.HELP, message = null) }

    fun goMenu() {
        if (_ui.value.screen == AppScreen.GAME) leaveGame()
        else _ui.value = _ui.value.copy(screen = AppScreen.MENU, message = null)
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }

    private suspend fun persist(state: GameState): GameState {
        return try {
            withContext(NonCancellable + Dispatchers.IO) {
                val result = repository.recordFinished(state)
                repository.save(result, 0)
                val stats = repository.stats()
                _ui.value = _ui.value.copy(
                    game = if (_ui.value.game?.matchId == result.matchId) result else _ui.value.game,
                    stats = stats,
                    autoSaveAvailable = true
                )
                result
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _ui.value = _ui.value.copy(message = "Автосохранение не удалось: ${error.message ?: "ошибка записи"}")
            state
        }
    }

    private fun startBotTurnsIfNeeded() {
        val initial = _ui.value
        val game = initial.game ?: return
        if (!appForeground || initial.screen != AppScreen.GAME || game.phase != MatchPhase.PLAYING || game.currentPlayerId == 0) return
        botJob?.cancel()
        botJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(botThinking = true, selectedTerritoryId = null)
            var attacksThisTurn = 0
            while (true) {
                val current = _ui.value.game ?: break
                if (_ui.value.screen != AppScreen.GAME || current.phase != MatchPhase.PLAYING || current.currentPlayerId == 0) break
                val action = withContext(Dispatchers.Default) {
                    val rng = Random(current.seed xor (current.round.toLong() shl 24) xor current.attacks.toLong() xor current.currentPlayerId.toLong())
                    BotPolicy.chooseAction(current, current.currentPlayerId, rng)
                }
                val nextState = if (action != null && attacksThisTurn < 48) {
                    attacksThisTurn++
                    when (val result = GameEngine.attack(current, action.first, action.second)) {
                        is EngineResult.Applied -> result.state
                        is EngineResult.Rejected -> {
                            attacksThisTurn = 0
                            (GameEngine.endTurn(current) as? EngineResult.Applied)?.state
                        }
                    }
                } else {
                    attacksThisTurn = 0
                    when (val result = GameEngine.endTurn(current)) {
                        is EngineResult.Applied -> result.state
                        is EngineResult.Rejected -> null
                    }
                }
                if (nextState == null) break
                if (nextState.attacks > current.attacks) audio.playEffect(nextState.lastBattle?.captured)
                val committed = persist(nextState)
                _ui.value = _ui.value.copy(game = committed, autoSaveAvailable = true)
                if (committed.phase == MatchPhase.FINISHED) break
                delay(if (_ui.value.settings.reduceMotion) 80 else 380)
            }
            _ui.value = _ui.value.copy(botThinking = false)
        }
    }

    private fun stopBots() {
        botJob?.cancel()
        botJob = null
        _ui.value = _ui.value.copy(botThinking = false)
    }

    override fun onCleared() {
        audio.release()
        AudioController.clear(audio)
        super.onCleared()
    }
}

