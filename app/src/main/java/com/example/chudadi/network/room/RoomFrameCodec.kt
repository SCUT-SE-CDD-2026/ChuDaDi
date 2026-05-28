package com.example.chudadi.network.room

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class RoomFrameCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @Synchronized
    @Throws(IOException::class)
    fun writeMessage(output: DataOutputStream, message: RoomWireMessage) {
        val payload = json.encodeToString(RoomWireMessage.serializer(), message).encodeToByteArray()
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    @Throws(IOException::class)
    fun readMessage(input: DataInputStream): RoomWireMessage {
        return try {
            val length = input.readInt()
            require(length in 1..MAX_FRAME_BYTES) { "Invalid room message length: $length" }
            val payload = ByteArray(length)
            input.readFully(payload)
            json.decodeFromString(RoomWireMessage.serializer(), payload.decodeToString())
        } catch (e: CancellationException) {
            failRead(e)
        } catch (e: IOException) {
            failRead(e)
        } catch (e: SerializationException) {
            failRead(IOException("Failed to decode room protocol message", e))
        } catch (e: IllegalArgumentException) {
            failRead(IOException("Invalid room protocol message", e))
        }
    }

    private companion object {
        const val MAX_FRAME_BYTES = 64 * 1024
    }
}

private fun <T> failRead(exception: Exception): T {
    throw exception
}
