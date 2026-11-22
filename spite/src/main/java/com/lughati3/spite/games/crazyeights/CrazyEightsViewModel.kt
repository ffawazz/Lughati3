package com.lughati3.spite.games.crazyeights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lughati3.spite.history.History
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.model.GameId
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Suit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EightsUiState(
    val game: EightsState,
    /** An 8 the human tapped; waiting for them to pick a suit. */
    val pendingEight: Card? = null,
    val message: String = "Match the suit or rank. 8s are wild.",
) {
    val isMyTurn: Boolean get() = game.winner == null && game.turn == 0
}

class CrazyEightsViewModel : ViewModel() {
    private val rules = CrazyEightsRules()
    private var job: Job? = null

    private val _ui = MutableStateFlow(EightsUiState(rules.newGame()))
    val uiState: StateFlow<EightsUiState> = _ui.asStateFlow()

    fun legal(): Set<Card> = rules.playable(_ui.value.game, 0).toSet()

    fun newGame() {
        job?.cancel()
        _ui.value = EightsUiState(rules.newGame())
    }

    fun onCardTapped(card: Card) {
        val ui = _ui.value
        if (!ui.isMyTurn) return
        if (!rules.isPlayable(ui.game, card)) {
            _ui.value = ui.copy(message = "Play a ${ui.game.activeSuit.symbol} or a ${ui.game.top.rank!!.label}, or an 8.")
            return
        }
        if (card.rank!!.label == "8") {
            _ui.value = ui.copy(pendingEight = card)
            return
        }
        val next = rules.play(ui.game, card) ?: return
        _ui.value = ui.copy(game = next, message = "")
        recordIfOver(next)
        advance()
    }

    fun onSuitChosen(suit: Suit) {
        val ui = _ui.value
        val card = ui.pendingEight ?: return
        val next = rules.play(ui.game, card, suit) ?: return
        _ui.value = ui.copy(game = next, pendingEight = null, message = "You chose ${suit.symbol}.")
        recordIfOver(next)
        advance()
    }

    fun cancelSuitChoice() = _ui.update { it.copy(pendingEight = null) }

    fun onStockTapped() {
        val ui = _ui.value
        if (!ui.isMyTurn) return
        val next = rules.draw(ui.game)
        if (next == null) {
            _ui.value = ui.copy(message = "You have a playable card — you can't draw.")
            return
        }
        _ui.value = ui.copy(game = next, message = if (next.turn != 0) "Nothing left to draw — you pass." else "")
        advance()
    }

    private fun advance() {
        job?.cancel()
        job = viewModelScope.launch {
            while (isActive) {
                val g = _ui.value.game
                if (g.winner != null || g.turn == 0) return@launch
                delay(AI_DELAY_MS)
                val move = rules.aiMove(g)
                if (move == null) {
                    val next = rules.draw(g) ?: return@launch
                    _ui.value = _ui.value.copy(game = next, message = "AI drew a card.")
                } else {
                    val next = rules.play(g, move.first, move.second) ?: return@launch
                    val eight = move.second?.let { " and chose ${it.symbol}" } ?: ""
                    _ui.value = _ui.value.copy(game = next, message = "AI played ${move.first.label}$eight.")
                    recordIfOver(next)
                }
            }
        }
    }

    private fun recordIfOver(g: EightsState) {
        val winner = g.winner ?: return
        History.record(
            GameId.CRAZY_EIGHTS, if (winner == 0) Outcome.WIN else Outcome.LOSS,
            if (winner == 0) "You played your last card" else "The AI went out — you held ${g.hands[0].size} cards",
        )
    }

    private companion object { const val AI_DELAY_MS = 800L }
}
