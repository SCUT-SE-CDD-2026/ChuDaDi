package com.example.chudadi.ai.onnx

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.rule.CombinationType

/**
 * Encodes one legal action into RLCard ChuDaDi DMC action feature (140 dims):
 * - action bits (52)
 * - future hand bits (52)
 * - action type one-hot (10)
 * - action main rank one-hot (13)
 * - action kicker rank one-hot (13)
 */
class ActionFeatureEncoder {

    companion object {
        const val ACTION_FEATURE_DIM = 140
        private const val CARD_SPACE = 52
        private const val FUTURE_HAND_OFFSET = CARD_SPACE
        private const val ACTION_TYPE_OFFSET = CARD_SPACE * 2
        private const val ACTION_MAIN_RANK_OFFSET = ACTION_TYPE_OFFSET + 10
        private const val ACTION_KICKER_RANK_OFFSET = ACTION_MAIN_RANK_OFFSET + 13

        private const val ACTION_TYPE_NONE = 0
        private const val ACTION_TYPE_SINGLE = 1
        private const val ACTION_TYPE_PAIR = 2
        private const val ACTION_TYPE_TRIPLE = 3
        private const val ACTION_TYPE_STRAIGHT = 4
        private const val ACTION_TYPE_FLUSH = 5
        private const val ACTION_TYPE_FULL_HOUSE = 6
        private const val ACTION_TYPE_FOUR_BUCKET = 7
        private const val ACTION_TYPE_STRAIGHT_FLUSH = 8
        private const val ACTION_TYPE_FOUR_BOMB = 9
        private const val FOUR_OF_A_KIND_CARD_COUNT = 4
    }

    fun encodeActionFeature(
        handCards: List<Card>,
        actionCards: List<Card>,
        actionType: CombinationType?,
    ): FloatArray {
        val feature = FloatArray(ACTION_FEATURE_DIM)

        val handBits = FloatArray(CARD_SPACE)
        for (card in handCards) {
            val index = GameStateEncoder.cardToIndex(card)
            if (index in 0 until CARD_SPACE) handBits[index] = 1f
        }

        val actionBits = FloatArray(CARD_SPACE)
        for (card in actionCards) {
            val index = GameStateEncoder.cardToIndex(card)
            if (index in 0 until CARD_SPACE) actionBits[index] = 1f
        }

        // action(52)
        for (i in 0 until CARD_SPACE) {
            feature[i] = actionBits[i]
        }
        // future_hand(52) = clip(hand - action, 0, 1)
        for (i in 0 until CARD_SPACE) {
            feature[FUTURE_HAND_OFFSET + i] = if (handBits[i] - actionBits[i] > 0f) 1f else 0f
        }

        // action_type(10)
        val actionTypeIndex = actionTypeToIndex(actionType, actionCards.size)
        feature[ACTION_TYPE_OFFSET + actionTypeIndex] = 1f

        // action_main_rank(13), action_kicker_rank(13)
        val sortedCards = actionCards.sortedWith(Card.gameComparator)
        val (mainRankIndex, kickerRankIndex) = extractMainAndKickerRank(actionType, sortedCards)
        if (mainRankIndex != null) feature[ACTION_MAIN_RANK_OFFSET + mainRankIndex] = 1f
        if (kickerRankIndex != null) feature[ACTION_KICKER_RANK_OFFSET + kickerRankIndex] = 1f

        return feature
    }

    private fun actionTypeToIndex(actionType: CombinationType?, actionLength: Int): Int {
        if (actionType == null) return ACTION_TYPE_NONE // none/pass
        return when (actionType) {
            CombinationType.SINGLE -> ACTION_TYPE_SINGLE
            CombinationType.PAIR -> ACTION_TYPE_PAIR
            CombinationType.TRIPLE -> ACTION_TYPE_TRIPLE
            CombinationType.STRAIGHT -> ACTION_TYPE_STRAIGHT
            CombinationType.FLUSH -> ACTION_TYPE_FLUSH
            CombinationType.FULL_HOUSE -> ACTION_TYPE_FULL_HOUSE
            CombinationType.FOUR_WITH_ONE,
            CombinationType.FOUR_WITH_TWO,
            -> ACTION_TYPE_FOUR_BUCKET // four_of_a_kind bucket

            CombinationType.STRAIGHT_FLUSH -> ACTION_TYPE_STRAIGHT_FLUSH
            CombinationType.FOUR_OF_A_KIND_BOMB ->
                if (actionLength == FOUR_OF_A_KIND_CARD_COUNT) ACTION_TYPE_FOUR_BOMB
                else ACTION_TYPE_FOUR_BUCKET
        }
    }

    private fun extractMainAndKickerRank(
        actionType: CombinationType?,
        sortedCards: List<Card>,
    ): Pair<Int?, Int?> {
        if (sortedCards.isEmpty()) return null to null
        return when (actionType) {
            CombinationType.SINGLE,
            CombinationType.PAIR,
            CombinationType.TRIPLE,
            -> sortedCards.first().rank.ordinal to null

            CombinationType.STRAIGHT,
            CombinationType.FLUSH,
            CombinationType.STRAIGHT_FLUSH,
            -> {
                val main = sortedCards.last().rank.ordinal
                val kicker = sortedCards.getOrNull(sortedCards.lastIndex - 1)?.rank?.ordinal
                main to kicker
            }

            CombinationType.FULL_HOUSE -> {
                val rankGroups = sortedCards.groupBy { it.rank }.mapValues { it.value.size }
                val main = rankGroups.entries.firstOrNull { it.value == 3 }?.key?.ordinal
                val kicker = rankGroups.entries.firstOrNull { it.value == 2 }?.key?.ordinal
                main to kicker
            }

            CombinationType.FOUR_WITH_ONE,
            CombinationType.FOUR_OF_A_KIND_BOMB,
            CombinationType.FOUR_WITH_TWO,
            -> {
                val rankGroups = sortedCards.groupBy { it.rank }.mapValues { it.value.size }
                val main = rankGroups.entries.firstOrNull { it.value == 4 }?.key?.ordinal
                val kickerCandidates = rankGroups.entries
                    .filterNot { it.value == 4 }
                    .map { it.key.ordinal }
                main to kickerCandidates.maxOrNull()
            }

            null -> null to null
        }
    }
}
