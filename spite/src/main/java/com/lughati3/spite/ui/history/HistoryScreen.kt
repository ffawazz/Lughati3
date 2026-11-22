package com.lughati3.spite.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lughati3.spite.history.GameRecord
import com.lughati3.spite.history.GameStats
import com.lughati3.spite.history.History
import com.lughati3.spite.history.Outcome
import com.lughati3.spite.history.statsFor
import com.lughati3.spite.model.Card
import com.lughati3.spite.model.GameId
import com.lughati3.spite.ui.components.PlayingCard
import com.lughati3.spite.ui.components.SmallButton
import com.lughati3.spite.ui.theme.FeltBrush
import com.lughati3.spite.ui.theme.Gold
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onClose: () -> Unit) {
    val records by History.records.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<GameId?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = records.filter { filter == null || it.game == filter }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Box(Modifier.fillMaxSize().background(FeltBrush).pointerInput(Unit) {}.safeDrawingPadding()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 860.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("History", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, modifier = Modifier.weight(1f))
                if (records.isNotEmpty()) {
                    SmallButton("Clear", { confirmClear = true })
                    Spacer(Modifier.padding(horizontal = 4.dp))
                }
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip("All games", filter == null) { filter = null } }
                items(GameId.entries.toList()) { g -> FilterChip(g.title, filter == g) { filter = g } }
            }
            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val games = if (filter == null) GameId.entries.toList() else listOf(filter!!)
                items(games) { g -> StatsCard(records.statsFor(g)) }
                item {
                    Text("Recent games", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                }
                if (shown.isEmpty()) {
                    item { Text("Nothing here yet — finish a game and it will show up.", color = Color.White.copy(alpha = 0.7f)) }
                }
                items(shown) { r -> RecordRow(r, fmt.format(Date(r.timeMillis))) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This removes every saved result and best score. It can't be undone.") },
            confirmButton = { TextButton(onClick = { History.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = if (selected) Color.Black else Color.White,
        modifier = Modifier
            .background(if (selected) Gold else Color(0x66000000), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun StatsCard(s: GameStats) {
    Row(
        Modifier.fillMaxWidth().background(Color(0x66000000), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayingCard(Card(s.game.ordinal, s.game.iconRank, s.game.iconSuit), width = 40.dp)
        Column(Modifier.padding(start = 14.dp)) {
            Text(s.game.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (s.played == 0) {
                Text("Not played yet", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            } else {
                val rate = s.winRate?.let { " · $it% wins" } ?: ""
                Text("Played ${s.played} · Won ${s.wins} · Lost ${s.losses}$rate", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                if (s.bestByVariant.isNotEmpty() && s.game.scoreLabel != null) {
                    val best = s.bestByVariant.entries.sortedBy { it.key ?: "" }.joinToString("   ") { (v, score) -> (v?.let { "$it: " } ?: "") + "$score" }
                    Text("Best (${s.game.scoreLabel}, lower is better): $best", color = Gold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun RecordRow(r: GameRecord, whenText: String) {
    val (label, color) = when (r.outcome) {
        Outcome.WIN -> "WIN" to Color(0xFF43A047)
        Outcome.LOSS -> "LOSS" to Color(0xFFE53935)
        Outcome.PLAYED -> "PLAYED" to Color(0xFF78909C)
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0x33000000), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("${r.game.title} — ${r.detail}", color = Color.White, fontSize = 14.sp)
            Text(whenText, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}
