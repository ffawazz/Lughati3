package com.lughati3.spite.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lughati3.spite.model.Card
import kotlin.math.roundToInt

/** A stack of cards currently being dragged; [topLeft] is where its first card is, in root coordinates. */
data class DragState<S>(val source: S, val cards: List<Card>, val topLeft: Offset)

/**
 * Makes a composable a drag handle. Tapping still works (use alongside `clickable`): the drag only
 * starts after the touch-slop distance. [onStart] gets the element's top-left (root coords) and the
 * touch point inside it; [onDelta] gets each movement in pixels.
 */
@Composable
fun Modifier.dragSource(
    enabled: Boolean,
    onStart: (origin: Offset, touch: Offset) -> Unit,
    onDelta: (Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val start by rememberUpdatedState(onStart)
    val delta by rememberUpdatedState(onDelta)
    val end by rememberUpdatedState(onEnd)
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(enabled) {
            if (enabled) {
                detectDragGestures(
                    onDragStart = { touch -> start(origin, touch) },
                    onDrag = { change, amount -> change.consume(); delta(amount) },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                )
            }
        }
}

/** Draws the dragged cards floating above everything, relative to [boardOrigin] (root coords of the parent). */
@Composable
fun <S> DragOverlay(drag: DragState<S>?, width: Dp, boardOrigin: Offset) {
    if (drag == null) return
    Box(
        Modifier.offset {
            IntOffset((drag.topLeft.x - boardOrigin.x).roundToInt(), (drag.topLeft.y - boardOrigin.y).roundToInt())
        },
    ) {
        drag.cards.forEachIndexed { i, card ->
            PlayingCard(card, width, Modifier.offset(y = width * 0.5f * i), selected = true)
        }
    }
}
