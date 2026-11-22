package com.lughati3.spite.games.memory

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.Rank
import kotlin.random.Random

enum class MemoryLevel(val label: String, val pairs: Int, val columns: Int) {
    EASY("Easy", 8, 4),
    MEDIUM("Medium", 12, 6),
    HARD("Hard", 18, 6),
}

/**
 * [cards] is the shuffled layout. [faceUp] holds the (at most two) cards currently turned over and
 * not yet matched; [matched] the indices already paired off. A "move" is one pair of flips.
 */
data class MemoryState(
    val level: MemoryLevel,
    val cards: List<Card>,
    val faceUp: Set<Int> = emptySet(),
    val matched: Set<Int> = emptySet(),
    val moves: Int = 0,
) {
    val isWon: Boolean get() = matched.size == cards.size
    /** Two non-matching cards are showing and must be hidden before the next flip. */
    val needsHide: Boolean get() = faceUp.size == 2
}

class MemoryRules(private val random: Random = Random.Default) {

    /** Two cards match when they have the same rank and the same colour (e.g. 7♥ and 7♦). */
    fun isMatch(a: Card, b: Card): Boolean = a.rank == b.rank && a.suit!!.isRed == b.suit!!.isRed

    fun newGame(level: MemoryLevel): MemoryState {
        val all = Deck.standard52()
        // every (rank, colour) has exactly two cards in a deck: pick `pairs` of those combinations
        val groups = all.groupBy { it.rank!! to it.suit!!.isRed }.values.shuffled(random).take(level.pairs)
        val cards = groups.flatten().shuffled(random)
        return MemoryState(level, cards)
    }

    /** Turns card [index] over. Returns null when the flip is not allowed. */
    fun flip(s: MemoryState, index: Int): MemoryState? {
        if (s.isWon || s.needsHide || index !in s.cards.indices) return null
        if (index in s.matched || index in s.faceUp) return null
        val up = s.faceUp + index
        if (up.size < 2) return s.copy(faceUp = up)
        val (a, b) = up.toList()
        return if (isMatch(s.cards[a], s.cards[b])) {
            s.copy(faceUp = emptySet(), matched = s.matched + up, moves = s.moves + 1)
        } else {
            s.copy(faceUp = up, moves = s.moves + 1)
        }
    }

    /** Turns a mismatched pair face down again. */
    fun hideMismatch(s: MemoryState): MemoryState = if (s.needsHide) s.copy(faceUp = emptySet()) else s
}
