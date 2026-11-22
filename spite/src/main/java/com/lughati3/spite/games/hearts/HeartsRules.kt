package com.lughati3.spite.games.hearts

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random

enum class HeartsPhase { PASSING, PLAYING, TRICK_COMPLETE, ROUND_OVER, GAME_OVER }

data class TrickPlay(val player: Int, val card: Card)

/**
 * Seats are clockwise: 0 = you (bottom), 1 = West (left), 2 = North (top), 3 = East (right).
 * Play and "pass left" both go to the next index.
 */
data class HeartsState(
    val hands: List<List<Card>>,
    val trick: List<TrickPlay>,
    val turn: Int,
    val heartsBroken: Boolean,
    val tricksPlayed: Int,
    val roundPoints: List<Int>,
    val totalScores: List<Int>,
    val lastRoundPoints: List<Int>,
    val moonShooter: Int?,
    val round: Int,
    val phase: HeartsPhase,
) {
    val winners: List<Int>
        get() = totalScores.min().let { best -> totalScores.indices.filter { totalScores[it] == best } }
}

val Card.heartsValue: Int get() = if (rank == Rank.ACE) 14 else rank!!.value
val Card.isPenalty: Boolean get() = suit == Suit.HEARTS || (suit == Suit.SPADES && rank == Rank.QUEEN)
val Card.points: Int get() = when {
    suit == Suit.HEARTS -> 1
    suit == Suit.SPADES && rank == Rank.QUEEN -> 13
    else -> 0
}

class HeartsRules(private val random: Random = Random.Default) {

    /** Seat offset of the pass this round: left, right, across, then no pass. */
    fun passOffset(round: Int): Int = when (round % 4) { 0 -> 1; 1 -> 3; 2 -> 2; else -> 0 }

    fun newRound(totalScores: List<Int> = List(4) { 0 }, round: Int = 0): HeartsState {
        val deck = Deck.standard52().shuffled(random)
        val hands = List(4) { p -> sortHand(deck.subList(p * 13, p * 13 + 13)) }
        val passing = passOffset(round) != 0
        return HeartsState(
            hands = hands, trick = emptyList(),
            turn = holderOfTwoOfClubs(hands),
            heartsBroken = false, tricksPlayed = 0,
            roundPoints = List(4) { 0 }, totalScores = totalScores,
            lastRoundPoints = List(4) { 0 }, moonShooter = null,
            round = round, phase = if (passing) HeartsPhase.PASSING else HeartsPhase.PLAYING,
        )
    }

    fun sortHand(hand: List<Card>): List<Card> =
        hand.sortedWith(compareBy({ SUIT_ORDER.indexOf(it.suit) }, { it.heartsValue }))

    private fun holderOfTwoOfClubs(hands: List<List<Card>>): Int =
        hands.indexOfFirst { h -> h.any { it.suit == Suit.CLUBS && it.rank == Rank.TWO } }

    // ------------------------------------------------------------------ passing

    /** [selections] = the 3 cards each seat gives away. Moves them and starts play. */
    fun applyPass(s: HeartsState, selections: List<List<Card>>): HeartsState {
        val off = passOffset(s.round)
        val hands = List(4) { p ->
            val from = (p - off + 4) % 4
            sortHand(s.hands[p] - selections[p].toSet() + selections[from])
        }
        return s.copy(hands = hands, turn = holderOfTwoOfClubs(hands), phase = HeartsPhase.PLAYING)
    }

    // ------------------------------------------------------------------ playing

    fun legalPlays(s: HeartsState, player: Int): List<Card> {
        val hand = s.hands[player]
        if (s.trick.isEmpty()) {
            if (s.tricksPlayed == 0) { // first trick: the 2 of clubs leads
                return hand.filter { it.suit == Suit.CLUBS && it.rank == Rank.TWO }.ifEmpty { hand }
            }
            if (!s.heartsBroken) {
                val nonHearts = hand.filter { it.suit != Suit.HEARTS }
                if (nonHearts.isNotEmpty()) return nonHearts
            }
            return hand
        }
        val led = s.trick.first().card.suit
        val follow = hand.filter { it.suit == led }
        if (follow.isNotEmpty()) return follow
        if (s.tricksPlayed == 0) { // no points on the first trick unless forced
            val safe = hand.filter { !it.isPenalty }
            if (safe.isNotEmpty()) return safe
        }
        return hand
    }

    fun play(s: HeartsState, card: Card): HeartsState? {
        if (s.phase != HeartsPhase.PLAYING) return null
        if (card !in legalPlays(s, s.turn)) return null
        val hands = s.hands.mapIndexed { i, h -> if (i == s.turn) h - card else h }
        val trick = s.trick + TrickPlay(s.turn, card)
        return s.copy(
            hands = hands, trick = trick,
            heartsBroken = s.heartsBroken || card.suit == Suit.HEARTS,
            turn = if (trick.size == 4) s.turn else (s.turn + 1) % 4,
            phase = if (trick.size == 4) HeartsPhase.TRICK_COMPLETE else HeartsPhase.PLAYING,
        )
    }

    fun trickWinner(trick: List<TrickPlay>): Int {
        val led = trick.first().card.suit
        return trick.filter { it.card.suit == led }.maxBy { it.card.heartsValue }.player
    }

    /** Gives the completed trick to its winner, who leads next; scores the round after trick 13. */
    fun resolveTrick(s: HeartsState): HeartsState? {
        if (s.phase != HeartsPhase.TRICK_COMPLETE) return null
        val winner = trickWinner(s.trick)
        val pts = s.roundPoints.mapIndexed { i, p -> if (i == winner) p + s.trick.sumOf { it.card.points } else p }
        val next = s.copy(
            trick = emptyList(), turn = winner, tricksPlayed = s.tricksPlayed + 1,
            roundPoints = pts, phase = HeartsPhase.PLAYING,
        )
        return if (next.tricksPlayed == 13) endRound(next) else next
    }

    private fun endRound(s: HeartsState): HeartsState {
        val moon = s.roundPoints.indexOfFirst { it == 26 }.takeIf { it >= 0 }
        val scored = if (moon == null) s.roundPoints else List(4) { if (it == moon) 0 else 26 }
        val totals = s.totalScores.mapIndexed { i, t -> t + scored[i] }
        return s.copy(
            totalScores = totals, lastRoundPoints = scored, moonShooter = moon,
            phase = if (totals.any { it >= 100 }) HeartsPhase.GAME_OVER else HeartsPhase.ROUND_OVER,
        )
    }

    fun nextRound(s: HeartsState): HeartsState = newRound(s.totalScores, s.round + 1)

    private companion object {
        val SUIT_ORDER = listOf(Suit.CLUBS, Suit.DIAMONDS, Suit.SPADES, Suit.HEARTS)
    }
}
