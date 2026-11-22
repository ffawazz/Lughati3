package com.lughati3.spite.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.width
import com.lughati3.spite.model.Suit
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        faceDown -> Brush.linearGradient(listOf(Color(0xFF3949AB), Color(0xFF1A237E)))
        card?.isJoker == true -> Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE1D5F0)))
        else -> Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1EFE9)))
    }

    Box(
        modifier
            .offset(y = lift)
            .size(width, height)
            .shadow(if (selected) 10.dp else 3.dp, shape)
            .clip(shape)
            .background(faceBrush)
            .border(border, shape)
            .semantics { contentDescription = if (faceDown || card == null) "Face-down card" else card.label }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            faceDown -> CardBack(width)
            card == null -> Unit
            card.isJoker -> JokerFace(width, jokerAs)
            else -> CardFace(card, width)
        }
    }
}

// ---------------------------------------------------------------------------------- card back

@Composable
private fun CardBack(width: Dp) {
    Canvas(Modifier.size(width, width * CARD_ASPECT)) {
        val w = size.width
        val h = size.height
        val inset = w * 0.09f
        val inner = Rect(inset, inset, w - inset, h - inset)
        val corner = CornerRadius(w * 0.06f)
        // diamond lattice clipped to the inner panel
        clipRect(inner.left, inner.top, inner.right, inner.bottom) {
            val step = w * 0.16f
            var x = -h
            while (x < w + h) {
                drawLine(Color(0x33FFFFFF), Offset(x, 0f), Offset(x + h, h), strokeWidth = w * 0.012f)
                drawLine(Color(0x33FFFFFF), Offset(x, h), Offset(x + h, 0f), strokeWidth = w * 0.012f)
                x += step
            }
        }
        drawRoundRect(Color(0xCCFFFFFF), inner.topLeft, inner.size, corner, style = Stroke(w * 0.025f))
        // centre medallion
        val c = Offset(w / 2, h / 2)
        drawCircle(Color(0xFF1A237E), w * 0.2f, c)
        drawCircle(Gold, w * 0.2f, c, style = Stroke(w * 0.03f))
        drawSuit(Suit.SPADES, c, w * 0.22f, Gold)
    }
}

// ---------------------------------------------------------------------------------- card face

/** Where the pips of each rank sit: (x, y) as fractions of the pip area, x in {0, .5, 1}. */
private fun pipLayout(value: Int): List<Pair<Float, Float>> {
    val l = 0f; val c = 0.5f; val r = 1f
    return when (value) {
        1 -> listOf(c to 0.5f)
        2 -> listOf(c to 0f, c to 1f)
        3 -> listOf(c to 0f, c to 0.5f, c to 1f)
        4 -> listOf(l to 0f, r to 0f, l to 1f, r to 1f)
        5 -> listOf(l to 0f, r to 0f, c to 0.5f, l to 1f, r to 1f)
        6 -> listOf(l to 0f, r to 0f, l to 0.5f, r to 0.5f, l to 1f, r to 1f)
        7 -> listOf(l to 0f, r to 0f, c to 0.25f, l to 0.5f, r to 0.5f, l to 1f, r to 1f)
        8 -> listOf(l to 0f, r to 0f, c to 0.25f, l to 0.5f, r to 0.5f, c to 0.75f, l to 1f, r to 1f)
        9 -> listOf(l to 0f, r to 0f, l to 0.33f, r to 0.33f, c to 0.5f, l to 0.67f, r to 0.67f, l to 1f, r to 1f)
        else -> listOf(
            l to 0f, r to 0f, c to 0.17f, l to 0.33f, r to 0.33f,
            l to 0.67f, r to 0.67f, c to 0.83f, l to 1f, r to 1f,
        )
    }
}

@Composable
private fun CardFace(card: Card, width: Dp) {
    val suit = card.suit!!
    val rank = card.rank!!
    val color = suit.color
    val isCourt = rank.value >= 11
    val h = width * CARD_ASPECT

    Box(Modifier.size(width, h)) {
        // centre artwork: pips for A-10, a framed panel for J/Q/K
        Canvas(Modifier.size(width, h)) {
            val w = size.width
            val hh = size.height
            val areaL = w * 0.37f   // keeps the pips clear of the corner indices
            val areaR = w * 0.63f
            val areaT = hh * 0.14f
            val areaB = hh * 0.86f
            if (isCourt) {
                val panel = Rect(w * 0.31f, areaT, w * 0.69f, areaB)
                drawRoundRect(color.copy(alpha = 0.07f), panel.topLeft, panel.size, CornerRadius(w * 0.05f))
                drawRoundRect(Gold, panel.topLeft, panel.size, CornerRadius(w * 0.05f), style = Stroke(w * 0.022f))
                drawSuit(suit, Offset(panel.left + w * 0.09f, panel.top + w * 0.09f), w * 0.12f, color)
                drawSuit(suit, Offset(panel.right - w * 0.09f, panel.bottom - w * 0.09f), w * 0.12f, color, upsideDown = true)
            } else {
                val single = rank.value == 1
                val pip = if (single) w * 0.5f else if (rank.value >= 9) w * 0.16f else w * 0.19f
                for ((fx, fy) in pipLayout(rank.value)) {
                    val p = Offset(areaL + fx * (areaR - areaL), areaT + fy * (areaB - areaT))
                    drawSuit(suit, p, pip, color, upsideDown = fy > 0.5f && !single)
                }
            }
        }
        if (isCourt) {
            Text(
                rank.label, color = color, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                fontSize = (width.value * 0.4f).sp, modifier = Modifier.align(Alignment.Center),
            )
        }
        Corner(rank.label, suit, width, Modifier.align(Alignment.TopStart))
        Corner(rank.label, suit, width, Modifier.align(Alignment.BottomEnd).rotate(180f))
    }
}

/** The rank + suit index in a card corner. */
@Composable
private fun Corner(label: String, suit: Suit, width: Dp, modifier: Modifier) {
    val size = if (label.length > 1) 0.21f else 0.27f
    Column(modifier.padding(start = width * 0.05f, top = width * 0.035f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, color = suit.color, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = (width.value * size).sp, lineHeight = (width.value * 0.3f).sp, maxLines = 1, softWrap = false,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
        SuitIcon(suit, width * 0.15f)
    }
}

// ---------------------------------------------------------------------------------- joker

@Composable
private fun JokerFace(width: Dp, jokerAs: Rank?) {
    val h = width * CARD_ASPECT
    Box(Modifier.size(width, h)) {
        Canvas(Modifier.size(width, h)) {
            val w = size.width
            val hh = size.height
            // jester star with a jingle at each point
            val c = Offset(w / 2, hh * 0.52f)
            val outer = w * 0.3f
            val inner = outer * 0.45f
            val star = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val a = Math.toRadians(-90.0 + i * 36.0)
                val p = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
                if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
            }
            star.close()
            drawPath(star, Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFF8F00))))
            drawPath(star, Color(0xFF6A1B9A), style = Stroke(w * 0.02f))
            val dots = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF1E88E5))
            for (i in 0 until 5) {
                val a = Math.toRadians(-90.0 + i * 72.0)
                val p = Offset(c.x + (outer * 1.12f * cos(a)).toFloat(), c.y + (outer * 1.12f * sin(a)).toFloat())
                drawCircle(dots[i], w * 0.045f, p)
            }
            drawCircle(Color(0xFF6A1B9A), w * 0.07f, c)
        }
        val purple = Color(0xFF6A1B9A)
        Text(
            "JOKER", color = purple, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            fontSize = (width.value * 0.17f).sp, letterSpacing = (width.value * 0.01f).sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = width * 0.05f),
        )
        if (jokerAs != null) {
            Text(
                "(${jokerAs.label})", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (width.value * 0.24f).sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = width * 0.06f)
                    .background(purple, RoundedCornerShape(50))
                    .padding(horizontal = width * 0.1f),
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
            .semantics { contentDescription = "Empty slot $label".trim() }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val suit = Suit.entries.firstOrNull { it.symbol == label }
        if (suit != null) {
            SuitIcon(suit, width * 0.4f, color = color.copy(alpha = if (highlighted) 1f else 0.7f))
        } else if (label.isNotEmpty()) {
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
