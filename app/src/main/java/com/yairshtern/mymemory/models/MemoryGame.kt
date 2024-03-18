package com.yairshtern.mymemory.models

import com.yairshtern.mymemory.utils.DEFAULT_ICONS

class MemoryGame(private val boardSize: BoardSize, private val customImages: List<String>?) {

    val cards: List<MemoryCard>
    var numPairsFound = 0

    private var numCardFlips = 0
    private var indexOfSelectedCard: Int? = null

    init {
        if (customImages == null) {
            val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            val randomizedImages = (chosenImages + chosenImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it) }
        } else {
            val randomizedImages = (customImages + customImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it.hashCode(),it) }
        }
    }

    fun flipCard(position: Int): Boolean {
        numCardFlips++
        val card = cards[position]
        // Three cases:
        // 0 or 2 cards previously flipped over => restore cards + flip over the selected card
        // 1 cards previously flipped over => flip over selected card + check if the images match
        var foundMatch = false
        if (indexOfSelectedCard == null) {
            // 0 or 2 cards flipped over
            restoreCards()
            indexOfSelectedCard = position
        } else {
            // exactly 1 card previously flipped over
            foundMatch = checkForMatch(indexOfSelectedCard!!, position)
            indexOfSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier) {
            return false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++
        return true
    }

    private fun restoreCards() {
        for (card in cards) {
            if (!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numCardFlips / 2
    }
}