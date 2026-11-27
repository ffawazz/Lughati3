package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card

/**
 * The Hard computer opponent. It uses the same turn-planning search as [AiPlayer] (Easy) but with a much
 * bigger search budget and better judgement:
 *
 *  - **Card counting.** It tracks every card it can see (centre piles, finished piles, its own cards, and
 *    the opponent's ground stacks) and so knows which cards are still hidden in the draw pile, the
 *    opponent's hand and the two Banks. From that it estimates how likely the opponent is to be holding
 *    the *specific* cards they need.
 *  - **Probabilistic defence.** Easy uses fixed "near miss" penalties. Hard asks "how likely is it that the
 *    opponent can play their Bank card after my move?": a pile one card short of their Bank card is
 *    dangerous when that rank is plentiful in the hidden cards and harmless when it is all gone, and
 *    certain when the missing card is sitting on their ground.
 *  - **Pressure.** As the opponent's Bank runs down, defence counts for more.
 *  - **Protects its own Bank.** Cards that build towards its own Bank card are treated as precious when
 *    choosing what to cover or park, and it avoids burying low cards under higher ones in a stack.
 *
 * Like Easy it only uses information a person at the table could see: no peeking at the draw pile or the
 * opponent's hand.
 */
/**
 * The weights that shape Hard's judgement. The defaults come from self-play against the Easy AI (see
 * HardTuningSweepTest): compared with Easy they put more value on tempo (playing cards out of the hand, which
 * means more fresh draws and the free refill) and on cards that build towards the Bank card, and they defend
 * harder as the opponent's Bank runs down.
 */
data class HardTuning(
    val groundValue: Double = 25.0,
    val handValue: Double = 14.0,
    val refillValue: Double = 90.0,
    val pileCompleteValue: Double = AiPlayer.PILE_COMPLETE_VALUE,
    val jokerCost: Double = AiPlayer.JOKER_COST,
    val bankThreat: Double = AiPlayer.BANK_THREAT,
    val groundThreat: Double = AiPlayer.GROUND_THREAT,
    val pressureSlope: Double = 0.2,
    val readiness: Double = 0.7,
    val unlockBonus: Double = 28.0,
    val inversionCost: Double = 1.5,
    /** Judge our own readiness by how many fresh cards we will really draw (fewer cards kept = more draws). */
    val drawAware: Boolean = true,
    /** How many of the best-looking Taki placements get a proper look-ahead (0 = just use the heuristic). */
    val lookaheadCandidates: Int = 6,
    /** How many imagined next draws each placement is tested against. */
    val lookaheadSamples: Int = 24,
    val lookaheadBudget: Int = 1200,
    val lookaheadDepth: Int = 12,
    /** How much the old placement heuristic still counts next to the look-ahead (tie-breaker). */
    val heuristicWeight: Double = 0.5,
)

class HardAiPlayer(rules: GameRules, private val t: HardTuning = HardTuning()) :
    AiPlayer(rules, nodeBudget = HARD_BUDGET, maxDepth = HARD_DEPTH) {

    override val groundValue get() = t.groundValue
    override val handValue get() = t.handValue
    override val refillValue get() = t.refillValue
    override val pileCompleteValue get() = t.pileCompleteValue
    override val jokerCost get() = t.jokerCost

    // Hidden-card knowledge, refreshed at the start of every decision (see prepare()).
    private val unseenByRank = IntArray(14)
    private var unseenJokers = 0
    private var unseenTotal = 1

    override fun prepare(state: GameState) {
        val me = state.current
        val opp = state.players[1 - state.currentPlayer]
        val seen = ArrayList<Card>(80)
        state.centerPiles.forEach { pile -> pile.forEach { seen += it.card } }
        seen += state.completedCards
        seen += me.hand
        me.ground.forEach { seen += it }
        me.bankTop?.let { seen += it }
        opp.ground.forEach { seen += it }
        opp.bankTop?.let { seen += it }

        unseenByRank.fill(8)
        unseenJokers = 4
        for (c in seen) {
            if (c.isJoker) unseenJokers-- else unseenByRank[c.rank!!.value]--
        }
        for (r in 1..13) if (unseenByRank[r] < 0) unseenByRank[r] = 0
        if (unseenJokers < 0) unseenJokers = 0
        unseenTotal = (108 - seen.size).coerceAtLeast(1)
    }

    /** Chance that [draws] cards drawn from the hidden cards include [rank] (or a Joker standing in for it). */
    internal fun handProbability(rank: Int, draws: Int = 5): Double {
        val k = unseenByRank[rank] + unseenJokers
        return 1.0 - Math.pow(1.0 - k.toDouble() / unseenTotal, draws.toDouble())
    }

    // ------------------------------------------------------------------ judging a position

    /**
     * Probability that [card] becomes playable on these piles, given [certain] = ranks of cards its owner
     * is known to hold (their visible ground tops, or our own hand) and [wildcards] known Jokers among them.
     * Looks at most 3 cards ahead.
     */
    private fun playProbability(state: GameState, card: Card, certain: List<Int>, wildcards: Int, draws: Int = 5): Double {
        if (card.isJoker) return 0.4 // wild: cannot really be denied
        val r = card.rank!!.value
        var best = 0.0
        for (pile in state.centerPiles) {
            val gap = r - 1 - pile.size
            if (gap < 0 || gap > 3) continue
            var prob = 1.0
            var wild = wildcards
            val have = certain.toMutableList()
            for (need in (pile.size + 1)..(r - 1)) {
                val idx = have.indexOf(need)
                prob *= when {
                    idx >= 0 -> { have.removeAt(idx); 1.0 }
                    wild > 0 -> { wild--; 1.0 }
                    else -> handProbability(need, draws)
                }
            }
            if (prob > best) best = prob
        }
        return best
    }

    override fun terminal(state: GameState): Double {
        val me = state.current
        val opp = state.players[1 - state.currentPlayer]
        // The closer the opponent is to winning, the more every denied card is worth.
        val pressure = 1.0 + t.pressureSlope * (BANK_SIZE - opp.bank.size).coerceAtLeast(0)
        var score = 0.0

        val oppTops = opp.ground.map { it.lastOrNull() }
        fun othersCertain(skip: Int?): Pair<List<Int>, Int> {
            val tops = oppTops.filterIndexed { i, _ -> i != skip }.filterNotNull()
            return tops.filter { !it.isJoker }.map { it.rank!!.value } to tops.count { it.isJoker }
        }

        opp.bankTop?.let { c ->
            val (ranks, jokers) = othersCertain(null)
            score -= pressure * t.bankThreat * playProbability(state, c, ranks, jokers)
        }
        oppTops.forEachIndexed { i, c ->
            if (c != null) {
                val (ranks, jokers) = othersCertain(i)
                score -= pressure * t.groundThreat * playProbability(state, c, ranks, jokers)
            }
        }

        // Readiness for our next turn: we know our own hand exactly.
        val myHand = me.hand.filter { !it.isJoker }.map { it.rank!!.value }
        val myJokers = me.hand.count { it.isJoker }
        // Next turn we draw back up to 5: ending the turn with fewer cards (one goes to the ground as the Taki)
        // means more fresh draws and so a better chance of finding the cards we are missing.
        val draws = if (t.drawAware) (6 - me.hand.size).coerceIn(0, 5) else 5
        me.bankTop?.let { c -> score += t.readiness * t.bankThreat * playProbability(state, c, myHand, myJokers, draws) }
        me.ground.forEachIndexed { i, s ->
            val c = s.lastOrNull() ?: return@forEachIndexed
            score += t.readiness * t.groundThreat * playProbability(state, c, myHand, myJokers, draws)
        }
        return score
    }

    // ------------------------------------------------------------------ the Taki, with look-ahead

    /**
     * Instead of trusting a rule of thumb, Hard tries its most promising Taki placements: for each it imagines
     * several likely next draws (sampled from the cards it has not seen), plans the following turn with its
     * normal search, and keeps the placement that leaves it the best expected next turn.
     */
    override fun takiMove(state: GameState): AiMove {
        val ranked = takiCandidates(state)
        if (ranked.size <= 1 || t.lookaheadCandidates <= 1) return ranked.firstOrNull()?.first ?: AiMove.Pass

        val me = state.currentPlayer
        val draws = (HAND_TARGET - (state.current.hand.size - 1)).coerceIn(0, HAND_TARGET)
        val worlds = sampleDraws(state, draws, t.lookaheadSamples)
        val sandbox = state.copy(drawPile = emptyList(), completedCards = emptyList())

        var best = ranked.first().first
        var bestScore = Double.NEGATIVE_INFINITY
        for ((move, cost) in ranked.take(t.lookaheadCandidates)) {
            val after = rules.discard(sandbox, move.handIndex, move.slot) ?: continue
            var sum = 0.0
            for (world in worlds) {
                // Pretend it is our turn again, with the placement made and the imagined cards drawn.
                val next = after.copy(
                    currentPlayer = me,
                    players = after.players.mapIndexed { i, p -> if (i == me) p.copy(hand = p.hand + world) else p },
                )
                sum += Search(t.lookaheadBudget).best(next, t.lookaheadDepth)
            }
            val score = sum / worlds.size - t.heuristicWeight * cost
            if (score > bestScore) { bestScore = score; best = move }
        }
        return best
    }

    /** [count] imagined draws of [n] cards each, sampled without replacement from the hidden cards. */
    private fun sampleDraws(state: GameState, n: Int, count: Int): List<List<Card>> {
        if (n == 0) return listOf(emptyList())
        val pool = ArrayList<Int>() // 0 = Joker, 1..13 = rank
        for (r in 1..13) repeat(unseenByRank[r]) { pool += r }
        repeat(unseenJokers) { pool += 0 }
        if (pool.size < n) return listOf(emptyList())
        // A fixed seed derived from the visible table keeps decisions repeatable and the comparison fair
        // (every placement is judged against the same imagined draws).
        val seed = state.current.hand.sumOf { it.id } * 31 + state.centerPiles.sumOf { it.size } + state.players[0].bank.size
        val rnd = kotlin.random.Random(seed)
        return List(count) { k ->
            val bag = pool.toIntArray()
            for (i in 0 until n) { val j = i + rnd.nextInt(bag.size - i); val tmp = bag[i]; bag[i] = bag[j]; bag[j] = tmp }
            List(n) { i ->
                val token = bag[i]
                val id = -1 - (k * HAND_TARGET + i)
                if (token == 0) Card(id, null, null) else Card(id, com.lughati3.spite.model.Rank.fromValue(token), com.lughati3.spite.model.Suit.SPADES)
            }
        }
    }

    // ------------------------------------------------------------------ what to keep, what to cover

    /** Ranks still missing between the best centre pile and our Bank card (the cards that would unlock it). */
    private fun unlockRanks(state: GameState): Set<Int> {
        val bank = state.current.bankTop ?: return emptySet()
        if (bank.isJoker) return emptySet()
        val b = bank.rank!!.value
        val pile = state.centerPiles
            .filter { b - 1 - it.size >= 0 }
            .minByOrNull { b - 1 - it.size } ?: return emptySet()
        return ((pile.size + 1) until b).toSet()
    }

    override fun usefulness(state: GameState, card: Card): Double {
        if (card.isJoker) return 100.0
        val r = card.rank!!.value
        var v = 14.0 - r
        val unlock = unlockRanks(state)
        val bank = state.current.bankTop
        when {
            r in unlock -> v += t.unlockBonus                                                   // builds towards our Bank card
            bank != null && !bank.isJoker && r < bank.rank!!.value && bank.rank.value - r <= 4 -> v += 4.0
        }
        when (gapTo(state, card)) {
            0 -> v += 6.0
            1 -> v += 3.0
        }
        return v
    }

    /** A lower card buried under a higher one has to wait for it: charge for every such inversion. */
    override fun extraTakiCost(state: GameState, card: Card, stack: List<Card>): Double {
        if (card.isJoker) return 0.0
        val r = card.rank!!.value
        return t.inversionCost * stack.count { !it.isJoker && it.rank!!.value < r }
    }

    companion object {
        const val HARD_BUDGET = 20_000
        const val HARD_DEPTH = 24
        private const val BANK_SIZE = 10
        private const val HAND_TARGET = 5
    }
}
