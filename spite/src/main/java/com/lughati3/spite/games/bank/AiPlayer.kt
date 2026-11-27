package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank

/** Anything that can pick the computer's next move. */
interface BankAi {
    fun chooseMove(state: GameState): AiMove
}

enum class AiLevel(val label: String) {
    EASY("Easy"),
    HARD("Hard");

    fun create(rules: GameRules): BankAi = when (this) {
        EASY -> AiPlayer(rules)
        HARD -> HardAiPlayer(rules)
    }
}

sealed interface AiMove {
    data class Play(val source: CardSource, val pile: Int) : AiMove
    /** Put a hand card on an empty ground slot (does not end the turn). */
    data class Fill(val handIndex: Int, val slot: Int) : AiMove
    /** The Taki: cover a slot of the full ground and end the turn. */
    data class Discard(val handIndex: Int, val slot: Int) : AiMove
    data object Pass : AiMove
}

/**
 * The computer opponent. It is asked for one move at a time (the ViewModel inserts the delay).
 *
 * What it knows: its own hand, both ground tables, both Bank tops and the centre piles — exactly what a
 * human at the table sees. It never looks at the draw pile or at the opponent's hand.
 *
 * How it plays:
 *  1. **Plans the whole turn.** A depth-limited search over every legal sequence of plays (Bank, Ground,
 *     Hand, Jokers on any pile) scores each line: Bank cards are worth by far the most, then freeing
 *     buried Ground cards, then emptying the hand (which earns a free refill). Using a Joker costs
 *     something unless it unlocks more than it spends, so Jokers are only burned when they pay off.
 *  2. **Plays defence.** Each line ends with a penalty for what it leaves on the table for you: a centre
 *     pile that now wants exactly your Bank top (or a Ground top) is bad, so the AI will hold a card back
 *     rather than hand you your next Bank card. With only 3 centre piles it also notices when piles
 *     are blocking your Aces.
 *  3. **Ends the turn well.** It fills empty ground slots with its least useful cards and Takis onto the
 *     slot where covering hurts least, preferring tidy stacks (low cards on top of higher ones, so the
 *     stack unwinds in the order it will be played).
 */
open class AiPlayer(
    protected val rules: GameRules,
    private val nodeBudget: Int = DEFAULT_BUDGET,
    private val maxDepth: Int = MAX_DEPTH,
) : BankAi {

    override fun chooseMove(state: GameState): AiMove {
        prepare(state)
        return planPlay(state) ?: endOfTurnMove(state)
    }

    // How much each kind of play is worth to the planner. Easy uses these defaults; Hard overrides them.
    protected open val groundValue: Double get() = GROUND_VALUE
    protected open val handValue: Double get() = HAND_VALUE
    protected open val refillValue: Double get() = REFILL_VALUE
    protected open val pileCompleteValue: Double get() = PILE_COMPLETE_VALUE
    protected open val jokerCost: Double get() = JOKER_COST

    /** Hook: called once at the start of every decision, before any search (a subclass can gather knowledge here). */
    protected open fun prepare(state: GameState) {}

    // ================================================================ 1. planning the plays

    protected class Candidate(val source: CardSource, val pile: Int, val result: MoveResult, val gain: Double)

    /** Search bookkeeping for one planning call. */
    protected inner class Search(private val budget: Int = nodeBudget) {
        var nodes = 0
        val memo = HashMap<String, Double>()

        /** Best total value obtainable from [state] on: either stop here, or play on. */
        fun best(state: GameState, depth: Int): Double {
            val stop = terminal(state)
            if (depth <= 0 || nodes >= budget) return stop
            val key = keyOf(state)
            memo[key]?.let { return it }
            nodes++
            var best = stop
            for (c in candidates(state)) {
                val v = c.gain + continuation(c, depth)
                if (v > best) best = v
            }
            memo[key] = best
            return best
        }

        fun continuation(c: Candidate, depth: Int): Double = when {
            c.result.state.isOver -> WIN_VALUE + depth * 1000.0 // winning sooner beats winning later
            // The draw pile is hidden information: stop planning when a refill happens.
            c.result.handRefilled -> refillValue + terminal(c.result.state)
            else -> best(c.result.state, depth - 1)
        }
    }

    private fun planPlay(real: GameState): AiMove.Play? {
        // The sandbox has no draw pile: no peeking, and no random numbers used while thinking.
        val state = real.copy(drawPile = emptyList(), completedCards = emptyList())
        val search = Search()
        var best: AiMove.Play? = null
        var bestValue = terminal(state) + MIN_IMPROVEMENT
        for (c in candidates(state)) {
            val v = c.gain + search.continuation(c, maxDepth)
            if (v > bestValue) { bestValue = v; best = AiMove.Play(c.source, c.pile) }
        }
        return best
    }

    /** All distinct legal plays for the current player, best-looking sources first. */
    private fun candidates(state: GameState): List<Candidate> {
        val me = state.current
        val out = ArrayList<Candidate>()

        fun add(source: CardSource, card: Card) {
            // Piles of the same length are interchangeable.
            val piles = state.centerPiles.indices
                .filter { GameRules.canPlay(card, state.centerPiles[it]) }
                .distinctBy { state.centerPiles[it].size }
            for (p in piles) {
                val r = rules.playToCenter(state, source, p) ?: continue
                out += Candidate(source, p, r, gainOf(source, card, r))
            }
        }

        me.bankTop?.let { add(CardSource.Bank, it) }
        me.ground.indices.forEach { slot -> me.groundTop(slot)?.let { add(CardSource.Ground(slot), it) } }
        // Equal cards in hand are interchangeable; so are all Jokers.
        val seen = HashSet<Pair<Boolean, Rank?>>()
        me.hand.forEachIndexed { i, card -> if (seen.add(card.isJoker to card.rank)) add(CardSource.Hand(i), card) }
        return out
    }

    private fun gainOf(source: CardSource, card: Card, r: MoveResult): Double {
        var g = when (source) {
            CardSource.Bank -> BANK_VALUE
            is CardSource.Ground -> groundValue
            is CardSource.Hand -> handValue
        }
        if (card.isJoker && source != CardSource.Bank) g -= jokerCost // a wildcard is worth saving
        if (r.pileCompleted) g += pileCompleteValue
        return g
    }

    private fun keyOf(state: GameState): String {
        val me = state.current
        return buildString {
            state.centerPiles.map { it.size }.sorted().forEach { append(it).append(',') }
            append('|').append(me.bank.size)
            append('|'); me.ground.forEach { s -> s.forEach { append(it.id).append(',') }; append(';') }
            append('|'); me.hand.map { it.id }.sorted().forEach { append(it).append(',') }
        }
    }

    // ================================================================ 2. judging a position (defence)

    /**
     * How good is the table for the current player if it stops playing now? Positive = ready for
     * our next turn; negative = we are handing the opponent plays.
     */
    protected open fun terminal(state: GameState): Double {
        val me = state.current
        val opp = state.players[1 - state.currentPlayer]
        var score = 0.0

        // Threat: what the visible cards of the opponent can do on these piles.
        score -= threat(state, opp.bankTop, BANK_THREAT, BANK_THREAT_NEAR)
        opp.ground.indices.forEach { score -= threat(state, opp.groundTop(it), GROUND_THREAT, GROUND_THREAT_NEAR) }

        // Readiness: the same for us (the opponent may still change the piles, so it counts for less).
        score += READINESS * threat(state, me.bankTop, BANK_THREAT, BANK_THREAT_NEAR)
        me.ground.indices.forEach { score += READINESS * threat(state, me.groundTop(it), GROUND_THREAT, GROUND_THREAT_NEAR) }
        return score
    }

    /** How many cards a pile still needs before [card] can be played (null = never / not applicable). */
    protected fun gapTo(state: GameState, card: Card): Int? {
        if (card.isJoker) return 0
        val r = card.rank!!.value
        return state.centerPiles
            .map { pile -> r - 1 - pile.size }
            .filter { it >= 0 }
            .minOrNull()
    }

    private fun threat(state: GameState, card: Card?, now: Double, soon: Double): Double {
        card ?: return 0.0
        if (card.isJoker) return now * 0.4 // a Joker is playable anywhere anyway: not something we can deny
        return when (gapTo(state, card)) {
            0 -> now
            1 -> soon
            2 -> soon * 0.35
            else -> 0.0
        }
    }

    // ================================================================ 3. ending the turn

    /** Stopping means: fill every empty ground slot first (turn goes on), then Taki. */
    protected open fun endOfTurnMove(state: GameState): AiMove {
        val me = state.current
        if (me.hand.isEmpty()) return AiMove.Pass
        return if (me.hasEmptyGroundSlot) fillMove(state) else takiMove(state)
    }

    /** How much we would miss [card] if it were stuck on the ground (higher = keep it accessible). */
    protected open fun usefulness(state: GameState, card: Card): Double {
        if (card.isJoker) return 100.0
        val me = state.current
        val r = card.rank!!.value
        var v = 14.0 - r                                     // low cards come into play first
        val bank = me.bankTop
        if (bank != null && !bank.isJoker) {
            val b = bank.rank!!.value
            if (r < b && b - r <= 4) v += 8.0                // helps build up to the Bank card
        }
        val gap = gapTo(state, card)
        if (gap == 0) v += 6.0                               // playable right now: worth keeping on top
        return v
    }

    /** Hook: extra cost of putting [card] on top of [stack] (0 for the Easy AI). */
    protected open fun extraTakiCost(state: GameState, card: Card, stack: List<Card>): Double = 0.0

    /** Park the least useful hand card on an empty slot. */
    protected open fun fillMove(state: GameState): AiMove {
        val me = state.current
        val slot = me.ground.indexOfFirst { it.isEmpty() }
        val i = me.hand.indices.minBy { usefulness(state, me.hand[it]) }
        return AiMove.Fill(i, slot)
    }

    /** The Taki: cover the slot where it costs least, with the card we need least. */
    protected open fun takiMove(state: GameState): AiMove =
        takiCandidates(state).firstOrNull()?.first ?: AiMove.Pass

    /** Every possible Taki with its heuristic cost, cheapest first (ties keep the order they were generated in). */
    protected fun takiCandidates(state: GameState): List<Pair<AiMove.Discard, Double>> {
        val me = state.current
        val out = ArrayList<Pair<AiMove.Discard, Double>>()
        for (i in me.hand.indices) {
            val card = me.hand[i]
            for (slot in me.ground.indices) {
                val stack = me.ground[slot]
                val top = stack.last()
                // The card we place stays reachable on top; the one we cover is buried until it is played.
                var cost = TAKI_CARD_WEIGHT * usefulness(state, card)
                val burial = when {
                    card.isJoker || top.isJoker -> 1.0
                    card.rank!!.value < top.rank!!.value -> BURY_UNDER_LOWER   // lower card goes first, top is freed soon
                    card.rank.value == top.rank!!.value -> BURY_UNDER_EQUAL
                    else -> 1.0                                                  // a higher card on a lower one: bad
                }
                cost += burial * usefulness(state, top)
                cost += STACK_DEPTH_COST * stack.size
                cost += extraTakiCost(state, card, stack)
                // A perfect descending run (6 on a 7) unwinds in playing order.
                if (!card.isJoker && !top.isJoker && top.rank!!.value - card.rank!!.value == 1) cost -= RUN_BONUS
                out += AiMove.Discard(i, slot) to cost
            }
        }
        return out.sortedBy { it.second } // stable: equal costs stay in generation order
    }

    companion object {
        const val DEFAULT_BUDGET = 5000
        internal const val MAX_DEPTH = 16
        private const val MIN_IMPROVEMENT = 1.0

        private const val WIN_VALUE = 1_000_000.0
        private const val BANK_VALUE = 1000.0
        internal const val GROUND_VALUE = 35.0
        internal const val HAND_VALUE = 8.0
        internal const val REFILL_VALUE = 45.0
        internal const val PILE_COMPLETE_VALUE = 12.0
        internal const val JOKER_COST = 38.0

        internal const val BANK_THREAT = 320.0
        internal const val BANK_THREAT_NEAR = 70.0
        internal const val GROUND_THREAT = 30.0
        internal const val GROUND_THREAT_NEAR = 8.0
        internal const val READINESS = 0.35

        private const val TAKI_CARD_WEIGHT = 0.6
        private const val BURY_UNDER_LOWER = 0.3
        private const val BURY_UNDER_EQUAL = 0.6
        private const val STACK_DEPTH_COST = 1.2
        private const val RUN_BONUS = 4.0
    }
}
