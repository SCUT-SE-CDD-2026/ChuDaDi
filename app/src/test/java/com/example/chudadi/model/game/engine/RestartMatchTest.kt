package com.example.chudadi.model.game.engine

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RestartMatchTest {
    @Test
    fun startLocalMatch_createsFreshMatchStateEachTime() {
        val engine = GameEngine(random = Random(1))

        val firstMatch = engine.startLocalMatch()
        val secondMatch = engine.startLocalMatch()

        assertNotEquals(firstMatch.matchId, secondMatch.matchId)
        assertNull(secondMatch.result)
        assertTrue(secondMatch.seats.all { it.hand.size == 13 })
    }
}
