package com.lughati3.spite.games.freecell

import androidx.lifecycle.ViewModel
import com.lughati3.spite.history.History
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.model.GameId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FreeCellUiState(
    val game: FreeCellState,
    val selected: FSource? = null,
    val canUndo: Boolean = false,
    val message: String = "Tap a card, then its destination. Tap it again to send it up (or park it in a free cell).",
)

class FreeCellViewModel : ViewModel() {
    val rules = FreeCellRules()
    private val history = ArrayDeque<FreeCellState>()

    private val _ui = MutableStateFlow(FreeCellUiState(rules.newGame()))
    val uiState: StateFlow<FreeCellUiState> = _ui.asStateFlow()

    fun newGame() {
        val g = _ui.value.game
        if (g.moves > 0 && !g.isWon) {
            History.record(GameId.FREECELL, Outcome.LOSS, "Gave up after ${g.moves} moves")
        }
        history.clear()
        _ui.value = FreeCellUiState(rules.newGame())
    }

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        _ui.value = FreeCellUiState(prev, canUndo = history.isNotEmpty(), message = "Undone.")
    }

    fun autoComplete() {
        val g = _ui.value.game
        val next = rules.autoComplete(g)
        commit(next.takeIf { it != g }, "Nothing can go up yet.")
    }

    /** Whether the current selection could be dropped on [target] (for highlighting). */
    fun isValidTarget(target: FTarget): Boolean {
        val ui = _ui.value
        val src = ui.selected ?: return false
        if (src is FSource.Cascade && target is FTarget.Cascade && src.column == target.column) return false
        val cards = rules.cardsAt(ui.game, src) ?: return false
        return rules.canPlace(ui.game, cards, target)
    }

    fun onCascadeCardTapped(column: Int, index: Int) {
        val ui = _ui.value
        val sel = ui.selected
        val tapped = FSource.Cascade(column, index)
        if (sel != null && sel != tapped && tryMove(FTarget.Cascade(column))) return
        tapSource(tapped)
    }

    fun onCascadeEmptyTapped(column: Int) {
        if (_ui.value.selected != null) tryMove(FTarget.Cascade(column))
    }

    fun onCellTapped(slot: Int) {
        val ui = _ui.value
        if (ui.selected != null && ui.selected != FSource.Cell(slot) && ui.game.cells[slot] == null) {
            tryMove(FTarget.Cell(slot))
            return
        }
        tapSource(FSource.Cell(slot))
    }

    fun onFoundationTapped(pile: Int) {
        if (_ui.value.selected != null) tryMove(FTarget.Foundation(pile))
    }

    /** Tap selects; tapping the same card again sends it to a foundation, else a free cell, else deselects. */
    private fun tapSource(source: FSource) {
        val ui = _ui.value
        val cards = rules.cardsAt(ui.game, source)
        if (cards == null) {
            _ui.value = ui.copy(message = "Only a proper run (descending, alternating colours) can be moved together.")
            return
        }
        if (ui.selected != source) {
            _ui.value = ui.copy(selected = source, message = "")
            return
        }
        val up = rules.autoToFoundation(ui.game, source)
        if (up != null) { commit(up, ""); return }
        val cell = rules.firstFreeCell(ui.game)
        if (source is FSource.Cascade && cards.size == 1 && cell != null) {
            commit(rules.move(ui.game, source, FTarget.Cell(cell)), "")
        } else {
            _ui.value = ui.copy(selected = null)
        }
    }

    private fun tryMove(target: FTarget): Boolean {
        val ui = _ui.value
        val source = ui.selected ?: return false
        val next = rules.move(ui.game, source, target)
        if (next == null) {
            _ui.value = ui.copy(message = "That move isn't allowed.")
            return false
        }
        commit(next, "")
        return true
    }

    private fun commit(next: FreeCellState?, failMessage: String) {
        val ui = _ui.value
        if (next == null) {
            _ui.value = ui.copy(message = failMessage)
            return
        }
        history.addLast(ui.game)
        if (next.isWon && !ui.game.isWon) {
            History.record(GameId.FREECELL, Outcome.WIN, "Solved in ${next.moves} moves", score = next.moves)
        }
        _ui.value = FreeCellUiState(next, null, true, if (next.isWon) "You won in ${next.moves} moves!" else "")
    }
}
