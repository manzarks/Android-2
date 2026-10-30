package com.dicewars.game.domain

import kotlin.random.Random

object GameEngine {
    private val directions = listOf(
        HexCell(1, 0), HexCell(1, -1), HexCell(0, -1),
        HexCell(-1, 0), HexCell(-1, 1), HexCell(0, 1)
    )

    fun territoryNeighbors(territories: List<Territory>): Map<String, Set<String>> {
        val byCell = HashMap<Pair<Int, Int>, String>()
        territories.forEach { territory -> territory.hexes.forEach { byCell[it.q to it.r] = territory.id } }
        val result = territories.associate { it.id to linkedSetOf<String>() }.toMutableMap()
        territories.forEach { territory ->
            territory.hexes.forEach { cell ->
                directions.forEach { direction ->
                    val other = byCell[(cell.q + direction.q) to (cell.r + direction.r)]
                    if (other != null && other != territory.id) {
                        result.getValue(territory.id).add(other)
                        result.getValue(other).add(territory.id)
                    }
                }
            }
        }
        return result
    }

    fun legalTargets(state: GameState, sourceId: String): Set<String> {
        if (state.phase != MatchPhase.PLAYING) return emptySet()
        val source = state.territories.firstOrNull { it.id == sourceId } ?: return emptySet()
        if (source.ownerId != state.currentPlayerId || source.dice < 2) return emptySet()
        val byId = state.territories.associateBy { it.id }
        return territoryNeighbors(state.territories)[sourceId].orEmpty()
            .filter { byId[it]?.ownerId != state.currentPlayerId }.toSet()
    }

    fun legalAttacks(state: GameState, playerId: Int = state.currentPlayerId): List<Pair<String, String>> {
        if (state.phase != MatchPhase.PLAYING || playerId != state.currentPlayerId) return emptyList()
        return state.territories.asSequence()
            .filter { it.ownerId == playerId && it.dice >= 2 }
            .flatMap { source -> legalTargets(state, source.id).asSequence().map { source.id to it } }
            .toList()
    }

    fun attack(state: GameState, fromId: String, toId: String, rng: Random = Random.Default): EngineResult {
        if (state.phase != MatchPhase.PLAYING) return EngineResult.Rejected("Партия уже завершена")
        val source = state.territories.firstOrNull { it.id == fromId }
            ?: return EngineResult.Rejected("Исходная территория не найдена")
        val target = state.territories.firstOrNull { it.id == toId }
            ?: return EngineResult.Rejected("Цель не найдена")
        if (source.ownerId != state.currentPlayerId) return EngineResult.Rejected("Сейчас ход другого участника")
        if (source.dice < 2) return EngineResult.Rejected("Для атаки нужно не менее двух кубиков")
        if (target.ownerId == state.currentPlayerId) return EngineResult.Rejected("Нельзя атаковать свою территорию")
        if (toId !in territoryNeighbors(state.territories)[fromId].orEmpty()) {
            return EngineResult.Rejected("Атаковать можно только соседнюю территорию")
        }

        val attackRoll = List(source.dice) { rng.nextInt(1, 7) }
        val defenseRoll = List(target.dice) { rng.nextInt(1, 7) }
        val attackSum = attackRoll.sum()
        val defenseSum = defenseRoll.sum()
        val captured = attackSum > defenseSum
        val updated = state.territories.map { territory ->
            when (territory.id) {
                source.id -> territory.copy(dice = 1)
                target.id -> if (captured) territory.copy(ownerId = source.ownerId, dice = source.dice - 1) else territory
                else -> territory
            }
        }
        val eliminated = if (captured && updated.none { it.ownerId == target.ownerId }) target.ownerId else null
        val remainingOwners = updated.map { it.ownerId }.distinct()
        val winner = remainingOwners.singleOrNull()
        val battle = BattleResult(
            fromId = fromId,
            toId = toId,
            attackDice = attackRoll,
            defenseDice = defenseRoll,
            attackSum = attackSum,
            defenseSum = defenseSum,
            captured = captured,
            eliminatedPlayerId = eliminated
        )
        return EngineResult.Applied(
            state = state.copy(
                territories = updated,
                attacks = state.attacks + 1,
                ownerChanges = state.ownerChanges + if (captured) 1 else 0,
                phase = if (winner != null) MatchPhase.FINISHED else state.phase,
                winnerId = winner,
                lastBattle = battle
            ),
            battle = battle
        )
    }

    fun endTurn(state: GameState, rng: Random = Random.Default): EngineResult {
        if (state.phase != MatchPhase.PLAYING) return EngineResult.Rejected("Партия уже завершена")
        if (state.currentPlayerId !in state.players.map { it.id }) return EngineResult.Rejected("Текущий игрок не найден")
        val current = state.currentPlayerId
        val groupSize = largestConnectedGroup(state, current)
        val mine = state.territories.filter { it.ownerId == current }
        if (mine.isEmpty()) return EngineResult.Rejected("У участника нет территорий")

        val diceById = mine.associate { it.id to it.dice }.toMutableMap()
        var available = mine.filter { it.dice < 8 }.map { it.id }
        var added = 0
        repeat(groupSize) {
            available = available.filter { (diceById[it] ?: 8) < 8 }
            if (available.isNotEmpty()) {
                val chosen = available[rng.nextInt(available.size)]
                diceById[chosen] = (diceById[chosen] ?: 0) + 1
                added++
            }
        }
        val lost = groupSize - added
        val territories = state.territories.map { territory ->
            if (territory.ownerId == current) territory.copy(dice = diceById[territory.id] ?: territory.dice) else territory
        }
        val alive = state.players.map { it.id }.filter { id -> territories.any { it.ownerId == id } }
        val next = nextLivingPlayer(state.players.map { it.id }, alive, current)
        val wrapped = next <= current
        val changedThisRound = state.ownerChanges > state.ownerChangesAtRoundStart
        val stalemate = if (!wrapped) state.staleRounds else if (changedThisRound) 0 else state.staleRounds + 1
        return EngineResult.Applied(
            state = state.copy(
                territories = territories,
                currentPlayerId = next,
                round = state.round + if (wrapped) 1 else 0,
                staleRounds = stalemate,
                ownerChangesAtRoundStart = if (wrapped) state.ownerChanges else state.ownerChangesAtRoundStart,
                lastBattle = null
            ),
            reinforcement = ReinforcementResult(added, lost, groupSize)
        )
    }

    fun largestConnectedGroup(state: GameState, playerId: Int): Int {
        val owned = state.territories.filter { it.ownerId == playerId }.associateBy { it.id }
        if (owned.isEmpty()) return 0
        val neighbors = territoryNeighbors(state.territories)
        val remaining = owned.keys.toMutableSet()
        var largest = 0
        while (remaining.isNotEmpty()) {
            val queue = ArrayDeque<String>()
            queue.add(remaining.first())
            var count = 0
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (!remaining.remove(id)) continue
                count++
                neighbors[id].orEmpty().filter { it in remaining && owned.containsKey(it) }.forEach { queue.add(it) }
            }
            largest = maxOf(largest, count)
        }
        return largest
    }

    fun validationErrors(state: GameState): List<String> {
        val errors = mutableListOf<String>()
        if (state.players.size !in 3..8) errors += "Число участников должно быть от 3 до 8"
        if (state.territories.isEmpty()) errors += "Карта пуста"
        val ids = state.territories.map { it.id }
        if (ids.distinct().size != ids.size) errors += "Есть повторяющиеся ID территорий"
        val cells = state.territories.flatMap { it.hexes }
        if (cells.isEmpty() || cells.distinct().size != cells.size) errors += "Есть пустые или повторяющиеся hex-клетки"
        state.territories.forEach { territory ->
            if (territory.hexes.isEmpty()) errors += "Территория ${territory.id} не содержит клеток"
            if (territory.ownerId !in state.players.map { it.id }) errors += "Неизвестный владелец территории ${territory.id}"
            if (territory.dice !in 1..8) errors += "В территории ${territory.id} должно быть от 1 до 8 кубиков"
            if (!isHexSetConnected(territory.hexes)) errors += "Территория ${territory.id} несвязна"
        }
        if (!isHexSetConnected(cells)) errors += "Карта состоит из несвязанных областей"
        if (state.players.any { player -> state.territories.none { it.ownerId == player.id } }) errors += "Каждый участник должен владеть территорией"
        if (state.territories.size < state.players.size) errors += "Территорий должно быть не меньше числа участников"
        if (territoryNeighbors(state.territories).values.any { it.isEmpty() }) errors += "Есть изолированная территория"
        return errors.distinct()
    }

    private fun isHexSetConnected(hexes: List<HexCell>): Boolean {
        if (hexes.isEmpty()) return false
        val all = hexes.toSet()
        val seen = mutableSetOf(all.first())
        val queue = ArrayDeque<HexCell>()
        queue.add(all.first())
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            directions.map { HexCell(cell.q + it.q, cell.r + it.r) }
                .filter { it in all && seen.add(it) }.forEach { queue.add(it) }
        }
        return seen.size == all.size
    }

    private fun nextLivingPlayer(order: List<Int>, alive: List<Int>, current: Int): Int {
        val start = order.indexOf(current)
        for (step in 1..order.size) {
            val next = order[(start + step) % order.size]
            if (next in alive) return next
        }
        return current
    }
}

