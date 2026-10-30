package com.dicewars.game.ai

import com.dicewars.game.domain.Difficulty
import com.dicewars.game.domain.GameEngine
import com.dicewars.game.domain.GameState
import com.dicewars.game.domain.Territory
import kotlin.math.pow
import kotlin.random.Random

object ProbabilityEngine {
    private val distributions: Array<LongArray> = Array(9) { dice -> ways(dice) }
    private val cache = Array(9) { DoubleArray(9) { Double.NaN } }

    fun attackWinProbability(attackDice: Int, defenseDice: Int): Double {
        require(attackDice in 1..8 && defenseDice in 1..8)
        val cached = cache[attackDice][defenseDice]
        if (!cached.isNaN()) return cached
        val attack = distributions[attackDice]
        val defense = distributions[defenseDice]
        var wins = 0L
        for (attackSum in attack.indices) {
            for (defenseSum in defense.indices) {
                if (attackSum > defenseSum) wins += attack[attackSum] * defense[defenseSum]
            }
        }
        val probability = wins.toDouble() / 6.0.pow(attackDice + defenseDice)
        cache[attackDice][defenseDice] = probability
        return probability
    }

    private fun ways(count: Int): LongArray {
        var current = longArrayOf(1L)
        repeat(count) {
            val next = LongArray(current.size + 6)
            current.forEachIndexed { sum, ways ->
                for (face in 1..6) next[sum + face] += ways
            }
            current = next
        }
        return current
    }
}

object BotPolicy {
    fun chooseAction(state: GameState, playerId: Int = state.currentPlayerId, rng: Random = Random.Default): Pair<String, String>? {
        val actions = GameEngine.legalAttacks(state, playerId)
        if (actions.isEmpty()) return null
        val byId = state.territories.associateBy { it.id }
        val neighbors = GameEngine.territoryNeighbors(state.territories)
        val candidates = actions.mapNotNull { (fromId, toId) ->
            val source = byId[fromId] ?: return@mapNotNull null
            val target = byId[toId] ?: return@mapNotNull null
            val probability = ProbabilityEngine.attackWinProbability(source.dice, target.dice)
            val enemyTerritories = state.territories.count { it.ownerId == target.ownerId }
            val eliminationValue = if (enemyTerritories == 1) 3.0 else 0.0
            val borderValue = neighbors[toId].orEmpty().count { byId[it]?.ownerId != target.ownerId } * 0.04
            val componentValue = componentGain(state, source, target)
            val counterRisk = expectedCounterRisk(state, source, target, neighbors, byId)
            val forceLoss = (source.dice - 1) * 0.12
            val base = probability * (0.9 + eliminationValue + borderValue + componentValue) -
                (1.0 - probability) * (0.30 + forceLoss) - counterRisk
            val score = when (state.difficulty) {
                Difficulty.EASY -> base + rng.nextDouble(-0.45, 0.45)
                Difficulty.NORMAL -> base
                Difficulty.HARD -> base + componentValue * 0.55 + eliminationValue * 0.20 - counterRisk * 0.25
            }
            Triple(fromId to toId, score, probability)
        }
        if (candidates.isEmpty()) return null
        val threshold = when {
            state.staleRounds >= 3 -> -0.6
            state.difficulty == Difficulty.EASY -> 0.12
            state.difficulty == Difficulty.NORMAL -> 0.24
            else -> 0.30
        }
        val best = candidates.maxByOrNull { it.second } ?: return null
        if (best.second < threshold && state.staleRounds < 3) return null
        return best.first
    }

    private fun componentGain(state: GameState, source: Territory, target: Territory): Double {
        val before = GameEngine.largestConnectedGroup(state, source.ownerId)
        val hypothetical = state.copy(territories = state.territories.map {
            if (it.id == target.id) it.copy(ownerId = source.ownerId) else it
        })
        val after = GameEngine.largestConnectedGroup(hypothetical, source.ownerId)
        return (after - before).coerceAtLeast(0) * 0.16
    }

    private fun expectedCounterRisk(
        state: GameState,
        source: Territory,
        target: Territory,
        neighbors: Map<String, Set<String>>,
        byId: Map<String, Territory>
    ): Double {
        val enemyThreats = neighbors[target.id].orEmpty().mapNotNull { byId[it] }
            .filter { it.ownerId != source.ownerId && it.dice >= 2 }
        if (enemyThreats.isEmpty()) return 0.0
        val defenderDiceAfterCapture = (source.dice - 1).coerceAtLeast(1)
        return enemyThreats.maxOf { enemy ->
            ProbabilityEngine.attackWinProbability(enemy.dice, defenderDiceAfterCapture) *
                (if (target.ownerId == source.ownerId) 0.0 else 0.20)
        }
    }
}

