package com.lughati3.spite.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.compose.ui.unit.dp
import com.lughati3.spite.games.klondike.KlondikeScreen
import com.lughati3.spite.games.klondike.KlondikeViewModel
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.GameId
import com.lughati3.spite.model.Rank
import com.lughati3.spite.model.Suit
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.hub.HubScreen
import com.lughati3.spite.ui.theme.FeltBrush
import com.lughati3.spite.ui.theme.TakiTheme
import java.io.File
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders screens to PNGs (build/screenshots) for visual review. Not an assertion test, and it needs
 * Robolectric's native graphics, so it only runs when RENDER_SCREENSHOTS is set in the environment.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun onlyWhenAsked() = Assume.assumeTrue(System.getenv("RENDER_SCREENSHOTS") != null)

    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("shots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun cardGallery() {
        var id = 0
        rule.setContent {
            TakiTheme {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (suit in listOf(Suit.SPADES, Suit.HEARTS)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Rank.entries.forEach { PlayingCard(Card(id++, it, suit), 80.dp) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (suit in listOf(Suit.DIAMONDS, Suit.CLUBS)) {
                            listOf(Rank.ACE, Rank.SEVEN, Rank.TEN, Rank.QUEEN).forEach { PlayingCard(Card(id++, it, suit), 80.dp) }
                        }
                        PlayingCard(Card(id++, null, null), 80.dp)
                        PlayingCard(Card(id++, null, null), 80.dp, jokerAs = Rank.FIVE)
                        PlayingCard(null, 80.dp, faceDown = true)
                        PlayingCard(Card(id++, Rank.KING, Suit.HEARTS), 80.dp, selected = true)
                    }
                }
            }
        }
        save("cards")
    }

    @Test fun hub() {
        rule.setContent { TakiTheme { HubScreen(onOpen = {}, onGuide = {}, onHistory = {}) } }
        save("hub")
    }

    @Test fun hearts() {
        rule.setContent { TakiTheme { com.lughati3.spite.games.hearts.HeartsScreen(onHome = {}, onHelp = {}, vm = com.lughati3.spite.games.hearts.HeartsViewModel()) } }
        save("hearts")
    }

    @Test fun bank() {
        val vm = com.lughati3.spite.games.bank.BankViewModel()
        vm.startGame(com.lughati3.spite.games.bank.GameMode.VS_AI)
        rule.setContent { TakiTheme { com.lughati3.spite.games.bank.BankScreen(onHome = {}, onHelp = {}, viewModel = vm) } }
        save("bank")
    }

    @Test fun memory() {
        val vm = com.lughati3.spite.games.memory.MemoryViewModel()
        rule.setContent { TakiTheme { com.lughati3.spite.games.memory.MemoryScreen(onHome = {}, onHelp = {}, vm = vm) } }
        rule.waitForIdle()
        vm.onCardTapped(0); vm.onCardTapped(1); rule.waitForIdle()
        Thread.sleep(500); rule.waitForIdle()
        save("memory")
    }

    @Test fun guide() {
        rule.setContent { TakiTheme { com.lughati3.spite.ui.help.GuideScreen(GameId.BANK, onClose = {}) } }
        save("guide")
    }

    @Test fun history() {
        com.lughati3.spite.history.History.init(com.lughati3.spite.history.InMemoryHistoryStore())
        val h = com.lughati3.spite.history.History
        h.record(GameId.SOLITAIRE, Outcome.WIN, "Draw 1 · 98 moves", 98, "Draw 1", now = 1_700_000_000_000)
        h.record(GameId.SOLITAIRE, Outcome.WIN, "Draw 3 · 131 moves", 131, "Draw 3", now = 1_700_000_100_000)
        h.record(GameId.MEMORY, Outcome.WIN, "Medium · 19 moves · 1:12", 19, "Medium", now = 1_700_000_200_000)
        h.record(GameId.HEARTS, Outcome.LOSS, "4 rounds · You 104, West 61, North 88, East 73", 104, now = 1_700_000_300_000)
        h.record(GameId.BANK, Outcome.WIN, "You emptied your Bank first", variant = "vs AI", now = 1_700_000_400_000)
        h.record(GameId.FREECELL, Outcome.LOSS, "Gave up after 12 moves", now = 1_700_000_500_000)
        rule.setContent { TakiTheme { com.lughati3.spite.ui.history.HistoryScreen(onClose = {}) } }
        save("history")
    }

    @Test fun solitaire() {
        rule.setContent { TakiTheme { KlondikeScreen(onHome = {}, onHelp = {}, vm = KlondikeViewModel()) } }
        save("solitaire")
    }
}
