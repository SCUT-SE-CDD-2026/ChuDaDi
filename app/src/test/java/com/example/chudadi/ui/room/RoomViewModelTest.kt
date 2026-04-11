package com.example.chudadi.ui.room

import com.example.chudadi.model.game.entity.RoundScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomViewModelTest {
    @Test
    fun swapWithAi_keepsSlotIndexStableAndMovesSeatIdWithOccupant() {
        val viewModel = RoomViewModel()

        viewModel.dispatch(RoomAction.AddAiToSlot(1, RoomAiDifficulty.RULE_NORMAL))
        viewModel.dispatch(RoomAction.RequestSwapWithSlot(1))

        val slots = viewModel.uiState.value.slots
        val localSlot = slots.first { it.isLocalPlayer }
        val aiSlot = slots.first { it.occupantType == SlotOccupantType.AI }

        assertEquals(0, slots[0].slotIndex)
        assertEquals(1, slots[1].slotIndex)
        assertEquals(1, localSlot.slotIndex)
        assertEquals(0, localSlot.seatId)
        assertEquals(0, aiSlot.slotIndex)
        assertEquals(1, aiSlot.seatId)
    }

    @Test
    fun accumulateScores_afterSwap_updatesMatchingSeatId() {
        val viewModel = RoomViewModel()

        viewModel.dispatch(RoomAction.AddAiToSlot(1, RoomAiDifficulty.RULE_NORMAL))
        viewModel.dispatch(RoomAction.RequestSwapWithSlot(1))
        viewModel.dispatch(
            RoomAction.AccumulateScores(
                listOf(
                    RoundScore(
                        seatId = 0,
                        playerName = "默认玩家",
                        remainingCards = 0,
                        roundScore = 5,
                    ),
                ),
            ),
        )

        val slots = viewModel.uiState.value.slots
        assertEquals(5, slots[1].cumulativeScore)
        assertEquals(0, slots[0].cumulativeScore)
    }

    @Test
    fun canStartGame_requiresAllSeatsFilledAndReady() {
        val viewModel = RoomViewModel()

        assertFalse(viewModel.uiState.value.canStartGame)

        viewModel.dispatch(RoomAction.AddAiToSlot(1, RoomAiDifficulty.RULE_NORMAL))
        viewModel.dispatch(RoomAction.AddAiToSlot(2, RoomAiDifficulty.RULE_NORMAL))
        viewModel.dispatch(RoomAction.AddAiToSlot(3, RoomAiDifficulty.RULE_NORMAL))

        assertTrue(viewModel.uiState.value.canStartGame)
    }

    @Test
    fun toggleAiPlaySpeed_cyclesIncludingVfast() {
        val viewModel = RoomViewModel()

        assertEquals(AiPlaySpeed.NORMAL, viewModel.uiState.value.aiPlaySpeed)

        viewModel.dispatch(RoomAction.ToggleAiPlaySpeed)
        assertEquals(AiPlaySpeed.FAST, viewModel.uiState.value.aiPlaySpeed)

        viewModel.dispatch(RoomAction.ToggleAiPlaySpeed)
        assertEquals(AiPlaySpeed.VFAST, viewModel.uiState.value.aiPlaySpeed)

        viewModel.dispatch(RoomAction.ToggleAiPlaySpeed)
        assertEquals(AiPlaySpeed.DEBUG_100_ROUNDS, viewModel.uiState.value.aiPlaySpeed)

        viewModel.dispatch(RoomAction.ToggleAiPlaySpeed)
        assertEquals(AiPlaySpeed.SLOW, viewModel.uiState.value.aiPlaySpeed)

        viewModel.dispatch(RoomAction.ToggleAiPlaySpeed)
        assertEquals(AiPlaySpeed.NORMAL, viewModel.uiState.value.aiPlaySpeed)
    }

    @Test
    fun hostCanRemoveSelfThenFillFourAiAndStart() {
        val viewModel = RoomViewModel()

        viewModel.dispatch(RoomAction.RemoveSlotOccupant(0))
        viewModel.dispatch(RoomAction.AddAiToSlot(0, RoomAiDifficulty.RULE_NORMAL))
        viewModel.dispatch(RoomAction.AddAiToSlot(1, RoomAiDifficulty.RULE_NORMAL))
        viewModel.dispatch(RoomAction.AddAiToSlot(2, RoomAiDifficulty.RULE_NORMAL))
        viewModel.dispatch(RoomAction.AddAiToSlot(3, RoomAiDifficulty.RULE_NORMAL))

        val state = viewModel.uiState.value
        assertTrue(state.slots.all { it.occupantType == SlotOccupantType.AI })
        assertTrue(state.slots.none { it.isLocalPlayer })
        assertTrue(state.canStartGame)
    }

    @Test
    fun accumulateScores_incrementsTotalGamesPlayed() {
        val viewModel = RoomViewModel()

        assertEquals(0, viewModel.uiState.value.totalGamesPlayed)

        viewModel.dispatch(
            RoomAction.AccumulateScores(
                listOf(
                    RoundScore(
                        seatId = 0,
                        playerName = "玩家1",
                        remainingCards = 0,
                        roundScore = 3,
                    ),
                ),
            ),
        )

        assertEquals(1, viewModel.uiState.value.totalGamesPlayed)
    }
}
