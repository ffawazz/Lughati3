package com.lughati3.spite.games.crazyeights

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random

/** Seat 0 = you, seat 1 = AI. [discard] top is the last element; [activeSuit] is what must be matched. */
data class EightsState(
    val hands: List<List<Card>>,
    val stock: List<Card>,
    val discard: List<Card>,
    val activeSuit: Suit,
    val turn: Int,
    val winner: Int? = null,
) {
    val top: Card get() = discard.last()
}

class CrazyEightsRules(private val random: Random = Random.Default) {

    fun newGame(): EightsState {
        var deck = Deck.standard52().shuffled(random)
        val hands = listOf(deck.take(7), deck.drop(7).take(7))
        deck = deck.drop(14)
        // The starter may not be an 8: push any 8s to the bottom of the stock.
        val eights = deck.takeWhile { it.rank == Rank.EIGHT }
        val rest = deck.drop(eights.size)
        val starter = rest.first()
        return EightsState(
            hands = hands, stock = (rest.drop(1) + eights).reversed(),
            discard = listOf(starter), activeSuit = starter.suit!!, turn = 0,
        )
    }

    fun isPlayable(s: EightsState, card: Card): Boolean =
        card.rank == Rank.EIGHT || card.suit == s.activeSuit || card.rank == s.top.rank

    fun playable(s: EightsState, seat: Int): List<Card> = s.hands[seat].filter { isPlayable(s, it) }

    /** Plays [card] for the current player; an 8 needs [chosenSuit]. */
    fun play(s: EightsState, card: Card, chosenSuit: Suit? = null): EightsState? {
        if (s.winner != null || card !in s.hands[s.turn] || !isPlayable(s, card)) return null
        val suit = if (card.rank == Rank.EIGHT) chosenSuit ?: return null else card.suit!!
        val hands = s.hands.mapIndexed { i, h -> if (i == s.turn) h - card else h }
        val won = hands[s.turn].isEmpty()
        return s.copy(
            hands = hands, discard = s.discard + card, activeSuit = suit,
            turn = if (won) s.turn else 1 - s.turn, winner = if (won) s.turn else null,
        )
    }

    /** Only allowed when nothing is playable. Passes the turn when no card is left to draw. */
    fun draw(s: EightsState): EightsState? {
        if (s.winner != null || playable(s, s.turn).isNotEmpty()) return null
        var stock = s.stock
        var discard = s.discard
        if (stock.isEmpty() && discard.size > 1) { // recycle everything under the top card
            stock = discard.dropLast(1).shuffled(random)
            discard = listOf(discard.last())
        }
        if (stock.isEmpty()) return s.copy(turn = 1 - s.turn) // nothing to draw: pass
        val hands = s.hands.mapIndexed { i, h -> if (i == s.turn) h + stock.last() else h }
        return s.copy(hands = hands, stock = stock.dropLast(1), discard = discard)
    }

    /** What the AI would do now: play a card (with a suit if it is an 8) or draw. */
    fun aiMove(s: EightsState): Pair<Card, Suit?>? {
        val options = playable(s, s.turn)
        if (options.isEmpty()) return null
        val hand = s.hands[s.turn]
        val normal = options.filter { it.rank != Rank.EIGHT }
        // Prefer plays in the suit we hold the most of (keeps future options open), high ranks first.
        val card = normal.maxWithOrNull(compareBy({ c -> hand.count { it.suit == c.suit } }, { it.rank!!.value }))
            ?: options.first()
        if (card.rank != Rank.EIGHT) return card to null
        val rest = hand - card
        val suit = Suit.entries.maxBy { su -> rest.count { it.suit == su } }
        return card to suit
    }
}
