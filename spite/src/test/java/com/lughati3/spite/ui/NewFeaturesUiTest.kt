package com.lughati3.spite.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.lughati3.spite.games.memory.MemoryScreen
import com.lughati3.spite.games.memory.MemoryViewModel
import com.lughati3.spite.history.History
import com.lughati3.spite.history.InMemoryHistoryStore
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.model.GameId
import com.lughati3.spite.ui.help.GuideScreen
import com.lughati3.spite.ui.history.HistoryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class NewFeaturesUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun memoryScreenFlipsCardsAndCountsMoves() {
        val vm = MemoryViewModel()
        rule.setContent { MemoryScreen(onHome = {}, onHelp = {}, vm = vm) }
        // 16 face-down cards on Easy; tap two of them -> one move
        val cards = rule.onAllNodesWithContentDescription("Face-down card")
        assertEquals(16, cards.fetchSemanticsNodes().size)
        cards[0].performClick(); cards[1].performClick()
        rule.waitForIdle()
        assertEquals(1, vm.uiState.value.game.moves)
        rule.onNodeWithText("Medium · 12 pairs").performClick()
        rule.waitForIdle()
        assertEquals(24, vm.uiState.value.game.cards.size)
    }

    @Test fun abandoningAMemoryGameIsLoggedAsALoss() {
        History.init(InMemoryHistoryStore())
        val vm = MemoryViewModel()
        rule.setContent { MemoryScreen(onHome = {}, onHelp = {}, vm = vm) }
        val cards = rule.onAllNodesWithContentDescription("Face-down card")
        cards[0].performClick(); cards[1].performClick()
        rule.waitForIdle()
        vm.newGame()
        assertEquals(Outcome.LOSS, History.records.value.single().outcome)
    }

    @Test fun guideScreenShowsTheRules() {
        rule.setContent { GuideScreen(GameId.BANK, onClose = {}) }
        rule.onNodeWithText("How to play").assertIsDisplayed()
        rule.onNodeWithText("Bank").assertIsDisplayed()
        rule.onNodeWithText("Goal").assertIsDisplayed()
    }

    @Test fun historyScreenListsRecords() {
        History.init(InMemoryHistoryStore())
        History.record(GameId.MEMORY, Outcome.WIN, "Easy · 11 moves · 0:42", score = 11, variant = "Easy")
        rule.setContent { HistoryScreen(onClose = {}) }
        rule.onNodeWithText("Memory — Easy · 11 moves · 0:42").assertIsDisplayed()
        rule.onNodeWithText("WIN").assertIsDisplayed()
    }
}
