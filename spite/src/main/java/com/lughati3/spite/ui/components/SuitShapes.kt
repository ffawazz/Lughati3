package com.lughati3.spite.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import com.lughati3.spite.model.Suit
import com.lughati3.spite.ui.theme.CardBlack
import com.lughati3.spite.ui.theme.CardRed

/*
 * Suits are drawn as vector paths rather than font glyphs: the ♥/♦ code points can render as
 * colour emoji on some Android versions, and glyph metrics make precise pip layouts impossible.
 * All shapes live in a unit box [-1, 1] x [-1, 1] and are scaled when drawn.
 */

private val heartPath = Path().apply {
    moveTo(0f, 0.9f)
    cubicTo(-1.25f, -0.05f, -1f, -0.95f, -0.45f, -0.95f)
    cubicTo(-0.2f, -0.95f, 0f, -0.75f, 0f, -0.5f)
    cubicTo(0f, -0.75f, 0.2f, -0.95f, 0.45f, -0.95f)
    cubicTo(1f, -0.95f, 1.25f, -0.05f, 0f, 0.9f)
    close()
}

private val diamondPath = Path().apply {
    moveTo(0f, -1f)
    cubicTo(0.15f, -0.5f, 0.5f, -0.15f, 0.75f, 0f)
    cubicTo(0.5f, 0.15f, 0.15f, 0.5f, 0f, 1f)
    cubicTo(-0.15f, 0.5f, -0.5f, 0.15f, -0.75f, 0f)
    cubicTo(-0.5f, -0.15f, -0.15f, -0.5f, 0f, -1f)
    close()
}

private val spadePath = Path().apply {
    moveTo(0f, -1f)
    cubicTo(-0.2f, -0.6f, -1.1f, -0.2f, -1.1f, 0.3f)
    cubicTo(-1.1f, 0.8f, -0.4f, 0.9f, -0.1f, 0.45f)
    cubicTo(-0.1f, 0.8f, -0.25f, 0.95f, -0.5f, 1f)
    lineTo(0.5f, 1f)
    cubicTo(0.25f, 0.95f, 0.1f, 0.8f, 0.1f, 0.45f)
    cubicTo(0.4f, 0.9f, 1.1f, 0.8f, 1.1f, 0.3f)
    cubicTo(1.1f, -0.2f, 0.2f, -0.6f, 0f, -1f)
    close()
}

private val clubPath = Path().apply {
    addOval(Rect(-0.45f, -0.95f, 0.45f, -0.05f))
    addOval(Rect(-0.97f, -0.25f, -0.07f, 0.65f))
    addOval(Rect(0.07f, -0.25f, 0.97f, 0.65f))
    moveTo(0f, -0.1f)
    lineTo(-0.12f, 0.3f)
    lineTo(-0.32f, 1f)
    lineTo(0.32f, 1f)
    lineTo(0.12f, 0.3f)
    close()
}

private fun pathFor(suit: Suit): Path = when (suit) {
    Suit.HEARTS -> heartPath
    Suit.DIAMONDS -> diamondPath
    Suit.SPADES -> spadePath
    Suit.CLUBS -> clubPath
}

val Suit.color: Color get() = if (isRed) CardRed else CardBlack

/** Draws [suit] centred on [center], filling a [size] x [size] box (optionally upside down, as on the lower half of a card). */
fun DrawScope.drawSuit(suit: Suit, center: Offset, size: Float, color: Color, upsideDown: Boolean = false) {
    withTransform({
        translate(center.x, center.y)
        if (upsideDown) rotate(180f, Offset.Zero)
        scale(size / 2f, size / 2f, Offset.Zero)
    }) {
        drawPath(pathFor(suit), color)
    }
}

/** A suit symbol as a composable, e.g. for labels and dialogs. */
@Composable
fun SuitIcon(suit: Suit, size: Dp, modifier: Modifier = Modifier, color: Color = suit.color) {
    Canvas(modifier.size(size)) {
        drawSuit(suit, Offset(this.size.width / 2, this.size.height / 2), this.size.minDimension * 0.9f, color)
    }
}
