package com.lughati3.spite.games.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lughati3.spite.history.History
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.model.GameId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MemoryUiState(
    val game: MemoryState,
    val elapsedSec: Int = 0,
    val message: String = "Flip two cards to look for a matching pair.",
)

class MemoryViewModel : ViewModel() {
    private val rules = MemoryRules()

    private val _ui = MutableStateFlow(MemoryUiState(rules.newGame(MemoryLevel.EASY)))
    val uiState: StateFlow<MemoryUiState> = _ui.asStateFlow()

    private var hideJob: Job? = null
    private var timerJob: Job? = null
    private var startedAtMs: Long? = null

    /** Starts a fresh game; abandoning a game in progress is logged as a loss. */
    fun newGame(level: MemoryLevel = _ui.value.game.level) {
        val g = _ui.value.game
        if (g.moves > 0 && !g.isWon) {
            History.record(GameId.MEMORY, Outcome.LOSS, "${g.level.label} · gave up after ${g.moves} moves")
        }
        hideJob?.cancel()
        timerJob?.cancel()
        startedAtMs = null
        _ui.value = MemoryUiState(rules.newGame(level))
    }

    fun onCardTapped(index: Int) {
        val ui = _ui.value
        val next = rules.flip(ui.game, index) ?: return
        if (startedAtMs == null) startTimer()

        val message = when {
            next.isWon -> "All pairs found!"
            next.faceUp.size == 2 -> "No match…"
            next.faceUp.isEmpty() && next.matched.size > ui.game.matched.size -> "A match!"
            else -> ui.message
        }
        _ui.value = ui.copy(game = next, message = message)

        if (next.isWon) {
            timerJob?.cancel()
            val sec = elapsedNow()
            _ui.update { it.copy(elapsedSec = sec) }
            History.record(
                GameId.MEMORY, Outcome.WIN,
                "${next.level.label} · ${next.moves} moves · ${formatTime(sec)}",
                score = next.moves, variant = next.level.label,
            )
        } else if (next.needsHide) {
            hideJob?.cancel()
            hideJob = viewModelScope.launch {
                delay(HIDE_DELAY_MS)
                _ui.update { it.copy(game = rules.hideMismatch(it.game)) }
            }
        }
    }

    private fun elapsedNow(): Int = startedAtMs?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0

    private fun startTimer() {
        startedAtMs = System.currentTimeMillis()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _ui.update { it.copy(elapsedSec = elapsedNow()) }
            }
        }
    }

    private companion object { const val HIDE_DELAY_MS = 950L }
}

fun formatTime(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
