package com.lughati3.spite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.lughati3.spite.games.bank.BankScreen
import com.lughati3.spite.games.crazyeights.CrazyEightsScreen
import com.lughati3.spite.games.freecell.FreeCellScreen
import com.lughati3.spite.games.hearts.HeartsScreen
import com.lughati3.spite.games.klondike.KlondikeScreen
import com.lughati3.spite.model.GameId
import com.lughati3.spite.games.memory.MemoryScreen
import com.lughati3.spite.ui.help.GuideScreen
import com.lughati3.spite.ui.history.HistoryScreen
import com.lughati3.spite.ui.hub.HubScreen

/**
 * Tiny navigator: null = the hub, otherwise the open game. Each game keeps its own ViewModel, so
 * going back to the hub and reopening a game resumes it (use the game's New button to restart).
 */
@Composable
fun AppRoot() {
    var current by rememberSaveable { mutableStateOf<GameId?>(null) }
    // Overlays sit on top of whatever is open, so a game in progress is never lost when you read the rules.
    var guide by rememberSaveable { mutableStateOf<GameId?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val home = { current = null }
    val help = { current?.let { guide = it } ; Unit }

    BackHandler(enabled = guide != null || showHistory || current != null) {
        when {
            guide != null -> guide = null
            showHistory -> showHistory = false
            else -> current = null
        }
    }

    when (current) {
        null -> HubScreen(onOpen = { current = it }, onGuide = { guide = it }, onHistory = { showHistory = true })
        GameId.BANK -> BankScreen(onHome = home, onHelp = help)
        GameId.SOLITAIRE -> KlondikeScreen(onHome = home, onHelp = help)
        GameId.FREECELL -> FreeCellScreen(onHome = home, onHelp = help)
        GameId.HEARTS -> HeartsScreen(onHome = home, onHelp = help)
        GameId.CRAZY_EIGHTS -> CrazyEightsScreen(onHome = home, onHelp = help)
        GameId.MEMORY -> MemoryScreen(onHome = home, onHelp = help)
    }

    guide?.let { GuideScreen(it, onClose = { guide = null }) }
    if (showHistory) HistoryScreen(onClose = { showHistory = false })
}
