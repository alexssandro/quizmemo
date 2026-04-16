package com.quizmemo.offline

internal data class BundledQuestion(
    val id: Int,
    val text: String,
    val level: String,
    val explanation: String?,
    val options: List<BundledOption>,
)

internal data class BundledOption(
    val id: Int,
    val text: String,
    val isCorrect: Boolean,
)
