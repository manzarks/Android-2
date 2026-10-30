package com.dicewars.game.generation

import com.dicewars.game.domain.BoardSize
import com.dicewars.game.domain.Difficulty
import com.dicewars.game.domain.GameConfig
import com.dicewars.game.domain.GameState
import com.dicewars.game.domain.GameEngine
import com.dicewars.game.domain.HexCell
import com.dicewars.game.domain.MatchPhase
import com.dicewars.game.domain.Player
import com.dicewars.game.domain.Territory
import com.dicewars.game.domain.newId
import kotlin.random.Random

object BoardGenerator {
    private val directions = listOf(
        HexCell(1, 0), HexCell(1, -1), HexCell(0, -1),
        HexCell(-1, 0), HexCell(-1, 1), HexCell(0, 1)
    )

    fun generate(config: GameConfig): GameState {
        require(config.playerCount in 3..8) { "Число игроков должно быть от 3 до 8" }
        val random = Random(config.seed)
        val cells = makeIrregularShape(config.boardSize, random)
        val groups = partition(cells, config.boardSize.territoryCount, random)
        val players = buildList {
            add(Player(0, "Вы", true))
            for (id in 1 until config.playerCount) add(Player(id, "Бот $id", false))
        }
        val provisional = groups.mapIndexed { index, hexes ->
            Territory("t-${index + 1}", hexes.sortedWith(compareBy<HexCell> { it.r }.thenBy { it.q }), 0, 2)
        }
        val territories = assignOwnersAndDice(provisional, config.playerCount, random)
        val state = GameState(
            matchId = newId(config.seed, "match"),
            mapId = newId(config.seed, "map"),
            seed = config.seed,
            territories = territories,
            players = players,
            currentPlayerId = 0,
            round = 1,
            difficulty = config.difficulty,
            boardSize = config.boardSize,
            phase = MatchPhase.PLAYING
        )
        check(GameEngine.validationErrors(state).isEmpty()) { "Генератор создал некорректную карту" }
        return state
    }

    private fun makeIrregularShape(size: BoardSize, random: Random): Set<HexCell> {
        val radius = size.radius
        val cells = buildSet {
            for (q in -radius..radius) {
                for (r in -radius..radius) {
                    if (maxOf(kotlin.math.abs(q), kotlin.math.abs(r), kotlin.math.abs(q + r)) <= radius) {
                        add(HexCell(q, r))
                    }
                }
            }
        }.toMutableSet()
        val desiredCuts = when (size) {
            BoardSize.SMALL -> 4
            BoardSize.MEDIUM -> 6
            BoardSize.LARGE -> 8
        }
        val boundary = cells.filter { cell ->
            maxOf(kotlin.math.abs(cell.q), kotlin.math.abs(cell.r), kotlin.math.abs(cell.q + cell.r)) == radius
        }.shuffled(random)
        var removed = 0
        for (cell in boundary) {
            if (removed == desiredCuts || cells.size - 1 < size.territoryCount * 2) break
            cells.remove(cell)
            if (connected(cells)) removed++ else cells.add(cell)
        }
        return cells
    }

    private fun partition(cells: Set<HexCell>, desiredTerritories: Int, random: Random): List<Set<HexCell>> {
        repeat(120) {
            val regions = cells.associateWith { mutableSetOf(it) }.toMutableMap()
            var attempts = 0
            while (regions.size > desiredTerritories && attempts++ < cells.size * 20) {
                val owners = HashMap<HexCell, HexCell>()
                regions.forEach { (root, members) -> members.forEach { owners[it] = root } }
                val edges = linkedSetOf<Pair<HexCell, HexCell>>()
                cells.forEach { cell ->
                    val a = owners[cell] ?: return@forEach
                    directions.forEach { direction ->
                        val other = HexCell(cell.q + direction.q, cell.r + direction.r)
                        val b = owners[other]
                        if (b != null && a != b) {
                            val pair = if (a.q < b.q || (a.q == b.q && a.r <= b.r)) a to b else b to a
                            if (regions.getValue(pair.first).size + regions.getValue(pair.second).size <= 5) edges += pair
                        }
                    }
                }
                if (edges.isEmpty()) break
                val edgeList = edges.toList()
                val minimumSum = edgeList.minOf { regions.getValue(it.first).size + regions.getValue(it.second).size }
                val preferred = edgeList.filter { regions.getValue(it.first).size + regions.getValue(it.second).size <= minimumSum + 1 }
                val (left, right) = preferred[random.nextInt(preferred.size)]
                regions.getValue(left).addAll(regions.remove(right).orEmpty())
            }
            if (regions.size == desiredTerritories && regions.values.all { it.size in 1..5 }) return regions.values.map { it.toSet() }
        }
        error("Не удалось разбить карту на связанные территории")
    }

    private fun assignOwnersAndDice(
        base: List<Territory>,
        playerCount: Int,
        random: Random
    ): List<Territory> {
        val targetTotal = kotlin.math.round(base.size.toDouble() / playerCount * 4.0).toInt()
        repeat(250) {
            val shuffled = base.shuffled(random)
            val owners = shuffled.mapIndexed { index, territory -> territory.id to index % playerCount }.toMap()
            val owned = base.map { it.copy(ownerId = owners.getValue(it.id)) }
            val neighborGraph = GameEngine.territoryNeighbors(owned)
            val hasRivalBorder = (0 until playerCount).all { playerId ->
                owned.filter { it.ownerId == playerId }.any { territory ->
                    neighborGraph[territory.id].orEmpty().any { neighbor -> owned.first { it.id == neighbor }.ownerId != playerId }
                }
            }
            if (!hasRivalBorder) return@repeat

            val dice = mutableMapOf<String, Int>()
            for (playerId in 0 until playerCount) {
                val list = owned.filter { it.ownerId == playerId }.shuffled(random)
                list.forEach { dice[it.id] = 2 }
                var remaining = targetTotal - 2 * list.size
                var index = 0
                while (remaining > 0 && list.any { dice.getValue(it.id) < 6 }) {
                    val territory = list[index % list.size]
                    if (dice.getValue(territory.id) < 6) {
                        dice[territory.id] = dice.getValue(territory.id) + 1
                        remaining--
                    }
                    index++
                }
            }
            return owned.map { it.copy(dice = dice.getValue(it.id)) }
        }
        error("Не удалось сбалансировать стартовые позиции")
    }

    private fun connected(cells: Set<HexCell>): Boolean {
        if (cells.isEmpty()) return false
        val remaining = cells.toMutableSet()
        val queue = ArrayDeque<HexCell>()
        queue.add(remaining.first())
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (!remaining.remove(cell)) continue
            directions.map { HexCell(cell.q + it.q, cell.r + it.r) }.filter { it in remaining }.forEach { queue.add(it) }
        }
        return remaining.isEmpty()
    }
}

