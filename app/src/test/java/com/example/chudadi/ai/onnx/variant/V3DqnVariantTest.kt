package com.example.chudadi.ai.onnx.variant

import com.example.chudadi.ai.onnx.ActionFeatureEncoder
import com.example.chudadi.ai.onnx.GameStateEncoder
import com.example.chudadi.ai.onnx.pipeline.V3DqnPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V3DqnVariantTest {

    @Test
    fun createDefault_usesTwoInputV3Contract() {
        val variant = V3DqnVariant.createDefault()
        val contract = variant.ioContract

        assertEquals("v3_dqn", variant.name)
        assertEquals("chudadi_v3_01.onnx", variant.modelFileName)
        assertEquals("obs", contract.obsInputName)
        assertEquals("actions", contract.actionsInputName)
        assertEquals("Q", contract.outputName)
        assertEquals(GameStateEncoder.INPUT_DIM, contract.obsDim)
        assertEquals(ActionFeatureEncoder.ACTION_FEATURE_DIM, contract.actionDim)
        assertEquals(1, contract.outputDim)
        assertNull(contract.historyInputName)
        assertNull(contract.historyPlayers)
        assertNull(contract.historyLen)
        assertNull(contract.historyDim)
        assertTrue(variant.createPipeline() is V3DqnPipeline)
    }
}
