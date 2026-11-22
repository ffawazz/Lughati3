package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank

sealed interface AiMove {
    data class Play(val source: CardSource, val pile: Int) : AiMove
    /** Put a hand card on an empty ground slot (does not end the turn). */
    data class Fill(val handIndex: Int, val slot: Int) : AiMove
    /** The Taki: cover a slot of the full ground and end the turn. */
    data class Discard(val handIndex: Int, val slot: Int) : AiMove
    data object Pass : AiMove
}

/**
 * Stateless AI. Asked for one move at a time (the ViewModel inserts the delay), it works on the
 * current player's perspective and never looks at the hidden draw pile.
 *
 * Priority: Bank -> Ground -> Hand (natural cards), then a Joker only if a short look-ahead shows
 * it unlocks a Bank/Ground card or chains several plays, then a "least harmful" discard.
 */
class AiPlayer(private val rules: GameRules) {

    fun chooseMove(state: GameState): AiMove =
        naturalMove(state) ?: jokerMove(state) ?: endOfTurnMove(state)

    // ---------------------------------------------------------------- 1. Bank > Ground > Hand

    private fun naturalMove(state: GameState): AiMove.Play? {
        val me = state.current

        // Bank (a Joker on top of the bank is always worth playing: it is free and reveals the next card).
        me.bankTop?.let { top ->
            bestPile(state, CardSource.Bank, top)?.let { return AiMove.Play(CardSource.Bank, it) }
        }
        // Ground: deepest stacks first, so buried cards come back sooner. Jokers are saved for jokerMove().
        me.ground.indices
            .filter { me.ground[it].isNotEmpty() }
            .sortedByDescending { me.ground[it].size }
            .forEach { slot ->
                val card = me.groundTop(slot)!!
                if (!card.isJoker) bestPile(state, CardSource.Ground(slot), card)
                    ?.let { return AiMove.Play(CardSource.Ground(slot), it) }
            }
        // Hand: low ranks first.
        me.hand.indices
            .filter { !me.hand[it].isJoker }
            .sortedBy { me.hand[it].rank!!.value }
            .forEach { i ->
                bestPile(state, CardSource.Hand(i), me.hand[i])
                    ?.let { return AiMove.Play(CardSource.Hand(i), it) }
            }
        return null
    }

    /** The legal pile for [card] that sets up the most follow-up plays, or null if none is legal. */
    private fun bestPile(state: GameState, source: CardSource, card: Card): Int? {
        val me = state.current
        val legal = rules.validPiles(state, card)
        if (legal.isEmpty()) return null

        // Cards that remain after this one has been played.
        val nextBank = if (source == CardSource.Bank) me.bank.getOrNull(me.bank.size - 2) else me.bankTop
        val groundTops = me.ground.indices.mapNotNull { slot ->
            if (source == CardSource.Ground(slot)) me.ground[slot].getOrNull(me.ground[slot].size - 2)
            else me.groundTop(slot)
        }
        val hand = me.hand.filterIndexed { i, _ -> source != CardSource.Hand(i) }

        fun score(pileIndex: Int): Double {
            val size = state.centerPiles[pileIndex].size
            val nextNeeded = if (size + 1 == Rank.KING.value) 1 else size + 2 // after this play
            fun wants(c: Card?) = c != null && !c.isJoker && c.rank!!.value == nextNeeded
            var s = 0.0
            if (wants(nextBank)) s += 50.0
            s += 10.0 * groundTops.count { wants(it) }
            s += 3.0 * hand.count { wants(it) }
            s += size * 0.1                       // finish long piles
            if (size == 0) s -= 0.5               // don't open a new pile needlessly
            return s
        }
        return legal.maxByOrNull { score(it) }
    }

    // ---------------------------------------------------------------- 2. Joker look-ahead

    /**
     * Tries every Joker (hand or ground top) on every distinct pile, then greedily plays natural
     * cards afterwards to see what it unlocked. Only fires when the payoff beats the Joker's value.
     */
    private fun jokerMove(state: GameState): AiMove.Play? {
        val me = state.current
        val jokerSources = buildList<CardSource> {
            me.ground.indices.filter { me.groundTop(it)?.isJoker == true }.forEach { add(CardSource.Ground(it)) }
            me.hand.indices.filter { me.hand[it].isJoker }.forEach { add(CardSource.Hand(it)) }
        }
        var best: AiMove.Play? = null
        var bestScore = 0.0
        for (source in jokerSources) {
            // Piles of equal length are interchangeable.
            val piles = state.centerPiles.indices.distinctBy { state.centerPiles[it].size }
            for (pile in piles) {
                val first = rules.playToCenter(state, source, pile) ?: continue
                var score = -JOKER_COST + if (source is CardSource.Ground) 1.0 else 0.0
                if (first.state.isOver) score += 1000.0
                else if (first.handRefilled) score += REFILL_BONUS // don't peek at the new cards
                else score += rollout(first.state)
                if (score > bestScore) { bestScore = score; best = AiMove.Play(source, pile) }
            }
        }
        return best
    }

    /** Greedy natural plays from [start]; returns the value gained. Stops at a refill (hidden info). */
    private fun rollout(start: GameState): Double {
        var s = start
        var gained = 0.0
        repeat(40) {
            val move = naturalMove(s) ?: return gained
            val result = rules.playToCenter(s, move.source, move.pile) ?: return gained
            gained += when (move.source) {
                CardSource.Bank -> 100.0
                is CardSource.Ground -> 20.0
                is CardSource.Hand -> 6.0
            }
            if (result.state.isOver) return gained + 1000.0
            if (result.handRefilled) return gained + REFILL_BONUS
            s = result.state
        }
        return gained
    }

    // ---------------------------------------------------------------- 3. End of turn

    /** Stopping means: fill every empty ground slot first (turn goes on), then Taki. */
    private fun endOfTurnMove(state: GameState): AiMove {
        val me = state.current
        if (me.hand.isEmpty()) return AiMove.Pass
        return if (me.hasEmptyGroundSlot) fillMove(state) else discardMove(state)
    }

    private fun cardValue(me: PlayerState, card: Card): Double {
        if (card.isJoker) return 100.0
        val bankRank = me.bankTop?.rank?.value
        val r = card.rank!!.value
        var v = 14.0 - r                                   // low cards are the most useful
        if (bankRank != null && (r == bankRank - 1 || r == bankRank - 2)) v += 8.0 // helps reach the bank card
        return v
    }

    /** Park the least useful hand card on an empty slot. */
    private fun fillMove(state: GameState): AiMove {
        val me = state.current
        val slot = me.ground.indexOfFirst { it.isEmpty() }
        val i = me.hand.indices.minBy { cardValue(me, me.hand[it]) }
        return AiMove.Fill(i, slot)
    }

    private fun discardMove(state: GameState): AiMove {
        val me = state.current

        fun value(card: Card) = cardValue(me, card)

        val slots = me.ground.indices
        var best: AiMove.Discard? = null
        var bestCost = Double.MAX_VALUE
        for (i in me.hand.indices) {
            val card = me.hand[i]
            for (slot in slots) {
                val stack = me.ground[slot]
                var cost = value(card)
                if (stack.isNotEmpty()) {
                    val top = stack.last()
                    cost += value(top) + 0.5 * stack.size       // burying the top card (and a deep stack) hurts
                    // Descending runs (e.g. 6 on a 7) unbury themselves in order.
                    if (!card.isJoker && !top.isJoker && top.rank!!.value - card.rank!!.value == 1) cost -= 3.0
                }
                if (cost < bestCost) { bestCost = cost; best = AiMove.Discard(i, slot) }
            }
        }
        return best ?: AiMove.Pass
    }

    private companion object {
        const val JOKER_COST = 12.0
        const val REFILL_BONUS = 40.0
    }
}
