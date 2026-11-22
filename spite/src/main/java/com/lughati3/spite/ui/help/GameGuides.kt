package com.lughati3.spite.ui.help

import com.lughati3.spite.model.GameId

/** One headed block of a how-to-play page. Each string in [lines] is rendered as a bullet. */
data class GuideSection(val title: String, val lines: List<String>)

data class Guide(val summary: String, val sections: List<GuideSection>)

fun guideFor(game: GameId): Guide = when (game) {
    GameId.BANK -> BANK
    GameId.SOLITAIRE -> SOLITAIRE
    GameId.FREECELL -> FREECELL
    GameId.HEARTS -> HEARTS
    GameId.CRAZY_EIGHTS -> CRAZY_EIGHTS
    GameId.MEMORY -> MEMORY
}

private val BANK = Guide(
    summary = "A race to empty your Bank — a Spite & Malice / Skip-Bo style game with Jokers and the Taki (تكي) rule.",
    sections = listOf(
        GuideSection("Goal", listOf(
            "Be the first player to play the last card of your Bank (a stack of 10 cards).",
            "The game ends the instant a player plays their final Bank card.",
        )),
        GuideSection("The cards", listOf(
            "108 cards: two standard decks plus 4 Jokers.",
            "A Joker is wild: it can be played anywhere as any card from Ace to King. It can start a pile (as an Ace), continue a pile (as the next rank) or finish a pile (as a King). Jokers on a centre pile show the rank they stand for, e.g. “JOKER (5)”.",
        )),
        GuideSection("Your areas", listOf(
            "Hand — 5 cards, private to you (the AI's hand is face down).",
            "Ground — 5 face-up slots in front of you; each slot already holds one card at the start. Each slot is a stack and only its top card can be played.",
            "Bank — 10 cards; only the top card is face up. When it is played the next card is revealed.",
            "Centre piles — up to 4 shared piles that both players build from Ace up to King, in any suit.",
            "Draw pile — the face-down deck. When it runs out, the finished (King) piles are shuffled back into it.",
        )),
        GuideSection("Your turn", listOf(
            "1. You automatically draw until you hold 5 cards.",
            "2. Play as many cards as you like onto the centre piles. A pile starts with an Ace (or a Joker) and goes up one rank at a time: A, 2, 3 … K. When a pile reaches King it is cleared.",
            "3. You may play from your hand, from the top of any ground slot, or from the top of your Bank.",
            "4. Empty hand: if you use up all the cards in your hand before you Taki, you immediately draw 5 new cards and keep playing — your turn does not end.",
        )),
        GuideSection("Stopping your turn: fill, then Taki (تكي)", listOf(
            "You can't end your turn while any of your 5 ground slots is empty. First put cards from your hand into the empty slots (this does not end your turn).",
            "If you run out of hand cards while filling, you draw 5 more at once and carry on, exactly as above.",
            "Once all 5 ground slots hold a card, you end your turn with a Taki: place one card from your hand on top of any ground slot. It covers the card beneath it, which is blocked until the top card is played away.",
            "As soon as the Taki card is down, the turn passes to your opponent.",
        )),
        GuideSection("How to play it on screen", listOf(
            "Tap a card (hand, ground top or Bank top). Centre piles that accept it light up green; tap one to play it.",
            "To stop, press the amber button. With empty ground slots it says “Fill ground”: tap a hand card, then an empty slot. When the ground is full it says “End Turn · Taki”: tap a hand card, then the slot to cover.",
            "Press the button again to cancel and go back to playing.",
            "2-player mode: the device is passed between turns; a cover screen hides each player's hand until they tap “I'm ready”.",
        )),
        GuideSection("Tips", listOf(
            "Bank cards are your goal — build piles so that your Bank's top card becomes playable.",
            "Cover high or useless cards when you Taki, and keep low cards (Aces, 2s, 3s) on top where you can reach them.",
            "Save Jokers for unlocking a Bank card or chaining several plays. The AI does the same.",
            "Don't cover a card you need soon; a covered card can't be used until the card on top is gone.",
        )),
    ),
)

private val SOLITAIRE = Guide(
    summary = "Klondike — the classic solitaire. Build the four suits up from Ace to King.",
    sections = listOf(
        GuideSection("Goal", listOf(
            "Move all 52 cards to the four foundation piles at the top right, one pile per suit, in order from Ace to King.",
        )),
        GuideSection("The layout", listOf(
            "Tableau — 7 columns. Column 1 starts with 1 card, column 2 with 2 … column 7 with 7. Only the top card of each column starts face up.",
            "Stock — the face-down draw pile (top left). Waste — the cards you have turned over from the stock.",
            "Foundations — four piles, one per suit (♠ ♥ ♦ ♣), built Ace, 2, 3 … King.",
        )),
        GuideSection("Rules", listOf(
            "On the tableau, build downwards in alternating colours: a red 6 goes on a black 7, a black 6 goes on a red 7.",
            "You can move a card, or a whole face-up run of cards, from one column to another if the bottom card of the run fits.",
            "Only a King (with its run) can be moved into an empty column.",
            "When you move the last face-up card from a column, the card underneath is turned face up.",
            "Tap the stock to turn over 1 card (or 3 in Draw 3 mode) onto the waste; the top waste card can be played. When the stock is empty, tap it to turn the waste over and go through it again.",
            "A card can move back from a foundation to the tableau if that helps you.",
        )),
        GuideSection("Controls", listOf(
            "Drag a card (or a run) and drop it on a column or foundation. Illegal drops snap back.",
            "Or tap a card, then tap where it should go; possible targets are highlighted. Tap a selected card again to send it to its foundation.",
            "Undo takes back your last move (as many times as you like). “Auto ▲” sends every card that can go up to the foundations. “Draw 1 / Draw 3” switches the difficulty (and starts a new game). “New” deals again.",
        )),
        GuideSection("Tips", listOf(
            "Uncover face-down cards first — they hold the cards you need.",
            "Don't rush Aces and 2s to the foundations if you need them as places for other cards; but move them up when nothing needs them.",
            "Try to empty a column early so a King can move in.",
            "Not every Klondike deal can be won, especially in Draw 3 mode.",
        )),
    ),
)

private val FREECELL = Guide(
    summary = "FreeCell — all 52 cards are dealt face up, so skill decides almost every game.",
    sections = listOf(
        GuideSection("Goal", listOf(
            "Move all 52 cards to the four foundation piles (one per suit), in order from Ace to King.",
        )),
        GuideSection("The layout", listOf(
            "Eight cascades (columns) holding all the cards face up: the first four have 7 cards, the last four have 6.",
            "Four free cells (top left): each can hold exactly one card as a temporary parking spot.",
            "Four foundations (top right): one pile per suit, built Ace, 2, 3 … King.",
        )),
        GuideSection("Rules", listOf(
            "Only the bottom (available) card of a cascade can be moved on its own.",
            "On a cascade, build downwards in alternating colours: a red 6 goes on a black 7.",
            "A free cell holds one card. Park a card there when you need to reach the one under it, and move it back later.",
            "You may move a run of cards (descending, alternating colours) in one go — but only as many as your empty free cells and empty columns allow: (empty free cells + 1) × 2 for each empty column. Moving to an empty column does not count that column.",
            "Any card can be moved into an empty column.",
        )),
        GuideSection("Controls", listOf(
            "Tap a card, then tap its destination (a cascade, a free cell or a foundation). Valid targets are highlighted.",
            "Tap a selected card again to send it to a foundation, or — if it can't go up — to the first free cell.",
            "Undo takes back moves. “Auto ▲” sends every card that can go up to the foundations. “New” deals a fresh game.",
        )),
        GuideSection("Tips", listOf(
            "Keep free cells empty as long as you can — they are your most valuable resource.",
            "Make empty columns; they are even more powerful than free cells.",
            "Look at where the Aces and 2s are buried and dig for them early.",
            "Think a few moves ahead before parking a card: a full set of free cells usually means you are stuck.",
        )),
    ),
)

private val HEARTS = Guide(
    summary = "Hearts — a trick-taking game for four where you want to take as few points as possible.",
    sections = listOf(
        GuideSection("Goal", listOf(
            "Finish with the lowest score. You play against three AI players (West, North and East).",
            "The game ends when any player reaches 100 points; the player with the fewest points wins.",
        )),
        GuideSection("Points", listOf(
            "Every heart you take in a trick is worth 1 point.",
            "The Queen of Spades (♠Q) is worth 13 points.",
            "That makes 26 points in every round. Taking nothing is a perfect round.",
            "Shooting the moon: if one player takes all 13 hearts AND the Queen of Spades, that player scores 0 and everyone else scores 26.",
        )),
        GuideSection("A round", listOf(
            "Each player is dealt 13 cards. Before play, in rounds 1–3 everyone passes 3 cards: left (round 1), right (round 2), across (round 3). Round 4 has no passing, then the cycle repeats.",
            "Pick 3 cards (usually your highest or most dangerous ones: Queen of Spades, high spades, high hearts) and press “Pass”.",
            "The player holding the 2 of clubs leads the first trick with it.",
        )),
        GuideSection("Playing tricks", listOf(
            "Players take turns clockwise, each playing one card. You must follow the suit that was led if you can.",
            "If you can't follow suit you may play anything — a great time to get rid of hearts or the Queen of Spades.",
            "The highest card of the suit that was led wins the trick (Ace is high). The winner takes the cards and leads the next trick.",
            "No points may be played on the very first trick unless you have nothing else.",
            "You can't lead a heart until hearts have been broken, meaning a heart has been played in an earlier trick — unless you hold only hearts.",
        )),
        GuideSection("On screen", listOf(
            "Tap a card to lift it, tap it again to play it. Cards you can't legally play right now are dimmed, and a message tells you why if you try.",
            "The scoreboard at the top shows each player's total and the points taken so far this round; the gold chip marks whose turn it is.",
            "After each round a table shows the round and total scores.",
        )),
        GuideSection("Tips", listOf(
            "Get rid of the Queen of Spades, or pass it on, unless you hold plenty of low spades to protect yourself.",
            "Try to “duck”: play a card lower than the current winner so someone else takes the points.",
            "Void a suit early (play it out) so you can discard hearts on others' tricks later.",
            "Watch the scores: if someone has taken lots of points, they may be going for the moon.",
        )),
    ),
)

private val CRAZY_EIGHTS = Guide(
    summary = "Crazy Eights — get rid of all your cards before the AI does.",
    sections = listOf(
        GuideSection("Goal", listOf("Be the first to play every card in your hand.")),
        GuideSection("Setup", listOf(
            "A standard 52-card deck. You and the AI get 7 cards each. One card is turned face up to start the discard pile (never an 8).",
        )),
        GuideSection("Your turn", listOf(
            "Play a card that matches the top card of the discard pile by suit or by rank. For example, on the 7♥ you can play any heart or any 7.",
            "8s are wild: you can play an 8 on any card, and you then choose which suit must be played next. The chosen suit is shown beside the discard pile.",
            "If you have no playable card you must draw: tap the draw pile. You can draw several times until you get a playable card. You can't draw while you hold a playable card.",
            "If the draw pile runs out, the discard pile (except its top card) is shuffled to make a new one. If there are no cards left to draw, you pass.",
        )),
        GuideSection("On screen", listOf(
            "Tap a card in your hand to play it. Cards you can't play are dimmed.",
            "If you play an 8, a dialog asks you to choose a suit.",
            "A message under the table says what the AI just did.",
        )),
        GuideSection("Tips", listOf(
            "Keep 8s for emergencies — they are your only guaranteed play.",
            "Choose the suit you hold the most of when you play an 8.",
            "Play off your high-numbered cards and your longest suits first.",
        )),
    ),
)

private val MEMORY = Guide(
    summary = "Memory — a concentration game. Flip cards two at a time and find the matching pairs.",
    sections = listOf(
        GuideSection("Goal", listOf(
            "Find every matching pair using as few moves — and as little time — as you can.",
        )),
        GuideSection("Rules", listOf(
            "All cards start face down. On each move you flip two cards.",
            "Two cards match when they have the same rank and the same colour, e.g. the 7♥ and the 7♦ (both red 7s) or the K♠ and the K♣ (both black kings).",
            "If they match, they stay face up (outlined in gold). If not, they turn back over after a moment — remember where they were!",
            "The game ends when every pair has been found.",
        )),
        GuideSection("Levels", listOf(
            "Easy — 8 pairs (16 cards).",
            "Medium — 12 pairs (24 cards).",
            "Hard — 18 pairs (36 cards).",
            "Tap a level to start a new game at that size. Your best result (fewest moves) is kept separately for each level in History.",
        )),
        GuideSection("Controls", listOf(
            "Tap a card to flip it. The counters at the top show your moves, the pairs found and the elapsed time (it starts at your first flip).",
        )),
        GuideSection("Tips", listOf(
            "A perfect game on Easy takes 8 moves, but that needs luck. Aim to remember at least the cards you've already seen.",
            "Work through the board in a systematic order for your first flips so you build a map in your head.",
            "When you see the first card of a pair you already know, flip it first, then its partner.",
        )),
    ),
)
