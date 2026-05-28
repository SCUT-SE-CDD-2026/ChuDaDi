package com.example.chudadi.ai.onnx.pipeline

import com.example.chudadi.ai.onnx.GameStateEncoder
import com.example.chudadi.model.game.entity.Card

/**
 * 52-bit bitmask 动作 ID 映射器。
 *
 * 将卡牌列表与 52 位掩码动作 ID 互转。
 * bit i = 1 表示出第 i 张牌（索引规则同 [GameStateEncoder.cardToIndex]）。
 * 动作 ID = 0 表示 pass。
 */
class BitmaskActionIdMapper {

    companion object {
        const val PASS_ACTION_ID = 0L
        private const val TOTAL_CARDS = 52
    }

    /**
     * 将卡牌列表转换为 52 位掩码动作 ID。
     */
    fun cardsToId(cards: List<Card>): Long {
        var mask = 0L
        for (card in cards) {
            val index = GameStateEncoder.cardToIndex(card)
            if (index in 0 until Long.SIZE_BITS) {
                mask = mask or (1L shl index)
            }
        }
        return mask
    }

    /**
     * 将 52 位掩码动作 ID 转换为卡牌列表。
     *
     * @param actionId 动作 ID
     * @param handCards 当前手牌（用于定位实际卡牌对象）
     * @return 对应的卡牌列表；pass 动作返回空列表
     */
    fun idToCards(actionId: Long, handCards: List<Card>): List<Card> {
        if (actionId == PASS_ACTION_ID) return emptyList()

        val result = mutableListOf<Card>()
        val handIndexMap = handCards.associateBy { GameStateEncoder.cardToIndex(it) }

        for (i in 0 until TOTAL_CARDS) {
            if ((actionId shr i) and 1L == 1L) {
                handIndexMap[i]?.let { result.add(it) }
            }
        }

        return result
    }
}
