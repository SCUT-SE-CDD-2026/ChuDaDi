package com.example.chudadi.ai.onnx

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * 游戏状态编码器（与 RLCard ChuDaDi 观测格式对齐）。
 *
 * 当前输出固定为 335 维，布局如下：
 * - 0..51：current_hand（当前手牌 one-hot，52）
 * - 52..103：last_action（上一手牌 one-hot，52）
 * - 104..113：action_type_one_hot（上一手牌型 one-hot，10）
 * - 114..127：action_length_one_hot（上一手牌张数 one-hot，14）
 * - 128..130：leader_relative_pos（领出者相对位置，3）
 * - 131..172：cards_left（三家剩余牌数 one-hot，42）
 * - 173..328：history（三家历史已出牌 one-hot，156）
 * - 329：is_leader（当前是否自己领出，1）
 * - 330..332：relative_pass_mask（三家是否已 pass，3）
 * - 333：is_next_warning（下家是否只剩 1 张，1）
 * - 334：is_northern_rule（是否北方规则，1）
 */
class GameStateEncoder {

    companion object {
        const val INPUT_DIM = 335
        const val TOTAL_CARDS = 52

        /**
         * 将牌映射到 0..51 索引。
         * 编码顺序：[D, C, H, S] x [3,4,5,6,7,8,9,10,J,Q,K,A,2]。
         */
        fun cardToIndex(card: Card): Int {
            val suitIndex = when (card.suit) {
                CardSuit.DIAMONDS -> 0
                CardSuit.CLUBS -> 1
                CardSuit.HEARTS -> 2
                CardSuit.SPADES -> 3
            }
            val rankIndex = card.rank.ordinal
            return suitIndex * 13 + rankIndex
        }

        /**
         * 将卡牌列表编码为52维 one-hot 向量
         */
        fun encodeCards(cards: List<Card>): FloatArray {
            val tensor = FloatArray(TOTAL_CARDS)
            for (card in cards) {
                val index = cardToIndex(card)
                if (index in 0 until TOTAL_CARDS) {
                    tensor[index] = 1f
                }
            }
            return tensor
        }
    }

    /**
     * 编码单个座位视角的观测向量（335 维）。
     *
     * @param match 当前对局状态
     * @param seatIndex 视角座位（0..3）
     */
    fun encode(match: Match, seatIndex: Int): FloatArray {
        val tensor = FloatArray(INPUT_DIM)
        val seat = match.seats.getOrNull(seatIndex) ?: return tensor

        var offset = 0
        val currentSeatPosition = seatIndex

        encodeHandCards(seat.hand, tensor, offset)
        offset += 52

        val lastAction = getLastAction(match)
        encodeLastAction(lastAction, tensor, offset)
        offset += 52

        encodeActionType(lastAction?.type, tensor, offset)
        offset += 10

        encodeActionLength(lastAction?.cards?.size ?: 0, tensor, offset)
        offset += 14

        val leaderSeat = getLeaderSeat(match)
        encodeLeaderRelativePosition(leaderSeat, currentSeatPosition, tensor, offset)
        offset += 3

        encodeCardsLeft(match.seats, currentSeatPosition, tensor, offset)
        offset += 42

        encodeHistory(match, currentSeatPosition, tensor, offset)
        offset += 156

        tensor[offset] = if (isLeader(match, currentSeatPosition)) 1f else 0f
        offset += 1

        encodePassMask(match, currentSeatPosition, tensor, offset)
        offset += 3

        tensor[offset] = if (isNextPlayerWarning(match, currentSeatPosition)) 1f else 0f
        offset += 1

        tensor[offset] = if (match.ruleSet == GameRuleSet.NORTHERN) 1f else 1f // 当前未实现南方（1表示北方，0表示南方）

        return tensor
    }

    private fun encodeHandCards(handCards: List<Card>, tensor: FloatArray, offset: Int) {
        for (card in handCards) {
            val index = cardToIndex(card)
            if (index in 0 until TOTAL_CARDS) {
                tensor[offset + index] = 1f
            }
        }
    }

    private fun getLastAction(match: Match): LastActionInfo? {
        val currentCombination = match.trickState.currentCombination ?: return null
        return LastActionInfo(
            cards = currentCombination.cards,
            type = currentCombination.type,
        )
    }

    private fun encodeLastAction(lastAction: LastActionInfo?, tensor: FloatArray, offset: Int) {
        if (lastAction == null) return

        for (card in lastAction.cards) {
            val index = cardToIndex(card)
            if (index in 0 until TOTAL_CARDS) {
                tensor[offset + index] = 1f
            }
        }
    }

    /**
     * 上一手牌型编码（10 维）：
     * none/single/pair/triple/straight/flush/full_house/four_of_a_kind/straight_flush/bomb
     */
    private fun encodeActionType(type: CombinationType?, tensor: FloatArray, offset: Int) {
        val index = when (type) {
            null -> 0
            CombinationType.SINGLE -> 1
            CombinationType.PAIR -> 2
            CombinationType.TRIPLE -> 3
            CombinationType.STRAIGHT -> 4
            CombinationType.FLUSH -> 5
            CombinationType.FULL_HOUSE -> 6
            CombinationType.FOUR_WITH_ONE,
            CombinationType.FOUR_WITH_TWO,
            -> 7
            CombinationType.STRAIGHT_FLUSH -> 8
            CombinationType.FOUR_OF_A_KIND_BOMB -> 9
        }
        tensor[offset + index] = 1f
    }

    private fun encodeActionLength(length: Int, tensor: FloatArray, offset: Int) {
        val clampedLength = length.coerceIn(0, 13)
        tensor[offset + clampedLength] = 1f
    }

    /**
     * 获取领出者座位索引
     */
    private fun getLeaderSeat(match: Match): Int? {
        return if (match.trickState.currentCombination == null) {
            match.activeSeatIndex
        } else {
            match.trickState.leadSeatIndex
        }
    }

    /**
     * 编码领出者相对位置 one-hot (3维)
     * [下家, 对家, 上家] - 自己领出则全0
     */
    private fun encodeLeaderRelativePosition(
        leaderSeat: Int?,
        currentSeat: Int,
        tensor: FloatArray,
        offset: Int,
    ) {
        if (leaderSeat == null || leaderSeat == currentSeat) return

        val relativePos = (leaderSeat - currentSeat + 4) % 4
        when (relativePos) {
            1 -> tensor[offset] = 1f      // 下家
            2 -> tensor[offset + 1] = 1f  // 对家
            3 -> tensor[offset + 2] = 1f  // 上家
        }
    }

    /**
     * 编码三家剩牌数 one-hot (42维 = 3 * 14)
     * 顺序: 下家(14), 对家(14), 上家(14)
     */
    private fun encodeCardsLeft(
        seats: List<Seat>,
        currentSeat: Int,
        tensor: FloatArray,
        offset: Int,
    ) {
        for ((i, relPos) in listOf(1, 2, 3).withIndex()) {
            val seatIndex = (currentSeat + relPos) % 4
            val seat = seats.getOrNull(seatIndex) ?: continue
            val cardCount = seat.hand.size.coerceIn(0, 13)
            tensor[offset + i * 14 + cardCount] = 1f
        }
    }

    /**
     * 历史已出牌编码（每家 52 维 one-hot）。
     * 读取的是累计历史 playedCardHistory，不是当前轮次 tablePlays。
     */
    private fun encodeHistory(
        match: Match,
        currentSeat: Int,
        tensor: FloatArray,
        offset: Int,
    ) {
        for ((i, relPos) in listOf(1, 2, 3).withIndex()) {
            val seatIndex = (currentSeat + relPos) % 4
            val playedCards = match.trickState.playedCardHistory[seatIndex].orEmpty()
            for (card in playedCards) {
                val cardIndex = cardToIndex(card)
                if (cardIndex in 0 until TOTAL_CARDS) {
                    tensor[offset + i * 52 + cardIndex] = 1f
                }
            }
        }
    }

    private fun isLeader(match: Match, currentSeat: Int): Boolean {
        return match.trickState.currentCombination == null ||
            match.trickState.leadSeatIndex == currentSeat
    }

    /**
     * 编码三家pass状态 (3维)
     * 顺序: 下家, 对家, 上家
     *
     * 根据 passCount 和当前轮次状态判断
     */
    private fun encodePassMask(
        match: Match,
        currentSeat: Int,
        tensor: FloatArray,
        offset: Int,
    ) {
        for ((i, relPos) in listOf(1, 2, 3).withIndex()) {
            val seatIndex = (currentSeat + relPos) % 4
            val seat = match.seats.getOrNull(seatIndex)
            if (seat?.status == SeatStatus.PASSED) {
                tensor[offset + i] = 1f
            }
        }
    }

    /**
     * 判断下家是否只剩1张牌
     */
    private fun isNextPlayerWarning(match: Match, currentSeat: Int): Boolean {
        val nextSeat = (currentSeat + 1) % 4
        val nextPlayer = match.seats.getOrNull(nextSeat)
        return nextPlayer?.hand?.size == 1
    }

    fun getInputDim(): Int = INPUT_DIM

    private data class LastActionInfo(
        val cards: List<Card>,
        val type: CombinationType?,
    )
}
