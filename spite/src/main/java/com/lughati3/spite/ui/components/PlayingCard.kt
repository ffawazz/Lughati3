package com.lughati3.spite.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank
import com.lughati3.spite.ui.theme.CardBlack
import com.lughati3.spite.ui.theme.CardRed
import com.lughati3.spite.ui.theme.Gold
import com.lughati3.spite.ui.theme.TargetGreen

const val CARD_ASPECT = 1.4f

/**
 * One playing card, scaled from [width]. Face-up cards show rank + suit; Jokers show "JOKER" and,
 * when sitting on a centre pile, the rank they currently stand for via [jokerAs] -> "JOKER (5)".
 */
@Composable
fun PlayingCard(
    card: Card?,
    width: Dp,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    jokerAs: Rank? = null,
    selected: Boolean = false,
    highlighted: Boolean = false,
    highlightColor: Color = TargetGreen,
    onClick: (() -> Unit)? = null,
) {
    val height = width * CARD_ASPECT
    val shape = RoundedCornerShape(width * 0.12f)
    val lift by animateDpAsState(if (selected) (-10).dp else 0.dp, label = "lift")
    val border = when {
        selected -> BorderStroke(3.dp, Gold)
        highlighted -> BorderStroke(3.dp, highlightColor)
        else -> BorderStroke(1.dp, Color(0x55000000))
    }
    val faceBrush = when {
        faceDown -> Brush.linearGradient(listOf(Color(0xFF283593), Color(0xFF1A237E)))
        card?.isJoker == true -> Brush.verticalGradient(listOf(Color(0xFFF3E5F5), Color(0xFFD1C4E9)))
        else -> Brush.verticalGradient(listOf(Color.White, Color(0xFFF5F5F5)))
    }

    Box(
        modifier
            .offset(y = lift)
            .size(width, height)
            .shadow(if (selected) 8.dp else 3.dp, shape)
            .clip(shape)
            .background(faceBrush)
            .border(border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            faceDown -> Box(
                Modifier
                    .size(width * 0.8f, height * 0.86f)
                    .border(1.dp, Color(0x88FFFFFF), RoundedCornerShape(width * 0.06f)),
            )
            card == null -> Unit
            card.isJoker -> JokerFace(width, jokerAs)
            else -> NumberFace(card, width)
        }
    }
}

@Composable
private fun NumberFace(card: Card, width: Dp) {
    val color = if (card.suit!!.isRed) CardRed else CardBlack
    Box(Modifier.size(width, width * CARD_ASPECT)) {
        Column(Modifier.padding(start = width * 0.08f, top = width * 0.04f)) {
            Text(
                card.rank!!.label, color = color, fontWeight = FontWeight.Bold,
                fontSize = (width.value * 0.30f).sp, lineHeight = (width.value * 0.32f).sp,
            )
            Text(card.suit.symbol, color = color, fontSize = (width.value * 0.24f).sp, lineHeight = (width.value * 0.24f).sp)
        }
        Text(
            card.suit.symbol, color = color, fontSize = (width.value * 0.55f).sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = width * 0.08f, bottom = width * 0.04f),
        )
    }
}

@Composable
private fun JokerFace(width: Dp, jokerAs: Rank?) {
    val purple = Color(0xFF6A1B9A)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("★", color = purple, fontSize = (width.value * 0.45f).sp)
        Text(
            "JOKER", color = purple, fontWeight = FontWeight.ExtraBold,
            fontSize = (width.value * 0.19f).sp, maxLines = 1,
        )
        if (jokerAs != null) {
            Text(
                "(${jokerAs.label})", color = purple, fontWeight = FontWeight.Bold,
                fontSize = (width.value * 0.26f).sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/** A dashed placeholder for an empty slot / pile, optionally highlighted as a valid target. */
@Composable
fun EmptySlot(
    width: Dp,
    modifier: Modifier = Modifier,
    label: String = "",
    highlighted: Boolean = false,
    highlightColor: Color = TargetGreen,
    onClick: (() -> Unit)? = null,
) {
    val color = if (highlighted) highlightColor else Color(0x66FFFFFF)
    val radius = width * 0.12f
    Box(
        modifier
            .size(width, width * CARD_ASPECT)
            .clip(RoundedCornerShape(radius))
            .background(if (highlighted) color.copy(alpha = 0.18f) else Color(0x14000000))
            .drawBehind {
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(
                        width = (if (highlighted) 3.dp else 1.5.dp).toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = (width.value * 0.3f).sp)
        }
    }
}

/**
 * A stack of cards: shows [top] (or an empty slot) with a count badge, plus a peek of the cards
 * underneath. Used for Bank, Ground slots, centre piles and the draw pile.
 */
@Composable
fun CardStack(
    top: Card?,
    count: Int,
    width: Dp,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    jokerAs: Rank? = null,
    emptyLabel: String = "",
    selected: Boolean = false,
    highlighted: Boolean = false,
    highlightColor: Color = TargetGreen,
    showCount: Boolean = count > 1,
    onClick: (() -> Unit)? = null,
) {
    if (top == null && !faceDown) {
        EmptySlot(width, modifier, emptyLabel, highlighted, highlightColor, onClick)
        return
    }
    Box(modifier) {
        if (count > 1) {
            PlayingCard(null, width, Modifier.offset(3.dp, 3.dp), faceDown = true)
        }
        PlayingCard(top, width, faceDown = faceDown, jokerAs = jokerAs, selected = selected,
            highlighted = highlighted, highlightColor = highlightColor, onClick = onClick)
        if (showCount) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(4.dp, 4.dp)
                    .size(width * 0.42f)
                    .background(Color(0xE6000000), CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (width.value * 0.22f).sp)
            }
        }
    }
}
