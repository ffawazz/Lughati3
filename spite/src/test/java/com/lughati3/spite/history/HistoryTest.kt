package com.lughati3.spite.history

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.lughati3.spite.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryTest {
    private fun rec(game: GameId, outcome: Outcome, score: Int? = null, variant: String? = null, t: Long = 1L) =
        GameRecord(game, t, outcome, "d", score, variant)

    @Test fun statsCountAndBestScorePerVariant() {
        val rs = listOf(
            rec(GameId.SOLITAIRE, Outcome.WIN, 120, "Draw 1"),
            rec(GameId.SOLITAIRE, Outcome.WIN, 95, "Draw 1"),
            rec(GameId.SOLITAIRE, Outcome.WIN, 140, "Draw 3"),
            rec(GameId.SOLITAIRE, Outcome.LOSS),
            rec(GameId.BANK, Outcome.WIN),
        )
        val s = rs.statsFor(GameId.SOLITAIRE)
        assertEquals(4, s.played); assertEquals(3, s.wins); assertEquals(1, s.losses)
        assertEquals(75, s.winRate)
        assertEquals(mapOf<String?, Int>("Draw 1" to 95, "Draw 3" to 140), s.bestByVariant)
        assertEquals(1, rs.statsFor(GameId.BANK).played)
        assertNull(rs.statsFor(GameId.HEARTS).winRate)
    }

    @Test fun twoPlayerGamesDoNotAffectWinRate() {
        val s = listOf(rec(GameId.BANK, Outcome.PLAYED), rec(GameId.BANK, Outcome.WIN)).statsFor(GameId.BANK)
        assertEquals(2, s.played); assertEquals(100, s.winRate)
    }

    @Test fun recordsSurviveARestartThroughSharedPreferences() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = PrefsHistoryStore(ctx)
        store.save(emptyList())
        History.init(store)
        History.record(GameId.MEMORY, Outcome.WIN, "Easy · 12 moves", score = 12, variant = "Easy", now = 1000)
        History.record(GameId.HEARTS, Outcome.LOSS, "lost", now = 2000)

        // "restart": a fresh store object reads what was written
        History.init(PrefsHistoryStore(ctx))
        val loaded = History.records.value
        assertEquals(2, loaded.size)
        assertEquals(GameId.HEARTS, loaded[0].game) // newest first
        assertEquals(12, loaded[1].score); assertEquals("Easy", loaded[1].variant)
        assertNull(loaded[0].score)

        History.clear()
        assertTrue(PrefsHistoryStore(ctx).load().isEmpty())
    }

    @Test fun keepsOnlyTheNewestFiveHundred() {
        History.init(InMemoryHistoryStore())
        repeat(520) { History.record(GameId.BANK, Outcome.WIN, "g$it", now = it.toLong()) }
        assertEquals(500, History.records.value.size)
        assertEquals("g519", History.records.value.first().detail)
    }
}
