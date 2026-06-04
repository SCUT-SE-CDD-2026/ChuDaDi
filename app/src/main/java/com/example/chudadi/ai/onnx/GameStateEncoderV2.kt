package com.example.chudadi.ai.onnx

import com.example.chudadi.ai.base.variant.ObservationEncoder
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * V2 non-history observation encoder.
 *
 * V1 layout is 334 dims. V2 keeps the same fields except the three cumulative
 * 52-card history blocks, producing 178 dims. Sequence history is provided by
 * [PlayedHistorySequenceEncoder] as a separate tensor.
 */
class GameStateEncoderV2(
    private val v1Encoder: GameStateEncoder = GameStateEncoder(),
) : ObservationEncoder {
    override val inputDim: Int = INPUT_DIM

    override fun encode(match: Match, seatIndex: Int, ruleSet: GameRuleSet): FloatArray {
        val v1 = v1Encoder.encode(match, seatIndex, ruleSet)
        val result = FloatArray(INPUT_DIM)
        System.arraycopy(v1, 0, result, 0, HISTORY_START)
        System.arraycopy(
            v1,
            HISTORY_END,
            result,
            HISTORY_START,
            v1.size - HISTORY_END,
        )
        return result
    }

    companion object {
        const val INPUT_DIM = 178
        private const val HISTORY_START = 172
        private const val HISTORY_END = HISTORY_START + 156
    }
}
