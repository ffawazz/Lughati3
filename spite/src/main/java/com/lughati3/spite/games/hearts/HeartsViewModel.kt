package com.lughati3.spite.games.hearts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lughati3.spite.history.History
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.model.GameId
import com.lughati3.spite.model.Card
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val SEAT_NAMES = listOf("You", "West", "North", "East")

data class HeartsUiState(
    val game: HeartsState,
    /** Cards ticked for the pass (up to 3). */
    val passSelection: Set<Card> = emptySet(),
    /** The card lifted in the hand; tap it again to play it. */
    val selected: Card? = null,
    val message: String = "",
) {
    val isMyTurn: Boolean get() = game.phase == HeartsPhase.PLAYING && game.turn == 0
}

class HeartsViewModel : ViewModel() {
    private val rules = HeartsRules()
    private val ai = HeartsAi(rules)
    private var job: Job? = null

    private val _ui = MutableStateFlow(fresh(rules.newRound()))
    val uiState: StateFlow<HeartsUiState> = _ui.asStateFlow()

    fun newGame() {
        job?.cancel()
        _ui.value = fresh(rules.newRound())
        advance()
    }

    private fun fresh(g: HeartsState) = HeartsUiState(g, message = hint(g))

    private fun hint(g: HeartsState): String = when (g.phase) {
        HeartsPhase.PASSING -> "Pick 3 cards to pass to ${SEAT_NAMES[(g.passOffsetSeat())]}."
        HeartsPhase.PLAYING -> if (g.turn == 0) "Your turn: tap a card, then tap it again to play." else ""
        else -> ""
    }

    private fun HeartsState.passOffsetSeat(): Int = (0 + rules.passOffset(round)) % 4

    fun onPassCardTapped(card: Card) {
        val ui = _ui.value
        if (ui.game.phase != HeartsPhase.PASSING) return
        val sel = if (card in ui.passSelection) ui.passSelection - card
        else if (ui.passSelection.size < 3) ui.passSelection + card else ui.passSelection
        _ui.value = ui.copy(passSelection = sel)
    }

    fun confirmPass() {
        val ui = _ui.value
        if (ui.game.phase != HeartsPhase.PASSING || ui.passSelection.size != 3) return
        val picks = List(4) { p -> if (p == 0) ui.passSelection.toList() else ai.choosePass(ui.game.hands[p]) }
        val next = rules.applyPass(ui.game, picks)
        _ui.value = HeartsUiState(next, message = "Passed. ${SEAT_NAMES[next.turn]} leads with the 2♣.")
        advance()
    }

    fun onHandCardTapped(card: Card) {
        val ui = _ui.value
        if (ui.game.phase == HeartsPhase.PASSING) { onPassCardTapped(card); return }
        if (!ui.isMyTurn) return
        if (card !in rules.legalPlays(ui.game, 0)) {
            _ui.value = ui.copy(message = illegalReason(ui.game))
            return
        }
        if (ui.selected != card) {
            _ui.value = ui.copy(selected = card, message = "")
            return
        }
        val next = rules.play(ui.game, card) ?: return
        _ui.value = ui.copy(game = next, selected = null, message = "")
        advance()
    }

    private fun illegalReason(g: HeartsState): String = when {
        g.trick.isEmpty() && g.tricksPlayed == 0 -> "The 2♣ must lead the first trick."
        g.trick.isEmpty() -> "Hearts haven't been broken yet."
        else -> "You must follow suit (${g.trick.first().card.suit!!.symbol}) if you can."
    }

    /** Cards the human may legally play right now (empty when it is not their turn). */
    fun legalCards(): Set<Card> {
        val g = _ui.value.game
        return if (_ui.value.isMyTurn) rules.legalPlays(g, 0).toSet() else emptySet()
    }

    fun nextRound() {
        val g = _ui.value.game
        if (g.phase != HeartsPhase.ROUND_OVER) return
        _ui.value = fresh(rules.nextRound(g))
        advance()
    }

    /** Drives AI seats and trick pickup until it is the human's turn (or a pass/round break). */
    private fun advance() {
        job?.cancel()
        job = viewModelScope.launch {
            while (isActive) {
                val g = _ui.value.game
                when (g.phase) {
                    HeartsPhase.TRICK_COMPLETE -> {
                        delay(PICKUP_DELAY_MS)
                        val next = rules.resolveTrick(g) ?: return@launch
                        _ui.value = _ui.value.copy(game = next, message = trickMessage(g, next))
                        if (next.phase == HeartsPhase.GAME_OVER) recordGameOver(next)
                    }
                    HeartsPhase.PLAYING -> {
                        if (g.turn == 0) { _ui.value = _ui.value.copy(message = _ui.value.message.ifEmpty { hint(g) }); return@launch }
                        delay(AI_DELAY_MS)
                        val card = ai.chooseCard(g, g.turn)
                        val next = rules.play(g, card) ?: return@launch
                        _ui.value = _ui.value.copy(game = next)
                    }
                    else -> return@launch
                }
            }
        }
    }

    private fun recordGameOver(g: HeartsState) {
        val won = 0 in g.winners
        val scores = SEAT_NAMES.indices.joinToString(", ") { "${SEAT_NAMES[it]} ${g.totalScores[it]}" }
        History.record(
            GameId.HEARTS, if (won) Outcome.WIN else Outcome.LOSS,
            "${g.round + 1} rounds · $scores", score = g.totalScores[0],
        )
    }

    private fun trickMessage(before: HeartsState, after: HeartsState): String {
        if (after.phase == HeartsPhase.ROUND_OVER || after.phase == HeartsPhase.GAME_OVER) return "Round over."
        val winner = rules.trickWinner(before.trick)
        val pts = before.trick.sumOf { it.card.points }
        return "${SEAT_NAMES[winner]} took the trick" + if (pts > 0) " (+$pts)" else ""
    }

    private companion object {
        const val AI_DELAY_MS = 700L
        const val PICKUP_DELAY_MS = 1300L
    }
}
