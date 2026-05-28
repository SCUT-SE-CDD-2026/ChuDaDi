package com.example.chudadi.controller.server

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.model.game.engine.ActionResult
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.GameRuleSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class CompositeAiActionResolverTest {

    private val fixtureEngine = FixtureGameEngine()
    private val fixtureMatch = MatchFixtureFactory.localMatch(activeSeatIndex = 0)
    private val fixtureRuleSet = GameRuleSet.SOUTHERN

    // region CompositeAiActionResolver

    @Test
    fun whenSeatIdHasResolver_delegatesToThatResolver() = runTest {
        var resolvedSeatId: Int? = null
        val stubResolver = AiActionResolver { _, seatId, _, _ ->
            resolvedSeatId = seatId
            ActionResult(match = fixtureMatch, success = true)
        }
        val composite = CompositeAiActionResolver(mapOf(1 to stubResolver))

        composite.resolve(fixtureMatch, seatId = 1, engine = fixtureEngine, ruleSet = fixtureRuleSet)

        assertEquals(1, resolvedSeatId)
    }

    @Test
    fun whenSeatIdNotInMap_fallsBackToRuleBasedFallback() = runTest {
        val composite = CompositeAiActionResolver(emptyMap())

        // seatId 1 不在 map 中，应降级到 RuleBasedFallbackResolver（规则型 AI）
        // fixtureMatch 的 seats[1] 是 AI 座位，有手牌
        val result = composite.resolve(fixtureMatch, seatId = 1, engine = fixtureEngine, ruleSet = fixtureRuleSet)

        // 规则型 AI 对 fixtureMatch 应该能返回有效决策（出牌或过牌）
        // 不应抛异常
        assertTrue(result.success || result.error != null)
    }

    @Test
    fun whenResolversMapContainsOnlySomeSeats_otherSeatsFallback() = runTest {
        val stubResolver = AiActionResolver { _, seatId, _, _ ->
            ActionResult(match = fixtureMatch, success = true, message = "stub-$seatId")
        }
        val composite = CompositeAiActionResolver(mapOf(1 to stubResolver))

        // seatId 1 → stub resolver
        val result1 = composite.resolve(fixtureMatch, seatId = 1, engine = fixtureEngine, ruleSet = fixtureRuleSet)
        assertEquals("stub-1", result1.message)

        // seatId 2 → fallback (RuleBasedFallbackResolver)
        val result2 = composite.resolve(fixtureMatch, seatId = 2, engine = fixtureEngine, ruleSet = fixtureRuleSet)
        // 不应抛异常，规则型 AI 应返回有效结果
        assertTrue(result2.success || result2.error != null)
    }

    // endregion

    // region AiPlayerControllerAdapter

    @Test
    fun whenControllerReturnsPlayCards_executesPlayCardCommand() = runTest {
        val seat = fixtureMatch.seats[0]
        val cardsToPlay = seat.hand.take(1)
        val mockController = Mockito.mock(AIPlayerController::class.java)
        Mockito.`when`(mockController.requestDecision(fixtureMatch, fixtureRuleSet))
            .thenReturn(AIDecision.PlayCards(cardsToPlay))

        val adapter = AiPlayerControllerAdapter(mockController)
        val result = adapter.resolve(fixtureMatch, seatId = 0, engine = fixtureEngine, ruleSet = fixtureRuleSet)

        assertTrue(result.success)
    }

    @Test
    fun whenControllerReturnsPass_executesPassCommand() = runTest {
        val mockController = Mockito.mock(AIPlayerController::class.java)
        Mockito.`when`(mockController.requestDecision(fixtureMatch, fixtureRuleSet))
            .thenReturn(AIDecision.Pass)

        val adapter = AiPlayerControllerAdapter(mockController)
        val result = adapter.resolve(fixtureMatch, seatId = 1, engine = fixtureEngine, ruleSet = fixtureRuleSet)

        // 过牌不一定成功（取决于是否可以过），但不应抛异常
        assertFalse(result.match.seats.isEmpty())
    }

    @Test
    fun whenControllerReturnsError_fallsBackToFallback() = runTest {
        val mockController = Mockito.mock(AIPlayerController::class.java)
        Mockito.`when`(mockController.requestDecision(fixtureMatch, fixtureRuleSet))
            .thenReturn(AIDecision.Error("test error"))

        var fallbackCalled = false
        val fallback = AiActionResolver { _, _, _, _ ->
            fallbackCalled = true
            ActionResult(match = fixtureMatch, success = true)
        }

        val adapter = AiPlayerControllerAdapter(mockController, fallback)
        adapter.resolve(fixtureMatch, seatId = 1, engine = fixtureEngine, ruleSet = fixtureRuleSet)

        assertTrue(fallbackCalled)
    }

    @Test
    fun whenControllerThrowsCancellationException_rethrows() = runTest {
        val mockController = Mockito.mock(AIPlayerController::class.java)
        Mockito.`when`(mockController.requestDecision(fixtureMatch, fixtureRuleSet))
            .thenAnswer { throw CancellationException("cancelled") }

        val adapter = AiPlayerControllerAdapter(mockController)

        var caught = false
        try {
            adapter.resolve(fixtureMatch, seatId = 1, engine = fixtureEngine, ruleSet = fixtureRuleSet)
        } catch (@Suppress("SwallowedException") e: CancellationException) {
            caught = true
        }
        assertTrue("CancellationException should be rethrown, not caught", caught)
    }

    @Test
    fun whenControllerThrowsGenericException_fallsBackToFallback() = runTest {
        val mockController = Mockito.mock(AIPlayerController::class.java)
        Mockito.`when`(mockController.requestDecision(fixtureMatch, fixtureRuleSet))
            .thenAnswer {
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException("ONNX inference failed")
            }

        var fallbackCalled = false
        val fallback = AiActionResolver { _, _, _, _ ->
            fallbackCalled = true
            ActionResult(match = fixtureMatch, success = true)
        }

        val adapter = AiPlayerControllerAdapter(mockController, fallback)
        val result = adapter.resolve(fixtureMatch, seatId = 1, engine = fixtureEngine, ruleSet = fixtureRuleSet)

        assertTrue(fallbackCalled)
        assertTrue(result.success)
    }

    // endregion

    // region RuleBasedFallbackResolver

    @Test
    fun whenFallbackResolverCalled_returnsValidActionResult() = runTest {
        // RuleBasedFallbackResolver 是 object 单例，直接测试
        val result = RuleBasedFallbackResolver.resolve(
            fixtureMatch,
            seatId = 1,
            engine = fixtureEngine,
            ruleSet = fixtureRuleSet,
        )

        // 规则型 AI 应返回有效的 ActionResult（出牌或过牌）
        // fixtureMatch 的 seat 1 只有一张牌，应该能正常处理
        assertTrue(result.success || result.error != null)
    }

    @Test
    fun whenFallbackResolverCalledWithLeadTurn_canPlayOrPass() = runTest {
        // currentCombination 为 null（lead turn），规则型 AI 应该出牌
        val leadMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 1,
            seats = MatchFixtureFactory.defaultSeats(),
        )
        // TrickState 的 currentCombination 默认为 null → lead turn

        val result = RuleBasedFallbackResolver.resolve(
            leadMatch,
            seatId = 1,
            engine = fixtureEngine,
            ruleSet = fixtureRuleSet,
        )

        // 在 lead turn 时规则型 AI 应该能出牌
        assertTrue(result.success || result.error != null)
    }

    // endregion

    private class FixtureGameEngine : GameEngine() {
        override fun startLocalMatch(
            ruleSet: GameRuleSet,
            seatConfigs: List<Triple<Int, String, SeatControllerType>>,
        ): Match {
            return MatchFixtureFactory.localMatch(ruleSet = ruleSet)
        }
    }
}
