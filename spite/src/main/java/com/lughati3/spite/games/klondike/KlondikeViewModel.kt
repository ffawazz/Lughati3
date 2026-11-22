package com.lughati3.spite.games.klondike

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KlondikeUiState(
    val game: KlondikeState,
    val selected: KSource? = null,
    val canUndo: Boolean = false,
    val message: String = "Tap a card, then where it should go. Tap it again to send it to a foundation.",
) {
    /** Where the currently selected cards could legally be dropped (for highlighting). */
    fun isValidTarget(rules: KlondikeRules, target: KTarget): Boolean {
        val cards = selected?.let { rules.cardsAt(game, it) } ?: return false
        if (selected is KSource.Tableau && target is KTarget.Tableau && selected.column == target.column) return false
        return rules.canPlace(game, cards, target)
    }
}

class KlondikeViewModel(initial: KlondikeState? = null) : ViewModel() {
    val rules = KlondikeRules()
    private val history = ArrayDeque<KlondikeState>()

    private val _ui = MutableStateFlow(KlondikeUiState(initial ?: rules.newGame()))
    val uiState: StateFlow<KlondikeUiState> = _ui.asStateFlow()

    fun newGame(drawCount: Int = _ui.value.game.drawCount) {
        history.clear()
        _ui.value = KlondikeUiState(rules.newGame(drawCount))
    }

    fun toggleDrawCount() = newGame(if (_ui.value.game.drawCount == 1) 3 else 1)

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        _ui.value = KlondikeUiState(prev, canUndo = history.isNotEmpty(), message = "Undone.")
    }

    fun autoComplete() = commit(rules.autoComplete(_ui.value.game).takeIf { it != _ui.value.game }, "Nothing can go up yet.")

    fun onStockTapped() = commit(rules.drawStock(_ui.value.game), "Stock and waste are empty.")

    fun onWasteTapped() = tapSource(KSource.Waste)

    fun onFoundationTapped(pile: Int) {
        val ui = _ui.value
        if (ui.selected != null && tryMove(KTarget.Foundation(pile))) return
        tapSource(KSource.Foundation(pile))
    }

    fun onTableauCardTapped(column: Int, index: Int) {
        val ui = _ui.value
        val sel = ui.selected
        if (sel != null && sel != KSource.Tableau(column, index) && tryMove(KTarget.Tableau(column))) return
        if (!ui.game.tableau[column][index].faceUp) return
        tapSource(KSource.Tableau(column, index))
    }

    fun onTableauEmptyTapped(column: Int) {
        if (_ui.value.selected != null) tryMove(KTarget.Tableau(column))
    }

    /** A drag began on [source]: select it so valid drop targets light up. */
    fun onDragStart(source: KSource) {
        if (rules.cardsAt(_ui.value.game, source) != null) _ui.update { it.copy(selected = source, message = "") }
    }

    /** The drag ended over [target] (null = dropped on nothing). Illegal drops just snap back. */
    fun onDrop(target: KTarget?) {
        if (target != null) tryMove(target)
        _ui.update { it.copy(selected = null) }
    }

    /** First tap selects; tapping the selected card again tries its foundation, else deselects. */
    private fun tapSource(source: KSource) {
        val ui = _ui.value
        if (rules.cardsAt(ui.game, source) == null) return
        if (ui.selected != source) {
            _ui.value = ui.copy(selected = source)
            return
        }
        val auto = rules.autoToFoundation(ui.game, source)
        if (auto != null) commit(auto, "") else _ui.value = ui.copy(selected = null)
    }

    private fun tryMove(target: KTarget): Boolean {
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

    private fun commit(next: KlondikeState?, failMessage: String) {
        val ui = _ui.value
        if (next == null) {
            _ui.value = ui.copy(message = failMessage)
            return
        }
        history.addLast(ui.game)
        _ui.update {
            KlondikeUiState(next, null, canUndo = true, message = if (next.isWon) "You won in ${next.moves} moves!" else "")
        }
    }
}
