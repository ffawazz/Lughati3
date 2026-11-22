package com.lughati3.spite.games.freecell

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeCellRulesTest {
    private val rules = FreeCellRules(Random(5))
    private var id = 900
    private fun c(r: Rank, s: Suit) = Card(id++, r, s)
    private fun total(s: FreeCellState) = s.cascades.sumOf { it.size } + s.cells.count { it != null } + s.foundations.sumOf { it.size }
    private fun state(cascades: List<List<Card>>, cells: List<Card?> = List(4) { null }) =
        FreeCellState(cascades + List(8 - cascades.size) { emptyList() }, cells, List(4) { emptyList() })

    @Test fun dealLayout() {
        val s = rules.newGame()
        assertEquals(listOf(7, 7, 7, 7, 6, 6, 6, 6), s.cascades.map { it.size })
        assertEquals(52, total(s))
    }

    @Test fun supermoveLimit() {
        val run = listOf(c(Rank.SIX, Suit.SPADES), c(Rank.FIVE, Suit.HEARTS), c(Rank.FOUR, Suit.CLUBS))
        val dest = listOf(c(Rank.SEVEN, Suit.HEARTS))
        // 4 free cells, no empty columns besides... build 8 non-empty columns -> limit 5
        val full = state(listOf(run, dest) + List(6) { listOf(c(Rank.KING, Suit.SPADES)) })
        assertEquals(5, rules.maxMovable(full, 1))
        assertNotNull(rules.move(full, FSource.Cascade(0, 0), FTarget.Cascade(1)))
        // fill all cells: only one card at a time
        val jammed = full.copy(cells = List(4) { c(Rank.TWO, Suit.CLUBS) })
        assertNull(rules.move(jammed, FSource.Cascade(0, 0), FTarget.Cascade(1)))
    }

    @Test fun emptyColumnsDoubleTheLimit() {
        val s = state(listOf(listOf(c(Rank.KING, Suit.SPADES))), List(4) { null }) // 7 empty columns
        assertEquals(5 shl 7, rules.maxMovable(s, 0)) // 7 empty columns, destination is not one of them
        assertEquals(5 shl 6, rules.maxMovable(s, 1)) // an empty destination does not count itself
    }

    @Test fun onlyRunsCanBePickedUp() {
        val s = state(listOf(listOf(c(Rank.SIX, Suit.SPADES), c(Rank.FIVE, Suit.CLUBS))))
        assertNull(rules.cardsAt(s, FSource.Cascade(0, 0)))
        assertNotNull(rules.cardsAt(s, FSource.Cascade(0, 1)))
    }

    @Test fun cellsHoldOneCardAndFoundationsBuildBySuit() {
        val s = state(listOf(listOf(c(Rank.ACE, Suit.CLUBS)), listOf(c(Rank.TWO, Suit.CLUBS))))
        val parked = rules.move(s, FSource.Cascade(1, 0), FTarget.Cell(0))!!
        assertNull(rules.move(parked, FSource.Cascade(0, 0), FTarget.Cell(0))) // occupied
        val up = rules.autoToFoundation(parked, FSource.Cascade(0, 0))!!
        val up2 = rules.autoToFoundation(up, FSource.Cell(0))!!
        assertEquals(2, up2.foundations[Suit.entries.indexOf(Suit.CLUBS)].size)
    }

    @Test fun randomPlayConservesCards() {
        val rnd = Random(11)
        repeat(20) {
            var s = rules.newGame()
            repeat(300) {
                val sources = buildList<FSource> {
                    s.cells.indices.forEach { add(FSource.Cell(it)) }
                    s.cascades.forEachIndexed { c, col -> col.indices.forEach { add(FSource.Cascade(c, it)) } }
                }
                val targets = (0..7).map { FTarget.Cascade(it) } + (0..3).map { FTarget.Cell(it) } + (0..3).map { FTarget.Foundation(it) }
                val moves = sources.flatMap { src -> targets.mapNotNull { t -> rules.move(s, src, t) } }
                if (moves.isEmpty()) return@repeat
                s = moves.random(rnd)
                assertEquals(52, total(s))
                assertTrue(s.cells.size == 4)
            }
        }
    }
}
