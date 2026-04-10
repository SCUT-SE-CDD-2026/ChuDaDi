package com.example.chudadi.ai.base

/**
 * AI难度级别枚举
 *
 * 定义三种难度级别，影响AI的决策策略：
 * - EASY: 简单难度，AI会犯一些明显的错误，适合新手练习
 * - NORMAL: 普通难度，AI有一定策略但非最优，适合一般玩家
 * - HARD: 困难难度，AI始终选择胜率最高的策略，适合高手挑战
 */
enum class AIDifficulty {
    EASY,
    NORMAL,
    HARD,
}
