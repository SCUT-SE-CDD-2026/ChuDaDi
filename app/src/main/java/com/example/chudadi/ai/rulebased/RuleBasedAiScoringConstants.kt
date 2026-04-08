package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit

internal const val ZERO_COUNT = 0
internal const val ZERO_SCORE = 0.0
internal const val SINGLETON_COUNT = 1
internal const val PAIR_COUNT = 2
internal const val TRIPLE_COUNT = 3
internal const val FOUR_OF_A_KIND_COUNT = 4
internal const val ONE_RANK_STEP = 1
internal const val EXTRA_SAFE_CARDS_AFTER_BOMB = 2
internal const val BOMB_RESPONSE_SAFE_HAND_SIZE = 5
internal const val HIGH_SMALL_COMBINATION_CARD_COUNT = 3
internal const val MID_GAME_HAND_THRESHOLD = 6
internal const val LATE_GAME_HAND_THRESHOLD = 4
internal const val DANGER_OPPONENT_CARD_COUNT = 2
internal const val PRESSURE_OPPONENT_CARD_COUNT = 4
internal const val FAST_FINISH_HAND_THRESHOLD = 3
internal const val FAST_FINISH_COMBINATION_THRESHOLD = 3
internal const val SMALL_BEAT_RANK_GAP = 1

internal const val LEAD_BASE_SCORE_FULL_HOUSE = 20.0
internal const val LEAD_BASE_SCORE_STRAIGHT = 18.0
internal const val LEAD_BASE_SCORE_FLUSH = 17.0
internal const val LEAD_BASE_SCORE_TRIPLE = 14.0
internal const val LEAD_BASE_SCORE_PAIR = 10.0
internal const val LEAD_BASE_SCORE_SINGLE = 5.0
internal const val LEAD_BASE_SCORE_STRAIGHT_FLUSH = 4.0
internal const val LEAD_BASE_SCORE_FOUR_OF_A_KIND_BOMB = 2.0
internal const val LEAD_BASE_SCORE_FOUR_WITH_ONE = 1.0
internal const val LEAD_BASE_SCORE_FOUR_WITH_TWO = 0.0
internal const val RESPONSE_BASE_SCORE = 40.0

internal const val SINGLE_LEAD_RANK_WEIGHT = 1.35
internal const val NON_SINGLE_LEAD_RANK_WEIGHT = 0.7
internal const val RESPONSE_CONTROL_LOSS_WEIGHT = 1.1

internal const val PLAY_ALL_HAND_BONUS = 100.0
internal const val LOW_SINGLETON_LEAD_BONUS = 4.0
internal const val NATURAL_PAIR_LEAD_BONUS = 3.0
internal const val OPENING_SINGLE_PENALTY = -10.0
internal const val OPENING_PAIR_BONUS = 2.0
internal const val OPENING_TRIPLE_BONUS = 4.0
internal const val OPENING_FIVE_CARD_STRUCTURE_BONUS = 8.0
internal const val OPENING_BOMB_PENALTY = -14.0
internal const val EARLY_BOMB_PENALTY = 20.0
internal const val SAME_TYPE_RESPONSE_BONUS = 10.0
internal const val BOMB_OVER_NON_BOMB_PENALTY = 8.0
internal const val SMALL_BEAT_BONUS = 4.0
internal const val LOW_SINGLE_RESPONSE_BONUS = 3.0
internal const val BOMB_RESPONSE_PENALTY = 10.0

internal const val PASS_SCORE_THRESHOLD_VERY_LOW = 0.0
internal const val PASS_SCORE_THRESHOLD_LOW = 6.0
internal const val PASS_SCORE_THRESHOLD_MEDIUM = 12.0
internal const val PASS_SCORE_THRESHOLD_HIGH = 18.0
internal const val PASS_PROBABILITY_VERY_LOW_SCORE = 0.82
internal const val PASS_PROBABILITY_LOW_SCORE = 0.62
internal const val PASS_PROBABILITY_MEDIUM_SCORE = 0.42
internal const val PASS_PROBABILITY_HIGH_SCORE = 0.26
internal const val PASS_PROBABILITY_TOP_SCORE = 0.12
internal const val MIN_PASS_PROBABILITY = 0.0
internal const val MAX_PASS_PROBABILITY = 0.9
internal const val LATE_GAME_PASS_REDUCTION = 0.28
internal const val DANGER_OPPONENT_PASS_REDUCTION = 0.30
internal const val PRESSURE_OPPONENT_PASS_REDUCTION = 0.15
internal const val BOMB_PASS_INCREASE = 0.42
internal const val NORTHERN_BOMB_PASS_INCREASE = 0.18
internal const val BOMB_VS_BOMB_PASS_INCREASE = 0.16
internal const val HIGH_BOMB_PASS_INCREASE = 0.10
internal const val SINGLE_TWO_PASS_INCREASE = 0.50
internal const val SINGLE_ACE_PASS_INCREASE = 0.26
internal const val SINGLE_KING_PASS_INCREASE = 0.14
internal const val NON_SINGLE_TWO_PASS_INCREASE = 0.14

internal const val SINGLE_BREAK_PAIR_PENALTY = 8.5
internal const val SINGLE_BREAK_TRIPLE_PENALTY = 4.5
internal const val SINGLE_BREAK_FIVE_CARD_PENALTY = 2.5
internal const val PAIR_BREAK_TRIPLE_PENALTY = 7.0
internal const val PAIR_BREAK_FOUR_OF_A_KIND_PENALTY = 3.0
internal const val PAIR_BREAK_FIVE_CARD_LINK_WEIGHT = 0.8
internal const val TRIPLE_BREAK_FOUR_OF_A_KIND_PENALTY = 5.0
internal const val TRIPLE_BREAK_OTHER_PAIR_PENALTY = 2.5
internal const val FIVE_CARD_LINK_UNIT = 1.0
internal const val STRUCTURE_PLAY_BASE_BREAK_PENALTY = 1.0
internal const val BOMB_BREAK_BASE_PENALTY = 12.0

internal const val SINGLE_CONTROL_LOSS_FACTOR = 1.0
internal const val PAIR_CONTROL_LOSS_FACTOR = 0.9
internal const val TRIPLE_CONTROL_LOSS_FACTOR = 0.8
internal const val FIVE_CARD_CONTROL_LOSS_FACTOR = 0.65
internal const val BOMB_CONTROL_LOSS_FACTOR = 0.5
internal const val CONTROL_LOSS_TWO_PENALTY = 10.0
internal const val CONTROL_LOSS_ACE_PENALTY = 6.0
internal const val CONTROL_LOSS_KING_PENALTY = 4.0
internal const val HIGH_SMALL_COMBINATION_RANK_WEIGHT = 1.5

internal const val BOMB_OVERKILL_PENALTY = 18.0
internal const val TYPE_GAP_OVERKILL_WEIGHT = 3.0
internal const val SINGLE_OVERKILL_RANK_FACTOR = 2.0
internal const val PAIR_OVERKILL_RANK_FACTOR = 1.7
internal const val TRIPLE_OVERKILL_RANK_FACTOR = 1.5
internal const val FIVE_CARD_OVERKILL_RANK_FACTOR = 1.2
internal const val BOMB_OVERKILL_RANK_FACTOR = 2.2
internal const val SUIT_GAP_OVERKILL_WEIGHT = 0.5

internal const val FINISHING_PLAY_BONUS = 100.0
internal const val VERY_LOW_REMAINING_BONUS = 20.0
internal const val LOW_REMAINING_BONUS = 12.0
internal const val MID_REMAINING_BONUS = 4.0
internal const val DANGER_OPPONENT_BONUS = 10.0
internal const val DANGER_OPPONENT_CARD_WEIGHT = 1.5
internal const val PRESSURE_OPPONENT_BONUS = 5.0
internal const val GLOBAL_DANGER_OPPONENT_BONUS = 4.0
internal const val FAST_FINISH_COMBINATION_BONUS = 4.0

internal val OPENING_CARD = Card(
    suit = CardSuit.DIAMONDS,
    rank = CardRank.THREE,
)
