package com.example.chudadi.model.game.fixture

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

object MatchFixtureFactory {
    fun card(
        rank: CardRank,
        suit: CardSuit,
    ): Card = Card(rank = rank, suit = suit)

    fun localMatch(
        activeSeatIndex: Int = 0,
        seats: List<Seat> = defaultSeats(),
        ruleSet: GameRuleSet = GameRuleSet.SOUTHERN,
    ): Match {
        return Match(
            matchId = "fixture-match",
            ruleSet = ruleSet,
            phase = MatchPhase.PLAYER_TURN,
            seats = seats,
            activeSeatIndex = activeSeatIndex,
            trickState = TrickState(
                leadSeatIndex = activeSeatIndex,
                lastWinningSeatIndex = activeSeatIndex,
                currentCombination = null,
                passCount = 0,
                roundNumber = 1,
            ),
            playHistory = emptyList(),
            totalBombCount = 0,
            result = null,
        )
    }

    fun defaultSeats(): List<Seat> {
        return listOf(
            Seat(
                seatId = 0,
                displayName = "You",
                controllerType = SeatControllerType.HUMAN,
                hand = listOf(
                    card(CardRank.THREE, CardSuit.DIAMONDS),
                    card(CardRank.FOUR, CardSuit.CLUBS),
                    card(CardRank.FIVE, CardSuit.HEARTS),
                ),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 1,
                displayName = "AI 1",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(card(CardRank.SIX, CardSuit.SPADES)),
            ),
            Seat(
                seatId = 2,
                displayName = "AI 2",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(card(CardRank.SEVEN, CardSuit.DIAMONDS)),
            ),
            Seat(
                seatId = 3,
                displayName = "AI 3",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(card(CardRank.EIGHT, CardSuit.CLUBS)),
            ),
        )
    }
}
