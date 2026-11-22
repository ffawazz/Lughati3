package com.lughati3.spite.model

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠", false),
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    CLUBS("♣", false),
}

enum class Rank(val value: Int, val label: String) {
    ACE(1, "A"), TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"), SIX(6, "6"),
    SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"), TEN(10, "10"),
    JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K");

    companion object {
        fun fromValue(value: Int): Rank = entries[value - 1]
    }
}

/**
 * A physical card. [id] makes the 2 identical decks distinguishable (stable keys, equality).
 * A Joker has no rank and no suit.
 */
data class Card(val id: Int, val rank: Rank?, val suit: Suit?) {
    val isJoker: Boolean get() = rank == null

    /** Short human-readable name, e.g. "7♥" or "Joker". */
    val label: String get() = if (rank == null) "Joker" else rank.label + suit!!.symbol
}

object Deck {
    const val SIZE = 108

    /** 2 standard decks + 4 Jokers = 108 cards, in a fixed (unshuffled) order. */
    fun create(): List<Card> {
        var id = 0
        val cards = ArrayList<Card>(SIZE)
        repeat(2) {
            for (suit in Suit.entries) for (rank in Rank.entries) cards += Card(id++, rank, suit)
        }
        repeat(4) { cards += Card(id++, null, null) }
        return cards
    }
}
