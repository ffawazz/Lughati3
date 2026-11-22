package com.lughati3.spite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lughati3.spite.domain.AiMove
import com.lughati3.spite.domain.AiPlayer
import com.lughati3.spite.domain.GameRules
import com.lughati3.spite.domain.MoveResult
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.CardSource
import com.lughati3.spite.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the screen renders: the game plus transient input state. */
data class GameUiState(
    val game: GameState,
    val selected: CardSource? = null,
    /** "Taki" mode: the player chose to end the turn and must now pick a hand card + ground slot. */
    val discardMode: Boolean = false,
    val message: String = "",
) {
    val isHumanTurn: Boolean get() = !game.isOver && game.currentPlayer == GameState.HUMAN
    val selectedCard: Card? get() = selected?.let { game.players[GameState.HUMAN].cardAt(it) }

    /** Centre piles that accept the selected card (highlighted in the UI). */
    val validPiles: Set<Int>
        get() = if (!isHumanTurn || discardMode) emptySet()
        else selectedCard?.let { card ->
            game.centerPiles.indices.filter { GameRules.canPlay(card, game.centerPiles[it]) }.toSet()
        } ?: emptySet()

    /** Ground slots that can receive the selected hand card in Taki mode. */
    val validDiscardSlots: Set<Int>
        get() {
            val me = game.players[GameState.HUMAN]
            if (!isHumanTurn || !discardMode || selected !is CardSource.Hand) return emptySet()
            return me.ground.indices.filter { !me.hasEmptyGroundSlot || me.ground[it].isEmpty() }.toSet()
        }
}

class GameViewModel : ViewModel() {

    private val rules = GameRules()
    private val ai = AiPlayer(rules)

    private val _uiState = MutableStateFlow(freshState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var aiJob: Job? = null

    // ------------------------------------------------------------------ lifecycle

    fun newGame() {
        aiJob?.cancel()
        _uiState.value = freshState()
    }

    private fun freshState() = GameUiState(rules.newGame(), message = YOUR_TURN_HINT)

    // ------------------------------------------------------------------ human input

    fun onBankTapped() = toggleSelection(CardSource.Bank)

    fun onHandCardTapped(index: Int) = toggleSelection(CardSource.Hand(index))

    /** Normal mode: selects the slot's top card. Taki mode: places the selected hand card there. */
    fun onGroundSlotTapped(slot: Int) {
        val ui = _uiState.value
        if (!ui.isHumanTurn) return
        if (!ui.discardMode) {
            toggleSelection(CardSource.Ground(slot))
            return
        }
        val hand = ui.selected as? CardSource.Hand
        if (hand == null) {
            say("Taki: first pick the hand card you want to place.")
            return
        }
        if (slot !in ui.validDiscardSlots) {
            say("You still have an empty slot — place the card there.")
            return
        }
        val card = ui.game.players[GameState.HUMAN].hand[hand.index]
        val next = rules.discard(ui.game, hand.index, slot) ?: return
        val taki = !ui.game.current.hasEmptyGroundSlot
        _uiState.value = ui.copy(
            game = next, selected = null, discardMode = false,
            message = "You placed ${card.label} on slot ${slot + 1}${if (taki) " (Taki!)" else ""}. AI's turn…",
        )
        startAiTurn()
    }

    /** Tapping a centre pile plays the selected card there if it is legal. */
    fun onPileTapped(pile: Int) {
        val ui = _uiState.value
        if (!ui.isHumanTurn || ui.discardMode) return
        val source = ui.selected ?: return
        val card = ui.selectedCard ?: return
        val result = rules.playToCenter(ui.game, source, pile)
        if (result == null) {
            say("${card.label} can't go on pile ${pile + 1}.")
            return
        }
        _uiState.value = ui.copy(
            game = result.state, selected = null,
            message = describePlay("You", card, pile, result),
        )
    }

    /** The "End turn (Taki / تكي)" button. */
    fun onEndTurnClicked() {
        val ui = _uiState.value
        if (!ui.isHumanTurn) return
        if (ui.game.current.hand.isEmpty()) { // nothing to place: just pass
            val next = rules.passTurn(ui.game) ?: return
            _uiState.value = ui.copy(game = next, selected = null, discardMode = false, message = "You passed. AI's turn…")
            startAiTurn()
            return
        }
        val enter = !ui.discardMode
        _uiState.value = ui.copy(
            discardMode = enter,
            selected = null,
            message = when {
                !enter -> YOUR_TURN_HINT
                rules.isTakiRequired(ui.game) -> "TAKI (تكي): pick a hand card, then a ground slot to cover."
                else -> "Pick a hand card, then an empty ground slot."
            },
        )
    }

    private fun toggleSelection(source: CardSource) {
        val ui = _uiState.value
        if (!ui.isHumanTurn) return
        if (ui.game.players[GameState.HUMAN].cardAt(source) == null) return
        if (ui.discardMode && source !is CardSource.Hand) return // in Taki mode only hand cards can be picked
        _uiState.value = ui.copy(selected = if (ui.selected == source) null else source)
    }

    private fun say(text: String) = _uiState.update { it.copy(message = text) }

    // ------------------------------------------------------------------ AI turn

    private fun startAiTurn() {
        val game = _uiState.value.game
        if (game.isOver || game.currentPlayer != GameState.AI) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            delay(AI_DELAY_MS)
            var moves = 0
            while (isActive && moves++ < MAX_AI_MOVES_PER_TURN) {
                val state = _uiState.value.game
                if (state.isOver || state.currentPlayer != GameState.AI) return@launch

                when (val move = withContext(Dispatchers.Default) { ai.chooseMove(state) }) {
                    is AiMove.Play -> {
                        val card = state.current.cardAt(move.source) ?: return@launch
                        val result = rules.playToCenter(state, move.source, move.pile) ?: return@launch
                        _uiState.update { it.copy(game = result.state, message = describePlay("AI", card, move.pile, result)) }
                        if (result.state.isOver) return@launch
                    }
                    is AiMove.Discard -> {
                        val card = state.current.hand[move.handIndex]
                        val taki = !state.current.hasEmptyGroundSlot
                        val next = rules.discard(state, move.handIndex, move.slot) ?: return@launch
                        _uiState.update {
                            it.copy(
                                game = next,
                                message = "AI placed a card on its ground slot ${move.slot + 1}${if (taki) " (Taki!)" else ""}. Your turn.",
                            )
                        }
                        return@launch
                    }
                    AiMove.Pass -> {
                        val next = rules.passTurn(state) ?: return@launch
                        _uiState.update { it.copy(game = next, message = "AI passed. Your turn.") }
                        return@launch
                    }
                }
                delay(AI_DELAY_MS) // let the human follow along
            }
        }
    }

    // ------------------------------------------------------------------ messages

    private fun describePlay(who: String, card: Card, pile: Int, r: MoveResult): String = buildString {
        append(who).append(" played ")
        append(if (card.isJoker) "a Joker as ${r.playedAs.label}" else card.label)
        append(" on pile ${pile + 1}")
        if (r.pileCompleted) append(" — pile complete!")
        if (r.state.isOver) append(" — that was the last Bank card!")
        else if (r.handRefilled) append(" — hand empty, drew 5 new cards")
        append('.')
    }

    private companion object {
        const val AI_DELAY_MS = 750L
        const val MAX_AI_MOVES_PER_TURN = 200
        const val YOUR_TURN_HINT = "Your turn: tap a card, then a highlighted pile. Press End Turn when done."
    }
}
