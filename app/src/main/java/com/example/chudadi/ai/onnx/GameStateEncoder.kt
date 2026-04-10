package com.example.chudadi.ai.onnx

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * 游戏状态编码器
 *
 * 将游戏状态转换为ONNX模型可接受的FloatArray输入张量。
 * 遵循RLCard ChuDaDi环境的332维状态表示规范。
 *
 * 状态向量组成（332维）：
 * - 0-51: current_hand (52) - 当前手牌 one-hot
 * - 52-103: last_action (52) - 上一手牌 one-hot
 * - 104-111: action_type_one_hot (8) - 上一手牌型
 * - 112-125: action_length_one_hot (14) - 上一手牌张数 (0..13)
 * - 126-128: leader_relative_pos (3) - 领出者相对位置
 * - 129-170: cards_left (42) - 三家剩牌数 one-hot (每人14维)
 * - 171-326: history (156) - 三家已出牌 one-hot (每人52维)
 * - 327: is_leader (1) - 是否自己领出
 * - 328-330: relative_pass_mask (3) - 三家是否已pass
 * - 331: is_next_warning (1) - 下家是否只剩1张
 */
class GameStateEncoder {

    companion object {
        const val INPUT_DIM = 332
        const val TOTAL_CARDS = 52

        /**
         * 将卡牌转换为索引 (0-51)
         * 编码顺序: [D, C, H, S] x [3, 4, 5, 6, 7, 8, 9, 10, J, Q, K, A, 2]
         */
        fun cardToIndex(card: Card): Int {
            val suitIndex = when (card.suit) {
                CardSuit.DIAMONDS -> 0
                CardSuit.CLUBS -> 1
                CardSuit.HEARTS -> 2
                CardSuit.SPADES -> 3
            }
            val rankIndex = card.rank.ordinal // THREE=0, FOUR=1, ..., TWO=12
            return suitIndex * 13 + rankIndex
        }

        /**
         * 将卡牌列表编码为52维 one-hot 向量
         */
        fun encodeCards(cards: List<Card>): FloatArray {
            val tensor = FloatArray(52) { 0f }
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
     * 将游戏状态编码为FloatArray
     *
     * @param match 当前游戏状态
     * @param seatIndex AI玩家座位索引 (0-3)
     * @return 编码后的FloatArray (332维)
     */
    fun encode(match: Match, seatIndex: Int): FloatArray {
        val tensor = FloatArray(INPUT_DIM) { 0f }
        var offset = 0

        val seat = match.seats.getOrNull(seatIndex) ?: return tensor
        val currentSeatPosition = seatIndex

        // 0-51: current_hand (52) - 当前手牌
        encodeHandCards(seat.hand, tensor, offset)
        offset += 52

        // 52-103: last_action (52) - 上一手牌
        val lastAction = getLastAction(match)
        encodeLastAction(lastAction, tensor, offset)
        offset += 52

        // 104-111: action_type_one_hot (8) - 上一手牌型
        encodeActionType(lastAction?.type, tensor, offset)
        offset += 8

        // 112-125: action_length_one_hot (14) - 上一手牌张数
        encodeActionLength(lastAction?.cards?.size ?: 0, tensor, offset)
        offset += 14

        // 126-128: leader_relative_pos (3) - 领出者相对位置
        val leaderSeat = getLeaderSeat(match)
        encodeLeaderRelativePosition(leaderSeat, currentSeatPosition, tensor, offset)
        offset += 3

        // 129-170: cards_left (42) - 三家剩牌数 one-hot
        encodeCardsLeft(match.seats, currentSeatPosition, tensor, offset)
        offset += 42

        // 171-326: history (156) - 三家已出牌 one-hot
        encodeHistory(match, currentSeatPosition, tensor, offset)
        offset += 156

        // 327: is_leader (1) - 是否自己领出
        tensor[offset] = if (isLeader(match, currentSeatPosition)) 1f else 0f
        offset += 1

        // 328-330: relative_pass_mask (3) - 三家是否已pass
        encodePassMask(match, currentSeatPosition, tensor, offset)
        offset += 3

        // 331: is_next_warning (1) - 下家是否只剩1张
        tensor[offset] = if (isNextPlayerWarning(match, currentSeatPosition)) 1f else 0f

        return tensor
    }

    /**
     * 编码手牌到 one-hot (52维)
     */
    private fun encodeHandCards(handCards: List<Card>, tensor: FloatArray, offset: Int) {
        for (card in handCards) {
            val index = cardToIndex(card)
            if (index in 0 until TOTAL_CARDS) {
                tensor[offset + index] = 1f
            }
        }
    }

    /**
     * 获取上一手动作（相对于当前玩家）
     */
    private fun getLastAction(match: Match): LastActionInfo? {
        val currentCombination = match.trickState.currentCombination ?: return null
        // 使用 lastWinningSeatIndex 作为最后出牌的座位
        val lastSeatIndex = match.trickState.lastWinningSeatIndex

        return LastActionInfo(
            cards = currentCombination.cards,
            type = currentCombination.type,
            fromSeatIndex = lastSeatIndex
        )
    }

    /**
     * 编码上一手牌 (52维)
     */
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
     * 编码动作类型 one-hot (8维)
     * 顺序: none, single, pair, straight, flush, full_house, four_of_a_kind, straight_flush
     */
    private fun encodeActionType(type: CombinationType?, tensor: FloatArray, offset: Int) {
        val index = when (type) {
            null -> 0
            CombinationType.SINGLE -> 1
            CombinationType.PAIR -> 2
            CombinationType.STRAIGHT -> 3
            CombinationType.FLUSH -> 4
            CombinationType.FULL_HOUSE -> 5
            CombinationType.FOUR_OF_A_KIND_BOMB,
            CombinationType.FOUR_WITH_ONE,
            CombinationType.FOUR_WITH_TWO -> 6
            CombinationType.STRAIGHT_FLUSH -> 7
            else -> 0 // TRIPLE 等未使用类型映射到 none
        }
        tensor[offset + index] = 1f
    }

    /**
     * 编码动作长度 one-hot (14维)
     * 索引 0-13 表示牌张数
     */
    private fun encodeActionLength(length: Int, tensor: FloatArray, offset: Int) {
        val clampedLength = length.coerceIn(0, 13)
        tensor[offset + clampedLength] = 1f
    }

    /**
     * 获取领出者座位索引
     */
    private fun getLeaderSeat(match: Match): Int? {
        // 如果 trickState.currentCombination 为 null，表示新一轮开始
        // 需要确定谁是领出者
        return if (match.trickState.currentCombination == null) {
            match.activeSeatIndex
        } else {
            // 使用 leadSeatIndex 获取领出者
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
        offset: Int
    ) {
        if (leaderSeat == null || leaderSeat == currentSeat) return

        // 计算相对位置 (0-3)
        val relativePos = (leaderSeat - currentSeat + 4) % 4
        // 映射到 one-hot: 1=下家, 2=对家, 3=上家
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
        offset: Int
    ) {
        // 按相对位置编码: 下家(1), 对家(2), 上家(3)
        val relativePositions = listOf(1, 2, 3)
        for ((i, relPos) in relativePositions.withIndex()) {
            val seatIndex = (currentSeat + relPos) % 4
            val seat = seats.getOrNull(seatIndex) ?: continue
            val cardCount = seat.hand.size.coerceIn(0, 13)
            tensor[offset + i * 14 + cardCount] = 1f
        }
    }

    /**
     * 编码三家历史出牌 one-hot (156维 = 3 * 52)
     * 顺序: 下家(52), 对家(52), 上家(52)
     *
     * 从 tablePlays 获取当前轮次已出的牌
     */
    private fun encodeHistory(
        match: Match,
        currentSeat: Int,
        tensor: FloatArray,
        offset: Int
    ) {
        // 按相对位置编码: 下家(1), 对家(2), 上家(3)
        val relativePositions = listOf(1, 2, 3)
        for ((i, relPos) in relativePositions.withIndex()) {
            val seatIndex = (currentSeat + relPos) % 4
            val playedCards = match.trickState.tablePlays[seatIndex]?.cards ?: emptyList()

            // 将已出牌编码到对应位置
            for (card in playedCards) {
                val cardIndex = cardToIndex(card)
                if (cardIndex in 0 until TOTAL_CARDS) {
                    tensor[offset + i * 52 + cardIndex] = 1f
                }
            }
        }
    }

    /**
     * 判断是否自己领出
     */
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
        offset: Int
    ) {
        // 按相对位置编码: 下家(1), 对家(2), 上家(3)
        val relativePositions = listOf(1, 2, 3)

        for ((i, relPos) in relativePositions.withIndex()) {
            val seatIndex = (currentSeat + relPos) % 4
            val seat = match.seats.getOrNull(seatIndex)

            // 如果座位状态是 PASSED，或者该座位在当前轮次已 pass
            val hasPassed = seat?.status == com.example.chudadi.model.game.entity.SeatStatus.PASSED
            if (hasPassed) {
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

    /**
     * 获取输入维度
     */
    fun getInputDim(): Int = INPUT_DIM

    /**
     * 辅助数据类：上一手动作信息
     */
    private data class LastActionInfo(
        val cards: List<Card>,
        val type: CombinationType?,
        val fromSeatIndex: Int
    )
}
