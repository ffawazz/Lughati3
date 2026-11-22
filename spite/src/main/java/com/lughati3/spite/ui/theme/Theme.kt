package com.lughati3.spite.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val FeltLight = Color(0xFF1F7A45)
val FeltDark = Color(0xFF0B3D22)
val Gold = Color(0xFFFFD54F)
val TargetGreen = Color(0xFF69F0AE)
val TakiAmber = Color(0xFFFFAB40)
val CardRed = Color(0xFFC62828)
val CardBlack = Color(0xFF212121)

val FeltBrush = Brush.radialGradient(listOf(FeltLight, FeltDark))

@Composable
fun TakiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Gold, onPrimary = Color.Black, surface = FeltDark),
    ) {
        Box(Modifier.fillMaxSize().background(FeltBrush)) { content() }
    }
}
