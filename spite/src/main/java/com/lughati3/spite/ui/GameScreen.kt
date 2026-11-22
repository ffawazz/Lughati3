package com.lughati3.spite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lughati3.spite.model.GameState
import com.lughati3.spite.model.PlayerState
import com.lughati3.spite.ui.components.CARD_ASPECT
import com.lughati3.spite.ui.components.CardStack
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.theme.Gold
import com.lughati3.spite.ui.theme.TakiAmber
import com.lughati3.spite.model.CardSource

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    GameContent(ui, viewModel)
}

@Composable
private fun GameContent(ui: GameUiState, vm: GameViewModel) {
    val game = ui.game
    val human = game.players[GameState.HUMAN]
    val ai = game.players[GameState.AI]

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val gap = 6.dp
        // 6 columns (bank + 5 ground slots) must fit the width; 4 card rows must fit the height.
        val byWidth = (maxWidth - 16.dp - gap * 5) / 6
        val byHeight = ((maxHeight - 250.dp) / 4f) / CARD_ASPECT
        val cardW = minOf(byWidth, byHeight, 72.dp).coerceAtLeast(36.dp)
        val cardH = cardW * CARD_ASPECT

        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Header(ui, vm::newGame)

            // ---- AI: hand (hidden), then bank + ground
            AiHandRow(ai.hand.size, cardW)
            ZoneRow(ai, cardW, isHuman = false, ui = ui, vm = vm)

            // ---- Centre
            CenterRow(ui, cardW, vm)

            MessageBar(ui.message, ui.discardMode)

            // ---- Human: bank + ground, then hand, then actions
            ZoneRow(human, cardW, isHuman = true, ui = ui, vm = vm)
            HandRow(human, ui, cardW, vm)
            ActionRow(ui, vm)
        }
    }

    game.winner?.let { winner ->
        val won = winner == GameState.HUMAN
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (won) "You win! 🎉" else "You lose 😕", fontWeight = FontWeight.Bold) },
            text = {
                Text(if (won) "You emptied your Bank first." else "The AI emptied its Bank first.")
            },
            confirmButton = { TextButton(onClick = vm::newGame) { Text("New Game") } },
        )
    }
}

// ------------------------------------------------------------------------------------ header

@Composable
private fun Header(ui: GameUiState, onNewGame: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Taki Cards", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.weight(1f))
        val yours = ui.isHumanTurn
        Text(
            if (ui.game.isOver) "Game over" else if (yours) "● YOUR TURN" else "● AI is playing…",
            color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier
                .background(if (yours) Gold else Color(0xFFB0BEC5), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
            Text("New Game", color = Color.White, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------------------------ zones

@Composable
private fun AiHandRow(count: Int, cardW: Dp) {
    val mini = cardW * 0.45f
    Row(Modifier.fillMaxWidth().height(mini * CARD_ASPECT), verticalAlignment = Alignment.CenterVertically) {
        Text("AI hand", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        repeat(count) { PlayingCard(null, mini, Modifier.padding(end = 4.dp), faceDown = true) }
    }
}

/** Bank on the left + the 5 ground slots. For the AI these are display-only. */
@Composable
private fun ZoneRow(player: PlayerState, cardW: Dp, isHuman: Boolean, ui: GameUiState, vm: GameViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        CardStack(
            top = player.bankTop,
            count = player.bank.size,
            width = cardW,
            showCount = true,
            emptyLabel = "0",
            selected = isHuman && ui.selected == CardSource.Bank,
            onClick = if (isHuman) vm::onBankTapped else null,
        )
        player.ground.forEachIndexed { slot, stack ->
            val discardTarget = isHuman && slot in ui.validDiscardSlots
            CardStack(
                top = stack.lastOrNull(),
                count = stack.size,
                width = cardW,
                emptyLabel = "",
                selected = isHuman && ui.selected == CardSource.Ground(slot),
                highlighted = discardTarget,
                highlightColor = TakiAmber,
                onClick = if (isHuman) ({ vm.onGroundSlotTapped(slot) }) else null,
            )
        }
    }
}

@Composable
private fun CenterRow(ui: GameUiState, cardW: Dp, vm: GameViewModel) {
    val game = ui.game
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            // Draw pile (face down) with its remaining count.
            CardStack(
                top = game.drawPile.lastOrNull(), count = game.drawPile.size, width = cardW,
                faceDown = game.drawPile.isNotEmpty(), emptyLabel = "∅", showCount = true,
            )
            game.centerPiles.forEachIndexed { i, pile ->
                val top = pile.lastOrNull()
                CardStack(
                    top = top?.card,
                    count = pile.size,
                    width = cardW,
                    jokerAs = if (top?.card?.isJoker == true) top.asRank else null,
                    emptyLabel = "A",
                    highlighted = i in ui.validPiles,
                    showCount = pile.isNotEmpty(),
                    onClick = { vm.onPileTapped(i) },
                )
            }
        }
        Text(
            "Draw pile ${game.drawPile.size} · Completed ${game.completedCards.size / 13} (reshuffled when the deck runs out)",
            color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun MessageBar(text: String, takiMode: Boolean) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (takiMode) Color(0x99BF6A00) else Color(0x66000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .height(36.dp),
        maxLines = 2,
    )
}

@Composable
private fun HandRow(human: PlayerState, ui: GameUiState, cardW: Dp, vm: GameViewModel) {
    Row(
        Modifier.fillMaxWidth().height(cardW * CARD_ASPECT + 12.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        if (human.hand.isEmpty()) {
            Text("Hand empty", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.CenterVertically))
        }
        human.hand.forEachIndexed { i, card ->
            PlayingCard(
                card = card,
                width = cardW * 1.1f,
                selected = ui.selected == CardSource.Hand(i),
                onClick = { vm.onHandCardTapped(i) },
            )
        }
    }
}

@Composable
private fun ActionRow(ui: GameUiState, vm: GameViewModel) {
    val taki = ui.discardMode
    val needsTaki = !ui.game.current.hasEmptyGroundSlot
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        Button(
            onClick = vm::onEndTurnClicked,
            enabled = ui.isHumanTurn,
            colors = ButtonDefaults.buttonColors(containerColor = if (taki) Color(0xFFB0BEC5) else TakiAmber, contentColor = Color.Black),
        ) {
            Text(
                when {
                    taki -> "Cancel"
                    ui.game.players[GameState.HUMAN].hand.isEmpty() -> "Pass"
                    needsTaki -> "End Turn · Taki (تكي)"
                    else -> "End Turn (place a card)"
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
