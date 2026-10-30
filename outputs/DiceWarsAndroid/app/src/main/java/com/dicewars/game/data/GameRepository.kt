package com.dicewars.game.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.dicewars.game.domain.BattleResult
import com.dicewars.game.domain.BoardSize
import com.dicewars.game.domain.Difficulty
import com.dicewars.game.domain.GameState
import com.dicewars.game.domain.HexCell
import com.dicewars.game.domain.MatchPhase
import com.dicewars.game.domain.Player
import com.dicewars.game.domain.Territory
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

data class SaveSummary(
    val slot: Int,
    val exists: Boolean,
    val changedAt: Long = 0,
    val mapName: String = "",
    val round: Int = 0,
    val playerCount: Int = 0,
    val currentPlayer: String = ""
)

data class SavedMapSummary(
    val mapId: String,
    val name: String,
    val changedAt: Long,
    val territoryCount: Int
)

data class LocalSettings(
    val music: Boolean = false,
    val effects: Boolean = true,
    val reduceMotion: Boolean = false,
    val effectVolume: Int = 75,
    val musicVolume: Int = 35
)

data class LocalStats(
    val games: Int = 0,
    val wins: Int = 0,
    val attacks: Int = 0,
    val captured: Int = 0
) {
    val winPercent: Int get() = if (games == 0) 0 else (wins * 100 / games)
}

class GameRepository(context: Context) {
    private val appContext = context.applicationContext
    private val savesDir = File(appContext.filesDir, "saves").apply { mkdirs() }
    private val mapsDir = File(appContext.filesDir, "maps").apply { mkdirs() }
    private val preferences = appContext.getSharedPreferences("dice-wars-local", Context.MODE_PRIVATE)

    fun save(state: GameState, slot: Int = 0) {
        require(slot in 0..5)
        atomicWrite(slotFile(slot), encode(state, "Случайная карта").toString())
    }

    fun load(slot: Int = 0): GameState? = runCatching {
        if (slot !in 0..5) return null
        val file = slotFile(slot)
        if (!file.isFile) null else decode(JSONObject(file.readText()))
    }.getOrNull()

    fun delete(slot: Int) {
        if (slot in 1..5) slotFile(slot).delete()
    }

    fun saveSummaries(): List<SaveSummary> = (0..5).map { slot ->
        val file = slotFile(slot)
        if (!file.isFile) SaveSummary(slot, false) else runCatching {
            val data = JSONObject(file.readText())
            val state = decode(data)
            SaveSummary(
                slot = slot,
                exists = true,
                changedAt = file.lastModified(),
                mapName = data.optString("mapName", "Случайная карта"),
                round = state.round,
                playerCount = state.players.size,
                currentPlayer = state.players.firstOrNull { it.id == state.currentPlayerId }?.name.orEmpty()
            )
        }.getOrElse { SaveSummary(slot, false) }
    }

    fun saveMap(state: GameState, name: String): SavedMapSummary {
        val mapId = state.mapId
        val file = File(mapsDir, "$mapId.json")
        atomicWrite(file, encode(state, name.ifBlank { "Моя карта" }).toString())
        return SavedMapSummary(mapId, name.ifBlank { "Моя карта" }, file.lastModified(), state.territories.size)
    }

    fun loadMap(mapId: String): GameState? = runCatching {
        val file = File(mapsDir, "$mapId.json")
        if (!file.isFile || file.parentFile != mapsDir) null else decode(JSONObject(file.readText()))
    }.getOrNull()

    fun maps(): List<SavedMapSummary> = mapsDir.listFiles { file -> file.extension == "json" }
        .orEmpty().mapNotNull { file -> runCatching {
            val json = JSONObject(file.readText())
            val game = decode(json)
            SavedMapSummary(game.mapId, json.optString("mapName", "Моя карта"), file.lastModified(), game.territories.size)
        }.getOrNull() }.sortedByDescending { it.changedAt }

    fun deleteMap(mapId: String) {
        File(mapsDir, "$mapId.json").takeIf { it.parentFile == mapsDir }?.delete()
    }

    fun settings(): LocalSettings = LocalSettings(
        music = preferences.getBoolean("music", false),
        effects = preferences.getBoolean("effects", true),
        reduceMotion = preferences.getBoolean("reduce_motion", false),
        effectVolume = preferences.getInt("effect_volume", 75),
        musicVolume = preferences.getInt("music_volume", 35)
    )

    fun saveSettings(settings: LocalSettings) {
        preferences.edit()
            .putBoolean("music", settings.music)
            .putBoolean("effects", settings.effects)
            .putBoolean("reduce_motion", settings.reduceMotion)
            .putInt("effect_volume", settings.effectVolume.coerceIn(0, 100))
            .putInt("music_volume", settings.musicVolume.coerceIn(0, 100))
            .apply()
    }

    fun stats(): LocalStats = LocalStats(
        games = preferences.getInt("stat_games", 0),
        wins = preferences.getInt("stat_wins", 0),
        attacks = preferences.getInt("stat_attacks", 0),
        captured = preferences.getInt("stat_captured", 0)
    )

    fun recordFinished(state: GameState): GameState {
        if (state.phase != MatchPhase.FINISHED || state.resultRecorded) return state
        synchronized(this) {
            val recorded = preferences.getStringSet("recorded_matches", emptySet()).orEmpty().toMutableSet()
            if (state.matchId !in recorded) {
                recorded += state.matchId
                preferences.edit()
                    .putStringSet("recorded_matches", recorded)
                    .putInt("stat_games", stats().games + 1)
                    .putInt("stat_wins", stats().wins + if (state.winnerId == 0) 1 else 0)
                    .putInt("stat_attacks", stats().attacks + state.attacks)
                    .putInt("stat_captured", stats().captured + state.ownerChanges)
                    .apply()
            }
        }
        return state.copy(resultRecorded = true)
    }

    private fun slotFile(slot: Int) = File(savesDir, if (slot == 0) "autosave.json" else "slot_$slot.json")

    private fun atomicWrite(destination: File, contents: String) {
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        val backup = File(destination.parentFile, destination.name + ".bak")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(contents.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (destination.exists() && !destination.renameTo(backup)) error("Не удалось сохранить предыдущую версию")
            if (!temporary.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Не удалось завершить запись файла")
            }
            backup.delete()
        } finally {
            temporary.delete()
        }
    }

    private fun encode(state: GameState, mapName: String): JSONObject = JSONObject().apply {
        put("schemaVersion", 4)
        put("mapName", mapName)
        put("matchId", state.matchId)
        put("mapId", state.mapId)
        put("seed", state.seed)
        put("savedAt", Instant.now().toEpochMilli())
        put("playerCount", state.players.size)
        put("players", JSONArray().apply {
            state.players.forEach { player -> put(JSONObject().apply {
                put("id", player.id); put("name", player.name); put("isHuman", player.isHuman)
            }) }
        })
        put("currentPlayerId", state.currentPlayerId)
        put("round", state.round)
        put("difficulty", state.difficulty.name)
        put("boardSize", state.boardSize.name)
        put("phase", state.phase.name)
        put("winnerId", state.winnerId ?: JSONObject.NULL)
        put("attacks", state.attacks)
        put("ownerChanges", state.ownerChanges)
        put("staleRounds", state.staleRounds)
        put("ownerChangesAtRoundStart", state.ownerChangesAtRoundStart)
        put("resultRecorded", state.resultRecorded)
        put("territories", JSONArray().apply {
            state.territories.forEach { territory -> put(JSONObject().apply {
                put("id", territory.id); put("ownerId", territory.ownerId); put("dice", territory.dice)
                put("hexes", JSONArray().apply { territory.hexes.forEach { put(JSONObject().put("q", it.q).put("r", it.r)) } })
            }) }
        })
        put("lastBattle", state.lastBattle?.let { battle -> JSONObject().apply {
            put("fromId", battle.fromId); put("toId", battle.toId)
            put("attackDice", JSONArray(battle.attackDice)); put("defenseDice", JSONArray(battle.defenseDice))
            put("attackSum", battle.attackSum); put("defenseSum", battle.defenseSum); put("captured", battle.captured)
            put("eliminatedPlayerId", battle.eliminatedPlayerId ?: JSONObject.NULL)
        } } ?: JSONObject.NULL)
    }

    private fun decode(json: JSONObject): GameState {
        require(json.optInt("schemaVersion") == 4) { "Неизвестная версия сохранения" }
        val players = json.getJSONArray("players").let { array ->
            (0 until array.length()).map { index -> array.getJSONObject(index).let {
                Player(it.getInt("id"), it.getString("name"), it.getBoolean("isHuman"))
            } }
        }
        val territories = json.getJSONArray("territories").let { array ->
            (0 until array.length()).map { index -> array.getJSONObject(index).let { item ->
                val hexArray = item.getJSONArray("hexes")
                val hexes = (0 until hexArray.length()).map { hexIndex -> hexArray.getJSONObject(hexIndex).let { HexCell(it.getInt("q"), it.getInt("r")) } }
                Territory(item.getString("id"), hexes, item.getInt("ownerId"), item.getInt("dice"))
            } }
        }
        val battleJson = json.optJSONObject("lastBattle")
        val battle = battleJson?.let { item ->
            BattleResult(
                fromId = item.getString("fromId"), toId = item.getString("toId"),
                attackDice = item.getJSONArray("attackDice").toIntList(),
                defenseDice = item.getJSONArray("defenseDice").toIntList(),
                attackSum = item.getInt("attackSum"), defenseSum = item.getInt("defenseSum"),
                captured = item.getBoolean("captured"),
                eliminatedPlayerId = if (item.isNull("eliminatedPlayerId")) null else item.getInt("eliminatedPlayerId")
            )
        }
        return GameState(
            matchId = json.getString("matchId"), mapId = json.getString("mapId"), seed = json.optLong("seed"),
            territories = territories, players = players,
            currentPlayerId = json.getInt("currentPlayerId"), round = json.getInt("round"),
            difficulty = Difficulty.valueOf(json.getString("difficulty")),
            boardSize = BoardSize.valueOf(json.getString("boardSize")),
            phase = MatchPhase.valueOf(json.getString("phase")),
            winnerId = if (json.isNull("winnerId")) null else json.getInt("winnerId"),
            attacks = json.optInt("attacks"), ownerChanges = json.optInt("ownerChanges"),
            staleRounds = json.optInt("staleRounds"),
            ownerChangesAtRoundStart = json.optInt("ownerChangesAtRoundStart"), lastBattle = battle,
            resultRecorded = json.optBoolean("resultRecorded", false)
        )
    }

    private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }
}

