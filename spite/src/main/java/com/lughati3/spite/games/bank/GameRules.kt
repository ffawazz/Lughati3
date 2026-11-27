package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.Rank
import kotlin.random.Random

/** Outcome of filling an empty ground slot. */
data class GroundResult(val state: GameState, val handRefilled: Boolean)

/** Outcome of a successful play to the centre. */
data class MoveResult(
    val state: GameState,
    /** The rank the card counts as on the pile (matters for Jokers). */
    val playedAs: Rank,
    /** The pile reached King and was swept away. */
    val pileCompleted: Boolean,
    /** The hand was emptied from the hand zone and 5 fresh cards were drawn (turn continues). */
    val handRefilled: Boolean,
)

/**
 * Pure, side-effect-free implementation of every rule. Functions return `null` for illegal moves,
 * so callers (UI and AI) can never corrupt the state. Randomness is injected for testability.
 */
class GameRules(private val random: Random = Random.Default) {

    // ---------------------------------------------------------------- setup

    fun newGame(twoPlayers: Boolean = false): GameState {
        val deck = Deck.create().shuffled(random)
        var cursor = 0
        fun take(n: Int): List<Card> = deck.subList(cursor, cursor + n).also { cursor += n }

        val players = listOf(
            dealPlayer(if (twoPlayers) "Player 1" else "You", true, ::take),
            dealPlayer(if (twoPlayers) "Player 2" else "AI", twoPlayers, ::take),
        )
        return GameState(
            players = players,
            drawPile = deck.subList(cursor, deck.size).toList(),
            centerPiles = List(CENTER_PILES) { emptyList() },
            completedCards = emptyList(),
            currentPlayer = GameState.HUMAN, // hands were dealt, so no start-of-turn draw is needed
        )
    }

    /** Hand of 5, a Bank of 10, and one face-up card already on each of the 5 ground slots. */
    private fun dealPlayer(name: String, human: Boolean, take: (Int) -> List<Card>): PlayerState {
        val hand = take(HAND_SIZE)
        val ground = take(GROUND_SLOTS).map { listOf(it) }
        val bank = take(BANK_SIZE)
        return PlayerState(name, human, hand, ground, bank)
    }

    // ---------------------------------------------------------------- playing to the centre

    /** Plays the card at [source] (current player) onto centre pile [pileIndex]. */
    fun playToCenter(state: GameState, source: CardSource, pileIndex: Int): MoveResult? {
        if (state.isOver) return null
        val pile = state.centerPiles.getOrNull(pileIndex) ?: return null
        val card = state.current.cardAt(source) ?: return null
        if (!canPlay(card, pile)) return null

        val playedAs = Rank.fromValue(pile.size + 1)
        val newPile = pile + PileCard(card, playedAs)
        val completed = newPile.size == Rank.KING.value

        var player = state.current.without(source)
        var next = state.copy(
            centerPiles = state.centerPiles.mapIndexed { i, p ->
                if (i != pileIndex) p else if (completed) emptyList() else newPile
            },
            completedCards = if (completed) state.completedCards + newPile.map { it.card } else state.completedCards,
            passStreak = 0,
        ).withPlayer(state.currentPlayer, player)

        // Win: the last Bank card has just been played.
        if (source == CardSource.Bank && player.bank.isEmpty()) {
            return MoveResult(next.copy(winner = state.currentPlayer), playedAs, completed, false)
        }

        // Empty Hand Refill Rule: all hand cards gone -> draw 5 and keep playing.
        var refilled = false
        if (source is CardSource.Hand && player.hand.isEmpty()) {
            next = draw(next, state.currentPlayer, HAND_SIZE)
            refilled = true
        }
        return MoveResult(next, playedAs, completed, refilled)
    }

    // ---------------------------------------------------------------- ending the turn

    /**
     * Fills an EMPTY ground slot with a hand card. This does not end the turn: the ground must be a
     * full five before the turn may end. If it empties the hand, 5 new cards are drawn at once and
     * the turn continues (same refill rule as playing the last hand card to the centre).
     */
    fun fillGround(state: GameState, handIndex: Int, slot: Int): GroundResult? {
        if (state.isOver) return null
        val player = state.current
        val card = player.hand.getOrNull(handIndex) ?: return null
        if (slot !in 0 until GROUND_SLOTS || player.ground[slot].isNotEmpty()) return null

        val updated = player.copy(
            hand = player.hand.filterIndexed { i, _ -> i != handIndex },
            ground = player.ground.mapIndexed { i, stack -> if (i == slot) listOf(card) else stack },
        )
        var next = state.withPlayer(state.currentPlayer, updated).copy(passStreak = 0)
        val refilled = updated.hand.isEmpty()
        if (refilled) next = draw(next, state.currentPlayer, HAND_SIZE)
        return GroundResult(next, refilled)
    }

    /**
     * The "Taki" (تكي): with the ground full (all 5 slots occupied), places a hand card on top of any
     * slot — covering what was there — and ends the turn. Illegal while any slot is empty.
     * Returns the state at the start of the opponent's turn (already refilled to 5 cards).
     */
    fun discard(state: GameState, handIndex: Int, slot: Int): GameState? {
        if (state.isOver) return null
        val player = state.current
        val card = player.hand.getOrNull(handIndex) ?: return null
        if (slot !in 0 until GROUND_SLOTS) return null
        if (player.hasEmptyGroundSlot) return null

        val updated = player.copy(
            hand = player.hand.filterIndexed { i, _ -> i != handIndex },
            ground = player.ground.mapIndexed { i, stack -> if (i == slot) stack + card else stack },
        )
        return endTurn(state.withPlayer(state.currentPlayer, updated).copy(passStreak = 0))
    }

    /** Only legal when the hand is empty (e.g. draw pile exhausted): nothing to place, just pass. */
    fun passTurn(state: GameState): GameState? {
        if (state.isOver || state.current.hand.isEmpty().not()) return null
        val streak = state.passStreak + 1
        if (streak >= STALEMATE_PASSES) return declareStalemate(state.copy(passStreak = streak))
        return endTurn(state.copy(passStreak = streak))
    }

    /**
     * With only 3 centre piles it can happen that every pile waits for a card nobody can reach (say all
     * three need a King that is buried) and the draw pile is empty. Rather than pass forever, the game
     * ends: whoever has fewer cards left in their Bank wins; equal Banks is a draw (no winner).
     */
    private fun declareStalemate(state: GameState): GameState {
        val a = state.players[0].bank.size
        val b = state.players[1].bank.size
        val winner = when { a < b -> 0; b < a -> 1; else -> null }
        return state.copy(stalemate = true, winner = winner)
    }

    private fun endTurn(state: GameState): GameState {
        val next = 1 - state.currentPlayer
        return drawUpTo(state.copy(currentPlayer = next), next, HAND_SIZE)
    }

    // ---------------------------------------------------------------- queries

    fun isTakiRequired(state: GameState): Boolean = !state.current.hasEmptyGroundSlot

    fun validPiles(state: GameState, card: Card): List<Int> =
        state.centerPiles.indices.filter { canPlay(card, state.centerPiles[it]) }

    /** Sources that currently hold a playable card for the current player. */
    fun sourcesOf(state: GameState): List<CardSource> {
        val p = state.current
        val result = ArrayList<CardSource>()
        if (p.bank.isNotEmpty()) result += CardSource.Bank
        p.ground.indices.filter { p.ground[it].isNotEmpty() }.forEach { result += CardSource.Ground(it) }
        p.hand.indices.forEach { result += CardSource.Hand(it) }
        return result
    }

    // ---------------------------------------------------------------- drawing

    /** Draws until the player holds [target] cards, reshuffling completed piles when the deck runs dry. */
    private fun drawUpTo(state: GameState, playerIndex: Int, target: Int): GameState =
        draw(state, playerIndex, target - state.players[playerIndex].hand.size)

    private fun draw(state: GameState, playerIndex: Int, count: Int): GameState {
        var deck = state.drawPile
        var completed = state.completedCards
        val drawn = ArrayList<Card>()
        while (drawn.size < count) {
            if (deck.isEmpty()) {
                if (completed.isEmpty()) break // nothing left anywhere
                deck = completed.shuffled(random)
                completed = emptyList()
            }
            drawn += deck.last()
            deck = deck.dropLast(1)
        }
        val p = state.players[playerIndex]
        return state.copy(drawPile = deck, completedCards = completed)
            .withPlayer(playerIndex, p.copy(hand = p.hand + drawn))
    }

    private fun GameState.withPlayer(index: Int, player: PlayerState): GameState =
        copy(players = players.mapIndexed { i, p -> if (i == index) player else p })

    companion object {
        const val HAND_SIZE = 5
        const val BANK_SIZE = 10
        const val GROUND_SLOTS = 5
        const val CENTER_PILES = 3

        /** This many turns in a row ending in a pass (nobody playing a card) means nobody can move any more. */
        const val STALEMATE_PASSES = 4

        /** A Joker fits anywhere; any other card must be exactly the next rank (an empty pile wants an Ace). */
        fun canPlay(card: Card, pile: List<PileCard>): Boolean =
            pile.size < Rank.KING.value && (card.isJoker || card.rank!!.value == pile.size + 1)
    }
}
