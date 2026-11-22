package com.lughati3.spite.games.klondike

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KlondikeRulesTest {
    private val rules = KlondikeRules(Random(7))
    private var id = 500
    private fun c(r: Rank, s: Suit) = Card(id++, r, s)
    private fun empty(drawCount: Int = 1, tableau: List<List<TableauCard>> = List(7) { emptyList() }, stock: List<Card> = emptyList(), waste: List<Card> = emptyList()) =
        KlondikeState(stock, waste, List(4) { emptyList() }, tableau, drawCount)

    private fun total(s: KlondikeState) = s.stock.size + s.waste.size + s.foundations.sumOf { it.size } + s.tableau.sumOf { it.size }

    @Test fun dealLayout() {
        val s = rules.newGame()
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), s.tableau.map { it.size })
        assertTrue(s.tableau.all { col -> col.last().faceUp && col.dropLast(1).none { it.faceUp } })
        assertEquals(24, s.stock.size)
        assertEquals(52, total(s))
    }

    @Test fun tableauNeedsAlternatingColourDescending() {
        val red6 = c(Rank.SIX, Suit.HEARTS)
        val s = empty(tableau = listOf(listOf(TableauCard(red6, true)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), waste = listOf(c(Rank.FIVE, Suit.CLUBS)))
        assertNotNull(rules.move(s, KSource.Waste, KTarget.Tableau(0)))
        val bad = s.copy(waste = listOf(c(Rank.FIVE, Suit.DIAMONDS)))
        assertNull(rules.move(bad, KSource.Waste, KTarget.Tableau(0)))
        // only a King may go on an empty column
        assertNull(rules.move(s, KSource.Waste, KTarget.Tableau(1)))
        assertNotNull(rules.move(s.copy(waste = listOf(c(Rank.KING, Suit.SPADES))), KSource.Waste, KTarget.Tableau(1)))
    }

    @Test fun movingARunFlipsTheCardUnderIt() {
        val col0 = listOf(TableauCard(c(Rank.NINE, Suit.CLUBS), false), TableauCard(c(Rank.FIVE, Suit.HEARTS), true), TableauCard(c(Rank.FOUR, Suit.SPADES), true))
        val col1 = listOf(TableauCard(c(Rank.SIX, Suit.CLUBS), true))
        val s = empty(tableau = listOf(col0, col1) + List(5) { emptyList() })
        val n = rules.move(s, KSource.Tableau(0, 1), KTarget.Tableau(1))!!
        assertEquals(3, n.tableau[1].size)
        assertEquals(1, n.tableau[0].size)
        assertTrue(n.tableau[0].single().faceUp)
    }

    @Test fun foundationsBySuitInOrder() {
        val s = empty(waste = listOf(c(Rank.TWO, Suit.SPADES)))
        assertNull(rules.autoToFoundation(s, KSource.Waste))
        val a = empty(waste = listOf(c(Rank.ACE, Suit.HEARTS)))
        val n = rules.autoToFoundation(a, KSource.Waste)!!
        assertEquals(1, n.foundations[Suit.entries.indexOf(Suit.HEARTS)].size)
    }

    @Test fun drawAndRecycle() {
        val s = empty(drawCount = 3, stock = List(4) { c(Rank.TWO, Suit.CLUBS) })
        val d1 = rules.drawStock(s)!!
        assertEquals(3, d1.waste.size); assertEquals(1, d1.stock.size)
        val d2 = rules.drawStock(d1)!!
        assertEquals(4, d2.waste.size); assertTrue(d2.stock.isEmpty())
        val recycled = rules.drawStock(d2)!!
        assertEquals(4, recycled.stock.size); assertTrue(recycled.waste.isEmpty())
    }

    @Test fun randomPlayConservesCards() {
        val rnd = Random(3)
        repeat(20) {
            var s = rules.newGame(if (it % 2 == 0) 1 else 3)
            repeat(400) {
                val sources = buildList<KSource> {
                    add(KSource.Waste); s.foundations.indices.forEach { add(KSource.Foundation(it)) }
                    s.tableau.forEachIndexed { c, col -> col.indices.forEach { add(KSource.Tableau(c, it)) } }
                }
                val targets = (0..3).map { KTarget.Foundation(it) } + (0..6).map { KTarget.Tableau(it) }
                val moves = sources.flatMap { src -> targets.mapNotNull { t -> rules.move(s, src, t) } }
                s = if (moves.isNotEmpty() && rnd.nextInt(4) != 0) moves.random(rnd) else (rules.drawStock(s) ?: s)
                assertEquals(52, total(s))
                assertTrue(s.tableau.all { col -> col.isEmpty() || col.last().faceUp })
            }
        }
    }

    @Test fun autoCompleteWinsASortedBoard() {
        val found = Suit.entries.map { su -> Rank.entries.dropLast(1).map { c(it, su) } }
        val tableau = Suit.entries.mapIndexed { i, su -> listOf(TableauCard(c(Rank.KING, su), true)) } + List(3) { emptyList() }
        val s = KlondikeState(emptyList(), emptyList(), found, tableau, 1)
        assertTrue(rules.autoComplete(s).isWon)
        assertFalse(s.isWon)
    }
}
