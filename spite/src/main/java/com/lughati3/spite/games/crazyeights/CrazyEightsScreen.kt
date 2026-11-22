package com.lughati3.spite.games.crazyeights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lughati3.spite.model.Suit
import com.lughati3.spite.ui.components.CARD_ASPECT
import com.lughati3.spite.ui.components.CardStack
import com.lughati3.spite.ui.components.GameTopBar
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.components.SmallButton
import com.lughati3.spite.ui.components.StatusText
import com.lughati3.spite.ui.theme.CardBlack
import com.lughati3.spite.ui.theme.CardRed
import com.lughati3.spite.ui.theme.Gold

@Composable
fun CrazyEightsScreen(onHome: () -> Unit, vm: CrazyEightsViewModel = viewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val g = ui.game
    val legal = vm.legal()

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp)) {
        val boardW = minOf(maxWidth, 900.dp)
        val w = minOf((maxHeight - 220.dp) / (CARD_ASPECT * 3.2f), boardW / 8, 120.dp).coerceAtLeast(40.dp)

        Column(
            Modifier.fillMaxHeight().width(boardW).align(Alignment.TopCenter),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            GameTopBar("Crazy Eights", onHome) { SmallButton("New", vm::newGame) }

            // AI hand (face down)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CardStack(null, g.hands[1].size, w * 0.7f, faceDown = true, showCount = true)
                Text("AI", color = Color.White, fontSize = 12.sp)
            }

            // Table: stock, discard, and the suit that must be matched
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CardStack(
                    top = null, count = g.stock.size, width = w, faceDown = g.stock.isNotEmpty(),
                    emptyLabel = "∅", showCount = true, onClick = vm::onStockTapped,
                )
                Box(Modifier.width(24.dp))
                PlayingCard(g.top, w)
                Box(Modifier.width(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Suit", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(
                        g.activeSuit.symbol, fontSize = (w.value * 0.7f).sp, fontWeight = FontWeight.Bold,
                        color = if (g.activeSuit.isRed) CardRed else CardBlack,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                StatusText(
                    when {
                        ui.message.isNotEmpty() -> ui.message
                        ui.isMyTurn && legal.isEmpty() -> "No playable card — tap the stock to draw."
                        ui.isMyTurn -> "Your turn."
                        else -> "AI is thinking…"
                    },
                    Modifier.padding(bottom = 6.dp),
                )
                val hand = g.hands[0]
                val step = minOf(w * 0.7f, ((boardW - w) / (hand.size - 1).coerceAtLeast(1)))
                Box(Modifier.height(w * CARD_ASPECT + 14.dp).width(w + step * (hand.size - 1).coerceAtLeast(0)).padding(top = 14.dp)) {
                    hand.forEachIndexed { i, card ->
                        PlayingCard(
                            card, w, Modifier.offset(x = step * i).alpha(if (ui.isMyTurn && card !in legal) 0.45f else 1f),
                            onClick = { vm.onCardTapped(card) },
                        )
                    }
                }
            }
        }
    }

    if (ui.pendingEight != null) {
        AlertDialog(
            onDismissRequest = vm::cancelSuitChoice,
            title = { Text("Choose a suit") },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Suit.entries.forEach { s ->
                        TextButton(onClick = { vm.onSuitChosen(s) }) {
                            Text(s.symbol, fontSize = 36.sp, color = if (s.isRed) CardRed else Color.Unspecified)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancelSuitChoice) { Text("Cancel") } },
        )
    }
    g.winner?.let { w ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (w == 0) "You win! 🎉" else "AI wins 😕") },
            confirmButton = { TextButton(onClick = vm::newGame) { Text("New Game") } },
        )
    }
}
