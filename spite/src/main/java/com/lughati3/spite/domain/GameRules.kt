package com.lughati3.spite.domain

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.CardSource
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.GameState
import com.lughati3.spite.model.PileCard
import com.lughati3.spite.model.PlayerState
import com.lughati3.spite.model.Rank
import kotlin.random.Random

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
            PlayerState(if (twoPlayers) "Player 1" else "You", true, take(HAND_SIZE), List(GROUND_SLOTS) { emptyList() }, take(BANK_SIZE)),
            PlayerState(if (twoPlayers) "Player 2" else "AI", twoPlayers, take(HAND_SIZE), List(GROUND_SLOTS) { emptyList() }, take(BANK_SIZE)),
        )
        return GameState(
            players = players,
            drawPile = deck.subList(cursor, deck.size).toList(),
            centerPiles = List(CENTER_PILES) { emptyList() },
            completedCards = emptyList(),
            currentPlayer = GameState.HUMAN, // hands were dealt, so no start-of-turn draw is needed
        )
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
     * Ends the turn by moving a hand card to the Ground Table. While a slot is empty the card must
     * go to an empty slot; once all 5 are filled it may go on top of any slot (the "Taki" move).
     * Returns the state at the start of the opponent's turn (already refilled to 5 cards).
     */
    fun discard(state: GameState, handIndex: Int, slot: Int): GameState? {
        if (state.isOver) return null
        val player = state.current
        val card = player.hand.getOrNull(handIndex) ?: return null
        if (slot !in 0 until GROUND_SLOTS) return null
        if (player.hasEmptyGroundSlot && player.ground[slot].isNotEmpty()) return null

        val updated = player.copy(
            hand = player.hand.filterIndexed { i, _ -> i != handIndex },
            ground = player.ground.mapIndexed { i, stack -> if (i == slot) stack + card else stack },
        )
        return endTurn(state.withPlayer(state.currentPlayer, updated))
    }

    /** Only legal when the hand is empty (e.g. draw pile exhausted): nothing to place, just pass. */
    fun passTurn(state: GameState): GameState? =
        if (!state.isOver && state.current.hand.isEmpty()) endTurn(state) else null

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
        const val CENTER_PILES = 4

        /** A Joker fits anywhere; any other card must be exactly the next rank (an empty pile wants an Ace). */
        fun canPlay(card: Card, pile: List<PileCard>): Boolean =
            pile.size < Rank.KING.value && (card.isJoker || card.rank!!.value == pile.size + 1)
    }
}
