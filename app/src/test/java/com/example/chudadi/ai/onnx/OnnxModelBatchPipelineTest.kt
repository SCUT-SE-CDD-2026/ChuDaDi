package com.example.chudadi.ai.onnx

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnnxModelBatchPipelineTest {

    private companion object {
        const val OBS_DIM = 3
        const val ACTION_DIM = 4
        const val ACTION_COUNT = 3
        const val ACTION_START_1 = 10f
        const val ACTION_START_2 = 20f
        const val ACTION_START_3 = 30f
        const val OBS_START = 1f
        const val ZERO_TOLERANCE = 0f

        const val LONG_VALUE_1 = 0.6f
        const val LONG_VALUE_2 = 0.2f
        const val LONG_VALUE_3 = -0.1f
        const val LONG_VALUE_4 = 0.9f
        const val SHORT_VALUE_1 = 0.7f
        const val SHORT_VALUE_2 = 0.5f
        const val TRIM_EXPECTED_SIZE = 3
        const val PADDED_EXPECTED_SIZE = 4
        const val PADDED_SLICE_END = 2
        const val PADDED_INDEX_1 = 2
        const val PADDED_INDEX_2 = 3
    }

    @Test
    fun buildBatchedActionInput_repeatsObsAndPreservesActionOrder() {
        val obs = buildContinuousVector(start = OBS_START, size = OBS_DIM)
        val actionFeatures = listOf(
            buildContinuousVector(start = ACTION_START_1, size = ACTION_DIM),
            buildContinuousVector(start = ACTION_START_2, size = ACTION_DIM),
            buildContinuousVector(start = ACTION_START_3, size = ACTION_DIM),
        )

        val batched = buildBatchedActionInput(obs = obs, actionFeatures = actionFeatures)

        assertEquals(ACTION_COUNT, batched.batchSize)
        assertEquals(OBS_DIM, batched.obsDim)
        assertEquals(ACTION_COUNT * OBS_DIM, batched.obsBatch.size)
        assertEquals(ACTION_COUNT * ACTION_DIM, batched.actionsBatch.size)

        assertArrayEquals(
            FloatArray(ACTION_COUNT * OBS_DIM) { index -> obs[index % OBS_DIM] },
            batched.obsBatch,
            ZERO_TOLERANCE,
        )
        assertArrayEquals(
            actionFeatures.flatMap { it.asList() }.toFloatArray(),
            batched.actionsBatch,
            ZERO_TOLERANCE,
        )
    }

    @Test
    fun alignActionValues_returnsExpectedLengthAndKeepsOrder() {
        val longValues = floatArrayOf(LONG_VALUE_1, LONG_VALUE_2, LONG_VALUE_3, LONG_VALUE_4)
        val trimmed = alignActionValues(values = longValues, expectedSize = TRIM_EXPECTED_SIZE)
        assertArrayEquals(longValues.copyOf(TRIM_EXPECTED_SIZE), trimmed, ZERO_TOLERANCE)

        val shortValues = floatArrayOf(SHORT_VALUE_1, SHORT_VALUE_2)
        val padded = alignActionValues(values = shortValues, expectedSize = PADDED_EXPECTED_SIZE)
        assertEquals(PADDED_EXPECTED_SIZE, padded.size)
        assertArrayEquals(shortValues, padded.copyOfRange(0, PADDED_SLICE_END), ZERO_TOLERANCE)
        assertTrue(padded[PADDED_INDEX_1].isInfinite() && padded[PADDED_INDEX_1] < ZERO_TOLERANCE)
        assertTrue(padded[PADDED_INDEX_2].isInfinite() && padded[PADDED_INDEX_2] < ZERO_TOLERANCE)
    }

    private fun buildContinuousVector(start: Float, size: Int): FloatArray {
        return FloatArray(size) { index -> start + index.toFloat() }
    }
}
