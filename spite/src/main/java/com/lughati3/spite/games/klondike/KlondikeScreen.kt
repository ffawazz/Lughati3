package com.lughati3.spite.games.klondike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lughati3.spite.model.Suit
import com.lughati3.spite.ui.components.CARD_ASPECT
import com.lughati3.spite.ui.components.CardColumn
import com.lughati3.spite.ui.components.CardStack
import com.lughati3.spite.ui.components.DragOverlay
import com.lughati3.spite.ui.components.DragState
import com.lughati3.spite.ui.components.EmptySlot
import com.lughati3.spite.ui.components.GameTopBar
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.components.SmallButton
import com.lughati3.spite.ui.components.StatusText
import com.lughati3.spite.ui.components.dragSource
import kotlin.math.abs

@Composable
fun KlondikeScreen(onHome: () -> Unit, onHelp: () -> Unit, vm: KlondikeViewModel = viewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val g = ui.game
    val density = LocalDensity.current

    // Drag & drop: what is being dragged, plus the on-screen rectangles of every drop zone.
    var drag by remember { mutableStateOf<DragState<KSource>?>(null) }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    val foundationRects = remember { mutableMapOf<Int, Rect>() }
    val tableauRects = remember { mutableMapOf<Int, Rect>() }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp)) {
        val gap = 8.dp
        val byWidth = (minOf(maxWidth, 1100.dp) - gap * 6) / 7
        val byHeight = (maxHeight - 110.dp) / (CARD_ASPECT + 5.4f)
        val w = minOf(byWidth, byHeight, 120.dp).coerceAtLeast(34.dp)
        val boardW = w * 7 + gap * 6
        val tableauMax = maxHeight - w * CARD_ASPECT - 100.dp
        val wPx = with(density) { w.toPx() }
        val slop = with(density) { 12.dp.toPx() }

        fun beginDrag(source: KSource, origin: Offset) {
            val cards = vm.rules.cardsAt(g, source) ?: return
            vm.onDragStart(source)
            drag = DragState(source, cards, origin)
        }

        fun moveDrag(delta: Offset) {
            drag = drag?.let { it.copy(topLeft = it.topLeft + delta) }
        }

        /** The drop zone under the dragged card's centre (foundations only take a single card). */
        fun targetUnder(d: DragState<KSource>): KTarget? {
            val center = d.topLeft + Offset(wPx / 2, wPx * CARD_ASPECT / 2)
            if (d.cards.size == 1) {
                foundationRects.entries.firstOrNull { it.value.inflate(slop).contains(center) }
                    ?.let { return KTarget.Foundation(it.key) }
            }
            // Tableau columns span the full board height, so pick the one nearest in x.
            return tableauRects.entries
                .filter { center.y >= it.value.top - slop && center.y <= it.value.bottom + slop }
                .minByOrNull { abs(center.x - it.value.center.x) }
                ?.takeIf { abs(center.x - it.value.center.x) <= wPx * 0.75f }
                ?.let { KTarget.Tableau(it.key) }
        }

        fun endDrag() {
            val d = drag
            drag = null
            vm.onDrop(d?.let { targetUnder(it) })
        }

        Box(Modifier.fillMaxSize().onGloballyPositioned { boardOrigin = it.positionInRoot() }) {
            Column(Modifier.fillMaxHeight().width(boardW).align(Alignment.TopCenter)) {
                GameTopBar("Solitaire", onHome, onHelp = onHelp) {
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
                        Waste(
                            g, ui, w, vm,
                            hideTop = drag?.source == KSource.Waste,
                            onDragStart = { origin -> beginDrag(KSource.Waste, origin) },
                            onDragBy = ::moveDrag, onDragEnd = ::endDrag,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Suit.entries.forEachIndexed { i, suit ->
                            val pile = g.foundations[i]
                            val source = KSource.Foundation(i)
                            CardStack(
                                top = pile.lastOrNull(), count = pile.size, width = w,
                                emptyLabel = suit.symbol, showCount = false,
                                selected = ui.selected == source && drag == null,
                                highlighted = ui.isValidTarget(vm.rules, KTarget.Foundation(i)),
                                onClick = { vm.onFoundationTapped(i) },
                                modifier = Modifier
                                    .onGloballyPositioned { foundationRects[i] = it.boundsInRoot() }
                                    .alpha(if (drag?.source == source) 0.35f else 1f)
                                    .dragSource(
                                        enabled = pile.isNotEmpty(),
                                        onStart = { origin, _ -> beginDrag(source, origin) },
                                        onDelta = ::moveDrag, onEnd = ::endDrag,
                                    ),
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    g.tableau.forEachIndexed { col, cards ->
                        val sel = (ui.selected as? KSource.Tableau)?.takeIf { it.column == col }?.index
                        val dragging = (drag?.source as? KSource.Tableau)?.takeIf { it.column == col }?.index
                        // The wrapper spans the whole column height so the whole strip is a drop zone.
                        Box(
                            Modifier
                                .width(w)
                                .fillMaxHeight()
                                .onGloballyPositioned { tableauRects[col] = it.boundsInRoot() },
                        ) {
                            CardColumn(
                                cards = cards.map { it.card },
                                width = w,
                                maxHeight = tableauMax,
                                faceUp = { cards[it].faceUp },
                                selectedFrom = if (drag == null) sel else null,
                                highlightTop = ui.isValidTarget(vm.rules, KTarget.Tableau(col)),
                                emptyLabel = "K",
                                onCardClick = { vm.onTableauCardTapped(col, it) },
                                onEmptyClick = { vm.onTableauEmptyTapped(col) },
                                onDragStart = { index, origin, _ -> beginDrag(KSource.Tableau(col, index), origin) },
                                onDragBy = ::moveDrag,
                                onDragEnd = ::endDrag,
                                hiddenFrom = dragging,
                            )
                        }
                    }
                }
                if (ui.message.isNotEmpty()) {
                    StatusText(ui.message, Modifier.padding(vertical = 6.dp).align(Alignment.CenterHorizontally))
                }
            }
            DragOverlay(drag, w, boardOrigin)
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
private fun Waste(
    g: KlondikeState, ui: KlondikeUiState, w: Dp, vm: KlondikeViewModel,
    hideTop: Boolean,
    onDragStart: (Offset) -> Unit, onDragBy: (Offset) -> Unit, onDragEnd: () -> Unit,
) {
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
                card, w,
                Modifier
                    .offset(x = step * i)
                    .alpha(if (top && hideTop) 0f else 1f)
                    .dragSource(
                        enabled = top,
                        onStart = { origin, _ -> onDragStart(origin) },
                        onDelta = onDragBy, onEnd = onDragEnd,
                    ),
                selected = top && ui.selected == KSource.Waste && !hideTop,
                onClick = if (top) vm::onWasteTapped else null,
            )
        }
    }
}
