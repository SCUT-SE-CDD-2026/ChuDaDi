@file:Suppress("ReturnCount")

package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules

class BaopeiChecker(
    private val evaluatorFactory: (GameRuleSet) -> CombinationEvaluator = { ruleSet ->
        CombinationEvaluator(GameRules.forRuleSet(ruleSet))
    },
) {
    fun checkAndApplyBaopei(
        match: Match,
        seatIndex: Int,
        candidate: PlayCombination,
    ): Match {
        if (candidate.type != CombinationType.SINGLE) {
            return match
        }
        val currentCombination = match.trickState.currentCombination
        if (currentCombination == null) {
            return match
        }

        val seat = match.seats.first { it.seatId == seatIndex }
        val nextSeatId = nextActiveSeatIndex(match.seats, seatIndex) ?: return match
        val nextSeat = match.seats.firstOrNull { it.seatId == nextSeatId } ?: return match
        if (nextSeat.hand.size != 1) {
            return match
        }

        val evaluator = evaluatorFactory(match.ruleSet)

        // 防御性检查：若玩家手头最大的单张也压不住当前牌型（即只能PASS），
        // 则不触发包赔。此条件通常已由 validatePlay 保证，但显式检查可避免
        // 将来验证逻辑调整时产生隐式耦合。
        val maxSingleInHand = seat.hand.maxWithOrNull(Card.gameComparator)
        val maxSingleCombination = maxSingleInHand?.let { evaluator.parse(listOf(it)) }
        if (maxSingleCombination == null || !evaluator.canBeat(maxSingleCombination, currentCombination)) {
            return match
        }

        // 若下家的唯一一张牌压不住当前出的单张（下家只能PASS），则不触发包赔
        val nextSeatCombination = evaluator.parse(nextSeat.hand)
        if (nextSeatCombination != null && !evaluator.canBeat(nextSeatCombination, candidate)) {
            return match
        }

        val playedCard = candidate.cards.single()
        val maxCardInHand = seat.hand
            .filter { it.id != playedCard.id }
            .maxWithOrNull(Card.gameComparator)
            ?: return match

        if (Card.gameComparator.compare(playedCard, maxCardInHand) >= 0) {
            return match
        }

        return match.copy(
            trickState = match.trickState.copy(baopeiSeatId = seatIndex),
        )
    }
}
