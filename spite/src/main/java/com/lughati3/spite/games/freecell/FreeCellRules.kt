package com.lughati3.spite.games.freecell

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.Suit
import kotlin.random.Random

/** Foundations are indexed by `Suit.entries`. */
data class FreeCellState(
    val cascades: List<List<Card>>,
    val cells: List<Card?>,
    val foundations: List<List<Card>>,
    val moves: Int = 0,
) {
    val isWon: Boolean get() = foundations.sumOf { it.size } == 52
}

sealed interface FSource {
    data class Cascade(val column: Int, val index: Int) : FSource
    data class Cell(val slot: Int) : FSource
}

sealed interface FTarget {
    data class Cascade(val column: Int) : FTarget
    data class Cell(val slot: Int) : FTarget
    data class Foundation(val pile: Int) : FTarget
}

class FreeCellRules(private val random: Random = Random.Default) {

    fun newGame(): FreeCellState {
        val deck = Deck.standard52().shuffled(random)
        val cascades = List(8) { col -> deck.filterIndexed { i, _ -> i % 8 == col } } // 7,7,7,7,6,6,6,6
        return FreeCellState(cascades, List(4) { null }, List(4) { emptyList() })
    }

    /** A run is descending in rank with alternating colours. */
    fun isRun(cards: List<Card>): Boolean = cards.zipWithNext().all { (a, b) ->
        b.rank!!.value == a.rank!!.value - 1 && a.suit!!.isRed != b.suit!!.isRed
    }

    fun cardsAt(s: FreeCellState, source: FSource): List<Card>? = when (source) {
        is FSource.Cell -> s.cells.getOrNull(source.slot)?.let { listOf(it) }
        is FSource.Cascade -> s.cascades.getOrNull(source.column)?.let { col ->
            if (source.index !in col.indices) null else col.drop(source.index).takeIf { isRun(it) }
        }
    }

    /** Classic supermove limit: (free cells + 1) * 2^(empty cascades other than the destination). */
    fun maxMovable(s: FreeCellState, targetColumn: Int): Int {
        val free = s.cells.count { it == null }
        val empties = s.cascades.indices.count { it != targetColumn && s.cascades[it].isEmpty() }
        return (free + 1) shl empties
    }

    fun canPlace(s: FreeCellState, cards: List<Card>, target: FTarget): Boolean {
        val first = cards.firstOrNull() ?: return false
        return when (target) {
            is FTarget.Foundation -> cards.size == 1 && first.suit == Suit.entries[target.pile] &&
                first.rank!!.value == s.foundations[target.pile].size + 1
            is FTarget.Cell -> cards.size == 1 && s.cells[target.slot] == null
            is FTarget.Cascade -> {
                val top = s.cascades[target.column].lastOrNull()
                cards.size <= maxMovable(s, target.column) &&
                    (top == null || (top.suit!!.isRed != first.suit!!.isRed && first.rank!!.value == top.rank!!.value - 1))
            }
        }
    }

    fun move(s: FreeCellState, source: FSource, target: FTarget): FreeCellState? {
        if (source is FSource.Cascade && target is FTarget.Cascade && source.column == target.column) return null
        val cards = cardsAt(s, source) ?: return null
        if (!canPlace(s, cards, target)) return null

        var cascades = s.cascades
        var cells = s.cells
        var found = s.foundations
        when (source) {
            is FSource.Cascade -> cascades = cascades.mapIndexed { c, col -> if (c == source.column) col.take(source.index) else col }
            is FSource.Cell -> cells = cells.mapIndexed { i, x -> if (i == source.slot) null else x }
        }
        when (target) {
            is FTarget.Cascade -> cascades = cascades.mapIndexed { c, col -> if (c == target.column) col + cards else col }
            is FTarget.Cell -> cells = cells.mapIndexed { i, x -> if (i == target.slot) cards.single() else x }
            is FTarget.Foundation -> found = found.mapIndexed { i, p -> if (i == target.pile) p + cards else p }
        }
        return FreeCellState(cascades, cells, found, s.moves + 1)
    }

    fun autoToFoundation(s: FreeCellState, source: FSource): FreeCellState? {
        val card = cardsAt(s, source)?.singleOrNull() ?: return null
        return move(s, source, FTarget.Foundation(Suit.entries.indexOf(card.suit)))
    }

    /** First empty free cell, for the "tap again to park the card" shortcut. */
    fun firstFreeCell(s: FreeCellState): Int? = s.cells.indexOfFirst { it == null }.takeIf { it >= 0 }

    fun autoComplete(s: FreeCellState): FreeCellState {
        var cur = s
        var progressed = true
        while (progressed) {
            progressed = false
            val sources = cur.cells.indices.map<Int, FSource> { FSource.Cell(it) } +
                cur.cascades.indices.map { FSource.Cascade(it, cur.cascades[it].lastIndex) }
            for (src in sources) autoToFoundation(cur, src)?.let { cur = it; progressed = true }
        }
        return cur
    }
}
