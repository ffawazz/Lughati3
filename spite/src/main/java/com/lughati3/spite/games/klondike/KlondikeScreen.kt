package com.lughati3.spite.games.klondike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lughati3.spite.model.Suit
import com.lughati3.spite.ui.components.CARD_ASPECT
import com.lughati3.spite.ui.components.CardColumn
import com.lughati3.spite.ui.components.CardStack
import com.lughati3.spite.ui.components.EmptySlot
import com.lughati3.spite.ui.components.GameTopBar
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.components.SmallButton
import com.lughati3.spite.ui.components.StatusText

@Composable
fun KlondikeScreen(onHome: () -> Unit, vm: KlondikeViewModel = viewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val g = ui.game

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp)) {
        val gap = 8.dp
        val byWidth = (minOf(maxWidth, 1100.dp) - gap * 6) / 7
        val byHeight = (maxHeight - 110.dp) / (CARD_ASPECT + 5.4f)
        val w = minOf(byWidth, byHeight, 120.dp).coerceAtLeast(34.dp)
        val boardW = w * 7 + gap * 6
        val tableauMax = maxHeight - w * CARD_ASPECT - 100.dp

        Column(Modifier.fillMaxHeight().width(boardW).align(Alignment.TopCenter)) {
            GameTopBar("Solitaire", onHome) {
                SmallButton("Undo", vm::undo, enabled = ui.canUndo)
                Box(Modifier.width(6.dp))
                SmallButton("Auto ▲", vm::autoComplete)
                Box(Modifier.width(6.dp))
                SmallButton("Draw ${g.drawCount}", vm::toggleDrawCount)
                Box(Modifier.width(6.dp))
                SmallButton("New", { vm.newGame() })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    // Stock: tap to draw (or recycle the waste when empty).
                    if (g.stock.isEmpty()) EmptySlot(w, label = "↺", onClick = vm::onStockTapped)
                    else CardStack(g.stock.last(), g.stock.size, w, faceDown = true, showCount = true, onClick = vm::onStockTapped)
                    Waste(g, ui, w, vm)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Suit.entries.forEachIndexed { i, suit ->
                        val pile = g.foundations[i]
                        CardStack(
                            top = pile.lastOrNull(), count = pile.size, width = w,
                            emptyLabel = suit.symbol, showCount = false,
                            selected = ui.selected == KSource.Foundation(i),
                            highlighted = ui.isValidTarget(vm.rules, KTarget.Foundation(i)),
                            onClick = { vm.onFoundationTapped(i) },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                g.tableau.forEachIndexed { col, cards ->
                    val sel = (ui.selected as? KSource.Tableau)?.takeIf { it.column == col }?.index
                    CardColumn(
                        cards = cards.map { it.card },
                        width = w,
                        maxHeight = tableauMax,
                        faceUp = { cards[it].faceUp },
                        selectedFrom = sel,
                        highlightTop = ui.isValidTarget(vm.rules, KTarget.Tableau(col)),
                        emptyLabel = "K",
                        onCardClick = { vm.onTableauCardTapped(col, it) },
                        onEmptyClick = { vm.onTableauEmptyTapped(col) },
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
            confirmButton = { TextButton(onClick = { vm.newGame() }) { Text("New Game") } },
        )
    }
}

/** Waste pile; in draw-3 mode the last three cards are fanned so you can see what is underneath. */
@Composable
private fun Waste(g: KlondikeState, ui: KlondikeUiState, w: Dp, vm: KlondikeViewModel) {
    val shown = g.waste.takeLast(if (g.drawCount == 3) 3 else 1)
    if (shown.isEmpty()) {
        EmptySlot(w)
        return
    }
    val step = w * 0.28f
    Box(Modifier.width(w + step * (shown.size - 1))) {
        shown.forEachIndexed { i, card ->
            val top = i == shown.lastIndex
            PlayingCard(
                card, w, Modifier.offset(x = step * i),
                selected = top && ui.selected == KSource.Waste,
                onClick = if (top) vm::onWasteTapped else null,
            )
        }
    }
}
