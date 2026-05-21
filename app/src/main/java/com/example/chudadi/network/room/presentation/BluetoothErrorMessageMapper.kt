package com.example.chudadi.network.room.presentation

import com.example.chudadi.network.room.UserVisibleRoomException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Maps bluetooth failures and known bluetooth error cases into user-facing messages.
 *
 * Keep copy changes here so transport and socket classes stay free of UI wording decisions.
 */
class BluetoothErrorMessageMapper {
    val hostUnsupportedBluetoothMessage: String = "当前设备不支持蓝牙"
    val connectUnsupportedBluetoothMessage: String = "设备不支持蓝牙"
    val createRoomMissingConnectPermissionMessage: String = "缺少蓝牙连接权限，无法创建房间"
    val hostListeningMissingConnectPermissionMessage: String = "缺少蓝牙连接权限，无法启动房主监听"
    val missingConnectPermissionMessage: String = "缺少蓝牙连接权限"
    val missingScanPermissionMessage: String = "缺少蓝牙扫描权限"
    val listenStartFailedMessage: String = "蓝牙房间监听启动失败"
    val connectFailedRetryMessage: String = "连接失败，请重试"
    val discoveryStartFailedMessage: String = "蓝牙扫描启动失败，请确认蓝牙已开启且当前未被系统占用"
    val roomConnectionAbnormalMessage: String = "房间连接异常，请重新加入房间"

    fun toUserFacingMessage(error: Throwable, defaultMessage: String): String {
        val rawMessage = error.message?.trim().orEmpty()
        return when {
            error is UserVisibleRoomException && rawMessage.isNotBlank() -> rawMessage
            error is CancellationException -> defaultMessage
            error.isRoomProtocolFailure() -> roomConnectionAbnormalMessage
            rawMessage.isUserFacingChineseText() -> rawMessage
            else -> mapKnownFailure(error, rawMessage, defaultMessage)
        }
    }

    private fun mapKnownFailure(
        error: Throwable,
        rawMessage: String,
        defaultMessage: String,
    ): String {
        return when {
            error is SecurityException -> "缺少蓝牙权限，请授权后重试"

            error is IllegalArgumentException &&
                rawMessage.contains("address", ignoreCase = true) -> {
                "蓝牙设备地址无效，请重新搜索房间"
            }

            error is IOException && rawMessage.containsAnyIgnoreCase(
                "read failed",
                "socket closed",
                "bt socket closed",
                "broken pipe",
                "connection reset",
                "software caused connection abort",
            ) -> {
                "蓝牙连接已断开，请确认双方设备蓝牙状态后重试"
            }

            error is IOException && rawMessage.containsAnyIgnoreCase(
                "timed out",
                "timeout",
            ) -> {
                "蓝牙连接超时，请确认双方设备距离和蓝牙状态后重试"
            }

            error is IOException && rawMessage.containsAnyIgnoreCase(
                "service discovery failed",
                "connection refused",
                "connection failure",
            ) -> {
                "蓝牙连接失败，请确认房主已开启房间后重试"
            }

            rawMessage.isNotBlank() && rawMessage.isUserFacingChineseText() -> rawMessage
            else -> defaultMessage
        }
    }

    private fun Throwable.isRoomProtocolFailure(): Boolean {
        val rawMessage = message.orEmpty()
        return this is SerializationException ||
            cause is SerializationException ||
            rawMessage.containsAnyIgnoreCase(
                "Failed to decode room protocol message",
                "Invalid room protocol message",
            )
    }

    private fun String.containsAnyIgnoreCase(vararg keywords: String): Boolean {
        return keywords.any { keyword -> contains(keyword, ignoreCase = true) }
    }

    private fun String.isUserFacingChineseText(): Boolean {
        return any { it in '\u4E00'..'\u9FFF' } &&
            !containsAnyIgnoreCase(
                "TimeoutCancellationException",
                "SerializationException",
                "IOException",
                "CancellationException",
            )
    }
}
