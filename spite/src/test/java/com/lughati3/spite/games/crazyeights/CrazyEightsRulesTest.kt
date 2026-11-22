package com.lughati3.spite.games.crazyeights

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrazyEightsRulesTest {
    private var id = 300
    private fun c(r: Rank, s: Suit) = Card(id++, r, s)

    @Test fun dealAndStarter() {
        repeat(50) { seed ->
            val g = CrazyEightsRules(Random(seed)).newGame()
            assertEquals(7, g.hands[0].size); assertEquals(7, g.hands[1].size)
            assertEquals(52, g.hands.sumOf { it.size } + g.stock.size + g.discard.size)
            assertTrue(g.top.rank != Rank.EIGHT)
        }
    }

    @Test fun matchingAndWildEights() {
        val rules = CrazyEightsRules(Random(1))
        val top = c(Rank.SEVEN, Suit.HEARTS)
        val hand = listOf(c(Rank.TWO, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.SPADES), c(Rank.EIGHT, Suit.SPADES))
        val s = EightsState(listOf(hand, listOf(c(Rank.ACE, Suit.CLUBS))), listOf(c(Rank.TWO, Suit.CLUBS)), listOf(top), Suit.HEARTS, 0)
        assertEquals(3, rules.playable(s, 0).size)
        assertNull(rules.play(s, hand[2]))                 // K♠ matches nothing
        assertNull(rules.play(s, hand[3]))                 // an 8 needs a chosen suit
        assertEquals(Suit.CLUBS, rules.play(s, hand[3], Suit.CLUBS)!!.activeSuit)
        assertNull(rules.draw(s))                          // can't draw while holding a playable card
    }

    @Test fun drawPassesWhenNothingLeft() {
        val rules = CrazyEightsRules(Random(1))
        val s = EightsState(listOf(listOf(c(Rank.KING, Suit.SPADES)), listOf(c(Rank.ACE, Suit.CLUBS))), emptyList(), listOf(c(Rank.SEVEN, Suit.HEARTS)), Suit.HEARTS, 0)
        assertEquals(1, rules.draw(s)!!.turn)
    }

    @Test fun aiVsAiGamesFinish() {
        repeat(60) { seed ->
            val r = CrazyEightsRules(Random(seed))
            var s = r.newGame(); var steps = 0
            while (s.winner == null) {
                check(steps++ < 5_000) { "seed $seed stuck" }
                val mv = r.aiMove(s)
                s = if (mv != null) r.play(s, mv.first, mv.second)!! else r.draw(s)!!
                assertEquals(52, s.hands.sumOf { it.size } + s.stock.size + s.discard.size)
            }
            assertNotNull(s.winner); assertEquals(0, s.hands[s.winner!!].size)
        }
    }
}
