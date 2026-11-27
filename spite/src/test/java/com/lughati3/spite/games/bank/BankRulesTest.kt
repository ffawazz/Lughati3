package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Deck
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankRulesTest {
    private val rules = GameRules(Random(1))
    private var nextId = 1000

    private fun c(rank: Rank, suit: Suit = Suit.SPADES) = Card(nextId++, rank, suit)
    private fun joker() = Card(nextId++, null, null)

    private fun player(
        hand: List<Card> = emptyList(),
        bank: List<Card> = listOf(c(Rank.KING)),
        ground: List<List<Card>> = List(5) { listOf(c(Rank.KING)) }, // a full ground by default
    ) = PlayerState("p", true, hand, ground, bank)

    private fun state(me: PlayerState, deck: List<Card> = emptyList(), piles: List<List<PileCard>>? = null) = GameState(
        players = listOf(me, player(hand = List(5) { c(Rank.NINE) })),
        drawPile = deck,
        centerPiles = piles ?: List(GameRules.CENTER_PILES) { emptyList() },
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
        g.players.forEach { assertEquals(5, it.hand.size); assertEquals(10, it.bank.size); assertEquals(5, it.ground.size); assertTrue(it.ground.all { g -> g.size == 1 }) }
        assertEquals(108 - 40, g.drawPile.size) // 2 x (5 hand + 5 ground + 10 bank)
        assertEquals(3, g.centerPiles.size)
        assertEquals(GameState.HUMAN, g.currentPlayer)
    }

    @Test fun twoPlayerGameNamesBothPlayersHuman() {
        val g = rules.newGame(twoPlayers = true)
        assertEquals(listOf("Player 1", "Player 2"), g.players.map { it.name })
        assertTrue(g.players.all { it.isHuman })
        assertEquals(68, g.drawPile.size) // 108 - 2 x 20 dealt
    }

    @Test fun onlyAceOrJokerStartsAPile() {
        val s = state(player(hand = listOf(c(Rank.TWO), c(Rank.ACE), joker())))
        assertNull(rules.playToCenter(s, CardSource.Hand(0), 0))
        assertNotNull(rules.playToCenter(s, CardSource.Hand(1), 0))
        val r = rules.playToCenter(s, CardSource.Hand(2), 0)!!
        assertEquals(Rank.ACE, r.playedAs)
    }

    @Test fun pilesMustAscendAndJokerTakesNextRank() {
        val s = state(player(hand = listOf(c(Rank.FIVE), c(Rank.FOUR), joker())), piles = listOf(pileOf(3), emptyList(), emptyList()))
        assertNull(rules.playToCenter(s, CardSource.Hand(0), 0))
        assertNotNull(rules.playToCenter(s, CardSource.Hand(1), 0))
        assertEquals(Rank.FOUR, rules.playToCenter(s, CardSource.Hand(2), 0)!!.playedAs)
    }

    @Test fun kingCompletesAndClearsThePile() {
        val s = state(player(hand = listOf(c(Rank.KING), joker())), piles = listOf(pileOf(12), emptyList(), emptyList()))
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

    @Test fun cannotTakiWhileAGroundSlotIsEmpty() {
        val ground = listOf(listOf(c(Rank.TWO)), emptyList(), listOf(c(Rank.TWO)), listOf(c(Rank.TWO)), listOf(c(Rank.TWO)))
        val s = state(player(hand = listOf(c(Rank.SEVEN), c(Rank.EIGHT)), ground = ground), List(10) { c(Rank.NINE) })
        assertNull(rules.discard(s, 0, 0)) // covering a slot is a Taki: not allowed yet
        assertNull(rules.discard(s, 0, 1)) // ...and even onto the empty slot it is not a Taki
    }

    @Test fun fillingTheGroundDoesNotEndTheTurn() {
        val ground = listOf(listOf(c(Rank.TWO)), emptyList(), emptyList(), listOf(c(Rank.TWO)), listOf(c(Rank.TWO)))
        val s = state(player(hand = listOf(c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.NINE)), ground = ground), List(10) { c(Rank.NINE) })
        assertNull(rules.fillGround(s, 0, 0)) // slot 0 is occupied
        val one = rules.fillGround(s, 0, 1)!!
        assertEquals(0, one.state.currentPlayer) // still my turn
        assertEquals(2, one.state.current.hand.size)
        val two = rules.fillGround(one.state, 0, 2)!!
        assertTrue(!two.state.current.hasEmptyGroundSlot)
        // now the ground is full: the Taki is allowed and ends the turn
        val taki = rules.discard(two.state, 0, 3)!!
        assertEquals(1, taki.currentPlayer)
        assertEquals(2, taki.players[0].ground[3].size)
        assertEquals(Rank.NINE, taki.players[0].groundTop(3)!!.rank)
    }

    @Test fun emptyingTheHandWhileFillingDrawsFiveAndTurnContinues() {
        val ground = listOf(listOf(c(Rank.TWO)), emptyList(), emptyList(), listOf(c(Rank.TWO)), listOf(c(Rank.TWO)))
        val s = state(player(hand = listOf(c(Rank.SEVEN)), ground = ground), List(10) { c(Rank.NINE) })
        val r = rules.fillGround(s, 0, 1)!!
        assertTrue(r.handRefilled)
        assertEquals(5, r.state.current.hand.size)
        assertEquals(0, r.state.currentPlayer) // must still fill slot 2 and Taki
    }

    @Test fun takiOnAFullGroundCoversTheTopCard() {
        val full = List(5) { listOf(c(Rank.TWO)) }
        val t = state(player(hand = listOf(c(Rank.SEVEN)), ground = full), List(10) { c(Rank.NINE) })
        val taki = rules.discard(t, 0, 3)!!
        assertEquals(2, taki.players[0].ground[3].size)
        assertEquals(Rank.SEVEN, taki.players[0].groundTop(3)!!.rank)
        assertEquals(1, taki.currentPlayer)
        assertEquals(5, taki.players[1].hand.size)
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

    @Test fun fourPassesInARowEndTheGameByStalemate() {
        // No hand cards, nothing to draw, no playable cards: every turn is a pass.
        val a = player(hand = emptyList(), bank = listOf(c(Rank.KING), c(Rank.KING), c(Rank.KING)))
        val b = player(hand = emptyList(), bank = listOf(c(Rank.KING), c(Rank.KING)))
        var s = GameState(listOf(a, b), emptyList(), List(3) { pileOf(10) }, emptyList(), 0)
        repeat(3) { s = rules.passTurn(s)!!; assertTrue(!s.isOver) }
        s = rules.passTurn(s)!!
        assertTrue(s.isOver && s.stalemate)
        assertEquals(1, s.winner)            // fewer Bank cards left (2 < 3)
        assertNull(rules.passTurn(s))        // nothing more can happen
        // equal Banks = draw
        val tie = GameState(listOf(a, a.copy(name = "q")), emptyList(), List(3) { pileOf(10) }, emptyList(), 0)
        var t = tie
        repeat(4) { t = rules.passTurn(t)!! }
        assertTrue(t.isOver && t.stalemate); assertNull(t.winner)
    }

    @Test fun anyPlayResetsThePassStreak() {
        val p0 = player(hand = emptyList(), bank = listOf(c(Rank.NINE), c(Rank.ACE)))
        val p1 = player(hand = emptyList(), bank = listOf(c(Rank.KING)))
        var s = GameState(listOf(p0, p1), emptyList(), List(3) { emptyList() }, emptyList(), 0)
        repeat(3) { s = rules.passTurn(s)!! }
        assertEquals(3, s.passStreak)
        // seat 1 is to move after 3 passes... make seat-0 play instead: hand it a playable bank card
        val back = s.copy(currentPlayer = 0)
        val played = rules.playToCenter(back, CardSource.Bank, 0)!!.state
        assertEquals(0, played.passStreak)
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
        // Nothing to unlock: the Joker is kept and a non-joker is used to Taki.
        val keep = state(player(hand = listOf(joker(), c(Rank.NINE)), bank = listOf(c(Rank.SIX))))
        val k = ai.chooseMove(keep) as AiMove.Discard
        assertEquals(1, k.handIndex)
    }

    @Test fun aiFillsEmptyGroundSlotsBeforeTaki() {
        val ground = listOf(listOf(c(Rank.TWO)), emptyList(), listOf(c(Rank.FIVE)), listOf(c(Rank.FIVE)), listOf(c(Rank.FIVE)))
        val s = state(player(hand = listOf(joker(), c(Rank.NINE)), bank = listOf(c(Rank.SIX)), ground = ground.map { it.map { x -> if (x.rank == Rank.TWO) c(Rank.KING) else x } }))
        val m = AiPlayer(rules).chooseMove(s) as AiMove.Fill
        assertEquals(1, m.slot)       // the empty slot
        assertEquals(1, m.handIndex)  // the 9, never the Joker
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
                    is AiMove.Fill -> r.fillGround(s, m.handIndex, m.slot)!!.state
                    is AiMove.Discard -> r.discard(s, m.handIndex, m.slot)!!
                    AiMove.Pass -> r.passTurn(s)!!
                }
                // Card conservation: 108 cards always exist somewhere.
                val total = s.players.sumOf { it.hand.size + it.bank.size + it.ground.sumOf { g -> g.size } } +
                    s.drawPile.size + s.completedCards.size + s.centerPiles.sumOf { it.size }
                assertEquals(108, total)
            }
            if (!s.stalemate) assertEquals(0, s.players[s.winner!!].bank.size)
        }
    }
}
