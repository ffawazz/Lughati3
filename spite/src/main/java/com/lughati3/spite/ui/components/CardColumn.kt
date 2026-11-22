package com.lughati3.spite.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lughati3.spite.model.Card
import com.lughati3.spite.ui.theme.Gold
import com.lughati3.spite.ui.theme.TargetGreen

/**
 * A fanned/overlapping column of cards (Klondike & FreeCell tableaus). The spacing shrinks
 * automatically so a tall column always fits in [maxHeight].
 *
 *  - [faceUp] says per card whether it is shown; hidden cards are drawn as backs with a tight overlap.
 *  - [selectedFrom]: that card and everything beneath it is shown lifted/selected.
 *  - [highlightTop]: outline the last card (or the empty slot) as a valid drop target.
 */
@Composable
fun CardColumn(
    cards: List<Card>,
    width: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    faceUp: (Int) -> Boolean = { true },
    selectedFrom: Int? = null,
    highlightTop: Boolean = false,
    emptyLabel: String = "",
    onCardClick: (Int) -> Unit,
    onEmptyClick: () -> Unit,
) {
    val cardH = width * CARD_ASPECT
    if (cards.isEmpty()) {
        EmptySlot(width, modifier, emptyLabel, highlightTop, TargetGreen, onEmptyClick)
        return
    }
    // Preferred vertical step per card, then compress if the column would overflow.
    val steps = cards.indices.map { if (faceUp(it)) width * 0.5f else width * 0.2f }
    val total = steps.dropLast(1).fold(0.dp) { a, b -> a + b }
    val room = (maxHeight - cardH).coerceAtLeast(0.dp)
    val k = if (total > room && total > 0.dp) room / total else 1f
    var y = 0.dp
    Box(modifier.width(width).height(cardH + total * k)) {
        cards.forEachIndexed { i, card ->
            val up = faceUp(i)
            PlayingCard(
                card = card,
                width = width,
                faceDown = !up,
                selected = selectedFrom != null && i >= selectedFrom,
                highlighted = highlightTop && i == cards.lastIndex,
                modifier = Modifier.offset(y = y),
                onClick = { onCardClick(i) },
            )
            y += steps[i] * k
        }
    }
}

/** Compact top bar shared by the collection's games: title, optional extras, and a way home. */
@Composable
fun GameTopBar(
    title: String,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        Spacer(Modifier.weight(1f))
        extra()
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = onHome, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
            Text("⌂ Games", color = Color.White, fontSize = 12.sp)
        }
    }
}

/** Small pill-shaped action button used in game top bars / action rows. */
@Composable
fun SmallButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) { Text(text, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 12.sp) }
}

/** Centered translucent text panel for hints/status. */
@Composable
fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 13.sp,
        modifier = modifier
            .background(Color(0x66000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
