package com.lughati3.spite.model

/** Every game in the collection. Add a new entry here (plus a screen in AppRoot and a guide) to register another game. */
enum class GameId(
    val title: String,
    val tagline: String,
    val players: String,
    /** The card shown as this game's icon (null rank = Joker). */
    val iconRank: Rank?,
    val iconSuit: Suit?,
    /** What a record's `score` means (lower is better), or null if the game has no score. */
    val scoreLabel: String? = null,
) {
    BANK("Bank", "Empty your Bank first — Jokers are wild", "1–2 players", null, null),
    SOLITAIRE("Solitaire", "Classic Klondike, draw 1 or 3", "1 player", Rank.ACE, Suit.HEARTS, "moves"),
    FREECELL("FreeCell", "Almost every deal can be won", "1 player", Rank.KING, Suit.DIAMONDS, "moves"),
    HEARTS("Hearts", "Dodge the hearts and the Queen of Spades", "You + 3 AI", Rank.QUEEN, Suit.SPADES, "points"),
    CRAZY_EIGHTS("Crazy Eights", "Match suit or rank — 8s are wild", "You vs AI", Rank.EIGHT, Suit.CLUBS),
    MEMORY("Memory", "Find the matching pairs in the fewest moves", "1 player", Rank.JACK, Suit.CLUBS, "moves"),
}
