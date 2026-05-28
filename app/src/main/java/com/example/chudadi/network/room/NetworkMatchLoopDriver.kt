package com.example.chudadi.network.room

import com.example.chudadi.controller.server.AuthoritativeTurnSnapshot
import com.example.chudadi.controller.server.BluetoothAuthoritativeMatchController
import com.example.chudadi.model.game.entity.MatchPhase
import kotlinx.coroutines.CancellationException
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
                val tickResult = safeTickOnce(
                    updateAllMatchSnapshots = updateAllMatchSnapshots,
                    seatDisplayName = seatDisplayName,
                    isSeatDisconnected = isSeatDisconnected,
                )
                if (tickResult == LoopTickResult.FINISHED) shouldStop = true
            }
        }
    }

    fun cancel() {
        matchLoopJob?.cancel()
    }

    companion object {
        private const val TAG = "NetworkMatchLoopDriver"
        private const val MATCH_LOOP_INTERVAL_MS = 250L
    }

    @Suppress("RethrowCaughtException", "TooGenericExceptionCaught")
    private suspend fun safeTickOnce(
        updateAllMatchSnapshots: (String?) -> Unit,
        seatDisplayName: (Int) -> String,
        isSeatDisconnected: (Int) -> Boolean,
    ): LoopTickResult {
        return try {
            tickOnce(
                updateAllMatchSnapshots = updateAllMatchSnapshots,
                seatDisplayName = seatDisplayName,
                isSeatDisconnected = isSeatDisconnected,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Match loop tick failed, continuing", e)
            LoopTickResult.RUNNING
        }
    }

    private suspend fun tickOnce(
        updateAllMatchSnapshots: (String?) -> Unit,
        seatDisplayName: (Int) -> String,
        isSeatDisconnected: (Int) -> Boolean,
    ): LoopTickResult {
        val match = hostMatchController.currentMatch()
        var result = LoopTickResult.RUNNING
        if (match?.phase == MatchPhase.FINISHED) {
            result = LoopTickResult.FINISHED
        } else if (match != null) {
            val turnSnapshot = hostMatchController.currentTurnSnapshot()
            if (turnSnapshot == null || !hostMatchController.isCurrentTurnExpired(turnSnapshot)) {
                updateAllMatchSnapshots(null)
            } else {
                val seatId = turnSnapshot.activeSeatIndex
                val seatName = seatDisplayName(seatId)
                val resolution = resolveExpiredTurn(
                    turnSnapshot = turnSnapshot,
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

    private suspend fun resolveExpiredTurn(
        turnSnapshot: AuthoritativeTurnSnapshot,
        seatId: Int,
        seatName: String,
        isSeatDisconnected: (Int) -> Boolean,
    ): ExpiredTurnResolution {
        val resolution: ExpiredTurnResolution
        if (hostMatchController.isCurrentActorAi(turnSnapshot)) {
            val thinkingMessage = buildThinkingMessage(seatId, seatName, isSeatDisconnected)
            val result = hostMatchController.resolveCurrentSeatByAi(
                lastMessage = thinkingMessage,
                expectedTurn = turnSnapshot,
            )
            resolution = ExpiredTurnResolution(
                message = result.message,
                shouldUpdateSnapshot = result.success || result.message != null,
            )
        } else if (isSeatDisconnected(seatId)) {
            val marked = hostMatchController.markDisconnected(
                seatId = seatId,
                disconnected = true,
                expectedTurn = turnSnapshot,
            )
            resolution = ExpiredTurnResolution(
                message = "$seatName 托管中",
                shouldUpdateSnapshot = marked,
            )
        } else if (hostMatchController.canCurrentSeatPass(turnSnapshot)) {
            val passResult = hostMatchController.handlePassRequest(
                seatId = seatId,
                expectedTurn = turnSnapshot,
            )
            resolution = ExpiredTurnResolution(
                message = if (passResult.success) "$seatName 超时过牌" else passResult.message,
                shouldUpdateSnapshot = passResult.success || passResult.message != null,
            )
        } else {
            val result = hostMatchController.resolveCurrentSeatByAi(
                lastMessage = "$seatName 超时，系统已代出",
                expectedTurn = turnSnapshot,
            )
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
