package com.example.chudadi.ui.room

sealed interface RoomExternalEvent {
    data object RequestStartHostListening : RoomExternalEvent
}
