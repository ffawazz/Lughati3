package com.lughati3.spite.games.freecell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lughati3.spite.model.Suit
import com.lughati3.spite.ui.components.CARD_ASPECT
import com.lughati3.spite.ui.components.CardColumn
import com.lughati3.spite.ui.components.CardStack
import com.lughati3.spite.ui.components.GameTopBar
import com.lughati3.spite.ui.components.SmallButton
import com.lughati3.spite.ui.components.StatusText
import com.lughati3.spite.ui.theme.TargetGreen

@Composable
fun FreeCellScreen(onHome: () -> Unit, vm: FreeCellViewModel = viewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val g = ui.game

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp)) {
        val gap = 8.dp
        val byWidth = (minOf(maxWidth, 1100.dp) - gap * 7) / 8
        val byHeight = (maxHeight - 110.dp) / (CARD_ASPECT + 5.4f)
        val w = minOf(byWidth, byHeight, 120.dp).coerceAtLeast(30.dp)
        val boardW = w * 8 + gap * 7
        val cascadeMax = maxHeight - w * CARD_ASPECT - 100.dp

        Column(Modifier.fillMaxHeight().width(boardW).align(Alignment.TopCenter)) {
            GameTopBar("FreeCell", onHome) {
                SmallButton("Undo", vm::undo, enabled = ui.canUndo)
                Box(Modifier.width(6.dp))
                SmallButton("Auto ▲", vm::autoComplete)
                Box(Modifier.width(6.dp))
                SmallButton("New", vm::newGame)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    g.cells.forEachIndexed { i, card ->
                        CardStack(
                            top = card, count = if (card == null) 0 else 1, width = w, emptyLabel = "",
                            selected = ui.selected == FSource.Cell(i),
                            highlighted = vm.isValidTarget(FTarget.Cell(i)),
                            onClick = { vm.onCellTapped(i) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Suit.entries.forEachIndexed { i, suit ->
                        val pile = g.foundations[i]
                        CardStack(
                            top = pile.lastOrNull(), count = pile.size, width = w,
                            emptyLabel = suit.symbol, showCount = false,
                            highlighted = vm.isValidTarget(FTarget.Foundation(i)),
                            highlightColor = TargetGreen,
                            onClick = { vm.onFoundationTapped(i) },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                g.cascades.forEachIndexed { col, cards ->
                    val sel = (ui.selected as? FSource.Cascade)?.takeIf { it.column == col }?.index
                    CardColumn(
                        cards = cards, width = w, maxHeight = cascadeMax,
                        selectedFrom = sel,
                        highlightTop = vm.isValidTarget(FTarget.Cascade(col)),
                        onCardClick = { vm.onCascadeCardTapped(col, it) },
                        onEmptyClick = { vm.onCascadeEmptyTapped(col) },
                    )
                }
            }
            if (ui.message.isNotEmpty()) StatusText(ui.message, Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
        }
    }

    if (g.isWon) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("You won! 🎉") },
            text = { Text("Solved in ${g.moves} moves.") },
            confirmButton = { TextButton(onClick = vm::newGame) { Text("New Game") } },
        )
    }
}
