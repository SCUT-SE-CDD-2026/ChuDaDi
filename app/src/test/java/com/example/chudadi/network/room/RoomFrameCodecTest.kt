package com.example.chudadi.network.room

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RoomFrameCodecTest {
    private val codec = RoomFrameCodec()

    @Test
    fun readMessage_whenSerializationFails_wrapsAsIOException() {
        val input = frameInput("not-json".encodeToByteArray())

        val error = assertReadThrowsIOException(input)

        assertEquals("Failed to decode room protocol message", error.message)
        assertTrue(error.cause is SerializationException)
    }

    @Test
    fun readMessage_whenFrameLengthInvalid_wrapsAsIOException() {
        val input = frameLengthInput(-1)

        val error = assertReadThrowsIOException(input)

        assertEquals("Invalid room protocol message", error.message)
        assertTrue(error.cause is IllegalArgumentException)
    }

    private fun assertReadThrowsIOException(input: DataInputStream): IOException {
        try {
            codec.readMessage(input)
            fail("Expected IOException")
        } catch (error: IOException) {
            return error
        } catch (error: Throwable) {
            fail("Expected IOException but caught ${error::class.simpleName}")
        }
        error("unreachable")
    }

    private fun frameInput(payload: ByteArray): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(payload.size)
            output.write(payload)
        }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }

    private fun frameLengthInput(length: Int): DataInputStream {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(length)
        }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }
}
