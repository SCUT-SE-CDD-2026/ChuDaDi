package com.example.chudadi.model.game.entity

data class TrickState(
    val leadSeatIndex: Int,
    val lastWinningSeatIndex: Int,
    val currentCombination: PlayCombination?,
    val roundNumber: Int,
    val tablePlays: Map<Int, PlayCombination> = emptyMap(),
    val playedCardHistory: Map<Int, List<Card>> = emptyMap(),
    val playedActionHistory: Map<Int, List<List<Card>>> = emptyMap(),
    /** 当前轮次中已 pass 的座位索引（与 RLCard trace 语义对齐） */
    val passedSeatIndices: Set<Int> = emptySet(),
    val baopeiSeatId: Int? = null,
    val tablePlayOrders: Map<Int, Int> = emptyMap(),
    val nextTablePlayOrder: Int = 0,
) {
    init {
        require(roundNumber >= 1) { "roundNumber must be at least 1" }
        require(tablePlays.keys.all { it in 0..3 }) {
            "tablePlays keys must be valid seatIds in range 0..3, got: ${tablePlays.keys}"
        }
        if (currentCombination == null) {
            require(baopeiSeatId == null) {
                "baopeiSeatId must be null when currentCombination is null (round reset)"
            }
        }
    }
}
