package com.lughati3.spite.games.bank

import com.lughati3.spite.model.Card
import com.lughati3.spite.model.Rank

/** Where a card the current player wants to play comes from. */
sealed interface CardSource {
    data class Hand(val index: Int) : CardSource
    data class Ground(val slot: Int) : CardSource
    data object Bank : CardSource
}

/**
 * Everything one player owns.
 *  - [hand]: private cards.
 *  - [ground]: 5 slots; each is a stack, bottom -> top (last element is the only playable card).
 *  - [bank]: the 10-card goal stack; the last element is the face-up top card.
 */
data class PlayerState(
    val name: String,
    val isHuman: Boolean,
    val hand: List<Card>,
    val ground: List<List<Card>>,
    val bank: List<Card>,
) {
    val bankTop: Card? get() = bank.lastOrNull()
    val hasEmptyGroundSlot: Boolean get() = ground.any { it.isEmpty() }

    fun groundTop(slot: Int): Card? = ground.getOrNull(slot)?.lastOrNull()

    fun cardAt(source: CardSource): Card? = when (source) {
        is CardSource.Hand -> hand.getOrNull(source.index)
        is CardSource.Ground -> groundTop(source.slot)
        CardSource.Bank -> bankTop
    }

    /** This player after the card at [source] has left its zone. */
    fun without(source: CardSource): PlayerState = when (source) {
        is CardSource.Hand -> copy(hand = hand.filterIndexed { i, _ -> i != source.index })
        is CardSource.Ground -> copy(
            ground = ground.mapIndexed { i, stack -> if (i == source.slot) stack.dropLast(1) else stack },
        )
        CardSource.Bank -> copy(bank = bank.dropLast(1))
    }
}
