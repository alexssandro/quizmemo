package com.quizmemo.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable data class GoogleLoginRequest(val idToken: String)
@Serializable data class TokenResponse(val token: String)

@Serializable data class OptionDto(val id: Int, val text: String)
@Serializable data class QuestionDto(
    val id: Int,
    val text: String,
    val options: List<OptionDto>,
)

@Serializable data class LevelStatDto(
    val level: String,
    val attempts: Int,
    val correct: Int,
    val accuracy: Double,
)

@Serializable data class LevelResponseDto(
    val estimatedLevel: String?,
    val nextLevel: String?,
    val totalAnswers: Int,
    val byLevel: List<LevelStatDto>,
    val recommendation: String,
)

@Serializable data class AnswerRequest(val questionId: Int, val optionId: Int)
@Serializable data class AnswerResponse(
    val correct: Boolean,
    val correctOptionId: Int,
    val correctOptionText: String,
    val explanation: String? = null,
    val todayPoints: Int,
    val sessionEnded: Boolean,
)

@Serializable data class SessionStatusDto(
    val sessionDate: String,
    val attempt: Int,
    val points: Int,
    val sessionEnded: Boolean,
    val canPlay: Boolean,
)

@Serializable data class HistoryItemDto(
    val questionId: Int,
    val questionText: String,
    val isCorrect: Boolean,
    val sessionDate: String,
    val sessionAttempt: Int,
    val answeredAt: String,
)

@Serializable data class NextLevelProgressDto(
    val level: String,
    val attempts: Int,
    val attemptsNeeded: Int,
    val accuracy: Double,
    val accuracyNeeded: Double,
    val attemptsMet: Boolean,
    val accuracyMet: Boolean,
)

@Serializable data class DashboardResponseDto(
    val session: SessionStatusDto,
    val totalSessions: Int,
    val totalAnswers: Int,
    val totalCorrect: Int,
    val bestSessionScore: Int,
    val estimatedLevel: String?,
    val nextLevel: String?,
    val nextLevelProgress: NextLevelProgressDto?,
    val byLevel: List<LevelStatDto>,
    val recommendation: String,
)

interface QuizApi {
    @POST("auth/google")
    suspend fun google(@Body body: GoogleLoginRequest): TokenResponse

    @GET("quiz/next")
    suspend fun next(): QuestionDto?

    @POST("quiz/answer")
    suspend fun answer(@Body body: AnswerRequest): AnswerResponse

    @GET("quiz/status")
    suspend fun status(): SessionStatusDto

    @POST("quiz/session/reset")
    suspend fun resetSession(): SessionStatusDto

    @GET("quiz/history")
    suspend fun history(): List<HistoryItemDto>

    @GET("quiz/level")
    suspend fun level(): LevelResponseDto

    @GET("quiz/dashboard")
    suspend fun dashboard(): DashboardResponseDto
}
