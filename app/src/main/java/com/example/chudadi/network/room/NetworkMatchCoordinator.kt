@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.room

import android.content.Context
import com.example.chudadi.ai.AIFactory
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.onnx.OnnxAIPlayerController
import com.example.chudadi.controller.client.BluetoothRemoteMatchController
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.controller.server.AiActionResolver
import com.example.chudadi.controller.server.AiPlayerControllerAdapter
import com.example.chudadi.controller.server.BluetoothAuthoritativeMatchController
import com.example.chudadi.controller.server.CompositeAiActionResolver
import com.example.chudadi.controller.server.RuleBasedFallbackResolver
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.game.GameWireMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class NetworkMatchCoordinator(
    scope: CoroutineScope,
    private val context: Context,
) {
    private val engine = GameEngine()
    private val matchMapper = MatchUiStateMapper(engine)
    private val defaultResolver = RuleBasedFallbackResolver
    private val hostMatchController = BluetoothAuthoritativeMatchController(engine, matchMapper, defaultResolver)
    private val localMatchController = BluetoothRemoteMatchController()
    private val actionRouter = NetworkMatchActionRouter(hostMatchController, localMatchController)
    private val loopDriver = NetworkMatchLoopDriver(scope, hostMatchController)
    private var activeMatchSeatAssignments: Map<String, Int> = emptyMap()
    private var aiControllersBySeatId: Map<Int, AIPlayerController> = emptyMap()

    val matchUiState: StateFlow<MatchUiState> = localMatchController.uiState

    fun currentMatchPhase(): MatchPhase? = hostMatchController.currentMatch()?.phase

    fun hasActiveMatchSeatAssignments(): Boolean = activeMatchSeatAssignments.isNotEmpty()

    fun clearCurrentMatch() {
        hostMatchController.clearCurrentMatch()
        releaseAiControllers()
        clearActiveMatchState()
        loopDriver.cancel()
    }

    fun reset() {
        localMatchController.reset()
        clearCurrentMatch()
    }

    fun exitMatchToRoom() {
        localMatchController.reset()
        clearCurrentMatch()
    }

    fun localSeatId(participantId: String, authorityStore: RoomAuthorityStore): Int? {
        return matchSeatIdOfParticipant(participantId, authorityStore)
    }

    fun matchSeatIdOfParticipant(participantId: String, authorityStore: RoomAuthorityStore): Int? {
        return activeMatchSeatAssignments[participantId] ?: authorityStore.slotIndexOfParticipant(participantId)
    }

    suspend fun startNetworkMatch(
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        onMatchStartedSendFailed: (String, Throwable) -> Unit,
        aiMoveDelayMillis: Long = 0L,
    ): Result<Unit> {
        activeMatchSeatAssignments = buildActiveMatchSeatAssignments(authorityStore)

        // 根据 seatConfigs 增量重建 AI 控制器
        val seatConfigs = authorityStore.buildSeatConfigs()
        rebuildAiControllers(seatConfigs)
        val compositeResolver = buildCompositeResolver(seatConfigs)
        hostMatchController.updateAiActionResolver(compositeResolver)

        return actionRouter.startNetworkMatch(
            authorityStore = authorityStore,
            localParticipantId = localParticipantId,
            activeMatchSeatAssignments = activeMatchSeatAssignments,
            sendToParticipant = sendToParticipant,
            onMatchStartedSendFailed = onMatchStartedSendFailed,
            localSeatId = ::localSeatId,
            aiMoveDelayMillis = aiMoveDelayMillis,
        )
    }

    fun onLocalGameActionAsHost(
        action: LocalGameAction,
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        updateAllMatchSnapshots: (String?) -> Unit,
    ) {
        actionRouter.onLocalGameActionAsHost(
            action = action,
            authorityStore = authorityStore,
            localParticipantId = localParticipantId,
            updateAllMatchSnapshots = updateAllMatchSnapshots,
            localSeatId = ::localSeatId,
        )
    }

    fun onLocalGameActionAsClient(
        action: LocalGameAction,
        sendToHost: (RoomWireMessage) -> Result<Unit>,
    ): Result<Unit> {
        return actionRouter.onLocalGameActionAsClient(action, sendToHost)
    }

    fun handleClientGameEnvelope(
        message: GameWireMessage,
        handleHostConnectionLost: (String) -> Unit,
    ) {
        actionRouter.handleClientGameEnvelope(message, handleHostConnectionLost)
    }

    fun handleHostGameEnvelope(
        participantId: String,
        message: GameWireMessage,
        authorityStore: RoomAuthorityStore,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        updateAllMatchSnapshots: (String?) -> Unit,
    ) {
        actionRouter.handleHostGameEnvelope(
            participantId = participantId,
            message = message,
            context = NetworkMatchActionRouter.HostGameEnvelopeContext(
                authorityStore = authorityStore,
                sendToParticipant = sendToParticipant,
                updateAllMatchSnapshots = updateAllMatchSnapshots,
                matchSeatIdOfParticipant = ::matchSeatIdOfParticipant,
            ),
        )
    }

    fun startMatchLoop(
        updateAllMatchSnapshots: (String?) -> Unit,
        seatDisplayName: (Int) -> String,
        isSeatDisconnected: (Int) -> Boolean,
    ) {
        loopDriver.startMatchLoop(updateAllMatchSnapshots, seatDisplayName, isSeatDisconnected)
    }

    fun updateAllMatchSnapshots(
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        onMatchFinished: (List<RoundScore>) -> Unit,
        lastActionMessage: String?,
    ) {
        actionRouter.updateAllMatchSnapshots(
            lastActionMessage = lastActionMessage,
            context = NetworkMatchActionRouter.MatchSnapshotDispatchContext(
                authorityStore = authorityStore,
                localParticipantId = localParticipantId,
                activeMatchSeatAssignments = activeMatchSeatAssignments,
                sendToParticipant = sendToParticipant,
                onMatchFinished = onMatchFinished,
                localSeatId = ::localSeatId,
                onMatchEnded = {
                    clearActiveMatchState()
                    loopDriver.cancel()
                },
            ),
        )
    }

    fun onParticipantDisconnected(participantId: String, authorityStore: RoomAuthorityStore) {
        matchSeatIdOfParticipant(participantId, authorityStore)?.let { seatId ->
            hostMatchController.markDisconnected(seatId, disconnected = true)
        }
    }

    fun onParticipantReconnected(participantId: String, authorityStore: RoomAuthorityStore) {
        val seatId = matchSeatIdOfParticipant(participantId, authorityStore) ?: return
        hostMatchController.onSeatReconnected(seatId)
    }

    fun sendMatchRecoveryMessage(
        participantId: String,
        authorityStore: RoomAuthorityStore,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
    ) {
        actionRouter.sendMatchRecoveryMessage(
            participantId = participantId,
            authorityStore = authorityStore,
            sendToParticipant = sendToParticipant,
            matchSeatIdOfParticipant = ::matchSeatIdOfParticipant,
        )
    }

    /**
     * 增量重建 AI 控制器。复用 controllerType + difficulty 不变的座位。
     */
    private suspend fun rebuildAiControllers(
        seatConfigs: List<Triple<Int, String, SeatControllerType>>,
    ) {
        // 确保 ONNX 变体已注册（蓝牙联机可能先于本地游戏启动）
        AIFactory.preloadModels(context)
        val aiSeats = seatConfigs.filter { it.third != SeatControllerType.HUMAN }
        val oldControllers = aiControllersBySeatId
        val reused = mutableMapOf<Int, AIPlayerController>()
        val toRelease = mutableListOf<AIPlayerController>()
        val toCreate = mutableListOf<Triple<Int, String, SeatControllerType>>()

        for ((seatId, _, controllerType) in aiSeats) {
            val old = oldControllers[seatId]
            if (old != null && isCompatibleController(old, controllerType)) {
                reused[seatId] = old
            } else {
                old?.let { toRelease += it }
                toCreate += Triple(seatId, "", controllerType)
            }
        }

        toRelease.forEach { it.close() }

        val created = toCreate.associate { (seatId, _, controllerType) ->
            val result = AIFactory.createAIPlayerWithStatus(
                context = context,
                seatIndex = seatId,
                isOnnxAI = controllerType == SeatControllerType.ONNX_RL_AI,
            )
            if (result.isFallback) {
                android.util.Log.w(TAG, "AI seat $seatId fallback to rule-based: ${result.errorMessage}")
            }
            seatId to result.controller
        }

        if (toRelease.isNotEmpty() || toCreate.isNotEmpty()) {
            android.util.Log.i(
                TAG,
                "BT AI controllers: reused=${reused.keys.sorted()}, " +
                    "released=${toRelease.map { it.seatIndex }.sorted()}, " +
                    "created=${created.keys.sorted()}",
            )
        }

        aiControllersBySeatId = reused + created
    }

    private fun isCompatibleController(controller: AIPlayerController, type: SeatControllerType): Boolean {
        val isOnnx = controller is OnnxAIPlayerController
        val expectOnnx = type == SeatControllerType.ONNX_RL_AI
        return isOnnx == expectOnnx
    }

    private fun buildCompositeResolver(
        seatConfigs: List<Triple<Int, String, SeatControllerType>>,
    ): CompositeAiActionResolver {
        val resolvers = mutableMapOf<Int, AiActionResolver>()
        for ((seatId, _, _) in seatConfigs) {
            val controller = aiControllersBySeatId[seatId]
            if (controller != null) {
                resolvers[seatId] = AiPlayerControllerAdapter(controller)
            }
            // 人类座位不放入 resolver map，
            // CompositeAiActionResolver 会自动降级到 RuleBasedFallbackResolver
        }
        return CompositeAiActionResolver(resolvers)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun releaseAiControllers() {
        aiControllersBySeatId.values.forEach { controller ->
            try {
                controller.close()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to close AI controller for seat ${controller.seatIndex}", e)
            }
        }
        aiControllersBySeatId = emptyMap()
    }

    private fun buildActiveMatchSeatAssignments(authorityStore: RoomAuthorityStore): Map<String, Int> {
        return authorityStore.state.slotAssignments.entries
            .mapNotNull { (slotIndex, participantId) -> participantId?.let { it to slotIndex } }
            .toMap()
    }

    private fun clearActiveMatchState() {
        activeMatchSeatAssignments = emptyMap()
    }

    companion object {
        private const val TAG = "NetworkMatchCoordinator"
    }
}
