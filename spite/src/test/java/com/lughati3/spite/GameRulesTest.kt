package com.lughati3.spite

import com.lughati3.spite.domain.AiMove
import com.lughati3.spite.domain.AiPlayer
import com.lughati3.spite.domain.GameRules
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.CardSource
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.GameState
import com.lughati3.spite.model.PileCard
import com.lughati3.spite.model.PlayerState
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    private val rules = GameRules(Random(1))
    private var nextId = 1000

    private fun c(rank: Rank, suit: Suit = Suit.SPADES) = Card(nextId++, rank, suit)
    private fun joker() = Card(nextId++, null, null)

    private fun player(
        hand: List<Card> = emptyList(),
        bank: List<Card> = listOf(c(Rank.KING)),
        ground: List<List<Card>> = List(5) { emptyList() },
    ) = PlayerState("p", true, hand, ground, bank)

    private fun state(me: PlayerState, deck: List<Card> = emptyList(), piles: List<List<PileCard>>? = null) = GameState(
        players = listOf(me, player(hand = List(5) { c(Rank.NINE) })),
        drawPile = deck,
        centerPiles = piles ?: List(4) { emptyList() },
        completedCards = emptyList(),
        currentPlayer = 0,
    )

    private fun pileOf(n: Int) = (1..n).map { PileCard(c(Rank.fromValue(it)), Rank.fromValue(it)) }

    @Test fun deckHas108CardsWith4Jokers() {
        val deck = Deck.create()
        assertEquals(108, deck.size)
        assertEquals(4, deck.count { it.isJoker })
        assertEquals(108, deck.map { it.id }.toSet().size)
    }

    @Test fun newGameDealsCorrectly() {
        val g = rules.newGame()
        g.players.forEach { assertEquals(5, it.hand.size); assertEquals(10, it.bank.size); assertEquals(5, it.ground.size) }
        assertEquals(108 - 30, g.drawPile.size)
        assertEquals(4, g.centerPiles.size)
        assertEquals(GameState.HUMAN, g.currentPlayer)
    }

    @Test fun onlyAceOrJokerStartsAPile() {
        val s = state(player(hand = listOf(c(Rank.TWO), c(Rank.ACE), joker())))
        assertNull(rules.playToCenter(s, CardSource.Hand(0), 0))
        assertNotNull(rules.playToCenter(s, CardSource.Hand(1), 0))
        val r = rules.playToCenter(s, CardSource.Hand(2), 0)!!
        assertEquals(Rank.ACE, r.playedAs)
    }

    @Test fun pilesMustAscendAndJokerTakesNextRank() {
        val s = state(player(hand = listOf(c(Rank.FIVE), c(Rank.FOUR), joker())), piles = listOf(pileOf(3), emptyList(), emptyList(), emptyList()))
        assertNull(rules.playToCenter(s, CardSource.Hand(0), 0))
        assertNotNull(rules.playToCenter(s, CardSource.Hand(1), 0))
        assertEquals(Rank.FOUR, rules.playToCenter(s, CardSource.Hand(2), 0)!!.playedAs)
    }

    @Test fun kingCompletesAndClearsThePile() {
        val s = state(player(hand = listOf(c(Rank.KING), joker())), piles = listOf(pileOf(12), emptyList(), emptyList(), emptyList()))
        val r = rules.playToCenter(s, CardSource.Hand(0), 0)!!
        assertTrue(r.pileCompleted)
        assertTrue(r.state.centerPiles[0].isEmpty())
        assertEquals(13, r.state.completedCards.size)
        // A Joker can also be the King.
        val rj = rules.playToCenter(s, CardSource.Hand(1), 0)!!
        assertEquals(Rank.KING, rj.playedAs)
        assertTrue(rj.pileCompleted)
    }

    @Test fun emptyingHandDrawsFiveAndTurnContinues() {
        val deck = List(10) { c(Rank.NINE) }
        val s = state(player(hand = listOf(c(Rank.ACE))), deck)
        val r = rules.playToCenter(s, CardSource.Hand(0), 0)!!
        assertTrue(r.handRefilled)
        assertEquals(5, r.state.current.hand.size)
        assertEquals(0, r.state.currentPlayer)
    }

    @Test fun bankRevealsNextCardAndLastBankCardWins() {
        val s = state(player(bank = listOf(c(Rank.NINE), c(Rank.ACE))))
        val r = rules.playToCenter(s, CardSource.Bank, 0)!!
        assertEquals(Rank.NINE, r.state.current.bankTop!!.rank)
        assertNull(r.state.winner)

        val last = state(player(bank = listOf(joker())))
        val w = rules.playToCenter(last, CardSource.Bank, 2)!!
        assertEquals(0, w.state.winner)
        assertNull(rules.playToCenter(w.state, CardSource.Bank, 0)) // game is over
    }

    @Test fun groundOnlyTopIsPlayable() {
        val ground = listOf(listOf(c(Rank.ACE), c(Rank.FIVE)), emptyList(), emptyList(), emptyList(), emptyList())
        val s = state(player(ground = ground))
        assertNull(rules.playToCenter(s, CardSource.Ground(0), 0)) // top is a 5
        assertNull(rules.playToCenter(s, CardSource.Ground(1), 0)) // empty slot
    }

    @Test fun discardMustUseEmptySlotUntilAllFilled_thenTakiCovers() {
        val ground = listOf(listOf(c(Rank.TWO)), emptyList(), emptyList(), emptyList(), emptyList())
        val s = state(player(hand = listOf(c(Rank.SEVEN)), ground = ground), List(10) { c(Rank.NINE) })
        assertNull(rules.discard(s, 0, 0)) // slot 0 is taken, empties exist
        val after = rules.discard(s, 0, 1)!!
        assertEquals(1, after.currentPlayer) // turn passed
        assertEquals(5, after.players[1].hand.size)

        val full = List(5) { listOf(c(Rank.TWO)) }
        val t = state(player(hand = listOf(c(Rank.SEVEN)), ground = full), List(10) { c(Rank.NINE) })
        val taki = rules.discard(t, 0, 3)!!
        assertEquals(2, taki.players[0].ground[3].size)
        assertEquals(Rank.SEVEN, taki.players[0].groundTop(3)!!.rank)
    }

    @Test fun drawPileReshufflesCompletedPiles() {
        val base = state(player(hand = listOf(c(Rank.SEVEN))), deck = emptyList())
        val s = base.copy(completedCards = List(13) { c(Rank.KING) })
        val after = rules.discard(s, 0, 0)!!
        assertEquals(5, after.players[1].hand.size) // had 5 already -> no draw; deck untouched
        // Force a real draw: opponent has an empty hand.
        val s2 = s.copy(players = listOf(s.players[0], s.players[1].copy(hand = emptyList())))
        val after2 = rules.discard(s2, 0, 0)!!
        assertEquals(5, after2.players[1].hand.size)
        assertEquals(8, after2.drawPile.size)
        assertTrue(after2.completedCards.isEmpty())
    }

    @Test fun aiPlaysBankBeforeHand() {
        val s = state(player(hand = listOf(c(Rank.ACE)), bank = listOf(c(Rank.NINE), c(Rank.ACE))))
        val move = AiPlayer(rules).chooseMove(s) as AiMove.Play
        assertEquals(CardSource.Bank, move.source)
    }

    @Test fun aiUsesJokerToUnlockBankButNotWastefully() {
        val ai = AiPlayer(rules)
        // Bank wants a 2 and no pile exists: a hand Joker as Ace unlocks it.
        val unlock = state(player(hand = listOf(joker(), c(Rank.NINE)), bank = listOf(c(Rank.TWO))))
        val m = ai.chooseMove(unlock) as AiMove.Play
        assertEquals(CardSource.Hand(0), m.source)
        // Nothing to unlock: the Joker is kept and a non-joker is discarded.
        val keep = state(player(hand = listOf(joker(), c(Rank.NINE)), bank = listOf(c(Rank.SIX))))
        val k = ai.chooseMove(keep) as AiMove.Discard
        assertEquals(1, k.handIndex)
    }

    @Test fun aiTakiAvoidsBuryingUsefulCards() {
        val ground = listOf(
            listOf(c(Rank.FIVE)), listOf(c(Rank.KING)), listOf(c(Rank.SEVEN)), listOf(c(Rank.SIX)), listOf(c(Rank.EIGHT)),
        )
        val s = state(player(hand = listOf(c(Rank.QUEEN)), bank = listOf(c(Rank.NINE)), ground = ground))
        val m = AiPlayer(rules).chooseMove(s) as AiMove.Discard
        assertEquals(1, m.slot) // cover the King, not a low/useful card
    }

    /** Two AIs play whole games: must always terminate with a winner and never make an illegal move. */
    @Test fun aiVsAiGamesAlwaysFinish() {
        repeat(40) { seed ->
            val r = GameRules(Random(seed))
            val ai = AiPlayer(r)
            var s = r.newGame()
            var steps = 0
            while (!s.isOver) {
                check(steps++ < 20_000) { "seed $seed did not finish" }
                s = when (val m = ai.chooseMove(s)) {
                    is AiMove.Play -> r.playToCenter(s, m.source, m.pile)!!.state
                    is AiMove.Discard -> r.discard(s, m.handIndex, m.slot)!!
                    AiMove.Pass -> r.passTurn(s)!!
                }
                // Card conservation: 108 cards always exist somewhere.
                val total = s.players.sumOf { it.hand.size + it.bank.size + it.ground.sumOf { g -> g.size } } +
                    s.drawPile.size + s.completedCards.size + s.centerPiles.sumOf { it.size }
                assertEquals(108, total)
            }
            assertEquals(0, s.players[s.winner!!].bank.size)
        }
    }
}
