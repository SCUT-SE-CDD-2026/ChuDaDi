package com.example.chudadi.ai.onnx

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match

/** Encodes three relative players' ordered non-pass play history as [3, historyLen, 52]. */
class PlayedHistorySequenceEncoder(
    private val historyLen: Int = DEFAULT_HISTORY_LEN,
) {
    fun encode(match: Match, seatIndex: Int): FloatArray {
        val tensor = FloatArray(RELATIVE_PLAYER_COUNT * historyLen * GameStateEncoder.TOTAL_CARDS)
        for (relativeIndex in 0 until RELATIVE_PLAYER_COUNT) {
            val relativeSeatId = (seatIndex + relativeIndex + 1) % PLAYER_COUNT
            val history = match.trickState.playedActionHistory[relativeSeatId].orEmpty()
            val start = (historyLen - history.size).coerceAtLeast(0)
            val trimmed = if (history.size > historyLen) history.takeLast(historyLen) else history
            for ((stepIndex, cards) in trimmed.withIndex()) {
                encodeCards(
                    cards = cards,
                    tensor = tensor,
                    base = ((relativeIndex * historyLen) + start + stepIndex) * GameStateEncoder.TOTAL_CARDS,
                )
            }
        }
        return tensor
    }

    private fun encodeCards(cards: List<Card>, tensor: FloatArray, base: Int) {
        for (card in cards) {
            val index = GameStateEncoder.cardToIndex(card)
            if (index in 0 until GameStateEncoder.TOTAL_CARDS) {
                tensor[base + index] = 1f
            }
        }
    }

    companion object {
        const val DEFAULT_HISTORY_LEN = 13
        const val RELATIVE_PLAYER_COUNT = 3
        private const val PLAYER_COUNT = 4
    }
}
