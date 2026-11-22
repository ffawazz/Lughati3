package com.lughati3.spite.games.memory

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRulesTest {
    private val rules = MemoryRules(Random(4))

    @Test fun everyCardHasExactlyOnePartner() {
        for (level in MemoryLevel.entries) {
            val s = rules.newGame(level)
            assertEquals(level.pairs * 2, s.cards.size)
            assertEquals(s.cards.size, s.cards.map { it.id }.toSet().size)
            val groups = s.cards.groupBy { it.rank to it.suit!!.isRed }
            assertTrue(groups.values.all { it.size == 2 })
            assertEquals(level.pairs, groups.size)
        }
    }

    @Test fun matchNeedsSameRankAndColour() {
        val s = rules.newGame(MemoryLevel.EASY)
        val (a, b) = s.cards.groupBy { it.rank to it.suit!!.isRed }.values.first()
        assertTrue(rules.isMatch(a, b))
        val other = s.cards.first { it.rank != a.rank }
        assertFalse(rules.isMatch(a, other))
    }

    @Test fun flipMatchAndMismatch() {
        val s = rules.newGame(MemoryLevel.EASY)
        val pairIdx = s.cards.indices.groupBy { s.cards[it].rank to s.cards[it].suit!!.isRed }.values.map { it }
        val (i, j) = pairIdx[0]
        val (k, _) = pairIdx[1]

        val one = rules.flip(s, i)!!
        assertEquals(0, one.moves)
        assertNull(rules.flip(one, i)) // can't flip the same card twice

        val matched = rules.flip(one, j)!!
        assertEquals(1, matched.moves)
        assertEquals(setOf(i, j), matched.matched)
        assertTrue(matched.faceUp.isEmpty())
        assertNull(rules.flip(matched, i)) // matched cards are done

        val miss = rules.flip(rules.flip(s, i)!!, k)!!
        assertTrue(miss.needsHide)
        assertEquals(1, miss.moves)
        assertNull(rules.flip(miss, j)) // must hide first
        assertFalse(rules.hideMismatch(miss).needsHide)
        assertNotNull(rules.flip(rules.hideMismatch(miss), j))
    }

    @Test fun aPerfectPlayerWinsInOneMovePerPair() {
        for (level in MemoryLevel.entries) {
            var s = rules.newGame(level)
            val pairs = s.cards.indices.groupBy { s.cards[it].rank to s.cards[it].suit!!.isRed }.values
            for ((a, b) in pairs) s = rules.flip(rules.flip(s, a)!!, b)!!
            assertTrue(s.isWon)
            assertEquals(level.pairs, s.moves)
            assertNull(rules.flip(s, 0))
        }
    }
}
