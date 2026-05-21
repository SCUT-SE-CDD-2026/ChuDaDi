package com.example.chudadi.model.game.engine

/**
 * 当 TurnResolver 遇到无法继续的异常状态时抛出。
 * 例如：找不到下一个 active seat（理论上不应发生）。
 */
class TurnResolutionException(
    message: String,
) : IllegalStateException(message)
