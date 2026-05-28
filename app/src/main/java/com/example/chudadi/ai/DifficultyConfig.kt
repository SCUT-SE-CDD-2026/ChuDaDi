package com.example.chudadi.ai

import com.example.chudadi.ai.base.AIDifficulty

/**
 * 难度配置数据类
 *
 * 定义不同难度级别的参数配置，包括温度参数、探索率等。
 * 这些参数影响AI的决策策略和行为。
 *
 * @property difficulty 难度级别
 * @property temperature 温度参数（用于概率分布的平滑/锐化）
 * @property explorationRate 探索率（随机选择的概率）
 * @property topK 从Top-K中选择（K值）
 * @property mistakeProbability 犯错概率（简单难度下选择非最优策略的概率）
 */
data class DifficultyConfig(
    val difficulty: AIDifficulty,
    val temperature: Float,
    val explorationRate: Float,
    val topK: Int,
    val mistakeProbability: Float,
) {
    companion object {
        /**
         * 获取指定难度的默认配置
         */
        fun forDifficulty(difficulty: AIDifficulty): DifficultyConfig {
            return when (difficulty) {
                AIDifficulty.EASY -> EASY_CONFIG
                AIDifficulty.NORMAL -> NORMAL_CONFIG
                AIDifficulty.HARD -> HARD_CONFIG
            }
        }

        /**
         * 简单难度配置
         * - 高温度（概率分布更平滑）
         * - 高探索率（更多随机选择）
         * - 高犯错概率
         */
        private val EASY_CONFIG = DifficultyConfig(
            difficulty = AIDifficulty.EASY,
            temperature = 1.5f,
            explorationRate = 0.7f,
            topK = 5,
            mistakeProbability = 0.5f,
        )

        /**
         * 普通难度配置
         * - 中等温度
         * - 中等探索率
         * - 中等犯错概率
         */
        private val NORMAL_CONFIG = DifficultyConfig(
            difficulty = AIDifficulty.NORMAL,
            temperature = 1.0f,
            explorationRate = 0.3f,
            topK = 3,
            mistakeProbability = 0.2f,
        )

        /**
         * 困难难度配置
         * - 低温度（概率分布更尖锐）
         * - 低探索率（更少随机选择）
         * - 无犯错
         */
        private val HARD_CONFIG = DifficultyConfig(
            difficulty = AIDifficulty.HARD,
            temperature = 0.5f,
            explorationRate = 0.0f,
            topK = 1,
            mistakeProbability = 0.0f,
        )
    }
}
