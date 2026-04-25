package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.rule.GameRuleSet
import java.util.UUID
import kotlin.random.Random

object MatchFactory {
    fun startLocalMatch(random: Random, ruleSet: GameRuleSet): Match {
        val shuffledDeck = Card.standardDeck().shuffled(random)
        val seats = listOf(
            createSeat(0, "You", SeatControllerType.HUMAN, shuffledDeck.subList(0, 13)),
            createSeat(1, "AI 1", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(13, 26)),
            createSeat(2, "AI 2", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(26, 39)),
            createSeat(3, "AI 3", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(39, 52)),
        )
        return buildMatch(ruleSet, seats)
    }

    fun startLocalMatch(
        random: Random,
        ruleSet: GameRuleSet,
        seatConfigs: List<Triple<Int, String, SeatControllerType>>,
    ): Match {
        require(seatConfigs.size == 4) { "seatConfigs must have exactly 4 entries" }
        val shuffledDeck = Card.standardDeck().shuffled(random)
        val sorted = seatConfigs.sortedBy { it.first }
        val seats = sorted.mapIndexed { listIndex, (seatId, name, controllerType) ->
            createSeat(seatId, name, controllerType, shuffledDeck.subList(listIndex * 13, (listIndex + 1) * 13))
        }
        return buildMatch(ruleSet, seats)
    }

    private fun buildMatch(ruleSet: GameRuleSet, seats: List<Seat>): Match {
        val openingSeat = seats.first { seat -> seat.hand.any { it.id == OPENING_CARD.id } }
        return Match(
            matchId = UUID.randomUUID().toString(),
            ruleSet = ruleSet,
            phase = MatchPhase.PLAYER_TURN,
            seats = seats,
            activeSeatIndex = openingSeat.seatId,
            trickState = TrickState(
                leadSeatIndex = openingSeat.seatId,
                lastWinningSeatIndex = openingSeat.seatId,
                currentCombination = null,
                passCount = 0,
                roundNumber = 1,
            ),
            playHistory = listOf("${openingSeat.displayName} leads the first round"),
            result = null,
        )
    }

    private fun createSeat(
        seatId: Int,
        displayName: String,
        controllerType: SeatControllerType,
        cards: List<Card>,
    ): Seat {
        return Seat(
            seatId = seatId,
            displayName = displayName,
            controllerType = controllerType,
            hand = cards.sortedWith(Card.gameComparator),
            status = SeatStatus.ACTIVE,
        )
    }

    val OPENING_CARD: Card = Card(
        suit = CardSuit.DIAMONDS,
        rank = CardRank.THREE,
    )
}
