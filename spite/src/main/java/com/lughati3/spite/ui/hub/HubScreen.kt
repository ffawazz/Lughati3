package com.lughati3.spite.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lughati3.spite.ui.theme.Gold

/** Every game in the collection. Add a new entry here (and in AppRoot) to register another game. */
enum class GameId(val title: String, val tagline: String, val players: String, val symbol: String, val red: Boolean) {
    BANK("Bank", "Empty your Bank first — Jokers are wild", "1–2 players", "♠", false),
    SOLITAIRE("Solitaire", "Classic Klondike, draw 1 or 3", "1 player", "♥", true),
    FREECELL("FreeCell", "Almost every deal can be won", "1 player", "♦", true),
    HEARTS("Hearts", "Dodge the hearts and the Queen of Spades", "You + 3 AI", "♥", true),
    CRAZY_EIGHTS("Crazy Eights", "Match suit or rank — 8s are wild", "You vs AI", "♣", false),
}

@Composable
fun HubScreen(onOpen: (GameId) -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Card Games", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
        Text("Pick a game", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(GameId.entries.toList()) { game -> GameTile(game) { onOpen(game) } }
        }
    }
}

@Composable
private fun GameTile(game: GameId, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x66000000), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            game.symbol, fontSize = 44.sp,
            color = if (game.red) Color(0xFFEF5350) else Color.White,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column {
            Text(game.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(game.tagline, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Text(game.players, color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
