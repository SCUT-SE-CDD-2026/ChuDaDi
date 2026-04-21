package com.example.chudadi.network.room

import com.example.chudadi.controller.server.BluetoothAuthoritativeMatchController
import com.example.chudadi.model.game.entity.MatchPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetworkMatchLoopDriver(
    private val scope: CoroutineScope,
    private val hostMatchController: BluetoothAuthoritativeMatchController,
) {
    private var matchLoopJob: Job? = null

    fun startMatchLoop(
        updateAllMatchSnapshots: (String?) -> Unit,
        seatDisplayName: (Int) -> String,
        isSeatDisconnected: (Int) -> Boolean,
    ) {
        matchLoopJob?.cancel()
        matchLoopJob = scope.launch {
            var shouldStop = false
            while (isActive && !shouldStop) {
                delay(MATCH_LOOP_INTERVAL_MS)
                shouldStop = tickOnce(
                    updateAllMatchSnapshots = updateAllMatchSnapshots,
                    seatDisplayName = seatDisplayName,
                    isSeatDisconnected = isSeatDisconnected,
                ) == LoopTickResult.FINISHED
            }
        }
    }

    fun cancel() {
        matchLoopJob?.cancel()
    }

    companion object {
        private const val MATCH_LOOP_INTERVAL_MS = 250L
    }

    private fun tickOnce(
        updateAllMatchSnapshots: (String?) -> Unit,
        seatDisplayName: (Int) -> String,
        isSeatDisconnected: (Int) -> Boolean,
    ): LoopTickResult {
        val match = hostMatchController.currentMatch()
        var result = LoopTickResult.RUNNING
        if (match?.phase == MatchPhase.FINISHED) {
            result = LoopTickResult.FINISHED
        } else if (match != null) {
            if (!hostMatchController.isCurrentTurnExpired()) {
                updateAllMatchSnapshots(null)
            } else {
                val seatId = match.activeSeatIndex
                val seatName = seatDisplayName(seatId)
                val resolution = resolveExpiredTurn(
                    seatId = seatId,
                    seatName = seatName,
                    isSeatDisconnected = isSeatDisconnected,
                )
                if (resolution.shouldUpdateSnapshot) {
                    updateAllMatchSnapshots(resolution.message)
                }
            }
        }
        return result
    }

    private fun resolveExpiredTurn(
        seatId: Int,
        seatName: String,
        isSeatDisconnected: (Int) -> Boolean,
    ): ExpiredTurnResolution {
        val resolution: ExpiredTurnResolution
        if (hostMatchController.isCurrentActorAi()) {
            val thinkingMessage = buildThinkingMessage(seatId, seatName, isSeatDisconnected)
            val result = hostMatchController.resolveCurrentSeatByAi(thinkingMessage)
            resolution = ExpiredTurnResolution(
                message = result.message,
                shouldUpdateSnapshot = result.success || result.message != null,
            )
        } else if (isSeatDisconnected(seatId)) {
            hostMatchController.markDisconnected(seatId, disconnected = true)
            resolution = ExpiredTurnResolution(
                message = "$seatName 托管中",
                shouldUpdateSnapshot = true,
            )
        } else if (hostMatchController.canCurrentSeatPass()) {
            val passResult = hostMatchController.handlePassRequest(seatId)
            resolution = ExpiredTurnResolution(
                message = if (passResult.success) "$seatName 超时过牌" else passResult.message,
                shouldUpdateSnapshot = passResult.success || passResult.message != null,
            )
        } else {
            val result = hostMatchController.resolveCurrentSeatByAi("$seatName 超时，系统已代出")
            resolution = ExpiredTurnResolution(
                message = result.message,
                shouldUpdateSnapshot = result.success || result.message != null,
            )
        }
        return resolution
    }

    private fun buildThinkingMessage(
        seatId: Int,
        seatName: String,
        isSeatDisconnected: (Int) -> Boolean,
    ): String {
        return if (isSeatDisconnected(seatId)) {
            "$seatName 托管中"
        } else {
            "$seatName 思考中"
        }
    }

    private data class ExpiredTurnResolution(
        val message: String?,
        val shouldUpdateSnapshot: Boolean,
    )

    private enum class LoopTickResult {
        RUNNING,
        FINISHED,
    }
}
