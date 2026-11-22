package com.lughati3.spite.games.klondike

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random

data class TableauCard(val card: Card, val faceUp: Boolean)

/** Foundations are indexed by `Suit.entries` (one pile per suit). */
data class KlondikeState(
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<List<Card>>,
    val tableau: List<List<TableauCard>>,
    val drawCount: Int,
    val moves: Int = 0,
) {
    val isWon: Boolean get() = foundations.sumOf { it.size } == 52
}

sealed interface KSource {
    data object Waste : KSource
    data class Foundation(val pile: Int) : KSource
    data class Tableau(val column: Int, val index: Int) : KSource
}

sealed interface KTarget {
    data class Foundation(val pile: Int) : KTarget
    data class Tableau(val column: Int) : KTarget
}

class KlondikeRules(private val random: Random = Random.Default) {

    fun newGame(drawCount: Int = 1): KlondikeState {
        val deck = Deck.standard52().shuffled(random)
        var cursor = 0
        val tableau = List(7) { col ->
            List(col + 1) { row -> TableauCard(deck[cursor++], faceUp = row == col) }
        }
        return KlondikeState(
            stock = deck.subList(cursor, deck.size).toList(),
            waste = emptyList(),
            foundations = List(4) { emptyList() },
            tableau = tableau,
            drawCount = drawCount,
        )
    }

    /** The cards that would travel if [source] were moved, or null if it cannot be picked up. */
    fun cardsAt(s: KlondikeState, source: KSource): List<Card>? = when (source) {
        KSource.Waste -> s.waste.lastOrNull()?.let { listOf(it) }
        is KSource.Foundation -> s.foundations.getOrNull(source.pile)?.lastOrNull()?.let { listOf(it) }
        is KSource.Tableau -> s.tableau.getOrNull(source.column)?.let { col ->
            if (source.index in col.indices && col[source.index].faceUp) col.drop(source.index).map { it.card } else null
        }
    }

    fun canPlace(s: KlondikeState, cards: List<Card>, target: KTarget): Boolean {
        val first = cards.firstOrNull() ?: return false
        return when (target) {
            is KTarget.Foundation -> cards.size == 1 &&
                first.suit == Suit.entries[target.pile] &&
                first.rank!!.value == s.foundations[target.pile].size + 1
            is KTarget.Tableau -> {
                val top = s.tableau[target.column].lastOrNull()
                if (top == null) first.rank == Rank.KING
                else top.faceUp &&
                    top.card.suit!!.isRed != first.suit!!.isRed &&
                    first.rank!!.value == top.card.rank!!.value - 1
            }
        }
    }

    fun move(s: KlondikeState, source: KSource, target: KTarget): KlondikeState? {
        if (source is KSource.Tableau && target is KTarget.Tableau && source.column == target.column) return null
        val cards = cardsAt(s, source) ?: return null
        if (!canPlace(s, cards, target)) return null

        var next = when (source) {
            KSource.Waste -> s.copy(waste = s.waste.dropLast(1))
            is KSource.Foundation -> s.copy(
                foundations = s.foundations.mapIndexed { i, p -> if (i == source.pile) p.dropLast(1) else p },
            )
            is KSource.Tableau -> s.copy(
                tableau = s.tableau.mapIndexed { c, col ->
                    if (c != source.column) col
                    else col.take(source.index).let { rest ->
                        // Turn the newly exposed card face up.
                        if (rest.isNotEmpty() && !rest.last().faceUp) rest.dropLast(1) + rest.last().copy(faceUp = true) else rest
                    }
                },
            )
        }
        next = when (target) {
            is KTarget.Foundation -> next.copy(
                foundations = next.foundations.mapIndexed { i, p -> if (i == target.pile) p + cards else p },
            )
            is KTarget.Tableau -> next.copy(
                tableau = next.tableau.mapIndexed { c, col ->
                    if (c == target.column) col + cards.map { TableauCard(it, true) } else col
                },
            )
        }
        return next.copy(moves = s.moves + 1)
    }

    /** Draws [KlondikeState.drawCount] cards to the waste; an empty stock recycles the waste. */
    fun drawStock(s: KlondikeState): KlondikeState? = when {
        s.stock.isNotEmpty() -> {
            val n = minOf(s.drawCount, s.stock.size)
            s.copy(stock = s.stock.dropLast(n), waste = s.waste + s.stock.takeLast(n).reversed(), moves = s.moves + 1)
        }
        s.waste.isNotEmpty() -> s.copy(stock = s.waste.reversed(), waste = emptyList(), moves = s.moves + 1)
        else -> null
    }

    fun foundationFor(card: Card): KTarget.Foundation = KTarget.Foundation(Suit.entries.indexOf(card.suit))

    /** Moves [source] to its foundation if that is legal. */
    fun autoToFoundation(s: KlondikeState, source: KSource): KlondikeState? {
        val card = cardsAt(s, source)?.singleOrNull() ?: return null
        return move(s, source, foundationFor(card))
    }

    /** Repeatedly sends every available top card (waste / tableau) to the foundations. */
    fun autoComplete(s: KlondikeState): KlondikeState {
        var cur = s
        var progressed = true
        while (progressed) {
            progressed = false
            val sources = listOf<KSource>(KSource.Waste) + cur.tableau.indices.map { KSource.Tableau(it, cur.tableau[it].lastIndex) }
            for (src in sources) {
                autoToFoundation(cur, src)?.let { cur = it; progressed = true }
            }
        }
        return cur
    }
}
