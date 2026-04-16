package com.quizmemo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quizmemo.auth.GoogleAuth
import com.quizmemo.network.AnswerRequest
import com.quizmemo.network.ApiClient
import com.quizmemo.network.DashboardResponseDto
import com.quizmemo.network.GoogleLoginRequest
import com.quizmemo.network.LevelStatDto
import com.quizmemo.network.NextLevelProgressDto
import com.quizmemo.network.QuestionDto
import com.quizmemo.network.QuizRepository
import com.quizmemo.network.SessionStatusDto
import com.quizmemo.network.TokenStore
import com.quizmemo.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.sin
import kotlin.random.Random

// Caps the visual progress bar width at N points; beyond that the bar stays full.
private const val PROGRESS_BAR_MAX = 20

// --- Dark theme palette ---
private val DarkBg = Color(0xFF1A1A2E)
private val DarkSurface = Color(0xFF16213E)
private val DarkCard = Color(0xFF1F2B47)
private val DarkCardLight = Color(0xFF263352)
private val TextPrimary = Color(0xFFE8E8F0)
private val TextSecondary = Color(0xFF8B8FA8)
private val TextMuted = Color(0xFF5C6080)
private val AccentGradientStart = Color(0xFFFF6B6B)
private val AccentGradientEnd = Color(0xFF845EC2)
private val AccentBlue = Color(0xFF4EA8DE)
private val AccentBlueBright = Color(0xFF5EB5F7)
private val GreenAccent = Color(0xFF4ADE80)
private val GreenDark = Color(0xFF166534)
private val GreenSurface = Color(0xFF14532D)
private val RedAccent = Color(0xFFF87171)
private val RedDark = Color(0xFF991B1B)
private val RedSurface = Color(0xFF7F1D1D)
private val WarmYellow = Color(0xFFFCD34D)

private enum class Screen { Login, Dashboard, Quiz }

@Composable
fun QuizApp() {
    // In offline mode the backend is disabled — skip the login screen entirely and
    // land on the dashboard. `LoginScreen` stays in the file (not removed) so flipping
    // `QuizRepository.OFFLINE` back to false restores the full flow.
    var screen by remember { mutableStateOf(if (QuizRepository.OFFLINE) Screen.Dashboard else Screen.Login) }
    // Bumping this key forces the QuizScreen to fully reset its state when the user
    // re-enters (so "Restart" / "Play again" lands on a blank question-1).
    var quizEpoch by remember { mutableStateOf(0) }

    when (screen) {
        Screen.Login -> LoginScreen(onLoggedIn = { screen = Screen.Dashboard })
        Screen.Dashboard -> DashboardScreen(
            onStartSession = {
                quizEpoch += 1
                screen = Screen.Quiz
            },
        )
        Screen.Quiz -> key(quizEpoch) {
            QuizScreen(onBack = { screen = Screen.Dashboard })
        }
    }
}

// --- Login ---

private data class LoginError(val summary: String, val detail: String?)

private fun mapLoginError(t: Throwable): LoginError {
    val raw = t.message
    return when (t) {
        is UnknownHostException -> LoginError(
            "Can't reach the server — check the API host in local.properties.",
            raw,
        )
        is ConnectException, is SocketTimeoutException -> LoginError(
            "The server didn't respond. Make sure it's running and that your phone is on the same network.",
            raw,
        )
        else -> LoginError(
            raw?.takeIf { it.isNotBlank() }?.let { if (it.length > 140) "Sign-in failed." else "Sign-in failed: $it" }
                ?: "Sign-in failed.",
            raw,
        )
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var error by remember { mutableStateOf<LoginError?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "QuizMemo",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Daily spaced-repetition quiz",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(40.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    disabledContainerColor = AccentBlue.copy(alpha = 0.5f),
                ),
                onClick = {
                    loading = true
                    error = null
                    showDetails = false
                    scope.launch {
                        runCatching {
                            val idToken = GoogleAuth.getIdToken(context)
                            ApiClient.api.google(GoogleLoginRequest(idToken))
                        }.onSuccess {
                            TokenStore.set(it.token)
                            onLoggedIn()
                        }.onFailure {
                            error = mapLoginError(it)
                            loading = false
                        }
                    }
                },
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "Sign in with Google",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }

            error?.let { err ->
                Spacer(Modifier.height(20.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = RedSurface.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.4f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            err.summary,
                            color = RedAccent,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                        )
                        if (!err.detail.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (showDetails) "Hide details" else "Show details",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { showDetails = !showDetails },
                            )
                            if (showDetails) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    err.detail,
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "API: ${BuildConfig.API_BASE_URL}",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Quiz state ---

private data class QuizUiState(
    val question: QuestionDto? = null,
    val points: Int = 0,
    val ended: Boolean = false,
    val answerResult: AnswerResultState? = null,
    val answering: Boolean = false,
    val loading: Boolean = true,
    val questionNumber: Int = 1,
)

private data class AnswerResultState(
    val correct: Boolean,
    val correctOptionId: Int,
    val selectedOptionId: Int,
    val correctOptionText: String,
    val selectedOptionText: String,
    val explanation: String?,
    val sessionEnded: Boolean,
)

@Composable
fun QuizScreen(onBack: () -> Unit) {
    var state by remember { mutableStateOf(QuizUiState()) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() = coroutineScope {
        val nextDef = async { runCatching { QuizRepository.next() }.getOrNull() }
        val statusDef = async { runCatching { QuizRepository.status() }.getOrNull() }
        val status = statusDef.await()
        state = state.copy(
            question = nextDef.await(),
            points = status?.points ?: state.points,
            ended = status?.sessionEnded ?: state.ended,
            loading = false,
        )
    }

    LaunchedEffect(Unit) { refresh() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackChip(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    ProgressHeader(points = state.points)
                }
            }

            Spacer(Modifier.height(28.dp))

            when {
                state.loading -> {
                    Spacer(Modifier.height(80.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 3.dp)
                    }
                }
                state.ended && state.answerResult == null -> SessionEndedContent()
                state.question == null && state.answerResult == null -> NoQuestionsContent()
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "Question ${state.questionNumber}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        state.question!!.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        lineHeight = 26.sp,
                        fontSize = 18.sp,
                    )

                    Spacer(Modifier.height(28.dp))

                    state.question!!.options.forEach { opt ->
                        OptionRow(
                            text = opt.text,
                            isSelected = state.answerResult?.selectedOptionId == opt.id,
                            isCorrectOption = state.answerResult?.correctOptionId == opt.id,
                            hasResult = state.answerResult != null,
                            wasCorrectAnswer = state.answerResult?.correct == true,
                            enabled = state.answerResult == null && !state.answering,
                            onClick = {
                                scope.launch {
                                    state = state.copy(answering = true)
                                    val currentQuestion = state.question ?: return@launch
                                    runCatching {
                                        QuizRepository.answer(AnswerRequest(currentQuestion.id, opt.id))
                                    }.onSuccess { res ->
                                        state = state.copy(
                                            points = res.todayPoints,
                                            ended = res.sessionEnded,
                                            answering = false,
                                            answerResult = AnswerResultState(
                                                correct = res.correct,
                                                correctOptionId = res.correctOptionId,
                                                selectedOptionId = opt.id,
                                                correctOptionText = res.correctOptionText,
                                                selectedOptionText = opt.text,
                                                explanation = res.explanation,
                                                sessionEnded = res.sessionEnded,
                                            ),
                                        )
                                    }.onFailure {
                                        state = state.copy(answering = false)
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    state.answerResult?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        FeedbackSection(result)

                        if (result.correct && !result.sessionEnded) {
                            Spacer(Modifier.height(24.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            state = state.copy(
                                                answerResult = null,
                                                loading = true,
                                                questionNumber = state.questionNumber + 1,
                                            )
                                            refresh()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    listOf(AccentBlue, AccentBlueBright),
                                                ),
                                                shape = RoundedCornerShape(26.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "Next",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }

                        if (result.sessionEnded) {
                            Spacer(Modifier.height(20.dp))
                            SessionEndedBanner()
                            Spacer(Modifier.height(16.dp))
                            BackToDashboardButton(onBack)
                        }
                    }
                }
            }

            if (state.ended && state.answerResult == null) {
                Spacer(Modifier.height(12.dp))
                BackToDashboardButton(onBack)
            }

            if (state.question == null && state.answerResult == null && !state.loading && !state.ended) {
                Spacer(Modifier.height(12.dp))
                BackToDashboardButton(onBack)
            }

            Spacer(Modifier.height(24.dp))
        }

        // Ambient full-screen effect: confetti for correct, falling red X's for wrong.
        // Neither blocks interaction — the inline FeedbackSection carries the message,
        // explanation, and the correct answer (same presentation style as correct).
        state.answerResult?.let { result ->
            if (result.correct) ConfettiEffect() else WrongAnswerEffect()
        }
    }
}

// --- Progress header ---

@Composable
private fun ProgressHeader(points: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkCard),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (points.coerceIn(0, PROGRESS_BAR_MAX) / PROGRESS_BAR_MAX.toFloat()).coerceIn(0.03f, 1f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(AccentGradientStart, AccentGradientEnd),
                        ),
                    ),
            )
        }

        Spacer(Modifier.width(14.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkCard,
        ) {
            Text(
                "\uD83D\uDD25 $points",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
    }
}

// --- Option row ---

@Composable
private fun OptionRow(
    text: String,
    isSelected: Boolean,
    isCorrectOption: Boolean,
    hasResult: Boolean,
    wasCorrectAnswer: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            !hasResult && isSelected -> DarkCardLight
            !hasResult -> DarkCard
            isCorrectOption -> GreenSurface
            isSelected && !wasCorrectAnswer -> RedSurface
            else -> DarkCard.copy(alpha = 0.5f)
        },
        animationSpec = tween(350),
        label = "optBg",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !hasResult -> Color.Transparent
            isCorrectOption -> GreenAccent
            isSelected && !wasCorrectAnswer -> RedAccent
            else -> Color.Transparent
        },
        animationSpec = tween(350),
        label = "optBorder",
    )

    val indicatorColor by animateColorAsState(
        targetValue = when {
            hasResult && isCorrectOption -> GreenAccent
            hasResult && isSelected && !wasCorrectAnswer -> RedAccent
            else -> TextMuted
        },
        animationSpec = tween(350),
        label = "indicator",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = if (hasResult && (isCorrectOption || isSelected))
            BorderStroke(1.5.dp, borderColor) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    hasResult && isCorrectOption -> GreenAccent
                    hasResult && isSelected && !wasCorrectAnswer -> RedAccent
                    hasResult && !isCorrectOption && !isSelected -> TextMuted
                    else -> TextPrimary
                },
                fontWeight = if (hasResult && isCorrectOption) FontWeight.Bold else FontWeight.Normal,
            )

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .then(
                        if (hasResult && (isCorrectOption || (isSelected && !wasCorrectAnswer)))
                            Modifier.clip(CircleShape).background(indicatorColor)
                        else
                            Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (hasResult && isCorrectOption) {
                    Text("\u2713", color = DarkBg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } else if (hasResult && isSelected && !wasCorrectAnswer) {
                    Text("\u2717", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } else {
                    Canvas(Modifier.size(26.dp)) {
                        drawCircle(
                            color = TextMuted,
                            radius = size.minDimension / 2 - 2f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                        )
                    }
                }
            }
        }
    }
}

// --- Feedback section ---

@Composable
private fun FeedbackSection(result: AnswerResultState) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(400)),
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (result.correct) GreenSurface else RedSurface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (result.correct) "\uD83C\uDF89" else "\uD83D\uDCA1",
                        fontSize = 26.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (result.correct) "Correct!" else "Wrong!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (result.correct) GreenAccent else RedAccent,
                        )
                        if (!result.correct) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Answer: ${result.correctOptionText}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GreenAccent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            result.explanation?.let { explanation ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkCard,
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text("\uD83D\uDCDA", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
    }
}

// --- Session ended ---

@Composable
private fun SessionEndedContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("\uD83C\uDF19", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "Session complete",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Head back to the dashboard to start another run.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SessionEndedBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\uD83C\uDF19", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Session over. Start a new run from the dashboard.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun NoQuestionsContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("\uD83D\uDCED", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "No questions available",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Check back later!",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
    }
}

// --- Wrong answer overlay ---

@Composable
private fun WrongAnswerOverlay(
    result: AnswerResultState,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        WrongAnswerEffect()

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) + fadeIn(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1111)),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("\u274C", fontSize = 52.sp)

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Wrong!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = RedAccent,
                    )

                    Spacer(Modifier.height(20.dp))

                    Text(
                        "You answered:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Text(
                        result.selectedOptionText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = RedAccent.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "Correct answer:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Text(
                        result.correctOptionText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent,
                        textAlign = TextAlign.Center,
                    )

                    result.explanation?.let { explanation ->
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedAccent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            "Got it",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

// --- Confetti effect ---

private data class ConfettiParticle(
    val x: Float,
    val delay: Float,      // fraction of total animation to wait before falling
    val duration: Float,   // fraction of total animation spent falling — guaranteed (delay + duration) <= 1
    val spin: Float,       // rotations over this particle's fall
    val angle: Float,
    val size: Float,
    val color: Color,
    val wobble: Float,
    val wobbleSpeed: Float,
)

@Composable
private fun ConfettiEffect() {
    val particles = remember {
        val colors = listOf(
            Color(0xFF4ADE80), Color(0xFF34D399), Color(0xFF22D3EE),
            Color(0xFFFCD34D), Color(0xFFF472B6), Color(0xFFA78BFA),
            Color(0xFF60A5FA), Color(0xFF818CF8), Color(0xFFFBBF24),
        )
        List(80) {
            // Pick a duration first, then a delay that leaves room for it to finish by t=1.
            val duration = 0.55f + Random.nextFloat() * 0.35f
            val delay = Random.nextFloat() * (1f - duration)
            ConfettiParticle(
                x = Random.nextFloat(),
                delay = delay,
                duration = duration,
                spin = 1f + Random.nextFloat() * 2f,
                angle = Random.nextFloat() * 360f,
                size = 4f + Random.nextFloat() * 10f,
                color = colors[Random.nextInt(colors.size)],
                wobble = 20f + Random.nextFloat() * 40f,
                wobbleSpeed = 1f + Random.nextFloat() * 3f,
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 3500, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value

        particles.forEach { p ->
            val local = ((t - p.delay) / p.duration).coerceIn(0f, 1f)
            // Start fully above the screen, travel past the bottom edge so every particle exits.
            val travel = h + p.size * 2 + 120f
            val y = -p.size - 60f + travel * local
            val wobbleOffset = sin(local * p.wobbleSpeed * 6.28f) * p.wobble
            val px = p.x * w + wobbleOffset
            val rotation = p.angle + local * 360f * p.spin

            if (local > 0f && y in -p.size..(h + p.size)) {
                rotate(rotation, pivot = Offset(px, y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(px - p.size / 2, y - p.size / 2),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f),
                    )
                }
            }
        }
    }
}

// --- Wrong answer effect ---

private data class XParticle(
    val x: Float,
    val delay: Float,
    val duration: Float,
    val size: Float,
    val alpha: Float,
    val baseRotation: Float,
    val spin: Float,
)

@Composable
private fun WrongAnswerEffect() {
    val particles = remember {
        List(36) {
            val duration = 0.55f + Random.nextFloat() * 0.35f
            val delay = Random.nextFloat() * (1f - duration)
            XParticle(
                x = Random.nextFloat(),
                delay = delay,
                duration = duration,
                size = 12f + Random.nextFloat() * 20f,
                alpha = 0.35f + Random.nextFloat() * 0.45f,
                baseRotation = Random.nextFloat() * 45f - 22.5f,
                spin = 0.5f + Random.nextFloat() * 1.5f,
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 3500, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value

        particles.forEach { p ->
            val local = ((t - p.delay) / p.duration).coerceIn(0f, 1f)
            val travel = h + p.size * 2 + 140f
            val y = -p.size - 70f + travel * local
            val px = p.x * w
            val rot = p.baseRotation + local * 180f * p.spin
            // Fade out near the end so the exit is softer than a hard clip.
            val alpha = p.alpha * (1f - local * 0.35f)

            if (local > 0f && y in -p.size..(h + p.size)) {
                rotate(rot, pivot = Offset(px, y)) {
                    val half = p.size / 2
                    drawLine(
                        color = RedAccent.copy(alpha = alpha),
                        start = Offset(px - half, y - half),
                        end = Offset(px + half, y + half),
                        strokeWidth = 2.5f,
                    )
                    drawLine(
                        color = RedAccent.copy(alpha = alpha),
                        start = Offset(px + half, y - half),
                        end = Offset(px - half, y + half),
                        strokeWidth = 2.5f,
                    )
                }
            }
        }
    }
}

// --- Dashboard ---

@Composable
fun DashboardScreen(onStartSession: () -> Unit) {
    var data by remember { mutableStateOf<DashboardResponseDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        data = runCatching { QuizRepository.dashboard() }.getOrNull()
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(
                "QuizMemo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(24.dp))

            if (loading && data == null) {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue, strokeWidth = 3.dp)
                }
            } else {
                val d = data
                if (d == null) {
                    Text(
                        "Couldn't load your stats. Pull to try again.",
                        color = RedAccent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    RecordsCard(d)
                    Spacer(Modifier.height(16.dp))
                    LevelProgressCard(d)
                    Spacer(Modifier.height(16.dp))
                    CurrentSessionCard(d.session)
                    Spacer(Modifier.height(24.dp))
                    StartSessionActions(
                        session = d.session,
                        working = working,
                        onContinue = onStartSession,
                        onStartFresh = {
                            scope.launch {
                                working = true
                                runCatching { QuizRepository.resetSession() }
                                working = false
                                onStartSession()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun RecordsCard(d: DashboardResponseDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Records",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(14.dp))
            val accuracy = if (d.totalAnswers == 0) 0 else (d.totalCorrect * 100 / d.totalAnswers)
            // IntrinsicSize.Min on each Row makes both tiles stretch to match the taller one,
            // so values, labels, and subtitles align on the same baselines across the row.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                StatTile(
                    label = "Sessions",
                    value = d.totalSessions.toString(),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Spacer(Modifier.width(10.dp))
                StatTile(
                    label = "Best run",
                    value = d.bestSessionScore.toString(),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                StatTile(
                    label = "Answers",
                    value = d.totalAnswers.toString(),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Spacer(Modifier.width(10.dp))
                StatTile(
                    label = "Accuracy",
                    value = "${accuracy}%",
                    subtitle = "${d.totalCorrect}/${d.totalAnswers}",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = DarkCardLight,
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 14.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun LevelProgressCard(d: DashboardResponseDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentBlue.copy(alpha = 0.15f),
                ) {
                    Text(
                        d.estimatedLevel ?: "\u2014",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlueBright,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Current CEFR level",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Text(
                        d.estimatedLevel?.let { "You're at $it" } ?: "Not enough data yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            d.nextLevelProgress?.let { p ->
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                Spacer(Modifier.height(14.dp))
                NextLevelProgress(p)
            }

            if (d.byLevel.any { it.attempts > 0 }) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                d.byLevel.filter { it.attempts > 0 }.forEach { stat ->
                    LevelRow(stat)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                d.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun NextLevelProgress(p: NextLevelProgressDto) {
    Text(
        "Progress to ${p.level}",
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(10.dp))

    val attemptsFrac = (p.attempts.toFloat() / p.attemptsNeeded.toFloat()).coerceIn(0f, 1f)
    ProgressRequirementRow(
        label = "Attempts",
        current = p.attempts.toString(),
        target = p.attemptsNeeded.toString(),
        fraction = attemptsFrac,
        met = p.attemptsMet,
    )
    Spacer(Modifier.height(8.dp))

    val accFrac = (p.accuracy.toFloat() / p.accuracyNeeded.toFloat()).coerceIn(0f, 1f)
    val accPct = (p.accuracy * 100).toInt()
    val targetPct = (p.accuracyNeeded * 100).toInt()
    ProgressRequirementRow(
        label = "Accuracy",
        current = "${accPct}%",
        target = "${targetPct}%",
        fraction = accFrac,
        met = p.accuracyMet,
    )

    Spacer(Modifier.height(10.dp))
    val message = when {
        p.attemptsMet && p.accuracyMet ->
            "You meet the bar — keep playing and your estimate should advance."
        !p.attemptsMet && !p.accuracyMet ->
            "Need ${p.attemptsNeeded - p.attempts} more attempts at ${p.level} with ${targetPct}%+ accuracy."
        !p.attemptsMet ->
            "Need ${p.attemptsNeeded - p.attempts} more attempts at ${p.level}."
        else ->
            "Accuracy is below ${targetPct}%. Focus on the ${p.level} mistakes."
    }
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (p.attemptsMet && p.accuracyMet) GreenAccent else WarmYellow,
    )
}

@Composable
private fun ProgressRequirementRow(
    label: String,
    current: String,
    target: String,
    fraction: Float,
    met: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.width(78.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkCardLight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (met) GreenAccent else AccentBlueBright),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$current / $target",
            style = MaterialTheme.typography.labelSmall,
            color = if (met) GreenAccent else TextMuted,
            modifier = Modifier.width(74.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LevelRow(stat: LevelStatDto) {
    val pct = (stat.accuracy * 100).toInt()
    val barColor = when {
        pct >= 70 -> GreenAccent
        pct >= 40 -> WarmYellow
        else -> RedAccent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stat.level,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.width(30.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkCardLight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = stat.accuracy.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${stat.correct}/${stat.attempts}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun CurrentSessionCard(session: SessionStatusDto) {
    val (tone, icon, headline) = when {
        session.sessionEnded -> Triple(RedAccent, "\uD83C\uDF19", "Last run ended — play again to reset")
        session.points > 0 -> Triple(GreenAccent, "\uD83D\uDD25", "In-session streak: ${session.points}")
        else -> Triple(AccentBlueBright, "\u2728", "No active run today")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Today's session",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
                Text(
                    headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tone,
                )
                Text(
                    "Attempt #${session.attempt} • ${session.points} pt",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun StartSessionActions(
    session: SessionStatusDto,
    working: Boolean,
    onContinue: () -> Unit,
    onStartFresh: () -> Unit,
) {
    // Continue is only meaningful when the user is mid-run (has points, not ended).
    val canContinue = session.canPlay && session.points > 0 && !session.sessionEnded
    val primaryLabel = when {
        canContinue -> "Continue session"
        session.sessionEnded -> "Play again"
        session.points == 0 && session.canPlay -> "Start session"
        else -> "Play again"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        GradientButton(
            label = primaryLabel,
            enabled = !working,
            onClick = if (canContinue) onContinue else onStartFresh,
        )
        if (canContinue) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onStartFresh,
                enabled = !working,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            ) {
                Text("Restart (resets counters)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GradientButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(AccentGradientStart, AccentGradientEnd),
                    ),
                    shape = RoundedCornerShape(27.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }
}

// --- Quiz screen helpers ---

@Composable
private fun BackChip(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = DarkCard,
    ) {
        Text(
            "\u2190",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BackToDashboardButton(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        ) {
            Text("Back to dashboard", fontWeight = FontWeight.SemiBold)
        }
    }
}
