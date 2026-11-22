package com.lughati3.spite.games.hearts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lughati3.spite.ui.components.CARD_ASPECT
import com.lughati3.spite.ui.components.CardStack
import com.lughati3.spite.ui.components.GameTopBar
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.components.SmallButton
import com.lughati3.spite.ui.components.StatusText
import com.lughati3.spite.ui.theme.Gold
import com.lughati3.spite.ui.theme.TakiAmber

@Composable
fun HeartsScreen(onHome: () -> Unit, vm: HeartsViewModel = viewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val g = ui.game

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp)) {
        val boardW = minOf(maxWidth, 900.dp)
        val byWidth = (boardW - 16.dp) / (1f + 12 * HAND_STEP)
        val byHeight = (maxHeight - 250.dp) / (CARD_ASPECT * 3.3f)
        val hw = minOf(byWidth, byHeight, 110.dp).coerceAtLeast(30.dp)
        val tw = hw * 0.85f

        Column(Modifier.fillMaxHeight().width(boardW).align(Alignment.TopCenter), verticalArrangement = Arrangement.SpaceBetween) {
            GameTopBar("Hearts", onHome) { SmallButton("New", vm::newGame) }
            Scoreboard(g)

            Seat(2, g, tw, Modifier.align(Alignment.CenterHorizontally))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Seat(1, g, tw)
                TrickArea(g, tw)
                Seat(3, g, tw)
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (ui.message.isNotEmpty()) StatusText(ui.message, Modifier.padding(bottom = 4.dp))
                if (g.phase == HeartsPhase.PASSING) {
                    Button(
                        onClick = vm::confirmPass, enabled = ui.passSelection.size == 3,
                        colors = ButtonDefaults.buttonColors(containerColor = TakiAmber, contentColor = Color.Black),
                    ) { Text("Pass ${ui.passSelection.size}/3 cards", fontWeight = FontWeight.Bold) }
                }
                Hand(ui, vm, hw)
            }
        }
    }

    when (g.phase) {
        HeartsPhase.ROUND_OVER -> AlertDialog(
            onDismissRequest = {},
            title = { Text(if (g.moonShooter != null) "${SEAT_NAMES[g.moonShooter]} shot the moon! 🌙" else "Round over") },
            text = { ScoreTable(g) },
            confirmButton = { TextButton(onClick = vm::nextRound) { Text("Next round") } },
        )
        HeartsPhase.GAME_OVER -> AlertDialog(
            onDismissRequest = {},
            title = { Text(if (0 in g.winners) "You win! 🎉" else "${g.winners.joinToString { SEAT_NAMES[it] }} wins") },
            text = { ScoreTable(g) },
            confirmButton = { TextButton(onClick = vm::newGame) { Text("New Game") } },
        )
        else -> Unit
    }
}

private const val HAND_STEP = 0.55f

@Composable
private fun Scoreboard(g: HeartsState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SEAT_NAMES.forEachIndexed { i, name ->
            Text(
                "$name  ${g.totalScores[i]}" + if (g.roundPoints[i] > 0) "  (+${g.roundPoints[i]})" else "",
                color = if (g.phase == HeartsPhase.PLAYING && g.turn == i) Color.Black else Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(if (g.phase == HeartsPhase.PLAYING && g.turn == i) Gold else Color(0x66000000), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ScoreTable(g: HeartsState) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.weight(1.4f)); Text("Round", Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Text("Total", Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }
        SEAT_NAMES.forEachIndexed { i, name ->
            Row(Modifier.fillMaxWidth()) {
                Text(name, Modifier.weight(1.4f))
                Text("+${g.lastRoundPoints[i]}", Modifier.weight(1f), textAlign = TextAlign.End)
                Text("${g.totalScores[i]}", Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        if (g.phase == HeartsPhase.ROUND_OVER) Text("First to 100 loses — lowest score wins.", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** An opponent: face-down stack with card count and name. */
@Composable
private fun Seat(seat: Int, g: HeartsState, w: Dp, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CardStack(null, g.hands[seat].size, w * 0.7f, faceDown = true, showCount = true)
        Text(SEAT_NAMES[seat], color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The 4 cards of the current trick laid out around the centre, each next to its seat. */
@Composable
private fun TrickArea(g: HeartsState, w: Dp) {
    val h = w * CARD_ASPECT
    Box(Modifier.size(w * 3.3f, h * 2.1f)) {
        g.trick.forEach { play ->
            val align = when (play.player) {
                0 -> Alignment.BottomCenter
                1 -> Alignment.CenterStart
                2 -> Alignment.TopCenter
                else -> Alignment.CenterEnd
            }
            PlayingCard(play.card, w, Modifier.align(align))
        }
        if (g.trick.isEmpty() && g.phase == HeartsPhase.PLAYING) {
            Text(
                if (g.heartsBroken) "♥ broken" else "", color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center), fontSize = 12.sp,
            )
        }
    }
}

/** Your 13 cards in an overlapping row; illegal cards are dimmed during play. */
@Composable
private fun Hand(ui: HeartsUiState, vm: HeartsViewModel, w: Dp) {
    val hand = ui.game.hands[0]
    val legal = vm.legalCards()
    val step = w * HAND_STEP
    val passing = ui.game.phase == HeartsPhase.PASSING
    Box(Modifier.height(w * CARD_ASPECT + 14.dp).width(w + step * (hand.size - 1).coerceAtLeast(0)).padding(top = 14.dp)) {
        hand.forEachIndexed { i, card ->
            val dim = !passing && ui.isMyTurn && card !in legal
            PlayingCard(
                card, w,
                Modifier.offset(x = step * i).alpha(if (dim) 0.45f else 1f),
                selected = if (passing) card in ui.passSelection else ui.selected == card,
                onClick = { vm.onHandCardTapped(card) },
            )
        }
    }
}
