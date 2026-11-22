package com.lughati3.spite.games.hearts

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

class HeartsRulesTest {
    private val rules = HeartsRules(Random(2))
    private var id = 700
    private fun c(r: Rank, s: Suit) = Card(id++, r, s)

    private fun playing(hands: List<List<Card>>, trick: List<TrickPlay> = emptyList(), turn: Int = 0, broken: Boolean = false, played: Int = 1) =
        HeartsState(hands, trick, turn, broken, played, List(4) { 0 }, List(4) { 0 }, List(4) { 0 }, null, 0, HeartsPhase.PLAYING)

    @Test fun dealGivesThirteenEach() {
        val s = rules.newRound()
        assertTrue(s.hands.all { it.size == 13 })
        assertEquals(52, s.hands.flatten().toSet().size)
        assertEquals(HeartsPhase.PASSING, s.phase)
        assertTrue(s.hands[s.turn].any { it.suit == Suit.CLUBS && it.rank == Rank.TWO })
    }

    @Test fun passMovesThreeCardsLeft() {
        val s = rules.newRound()
        val picks = List(4) { p -> s.hands[p].take(3) }
        val n = rules.applyPass(s, picks)
        assertTrue(n.hands.all { it.size == 13 })
        picks[0].forEach { assertTrue(it in n.hands[1]) } // left = next seat
        assertEquals(HeartsPhase.PLAYING, n.phase)
    }

    @Test fun firstTrickLeadsTwoOfClubsAndAvoidsPoints() {
        val two = c(Rank.TWO, Suit.CLUBS)
        val hands = listOf(listOf(two, c(Rank.ACE, Suit.HEARTS)), listOf(c(Rank.QUEEN, Suit.SPADES), c(Rank.NINE, Suit.DIAMONDS)), emptyList(), emptyList())
        val lead = playing(hands, played = 0)
        assertEquals(listOf(two), rules.legalPlays(lead, 0))
        val after = rules.play(lead, two)!!
        // seat 1 is void in clubs: may not dump the Queen/hearts on trick one while it has a safe card
        assertEquals(listOf(hands[1][1]), rules.legalPlays(after, 1))
    }

    @Test fun mustFollowSuitAndHeartsCantLeadUntilBroken() {
        val hands = listOf(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS)), listOf(c(Rank.TWO, Suit.CLUBS), c(Rank.KING, Suit.HEARTS)), emptyList(), emptyList())
        assertEquals(listOf(hands[0][1]), rules.legalPlays(playing(hands), 0))           // can't lead a heart
        assertEquals(2, rules.legalPlays(playing(hands, broken = true), 0).size)
        val led = playing(hands, trick = listOf(TrickPlay(0, hands[0][1])), turn = 1)
        assertEquals(listOf(hands[1][0]), rules.legalPlays(led, 1))                     // must follow clubs
        assertNull(rules.play(led, hands[1][1]))
    }

    @Test fun aceIsHighAndOnlyLedSuitWins() {
        val t = listOf(
            TrickPlay(0, c(Rank.KING, Suit.CLUBS)), TrickPlay(1, c(Rank.ACE, Suit.CLUBS)),
            TrickPlay(2, c(Rank.ACE, Suit.HEARTS)), TrickPlay(3, c(Rank.TWO, Suit.CLUBS)),
        )
        assertEquals(1, rules.trickWinner(t))
    }

    @Test fun moonShotFlipsScores() {
        // seat 2 holds every point card: they take all 13 tricks worth 26
        var s = playing(List(4) { emptyList() }).copy(tricksPlayed = 12, roundPoints = listOf(0, 0, 13, 0))
        val trick = listOf(
            TrickPlay(0, c(Rank.TWO, Suit.CLUBS)), TrickPlay(1, c(Rank.THREE, Suit.CLUBS)),
            TrickPlay(2, c(Rank.ACE, Suit.CLUBS)), TrickPlay(3, c(Rank.FOUR, Suit.HEARTS)),
        )
        // make the last trick worth 13 more by using Q♠ on a spade trick instead
        val spades = listOf(
            TrickPlay(0, c(Rank.TWO, Suit.SPADES)), TrickPlay(1, c(Rank.QUEEN, Suit.SPADES)),
            TrickPlay(2, c(Rank.ACE, Suit.SPADES)), TrickPlay(3, c(Rank.FOUR, Suit.SPADES)),
        )
        s = s.copy(trick = spades, phase = HeartsPhase.TRICK_COMPLETE)
        val done = rules.resolveTrick(s)!!
        assertEquals(2, done.moonShooter)
        assertEquals(listOf(26, 26, 0, 26), done.lastRoundPoints)
        assertNotNull(trick)
    }

    /** All four seats driven by the AI: every round awards exactly 26 (or 78 on a moon) and the game ends. */
    @Test fun aiOnlyGamesFinish() {
        repeat(25) { seed ->
            val r = HeartsRules(Random(seed)); val ai = HeartsAi(r)
            var s = r.newRound(); var steps = 0
            while (s.phase != HeartsPhase.GAME_OVER) {
                check(steps++ < 20_000) { "seed $seed stuck" }
                s = when (s.phase) {
                    HeartsPhase.PASSING -> r.applyPass(s, List(4) { ai.choosePass(s.hands[it]) })
                    HeartsPhase.PLAYING -> r.play(s, ai.chooseCard(s, s.turn))!!
                    HeartsPhase.TRICK_COMPLETE -> r.resolveTrick(s)!!
                    HeartsPhase.ROUND_OVER -> {
                        assertTrue(s.lastRoundPoints.sum().let { it == 26 || it == 78 })
                        r.nextRound(s)
                    }
                    HeartsPhase.GAME_OVER -> s
                }
                if (s.phase == HeartsPhase.PLAYING || s.phase == HeartsPhase.TRICK_COMPLETE)
                    assertEquals(52, s.hands.sumOf { it.size } + s.trick.size + s.tricksPlayed * 4)
            }
            assertTrue(s.totalScores.any { it >= 100 })
            assertFalse(s.winners.isEmpty())
        }
    }
}
