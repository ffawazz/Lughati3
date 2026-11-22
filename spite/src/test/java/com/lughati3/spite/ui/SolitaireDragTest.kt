package com.lughati3.spite.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import com.lughati3.spite.games.klondike.KlondikeScreen
import com.lughati3.spite.games.klondike.KlondikeState
import com.lughati3.spite.games.klondike.KlondikeViewModel
import com.lughati3.spite.games.klondike.TableauCard
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Drives the real Solitaire screen with real touch events to prove grab-and-move works. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class SolitaireDragTest {
    @get:Rule val rule = createComposeRule()

    private fun card(id: Int, r: Rank, s: Suit) = Card(id, r, s)
    private fun up(c: Card) = TableauCard(c, true)

    private fun vm(): KlondikeViewModel {
        val sevenH = card(1, Rank.SEVEN, Suit.HEARTS)
        val sixS = card(2, Rank.SIX, Suit.SPADES)
        val eightC = card(3, Rank.EIGHT, Suit.CLUBS)
        val aceD = card(4, Rank.ACE, Suit.DIAMONDS)
        val tableau = listOf(
            listOf(up(sevenH), up(sixS)), // a 2-card run: 7♥ 6♠
            listOf(up(eightC)),
            listOf(up(aceD)),
            emptyList(), emptyList(), emptyList(), emptyList(),
        )
        return KlondikeViewModel(KlondikeState(emptyList(), emptyList(), List(4) { emptyList() }, tableau, 1))
    }

    private fun dragCardOnto(from: String, to: String) {
        val node = rule.onNodeWithContentDescription(from)
        val src = node.fetchSemanticsNode().boundsInRoot
        val dst = rule.onNodeWithContentDescription(to).fetchSemanticsNode().boundsInRoot
        // performTouchInput coordinates are local to the node: grab the card by its visible top strip
        // (lower cards of a fanned column are covered at their centre), then drop on the target's centre.
        val grab = Offset(src.width / 2, 12f)
        val dropLocal = Offset(dst.center.x - src.left, dst.center.y - src.top + 30f)
        node.performTouchInput {
            down(grab)
            moveTo(grab + Offset(24f, 24f))   // beyond touch slop: the drag starts
            moveTo(dropLocal)
            up()
        }
        rule.waitForIdle()
    }

    @Test fun draggingARunMovesAllItsCards() {
        val vm = vm()
        rule.setContent { KlondikeScreen(onHome = {}, vm = vm) }
        dragCardOnto(from = "7♥", to = "8♣")       // red 7 onto black 8, carrying the 6♠ along
        val g = vm.uiState.value.game
        assertEquals(listOf(3, 1, 2), g.tableau[1].map { it.card.id })
        assertEquals(0, g.tableau[0].size)
    }

    @Test fun draggingToAnIllegalPlaceSnapsBack() {
        val vm = vm()
        rule.setContent { KlondikeScreen(onHome = {}, vm = vm) }
        dragCardOnto(from = "6♠", to = "A♦")        // 6♠ cannot go on an Ace
        val g = vm.uiState.value.game
        assertEquals(2, g.tableau[0].size)
        assertEquals(1, g.tableau[2].size)
        assertEquals(null, vm.uiState.value.selected)
    }

    @Test fun draggingAnAceToItsFoundationWorks() {
        val vm = vm()
        rule.setContent { KlondikeScreen(onHome = {}, vm = vm) }
        dragCardOnto(from = "A♦", to = "Empty slot ♦")
        val g = vm.uiState.value.game
        assertEquals(1, g.foundations[Suit.entries.indexOf(Suit.DIAMONDS)].size)
        assertEquals(0, g.tableau[2].size)
    }

    @Test fun tappingStillWorks() {
        val vm = vm()
        rule.setContent { KlondikeScreen(onHome = {}, vm = vm) }
        rule.onNodeWithContentDescription("8♣").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(com.lughati3.spite.games.klondike.KSource.Tableau(1, 0), vm.uiState.value.selected)
    }
}
