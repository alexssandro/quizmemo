package com.quizmemo.network

import com.quizmemo.offline.LocalQuiz

/**
 * Single switch between the offline, bundled implementation and the REST backend.
 * Flip `OFFLINE` to `false` to route UI calls back through [ApiClient]. All DTOs are
 * shared so no call-site changes are needed when toggling.
 */
object QuizRepository {
    const val OFFLINE: Boolean = true

    suspend fun next(): QuestionDto? =
        if (OFFLINE) LocalQuiz.next() else ApiClient.api.next()

    suspend fun answer(req: AnswerRequest): AnswerResponse =
        if (OFFLINE) LocalQuiz.answer(req) else ApiClient.api.answer(req)

    suspend fun status(): SessionStatusDto =
        if (OFFLINE) LocalQuiz.status() else ApiClient.api.status()

    suspend fun resetSession(): SessionStatusDto =
        if (OFFLINE) LocalQuiz.resetSession() else ApiClient.api.resetSession()

    suspend fun dashboard(): DashboardResponseDto =
        if (OFFLINE) LocalQuiz.dashboard() else ApiClient.api.dashboard()
}
