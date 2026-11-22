package com.lughati3.spite.games.hearts

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit

/** Simple but sensible Hearts heuristics (duck tricks, dump the Queen of Spades, pass high cards). */
class HeartsAi(private val rules: HeartsRules) {

    /** The 3 most dangerous cards: Q♠, high spades, high hearts, then plain high cards. */
    fun choosePass(hand: List<Card>): List<Card> = hand.sortedByDescending { danger(it) }.take(3)

    private fun danger(c: Card): Int = when {
        c.suit == Suit.SPADES && c.rank == Rank.QUEEN -> 100
        c.suit == Suit.SPADES && (c.rank == Rank.ACE || c.rank == Rank.KING) -> 90
        c.suit == Suit.HEARTS -> 40 + c.heartsValue
        else -> c.heartsValue
    }

    fun chooseCard(s: HeartsState, player: Int): Card {
        val legal = rules.legalPlays(s, player)
        if (legal.size == 1) return legal.first()
        val queen = legal.firstOrNull { it.suit == Suit.SPADES && it.rank == Rank.QUEEN }
        val notQueen = legal.filter { it != queen }.ifEmpty { legal }

        // Leading: a low, harmless card.
        if (s.trick.isEmpty()) {
            return notQueen.filter { !it.isPenalty }.minByOrNull { it.heartsValue }
                ?: notQueen.minBy { it.heartsValue }
        }

        val led = s.trick.first().card.suit
        if (legal.first().suit == led) { // following suit
            val high = s.trick.filter { it.card.suit == led }.maxOf { it.card.heartsValue }
            val under = legal.filter { it.heartsValue < high }
            if (under.isNotEmpty()) return under.maxBy { it.heartsValue } // duck as high as safely possible
            // Forced to take the trick: burn a big card if last, otherwise stay low.
            return if (s.trick.size == 3) notQueen.maxBy { it.heartsValue } else notQueen.minBy { it.heartsValue }
        }

        // Void in the led suit: dump the Queen, then the highest heart, then the highest card.
        if (queen != null) return queen
        legal.filter { it.suit == Suit.HEARTS }.maxByOrNull { it.heartsValue }?.let { return it }
        return legal.maxBy { it.heartsValue }
    }
}
