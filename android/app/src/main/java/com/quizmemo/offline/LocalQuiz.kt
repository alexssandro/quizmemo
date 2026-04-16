package com.quizmemo.offline

import android.content.Context
import com.quizmemo.network.AnswerRequest
import com.quizmemo.network.AnswerResponse
import com.quizmemo.network.AppJson
import com.quizmemo.network.DashboardResponseDto
import com.quizmemo.network.LevelStatDto
import com.quizmemo.network.NextLevelProgressDto
import com.quizmemo.network.OptionDto
import com.quizmemo.network.QuestionDto
import com.quizmemo.network.SessionStatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * In-memory, fully-offline stand-in for the REST API. Mirrors the shape and semantics of
 * `QuizApi` so the UI can consume the same DTOs.
 */
object LocalQuiz {
    private val CEFR_ORDER = listOf("A1", "A2", "B1", "B2", "C1", "C2")
    private const val MIN_ATTEMPTS = 3
    private const val PASS_THRESHOLD = 0.7

    private val answers = mutableListOf<LocalAnswer>()
    private var sessionAttempt: Int = 1
    private var sessionAttemptDate: LocalDate? = null

    private var stateFile: File? = null

    fun init(context: Context) {
        if (stateFile != null) return
        val file = File(context.filesDir, "local_quiz_state.json")
        stateFile = file
        runCatching {
            val raw = file.readText()
            if (raw.isBlank()) return@runCatching
            val saved = AppJson.decodeFromString(PersistedState.serializer(), raw)
            answers.clear()
            saved.answers.forEach {
                answers += LocalAnswer(
                    questionId = it.questionId,
                    isCorrect = it.isCorrect,
                    sessionDate = LocalDate.parse(it.sessionDate),
                    sessionAttempt = it.sessionAttempt,
                )
            }
            sessionAttempt = saved.sessionAttempt
            sessionAttemptDate = saved.sessionAttemptDate?.let(LocalDate::parse)
        }.onFailure { if (it !is FileNotFoundException) it.printStackTrace() }
    }

    private suspend fun persist() {
        val file = stateFile ?: return
        val snapshot = PersistedState(
            answers = answers.map {
                PersistedAnswer(it.questionId, it.isCorrect, it.sessionDate.toString(), it.sessionAttempt)
            },
            sessionAttempt = sessionAttempt,
            sessionAttemptDate = sessionAttemptDate?.toString(),
        )
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(AppJson.encodeToString(snapshot)) }
        }
    }

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    private fun currentAttempt(): Int =
        if (sessionAttemptDate == today()) sessionAttempt else 1

    private fun answersForCurrent(): List<LocalAnswer> {
        val t = today()
        val attempt = currentAttempt()
        return answers.filter { it.sessionDate == t && it.sessionAttempt == attempt }
    }

    suspend fun next(): QuestionDto? {
        val current = answersForCurrent()
        if (current.any { !it.isCorrect }) return null
        val answeredIds = current.map { it.questionId }.toSet()

        val stats = answers.groupBy { it.questionId }
            .mapValues { (_, v) -> v.count { !it.isCorrect } to v.count { it.isCorrect } }

        val next = BundledQuestions.all
            .filter { it.id !in answeredIds }
            .sortedWith(
                compareBy<BundledQuestion> { if (stats.containsKey(it.id)) 1 else 0 }
                    .thenByDescending { stats[it.id]?.first ?: 0 }
                    .thenBy { stats[it.id]?.second ?: 0 }
                    .thenBy { it.id },
            )
            .firstOrNull() ?: return null

        return QuestionDto(
            id = next.id,
            text = next.text,
            options = next.options.map { OptionDto(it.id, it.text) },
        )
    }

    suspend fun answer(req: AnswerRequest): AnswerResponse {
        val today = today()
        if (sessionAttemptDate != today) {
            sessionAttemptDate = today
            sessionAttempt = currentAttempt()
        }
        val current = answersForCurrent()

        require(current.none { !it.isCorrect }) { "session already ended for today" }
        require(current.none { it.questionId == req.questionId }) { "question already answered in this session" }

        val question = BundledQuestions.all.first { it.id == req.questionId }
        val selected = question.options.first { it.id == req.optionId }
        val correct = question.options.first { it.isCorrect }

        answers += LocalAnswer(
            questionId = req.questionId,
            isCorrect = selected.isCorrect,
            sessionDate = today,
            sessionAttempt = sessionAttempt,
        )
        persist()

        return AnswerResponse(
            correct = selected.isCorrect,
            correctOptionId = correct.id,
            correctOptionText = correct.text,
            explanation = question.explanation,
            todayPoints = current.count { it.isCorrect } + (if (selected.isCorrect) 1 else 0),
            sessionEnded = !selected.isCorrect,
        )
    }

    suspend fun status(): SessionStatusDto {
        val current = answersForCurrent()
        val ended = current.any { !it.isCorrect }
        return SessionStatusDto(
            sessionDate = today().toString(),
            attempt = currentAttempt(),
            points = current.count { it.isCorrect },
            sessionEnded = ended,
            canPlay = !ended && (BundledQuestions.all.size - current.size) > 0,
        )
    }

    suspend fun resetSession(): SessionStatusDto {
        val today = today()
        val attempt = currentAttempt()
        val hasAnswers = answers.any { it.sessionDate == today && it.sessionAttempt == attempt }
        sessionAttemptDate = today
        sessionAttempt = if (hasAnswers) attempt + 1 else attempt
        persist()
        return SessionStatusDto(
            sessionDate = today.toString(),
            attempt = sessionAttempt,
            points = 0,
            sessionEnded = false,
            canPlay = BundledQuestions.all.isNotEmpty(),
        )
    }

    suspend fun dashboard(): DashboardResponseDto {
        val session = status()

        val totalAnswers = answers.size
        val totalCorrect = answers.count { it.isCorrect }
        val bySession = answers.groupBy { it.sessionDate to it.sessionAttempt }
        val totalSessions = bySession.size
        val bestSessionScore = bySession.values.maxOfOrNull { grp -> grp.count { it.isCorrect } } ?: 0

        val levelById = BundledQuestions.all.associate { it.id to it.level }
        val byLevel = CEFR_ORDER.map { level ->
            val ofLevel = answers.filter { levelById[it.questionId] == level }
            val attempts = ofLevel.size
            val correct = ofLevel.count { it.isCorrect }
            val accuracy = if (attempts > 0) correct.toDouble() / attempts else 0.0
            LevelStatDto(level, attempts, correct, accuracy)
        }

        // Break at the first level that fails the bar — we trust the curriculum order, not
        // cherry-picked wins. Matches backend CefrLevels.Estimate exactly.
        var estimated: String? = null
        for (stat in byLevel) {
            if (stat.attempts >= MIN_ATTEMPTS && stat.accuracy >= PASS_THRESHOLD) estimated = stat.level
            else break
        }
        val nextLevel = nextLevelAfter(estimated)
        val progress = nextLevel?.let { level ->
            val s = byLevel.first { it.level == level }
            NextLevelProgressDto(
                level = level,
                attempts = s.attempts,
                attemptsNeeded = MIN_ATTEMPTS,
                accuracy = s.accuracy,
                accuracyNeeded = PASS_THRESHOLD,
                attemptsMet = s.attempts >= MIN_ATTEMPTS,
                accuracyMet = s.accuracy >= PASS_THRESHOLD,
            )
        }

        return DashboardResponseDto(
            session = session,
            totalSessions = totalSessions,
            totalAnswers = totalAnswers,
            totalCorrect = totalCorrect,
            bestSessionScore = bestSessionScore,
            estimatedLevel = estimated,
            nextLevel = nextLevel,
            nextLevelProgress = progress,
            byLevel = byLevel,
            recommendation = recommendationFor(estimated),
        )
    }

    private fun nextLevelAfter(level: String?): String? {
        if (level.isNullOrEmpty()) return CEFR_ORDER.first()
        val idx = CEFR_ORDER.indexOf(level)
        return CEFR_ORDER.getOrNull(idx + 1)
    }

    private fun recommendationFor(level: String?): String = when (level) {
        null, "" ->
            "Not enough data yet. Answer at least 3 A1 questions with 70%+ accuracy to unlock your first level estimate. " +
                "Start by watching subject-verb agreement, basic articles (a/an/the), and simple prepositions (in/on/at)."
        "A1" ->
            "To reach A2: don't drop subjects (\"I believe...\" not just \"believe...\"), watch agreement with " +
                "\"there is/are anything\", and drop the preposition in time phrases like \"the same day\" (not \"in the same day\")."
        "A2" ->
            "To reach B1: master separable phrasal verbs and pronoun placement (\"back itself up\", not \"back up itself\"), " +
                "always use gerund after \"end up\"/\"stop\"/\"keep\", and watch out for false cognates — \"notice\" in English " +
                "does NOT mean \"avisar\"."
        "B1" ->
            "To reach B2: stop using literal Portuguese calques like \"gain time\" (→ buy time), \"put the password\" (→ enter), " +
                "\"as a repair\" (→ to make up for it). Learn the make/do/take/have collocation families, and the difference between " +
                "\"trust\" (confiar) and \"assume\" (assumir)."
        "B2" ->
            "To reach C1: learn native hedging and softeners (\"I was wondering if...\", \"would you mind...\", \"could you\" " +
                "instead of \"is it possible to\"). Master advanced phrasal verbs (walk through, run into, come up with, " +
                "make up for), and focus on register — when to be casual, diplomatic, or formal."
        "C1" ->
            "To reach C2: polish subtle register shifts, culture-bound idioms, and precision between near-synonyms " +
                "(assume vs. suppose vs. reckon vs. figure; handle vs. deal with vs. address vs. tackle). Consume native media, " +
                "and pay attention to the rare misses."
        "C2" ->
            "You're at the top of the scale. Keep consuming native media, pay attention to idiomatic fine-tuning, " +
                "and focus on domain-specific register (legal, technical, casual) since that's where the final polish lives."
        else -> ""
    }
}

internal data class LocalAnswer(
    val questionId: Int,
    val isCorrect: Boolean,
    val sessionDate: LocalDate,
    val sessionAttempt: Int,
)

@Serializable
private data class PersistedState(
    val answers: List<PersistedAnswer> = emptyList(),
    val sessionAttempt: Int = 1,
    val sessionAttemptDate: String? = null,
)

@Serializable
private data class PersistedAnswer(
    val questionId: Int,
    val isCorrect: Boolean,
    val sessionDate: String,
    val sessionAttempt: Int,
)
