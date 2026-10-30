package com.dicewars.game.domain

import java.util.UUID

data class HexCell(val q: Int, val r: Int)

data class Territory(
    val id: String,
    val hexes: List<HexCell>,
    val ownerId: Int,
    val dice: Int
)

data class Player(
    val id: Int,
    val name: String,
    val isHuman: Boolean
)

enum class Difficulty { EASY, NORMAL, HARD }
enum class BoardSize(val radius: Int, val territoryCount: Int) {
    SMALL(4, 28), MEDIUM(5, 40), LARGE(6, 56)
}
enum class MatchPhase { PLAYING, FINISHED }

data class BattleResult(
    val fromId: String,
    val toId: String,
    val attackDice: List<Int>,
    val defenseDice: List<Int>,
    val attackSum: Int,
    val defenseSum: Int,
    val captured: Boolean,
    val eliminatedPlayerId: Int? = null
)

data class GameState(
    val matchId: String,
    val mapId: String,
    val seed: Long,
    val territories: List<Territory>,
    val players: List<Player>,
    val currentPlayerId: Int,
    val round: Int,
    val difficulty: Difficulty,
    val boardSize: BoardSize,
    val phase: MatchPhase = MatchPhase.PLAYING,
    val winnerId: Int? = null,
    val attacks: Int = 0,
    val ownerChanges: Int = 0,
    val staleRounds: Int = 0,
    val ownerChangesAtRoundStart: Int = 0,
    val lastBattle: BattleResult? = null,
    val resultRecorded: Boolean = false
)

data class GameConfig(
    val playerCount: Int = 4,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val boardSize: BoardSize = BoardSize.MEDIUM,
    val seed: Long = System.currentTimeMillis()
)

data class ReinforcementResult(
    val added: Int,
    val lost: Int,
    val groupSize: Int
)

sealed interface EngineResult {
    data class Applied(
        val state: GameState,
        val battle: BattleResult? = null,
        val reinforcement: ReinforcementResult? = null
    ) : EngineResult
    data class Rejected(val reason: String) : EngineResult
}

fun newId(seed: Long, suffix: String): String =
    UUID.nameUUIDFromBytes("$seed:$suffix".toByteArray()).toString()

