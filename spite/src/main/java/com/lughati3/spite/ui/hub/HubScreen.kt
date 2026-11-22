package com.lughati3.spite.ui.hub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lughati3.spite.R
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.GameId
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.theme.Gold

@Composable
fun HubScreen(onOpen: (GameId) -> Unit, onGuide: (GameId) -> Unit, onHistory: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Logo()
        Spacer(Modifier.height(8.dp))
        Text("Card Games", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
        Text("Pick a game", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "History & best scores",
            color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier
                .background(Gold, RoundedCornerShape(50))
                .clickable(onClick = onHistory)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.widthIn(max = 940.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(GameId.entries.toList()) { game -> GameTile(game, onClick = { onOpen(game) }, onGuide = { onGuide(game) }) }
        }
    }
}

/** The app logo (same artwork as the launcher icon) on its felt disc. */
@Composable
fun Logo(size: androidx.compose.ui.unit.Dp = 128.dp) {
    Box(
        Modifier
            .size(size)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2E9A5B), Color(0xFF0A3A20))))
            .border(2.dp, Gold.copy(alpha = 0.8f), CircleShape),
    ) {
        Image(
            painterResource(R.drawable.logo_cards), contentDescription = "Card Games logo",
            modifier = Modifier.size(size).scale(1.3f), // the artwork has launcher-safe-zone padding
        )
    }
}

@Composable
private fun GameTile(game: GameId, onClick: () -> Unit, onGuide: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(136.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xCC0B2A1A), Color(0xCC15472B))))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayingCard(Card(game.ordinal, game.iconRank, game.iconSuit), width = 60.dp)
        Column(Modifier.padding(start = 16.dp)) {
            Text(game.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(game.tagline, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 2)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(game.players, color = Gold, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                Text(
                    "How to play",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .background(Color(0x33FFFFFF), RoundedCornerShape(50))
                        .clickable(onClick = onGuide)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
