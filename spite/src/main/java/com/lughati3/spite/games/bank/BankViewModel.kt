package com.lughati3.spite.games.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lughati3.spite.history.History
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.model.GameId
import com.lughati3.spite.model.Card
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
enum class GameMode { VS_AI, TWO_PLAYERS }

data class GameUiState(
    val game: GameState,
    /** null = the mode menu is showing. */
    val mode: GameMode? = null,
    /** Pass-and-play: cover screen shown between turns so the next player's hand stays private. */
    val handoff: Boolean = false,
    val selected: CardSource? = null,
    /** "Taki" mode: the player chose to end the turn and must now pick a hand card + ground slot. */
    val discardMode: Boolean = false,
    val message: String = "",
) {
    /** Index of the player shown at the bottom of the screen (always the one whose turn it is in 2-player mode). */
    val viewer: Int get() = if (mode == GameMode.TWO_PLAYERS) game.currentPlayer else GameState.HUMAN
    val isHumanTurn: Boolean
        get() = !game.isOver && !handoff && (mode == GameMode.TWO_PLAYERS || game.currentPlayer == GameState.HUMAN)
    val selectedCard: Card? get() = selected?.let { game.players[viewer].cardAt(it) }

    /** Centre piles that accept the selected card (highlighted in the UI). */
    val validPiles: Set<Int>
        get() = if (!isHumanTurn || discardMode) emptySet()
        else selectedCard?.let { card ->
            game.centerPiles.indices.filter { GameRules.canPlay(card, game.centerPiles[it]) }.toSet()
        } ?: emptySet()

    /** Ground slots that can receive the selected hand card in Taki mode. */
    val validDiscardSlots: Set<Int>
        get() {
            val me = game.players[viewer]
            if (!isHumanTurn || !discardMode || selected !is CardSource.Hand) return emptySet()
            return me.ground.indices.filter { !me.hasEmptyGroundSlot || me.ground[it].isEmpty() }.toSet()
        }
}

class BankViewModel : ViewModel() {

    private val rules = GameRules()
    private val ai = AiPlayer(rules)

    private val _uiState = MutableStateFlow(GameUiState(rules.newGame()))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var aiJob: Job? = null

    // ------------------------------------------------------------------ lifecycle

    /** Back to the mode menu. */
    fun newGame() {
        aiJob?.cancel()
        _uiState.value = GameUiState(rules.newGame())
    }

    fun startGame(mode: GameMode) {
        aiJob?.cancel()
        _uiState.value = GameUiState(
            game = rules.newGame(twoPlayers = mode == GameMode.TWO_PLAYERS),
            mode = mode,
            message = if (mode == GameMode.TWO_PLAYERS) "Player 1 starts. $YOUR_TURN_HINT" else YOUR_TURN_HINT,
        )
    }

    /** The next player confirmed they hold the device: reveal their cards. */
    fun onHandoffConfirmed() = _uiState.update { it.copy(handoff = false, message = YOUR_TURN_HINT) }

    /** After the human's turn ended: AI mode -> AI plays; 2-player mode -> cover the screen. */
    private fun afterTurnEnded(ui: GameUiState, summary: String) {
        if (ui.mode == GameMode.TWO_PLAYERS) {
            _uiState.value = ui.copy(handoff = true, selected = null, discardMode = false, message = summary)
        } else {
            _uiState.value = ui.copy(selected = null, discardMode = false, message = "$summary AI's turn…")
            startAiTurn()
        }
    }

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
            say("Your ground isn't full yet — fill the empty slots first.")
            return
        }
        val card = ui.game.current.hand[hand.index]
        val who = ui.game.current.name

        // Ground not full: this card just fills an empty slot and the turn goes on.
        if (ui.game.current.hasEmptyGroundSlot) {
            val r = rules.fillGround(ui.game, hand.index, slot) ?: return
            val empties = r.state.current.ground.count { it.isEmpty() }
            val note = if (r.handRefilled) " Hand empty — drew 5 new cards." else ""
            _uiState.value = ui.copy(
                game = r.state, selected = null,
                discardMode = empties > 0, // stay in fill mode until the ground is full
                message = when {
                    empties > 0 -> "Placed ${card.label} on slot ${slot + 1}. $empties empty slot(s) left.$note"
                    else -> "Ground is full.$note Keep playing, or Taki (cover any slot) to end your turn."
                },
            )
            return
        }

        // Ground full: this is the Taki and it ends the turn.
        val next = rules.discard(ui.game, hand.index, slot) ?: return
        afterTurnEnded(ui.copy(game = next), "$who played Taki: ${card.label} on slot ${slot + 1}.")
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
            message = describePlay(ui.game.current.name, card, pile, result),
        )
        recordIfOver(result.state, ui.mode)
    }

    /** Logs a finished game once (a winner exists only on the transition that ends it). */
    private fun recordIfOver(game: GameState, mode: GameMode?) {
        val winner = game.winner ?: return
        if (mode == GameMode.TWO_PLAYERS) {
            History.record(GameId.BANK, Outcome.PLAYED, "2 players · ${game.players[winner].name} won", variant = "2 players")
        } else {
            val won = winner == GameState.HUMAN
            History.record(
                GameId.BANK, if (won) Outcome.WIN else Outcome.LOSS,
                if (won) "You emptied your Bank first" else "The AI emptied its Bank first", variant = "vs AI",
            )
        }
    }

    /** The "End turn (Taki / تكي)" button. */
    fun onEndTurnClicked() {
        val ui = _uiState.value
        if (!ui.isHumanTurn) return
        if (ui.game.current.hand.isEmpty()) { // nothing to place: just pass
            val next = rules.passTurn(ui.game) ?: return
            afterTurnEnded(ui.copy(game = next), "${ui.game.current.name} passed.")
            return
        }
        val enter = !ui.discardMode
        _uiState.value = ui.copy(
            discardMode = enter,
            selected = null,
            message = when {
                !enter -> YOUR_TURN_HINT
                rules.isTakiRequired(ui.game) -> "TAKI (تكي): pick a hand card, then a ground slot to cover. This ends your turn."
                else -> "Fill your ground first: pick a hand card, then an empty slot (${ui.game.current.ground.count { it.isEmpty() }} empty)."
            },
        )
    }

    private fun toggleSelection(source: CardSource) {
        val ui = _uiState.value
        if (!ui.isHumanTurn) return
        if (ui.game.current.cardAt(source) == null) return
        if (ui.discardMode && source !is CardSource.Hand) return // in Taki mode only hand cards can be picked
        _uiState.value = ui.copy(selected = if (ui.selected == source) null else source)
    }

    private fun say(text: String) = _uiState.update { it.copy(message = text) }

    // ------------------------------------------------------------------ AI turn

    private fun startAiTurn() {
        val game = _uiState.value.game
        if (_uiState.value.mode != GameMode.VS_AI || game.isOver || game.currentPlayer != GameState.AI) return
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
                        if (result.state.isOver) { recordIfOver(result.state, _uiState.value.mode); return@launch }
                    }
                    is AiMove.Fill -> {
                        val r = rules.fillGround(state, move.handIndex, move.slot) ?: return@launch
                        val note = if (r.handRefilled) " — hand empty, drew 5 new cards" else ""
                        _uiState.update { it.copy(game = r.state, message = "AI filled ground slot ${move.slot + 1}$note.") }
                    }
                    is AiMove.Discard -> {
                        val next = rules.discard(state, move.handIndex, move.slot) ?: return@launch
                        _uiState.update {
                            it.copy(game = next, message = "AI played Taki on slot ${move.slot + 1}. Your turn.")
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
        const val YOUR_TURN_HINT = "Your turn: play cards to the centre. To stop: fill any empty ground slots, then Taki."
    }
}
