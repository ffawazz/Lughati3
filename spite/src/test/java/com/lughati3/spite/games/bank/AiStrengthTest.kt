package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStrengthTest {
    private var id = 5000
    private fun c(r: Rank, s: Suit = Suit.SPADES) = Card(id++, r, s)
    private fun joker() = Card(id++, null, null)
    private fun pileOf(n: Int) = (1..n).map { PileCard(c(Rank.fromValue(it)), Rank.fromValue(it)) }

    private fun player(
        hand: List<Card>, bank: List<Card>, name: String = "p",
        ground: List<List<Card>> = List(5) { listOf(c(Rank.KING)) },
    ) = PlayerState(name, false, hand, ground, bank)

    private fun table(me: PlayerState, opp: PlayerState, piles: List<List<PileCard>>) =
        GameState(listOf(me, opp), emptyList(), piles, emptyList(), 0)

    private val rules = GameRules(Random(1))
    private val ai = AiPlayer(rules)

    // ---------------------------------------------------------------- defence

    @Test fun doesNotHandTheOpponentTheirBankCard() {
        // Opponent's Bank top is a 5. A pile sits at 3 (needs a 4). Playing my 4 would make that pile want
        // exactly a 5 — their Bank card. A smart player keeps the 4 back.
        val me = player(listOf(c(Rank.FOUR), c(Rank.NINE)), bank = listOf(c(Rank.KING)))
        val opp = player(emptyList(), bank = listOf(c(Rank.FIVE)))
        val s = table(me, opp, listOf(pileOf(3), pileOf(9), pileOf(10)))
        val m = ai.chooseMove(s)
        assertTrue("should not play the 4 on the 3-pile, got $m", m !is AiMove.Play)
    }

    @Test fun stillPlaysItsOwnBankCardEvenIfItHelpsTheOpponent() {
        // Own Bank progress (here: winning) beats defence.
        val me = player(listOf(c(Rank.NINE)), bank = listOf(c(Rank.FOUR)))
        val opp = player(emptyList(), bank = listOf(c(Rank.FIVE)))
        val s = table(me, opp, listOf(pileOf(3), pileOf(9), pileOf(10)))
        val m = ai.chooseMove(s) as AiMove.Play
        assertEquals(CardSource.Bank, m.source)
    }

    @Test fun clearsAPileThatWouldHandTheOpponentTheirKing() {
        // Opponent's Bank top is a King and one pile stands at 12, so the King is playable for them.
        // I hold a King on my ground: playing it completes and clears the pile — pure defence.
        val ground = listOf(listOf(c(Rank.KING)), listOf(c(Rank.TWO)), listOf(c(Rank.TWO)), listOf(c(Rank.TWO)), listOf(c(Rank.TWO)))
        val me = player(listOf(c(Rank.NINE)), bank = listOf(c(Rank.EIGHT), c(Rank.NINE)), ground = ground)
        val opp = player(emptyList(), bank = listOf(c(Rank.KING)))
        val m = ai.chooseMove(table(me, opp, listOf(pileOf(12), pileOf(5), pileOf(6)))) as AiMove.Play
        assertEquals(CardSource.Ground(0), m.source)
    }

    // ---------------------------------------------------------------- planning

    @Test fun findsAMultiCardChainToUnlockTheBank() {
        // Bank top is a 4. One pile holds an Ace; I hold 2 and 3. The Bank card only becomes playable after
        // two hand plays, and the AI must see that far ahead.
        val me = player(listOf(c(Rank.THREE), c(Rank.TWO), c(Rank.NINE)), bank = listOf(c(Rank.SEVEN), c(Rank.FOUR)))
        val opp = player(emptyList(), bank = listOf(c(Rank.KING)))
        var s = table(me, opp, listOf(pileOf(1), emptyList(), pileOf(10)))
        var played = 0
        while (true) {
            val m = ai.chooseMove(s) as? AiMove.Play ?: break
            s = rules.playToCenter(s, m.source, m.pile)!!.state
            played++
        }
        assertEquals(1, s.current.bank.size) // the 4 went out, revealing the 7
        assertTrue(played >= 3)
    }

    @Test fun burnsAJokerOnlyWhenItPaysOff() {
        val opp = player(emptyList(), bank = listOf(c(Rank.KING)))
        // The Joker (as an Ace) unlocks the Bank's 2: worth it.
        val pays = table(player(listOf(joker(), c(Rank.NINE)), bank = listOf(c(Rank.TWO))), opp, List(3) { emptyList() })
        assertTrue(ai.chooseMove(pays) is AiMove.Play)
        // Nothing to unlock: the Joker stays in the hand.
        val wasted = table(player(listOf(joker(), c(Rank.TWO)), bank = listOf(c(Rank.SIX))), opp, listOf(pileOf(8), pileOf(10), pileOf(10)))
        assertTrue(ai.chooseMove(wasted) !is AiMove.Play)
    }

    @Test fun planningNeverPeeksAtTheDrawPile() {
        // Same visible table, different (hidden) draw piles => identical decision.
        val me = player(listOf(c(Rank.ACE)), bank = listOf(c(Rank.NINE)))
        val opp = player(emptyList(), bank = listOf(c(Rank.KING)))
        val a = table(me, opp, List(3) { emptyList() }).copy(drawPile = List(20) { c(Rank.TWO) })
        val b = table(me, opp, List(3) { emptyList() }).copy(drawPile = List(20) { c(Rank.ACE) })
        assertEquals(ai.chooseMove(a), ai.chooseMove(b))
    }

    // ---------------------------------------------------------------- end of turn

    @Test fun coversTheKingNotTheFive() {
        val ground = listOf(listOf(c(Rank.FIVE)), listOf(c(Rank.KING)), listOf(c(Rank.SEVEN)), listOf(c(Rank.SIX)), listOf(c(Rank.EIGHT)))
        val me = player(listOf(c(Rank.QUEEN)), bank = listOf(c(Rank.NINE)), ground = ground)
        val opp = player(emptyList(), bank = listOf(c(Rank.KING)))
        val m = ai.chooseMove(table(me, opp, listOf(pileOf(1), pileOf(2), pileOf(9)))) as AiMove.Discard
        assertEquals(1, m.slot)
    }

    @Test fun neverPilesOntoTheDeepStack() {
        val ground = listOf(listOf(c(Rank.TEN), c(Rank.NINE), c(Rank.SEVEN)), listOf(c(Rank.SEVEN)), listOf(c(Rank.KING)), listOf(c(Rank.KING)), listOf(c(Rank.KING)))
        val me = player(listOf(c(Rank.SIX)), bank = listOf(c(Rank.KING)), ground = ground)
        val opp = player(emptyList(), bank = listOf(c(Rank.KING)))
        val m = ai.chooseMove(table(me, opp, listOf(pileOf(1), pileOf(2), pileOf(9)))) as AiMove.Discard
        assertTrue(m.slot != 0)
    }

    // ---------------------------------------------------------------- strength

    private fun playGame(seed: Int, newAiSeat: Int, budgetNew: Int = AiPlayer.DEFAULT_BUDGET): Pair<Int, Boolean> {
        val r = GameRules(Random(seed))
        val fresh = AiPlayer(r, budgetNew)
        val old = LegacyAiPlayer(r)
        var s = r.newGame().let { if (newAiSeat == 1) it else it } // seat 0 always moves first
        var steps = 0
        while (!s.isOver) {
            check(steps++ < 30_000) { "game $seed did not finish" }
            val useNew = s.currentPlayer == newAiSeat
            val m = if (useNew) fresh.chooseMove(s) else old.chooseMove(s)
            s = when (m) {
                is AiMove.Play -> r.playToCenter(s, m.source, m.pile)!!.state
                is AiMove.Fill -> r.fillGround(s, m.handIndex, m.slot)!!.state
                is AiMove.Discard -> r.discard(s, m.handIndex, m.slot)!!
                AiMove.Pass -> r.passTurn(s)!!
            }
        }
        return (s.winner ?: -1) to s.stalemate // winner (-1 = drawn); whether the stalemate rule ended it
    }

    @Test fun newAiBeatsTheOldGreedyAiOverManyGames() {
        val games = System.getenv("AI_GAMES")?.toIntOrNull() ?: 60 // set AI_GAMES for a bigger benchmark
        var newWins = 0
        var stalemates = 0
        repeat(games) { g ->
            val seat = g % 2 // alternate who moves first so the first-move edge cancels out
            val (winner, stalled) = playGame(seed = 1000 + g / 2, newAiSeat = seat)
            if (winner == seat) newWins++
            if (stalled) stalemates++
        }
        println("NEW AI wins $newWins / $games ($stalemates ended by stalemate)")
        assertTrue("new AI only won $newWins of $games", newWins * 100 / games >= 60)
    }
}
