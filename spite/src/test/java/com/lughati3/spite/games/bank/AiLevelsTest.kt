package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLevelsTest {
    private var id = 8000
    private fun c(r: Rank, s: Suit = Suit.SPADES) = Card(id++, r, s)
    private fun joker() = Card(id++, null, null)
    private fun pileOf(n: Int) = (1..n).map { PileCard(c(Rank.fromValue(it)), Rank.fromValue(it)) }

    private fun apply(r: GameRules, s: GameState, m: AiMove): GameState = when (m) {
        is AiMove.Play -> r.playToCenter(s, m.source, m.pile)!!.state
        is AiMove.Fill -> r.fillGround(s, m.handIndex, m.slot)!!.state
        is AiMove.Discard -> r.discard(s, m.handIndex, m.slot)!!
        AiMove.Pass -> r.passTurn(s)!!
    }

    // ---------------------------------------------------------------- Easy must not change

    @Test fun easyPlaysExactlyLikeTheAiThatShippedBeforeDifficultyLevels() {
        repeat(25) { seed ->
            val r = GameRules(Random(seed))
            val easy = AiLevel.EASY.create(r)
            val frozen = FrozenEasyAiPlayer(r)
            var s = r.newGame()
            var steps = 0
            while (!s.isOver) {
                check(steps++ < 30_000)
                val a = easy.chooseMove(s)
                val b = frozen.chooseMove(s)
                assertEquals("seed $seed step $steps", b, a)
                s = apply(r, s, a)
            }
        }
    }

    // ---------------------------------------------------------------- Hard thinks differently

    private fun player(hand: List<Card>, bank: List<Card>, ground: List<List<Card>> = List(5) { listOf(c(Rank.KING)) }) =
        PlayerState("p", false, hand, ground, bank)

    private fun table(me: PlayerState, opp: PlayerState, piles: List<List<PileCard>>, completed: List<Card> = emptyList()) =
        GameState(listOf(me, opp), emptyList(), piles, completed, 0)

    /** All eight 5s and all four Jokers are accounted for (finished piles), so nobody can hold one. */
    private fun allFivesAndJokersGone() = Suit.entries.flatMap { listOf(c(Rank.FIVE, it), c(Rank.FIVE, it)) } + List(4) { joker() }

    @Test fun holdsACardBackWhenTheOpponentCouldPlausiblyFinishTheChain() {
        // Opponent's Bank top is a 6; a pile stands at 3. If I play my 4, the pile needs a 5 and then the 6 is theirs.
        // Plenty of 5s are hidden, so that is a real danger.
        val me = player(listOf(c(Rank.FOUR), c(Rank.NINE)), bank = listOf(c(Rank.KING)))
        val opp = player(emptyList(), bank = listOf(c(Rank.SIX)))
        val s = table(me, opp, listOf(pileOf(3), pileOf(9), pileOf(10)))
        val hard = HardAiPlayer(GameRules(Random(1)))
        assertTrue("Hard should hold the 4 back", hard.chooseMove(s) !is AiMove.Play)
    }

    @Test fun playsFreelyWhenTheCardTheOpponentWouldNeedIsGone() {
        // Same table, but every 5 and Joker has been seen: the opponent cannot possibly have the missing 5,
        // so playing the 4 costs nothing. Easy still sees a "near miss" and holds back; Hard knows better.
        val me = player(listOf(c(Rank.FOUR), c(Rank.NINE)), bank = listOf(c(Rank.KING)))
        val opp = player(emptyList(), bank = listOf(c(Rank.SIX)))
        val s = table(me, opp, listOf(pileOf(3), pileOf(9), pileOf(10)), completed = allFivesAndJokersGone())
        val hard = HardAiPlayer(GameRules(Random(1)))
        val m = hard.chooseMove(s)
        assertTrue("Hard should play the 4, got $m", m is AiMove.Play && (m.source as? CardSource.Hand)?.index == 0)
        val easy = AiPlayer(GameRules(Random(1)))
        assertTrue("Easy holds back here", easy.chooseMove(s) !is AiMove.Play)
    }

    @Test fun countsTheCardsItCanSee() {
        val me = player(listOf(c(Rank.FIVE), c(Rank.FIVE)), bank = listOf(c(Rank.KING)))
        val opp = player(emptyList(), bank = listOf(c(Rank.SIX)))
        val s = table(me, opp, listOf(pileOf(3), pileOf(0), pileOf(0)), completed = allFivesAndJokersGone())
        val hard = HardAiPlayer(GameRules(Random(1)))
        hard.chooseMove(s)                      // prepare() runs first
        assertEquals(0.0, hard.handProbability(5), 1e-9)   // every 5 and Joker is known
        assertTrue(hard.handProbability(7) > 0.2)          // sevens are mostly hidden
    }

    @Test fun neverPeeksAtHiddenCards() {
        val me = player(listOf(c(Rank.ACE), c(Rank.NINE)), bank = listOf(c(Rank.NINE)))
        val opp = player(listOf(c(Rank.ACE)), bank = listOf(c(Rank.KING)))
        val base = table(me, opp, List(3) { emptyList() })
        val a = base.copy(drawPile = List(20) { c(Rank.TWO) }, players = listOf(me, opp.copy(hand = List(5) { c(Rank.TWO) })))
        val b = base.copy(drawPile = List(20) { c(Rank.ACE) }, players = listOf(me, opp.copy(hand = List(5) { c(Rank.ACE) })))
        val hard = HardAiPlayer(GameRules(Random(1)))
        assertEquals(hard.chooseMove(a), hard.chooseMove(b))
    }

    // ---------------------------------------------------------------- strength

    private fun playGame(seed: Int, hardSeat: Int): Pair<Int, Boolean> {
        val r = GameRules(Random(seed))
        val hard = AiLevel.HARD.create(r)
        val easy = AiLevel.EASY.create(r)
        var s = r.newGame()
        var steps = 0
        while (!s.isOver) {
            check(steps++ < 30_000) { "game $seed did not finish" }
            val m = (if (s.currentPlayer == hardSeat) hard else easy).chooseMove(s)
            s = apply(r, s, m)
        }
        return (s.winner ?: -1) to s.stalemate
    }

    @Test fun hardBeatsEasyOverManyGames() {
        // 80 deals keep the normal test run short; set AI_GAMES=600 for a proper measurement (Hard wins about 60%).
        val games = System.getenv("AI_GAMES")?.toIntOrNull() ?: 80
        var hardWins = 0; var draws = 0
        repeat(games) { g ->
            val seat = g % 2 // alternate who moves first so the first-move edge cancels out
            val (winner, _) = playGame(seed = 3000 + g / 2, hardSeat = seat)
            if (winner == seat) hardWins++ else if (winner == -1) draws++
        }
        println("HARD wins $hardWins / $games vs EASY ($draws drawn)")
        assertTrue("Hard only won $hardWins of $games", hardWins * 2 > games) // must at least be the stronger side
    }

    @Test fun hardAnswersQuicklyEnoughToPlayAgainst() {
        val r = GameRules(Random(77))
        val hard = AiLevel.HARD.create(r)
        val easy = AiLevel.EASY.create(r)
        var s = r.newGame()
        var worstMs = 0L; var totalMs = 0L; var n = 0; var steps = 0
        while (!s.isOver) {
            check(steps++ < 30_000)
            val isHard = s.currentPlayer == 0
            val t0 = System.nanoTime()
            val m = (if (isHard) hard else easy).chooseMove(s)
            if (isHard) { val ms = (System.nanoTime() - t0) / 1_000_000; worstMs = maxOf(worstMs, ms); totalMs += ms; n++ }
            s = apply(r, s, m)
        }
        println("HARD decision time: avg ${totalMs / n} ms, worst $worstMs ms over $n decisions")
        assertTrue("worst decision took $worstMs ms", worstMs < 3000)
    }
}
