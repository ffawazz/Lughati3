package com.lughati3.spite.games.bank

import kotlin.random.Random
import org.junit.Assume
import org.junit.Test

/**
 * Developer tool, skipped unless TUNE is set in the environment: plays variants of the Hard AI's weights against
 * Easy on identical deals and prints win rates (`TUNE=1 TUNE_GAMES=160 ./gradlew :spite:testDebugUnitTest --tests '*HardTuningSweepTest*' -i`).
 * Single-weight changes move the result by less than the noise of a 160-game run, so use many games.
 */
class HardTuningSweepTest {
    private fun apply(r: GameRules, s: GameState, m: AiMove): GameState = when (m) {
        is AiMove.Play -> r.playToCenter(s, m.source, m.pile)!!.state
        is AiMove.Fill -> r.fillGround(s, m.handIndex, m.slot)!!.state
        is AiMove.Discard -> r.discard(s, m.handIndex, m.slot)!!
        AiMove.Pass -> r.passTurn(s)!!
    }

    private fun wins(t: HardTuning, games: Int, seedBase: Int): Int {
        var w = 0
        repeat(games) { g ->
            val seat = g % 2
            val r = GameRules(Random(seedBase + g / 2))
            val hard = HardAiPlayer(r, t); val easy = AiPlayer(r)
            var s = r.newGame(); var steps = 0
            while (!s.isOver && steps++ < 30_000) {
                s = apply(r, s, (if (s.currentPlayer == seat) hard else easy).chooseMove(s))
            }
            if (s.winner == seat) w++
        }
        return w
    }

    @Test fun sweep() {
        Assume.assumeTrue(System.getenv("TUNE") != null)
        val games = System.getenv("TUNE_GAMES")?.toInt() ?: 160
        val b = HardTuning()
        val variants = linkedMapOf("defaults" to b)
        for ((name, t) in variants) {
            val w = wins(t, games, System.getenv("TUNE_SEED")?.toInt() ?: 20000)
            println("TUNE %-12s %3d / %d  (%.1f%%)".format(name, w, games, 100.0 * w / games))
        }
    }
}
