package com.lughati3.spite.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.lughati3.spite.model.Card
import com.lughati3.spite.ui.theme.Gold

/** A card that flips around its vertical axis between its back and [card]'s face. */
@Composable
fun FlipCard(
    card: Card,
    faceUp: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    matched: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(if (faceUp) 180f else 0f, tween(280), label = "flip")
    val density = LocalDensity.current.density
    Box(
        modifier
            .size(width, width * CARD_ASPECT)
            .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density },
    ) {
        if (rotation <= 90f) {
            PlayingCard(null, width, faceDown = true, onClick = onClick)
        } else {
            // the face is drawn mirrored by the 180° parent rotation, so flip it back
            Box(Modifier.graphicsLayer { rotationY = 180f }) {
                PlayingCard(card, width, highlighted = matched, highlightColor = Gold, onClick = onClick)
            }
        }
    }
}
