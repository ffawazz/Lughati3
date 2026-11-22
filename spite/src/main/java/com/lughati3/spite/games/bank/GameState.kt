package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank

/** A card sitting on a centre pile, plus the rank it counts as (differs from its face for Jokers). */
data class PileCard(val card: Card, val asRank: Rank)

/**
 * Immutable snapshot of the whole table. Every rule returns a new instance.
 *
 *  - [centerPiles]: always exactly 4 slots; an empty list means "no pile started here".
 *    A pile's next required rank is `size + 1`; at 13 (King) it is moved to [completedCards].
 *  - [drawPile]: last element is the top.
 */
data class GameState(
    val players: List<PlayerState>,
    val drawPile: List<Card>,
    val centerPiles: List<List<PileCard>>,
    val completedCards: List<Card>,
    val currentPlayer: Int,
    val winner: Int? = null,
) {
    val current: PlayerState get() = players[currentPlayer]
    val isOver: Boolean get() = winner != null

    companion object {
        const val HUMAN = 0
        const val AI = 1
    }
}
