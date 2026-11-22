package com.lughati3.spite.ui.help

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.GameId
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.theme.Gold

/** Full-screen "How to play" page for one game. */
@Composable
fun GuideScreen(game: GameId, onClose: () -> Unit) {
    val guide = guideFor(game)
    Box(Modifier.fillMaxSize().background(Color(0xF7082B18)).pointerInput(Unit) {}.safeDrawingPadding()) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayingCard(Card(game.ordinal, game.iconRank, game.iconSuit), width = 44.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("How to play", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(game.title, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                ) { Text("Close", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text(guide.summary, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp)
                }
                items(guide.sections) { section ->
                    Column(
                        Modifier.fillMaxWidth().background(Color(0x55000000), RoundedCornerShape(14.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(section.title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        section.lines.forEach { line ->
                            val numbered = line.length > 2 && line[0].isDigit() && line[1] == '.'
                            Row {
                                if (!numbered) Text("•  ", color = Gold, fontSize = 15.sp)
                                Text(line, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 21.sp)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
