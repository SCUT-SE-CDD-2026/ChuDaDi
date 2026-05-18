package com.example.chudadi.network.room

import java.io.IOException

class UserVisibleRoomException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
