package com.lughati3.spite.games.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lughati3.spite.ui.components.CARD_ASPECT
import com.lughati3.spite.ui.components.FlipCard
import com.lughati3.spite.ui.components.GameTopBar
import com.lughati3.spite.ui.components.SmallButton
import com.lughati3.spite.ui.components.StatusText
import com.lughati3.spite.ui.theme.Gold
import kotlin.math.ceil

@Composable
fun MemoryScreen(onHome: () -> Unit, onHelp: () -> Unit, vm: MemoryViewModel = viewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val g = ui.game

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp)) {
        val boardW = minOf(maxWidth, 900.dp)
        val gap = 8.dp
        val cols = g.level.columns
        val rows = ceil(g.cards.size / cols.toFloat()).toInt()
        val byWidth = (boardW - gap * (cols - 1)) / cols
        val byHeight = ((maxHeight - 270.dp) - gap * (rows - 1)) / rows / CARD_ASPECT
        val w = minOf(byWidth, byHeight, 110.dp).coerceAtLeast(36.dp)

        Column(
            Modifier.fillMaxHeight().width(boardW).align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameTopBar("Memory", onHome, onHelp = onHelp) { SmallButton("New", { vm.newGame() }) }

            // level picker
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                MemoryLevel.entries.forEach { level ->
                    val selected = level == g.level
                    Text(
                        "${level.label} · ${level.pairs} pairs",
                        color = if (selected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier
                            .background(if (selected) Gold else Color(0x66000000), RoundedCornerShape(50))
                            .clickableNoRipple { vm.newGame(level) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }

            // live stats
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Moves", "${g.moves}")
                Stat("Pairs", "${g.matched.size / 2} / ${g.level.pairs}")
                Stat("Time", formatTime(ui.elapsedSec))
            }

            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            if (i >= g.cards.size) { Box(Modifier.width(w)); continue }
                            FlipCard(
                                card = g.cards[i],
                                faceUp = i in g.faceUp || i in g.matched,
                                width = w,
                                matched = i in g.matched,
                                onClick = { vm.onCardTapped(i) },
                            )
                        }
                    }
                }
            }
            StatusText(ui.message, Modifier.padding(top = 10.dp))
        }
    }

    if (g.isWon) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("You found them all! 🎉") },
            text = { Text("${g.level.label}: ${g.moves} moves in ${formatTime(ui.elapsedSec)}.") },
            confirmButton = { TextButton(onClick = { vm.newGame() }) { Text("Play again") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
