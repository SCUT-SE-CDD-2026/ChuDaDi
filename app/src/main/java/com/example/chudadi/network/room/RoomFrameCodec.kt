package com.example.chudadi.network.room

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.serialization.json.Json

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
        val length = input.readInt()
        require(length in 1..MAX_FRAME_BYTES) { "Invalid room message length: $length" }
        val payload = ByteArray(length)
        input.readFully(payload)
        return json.decodeFromString(RoomWireMessage.serializer(), payload.decodeToString())
    }

    private companion object {
        const val MAX_FRAME_BYTES = 64 * 1024
    }
}
