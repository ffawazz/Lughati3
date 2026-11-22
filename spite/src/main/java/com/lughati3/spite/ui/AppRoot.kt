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
import com.lughati3.spite.ui.hub.GameId
import com.lughati3.spite.ui.hub.HubScreen

/**
 * Tiny navigator: null = the hub, otherwise the open game. Each game keeps its own ViewModel, so
 * going back to the hub and reopening a game resumes it (use the game's New button to restart).
 */
@Composable
fun AppRoot() {
    var current by rememberSaveable { mutableStateOf<GameId?>(null) }
    val home = { current = null }

    BackHandler(enabled = current != null, onBack = home)

    when (current) {
        null -> HubScreen(onOpen = { current = it })
        GameId.BANK -> BankScreen(onHome = home)
        GameId.SOLITAIRE -> KlondikeScreen(onHome = home)
        GameId.FREECELL -> FreeCellScreen(onHome = home)
        GameId.HEARTS -> HeartsScreen(onHome = home)
        GameId.CRAZY_EIGHTS -> CrazyEightsScreen(onHome = home)
    }
}
