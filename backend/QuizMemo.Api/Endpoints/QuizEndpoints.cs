using Microsoft.EntityFrameworkCore;
using QuizMemo.Api.Data;
using QuizMemo.Api.Entities;
using QuizMemo.Api.Quiz;
using System.Security.Claims;

namespace QuizMemo.Api.Endpoints;

public static class QuizEndpoints
{
    public record OptionDto(int Id, string Text);
    public record QuestionDto(int Id, string Text, List<OptionDto> Options);
    public record AnswerRequest(int QuestionId, int OptionId);
    public record AnswerResponse(bool Correct, int CorrectOptionId, string CorrectOptionText, string? Explanation, int TodayPoints, bool SessionEnded);
    public record SessionStatusDto(DateOnly SessionDate, int Attempt, int Points, bool SessionEnded, bool CanPlay);
    public record HistoryItemDto(int QuestionId, string QuestionText, bool IsCorrect, DateOnly SessionDate, int SessionAttempt, DateTime AnsweredAt);
    public record LevelStatDto(string Level, int Attempts, int Correct, double Accuracy);
    public record LevelResponseDto(string? EstimatedLevel, string? NextLevel, int TotalAnswers, List<LevelStatDto> ByLevel, string Recommendation);

    public record NextLevelProgressDto(
        string Level,
        int Attempts,
        int AttemptsNeeded,
        double Accuracy,
        double AccuracyNeeded,
        bool AttemptsMet,
        bool AccuracyMet);

    public record DashboardResponseDto(
        SessionStatusDto Session,
        int TotalSessions,
        int TotalAnswers,
        int TotalCorrect,
        int BestSessionScore,
        string? EstimatedLevel,
        string? NextLevel,
        NextLevelProgressDto? NextLevelProgress,
        List<LevelStatDto> ByLevel,
        string Recommendation);

    public static void MapQuizEndpoints(this WebApplication app)
    {
        var g = app.MapGroup("/quiz").RequireAuthorization();

        g.MapGet("/next", async (HttpContext ctx, AppDbContext db) =>
        {
            var userId = GetUserId(ctx);
            var session = await SessionStateQuery.LoadAsync(db, userId);

            if (session.Ended)
                return Results.Ok<QuestionDto?>(null);

            var answeredTodayIds = session.AnsweredQuestionIds();

            var stats = await db.Answers
                .AsNoTracking()
                .Where(a => a.UserId == userId)
                .GroupBy(a => a.QuestionId)
                .Select(grp => new
                {
                    QuestionId = grp.Key,
                    Errors = grp.Count(a => !a.IsCorrect),
                    Correct = grp.Count(a => a.IsCorrect),
                })
                .ToListAsync();

            var statsMap = stats.ToDictionary(s => s.QuestionId);

            var allQuestions = await db.Questions
                .AsNoTracking()
                .Include(q => q.Options)
                .ToListAsync();

            // Ordering: (1) never answered first,
            //           (2) then most errors,
            //           (3) then fewest corrects (so well-known questions appear last).
            var nextQuestion = allQuestions
                .Where(q => !answeredTodayIds.Contains(q.Id))
                .OrderBy(q => statsMap.ContainsKey(q.Id) ? 1 : 0)
                .ThenByDescending(q => statsMap.TryGetValue(q.Id, out var s) ? s.Errors : 0)
                .ThenBy(q => statsMap.TryGetValue(q.Id, out var s) ? s.Correct : 0)
                .ThenBy(q => q.Id)
                .FirstOrDefault();

            if (nextQuestion is null)
                return Results.Ok<QuestionDto?>(null);

            var dto = new QuestionDto(
                nextQuestion.Id,
                nextQuestion.Text,
                nextQuestion.Options
                    .OrderBy(o => o.Id)
                    .Select(o => new OptionDto(o.Id, o.Text))
                    .ToList());
            return Results.Ok<QuestionDto?>(dto);
        });

        g.MapPost("/answer", async (AnswerRequest req, HttpContext ctx, AppDbContext db) =>
        {
            var userId = GetUserId(ctx);
            var user = await db.Users.FirstAsync(u => u.Id == userId);
            var today = SessionStateQuery.Today();
            var attempt = SessionStateQuery.CurrentAttempt(user, today);

            // Persist the normalized current-attempt fields (roll over on new UTC day).
            if (user.SessionAttemptDate != today)
            {
                user.SessionAttemptDate = today;
                user.SessionAttempt = attempt;
            }

            var answeredToday = await db.Answers
                .AsNoTracking()
                .Where(a => a.UserId == userId && a.SessionDate == today && a.SessionAttempt == attempt)
                .ToListAsync();

            if (answeredToday.Any(a => !a.IsCorrect))
                return Results.BadRequest(new { error = "session already ended for today" });

            if (answeredToday.Any(a => a.QuestionId == req.QuestionId))
                return Results.BadRequest(new { error = "question already answered in this session" });

            var question = await db.Questions
                .AsNoTracking()
                .Include(q => q.Options)
                .FirstOrDefaultAsync(q => q.Id == req.QuestionId);

            if (question is null)
                return Results.NotFound();

            var selected = question.Options.FirstOrDefault(o => o.Id == req.OptionId);
            var correct = question.Options.FirstOrDefault(o => o.IsCorrect);
            if (selected is null || correct is null)
                return Results.NotFound();

            db.Answers.Add(new Answer
            {
                UserId = userId,
                QuestionId = req.QuestionId,
                SelectedOptionId = req.OptionId,
                IsCorrect = selected.IsCorrect,
                SessionDate = today,
                SessionAttempt = attempt,
                AnsweredAt = DateTime.UtcNow,
            });
            await db.SaveChangesAsync();

            var todayPoints = answeredToday.Count(a => a.IsCorrect) + (selected.IsCorrect ? 1 : 0);
            return Results.Ok(new AnswerResponse(selected.IsCorrect, correct.Id, correct.Text, question.Explanation, todayPoints, !selected.IsCorrect));
        });

        g.MapGet("/status", async (HttpContext ctx, AppDbContext db) =>
        {
            var userId = GetUserId(ctx);
            var session = await SessionStateQuery.LoadAsync(db, userId);
            var questionCount = await db.Questions.AsNoTracking().CountAsync();

            return Results.Ok(new SessionStatusDto(
                session.Today, session.Attempt, session.Points, session.Ended,
                CanPlay(session, questionCount)));
        });

        // Start a fresh session — every press resets the counters. If the current attempt
        // has no answers yet, it's already fresh, so we don't bump (avoids skipping attempt numbers).
        g.MapPost("/session/reset", async (HttpContext ctx, AppDbContext db) =>
        {
            var userId = GetUserId(ctx);
            var user = await db.Users.FirstAsync(u => u.Id == userId);
            var today = SessionStateQuery.Today();
            var current = SessionStateQuery.CurrentAttempt(user, today);

            var hasAnswers = await db.Answers
                .AsNoTracking()
                .AnyAsync(a => a.UserId == userId && a.SessionDate == today && a.SessionAttempt == current);

            var next = hasAnswers ? current + 1 : current;
            user.SessionAttemptDate = today;
            user.SessionAttempt = next;
            await db.SaveChangesAsync();

            var questionCount = await db.Questions.AsNoTracking().CountAsync();
            var canPlay = questionCount > 0;
            return Results.Ok(new SessionStatusDto(today, next, 0, false, canPlay));
        });

        g.MapGet("/history", async (HttpContext ctx, AppDbContext db) =>
        {
            var userId = GetUserId(ctx);

            var history = await db.Answers
                .AsNoTracking()
                .Where(a => a.UserId == userId)
                .OrderByDescending(a => a.AnsweredAt)
                .Select(a => new HistoryItemDto(
                    a.QuestionId,
                    a.Question.Text,
                    a.IsCorrect,
                    a.SessionDate,
                    a.SessionAttempt,
                    a.AnsweredAt))
                .ToListAsync();

            return Results.Ok(history);
        });

        g.MapGet("/level", async (HttpContext ctx, AppDbContext db) =>
        {
            var userId = GetUserId(ctx);
            var answers = await LoadAnswersWithLevelAsync(db, userId);
            var summary = BuildLevelSummary(answers);

            return Results.Ok(new LevelResponseDto(
                summary.Estimated,
                summary.NextLevel,
                summary.ByLevel.Sum(b => b.Attempts),
                summary.ByLevel,
                summary.Recommendation));
        });

        g.MapGet("/dashboard", async (HttpContext ctx, AppDbContext db) =>
        {
            var userId = GetUserId(ctx);
            var session = await SessionStateQuery.LoadAsync(db, userId);

            var questionCount = await db.Questions.AsNoTracking().CountAsync();
            var sessionDto = new SessionStatusDto(
                session.Today, session.Attempt, session.Points, session.Ended,
                CanPlay(session, questionCount));

            // Records, computed across all attempts — reset only wipes the *current* counter;
            // history stays for spaced-repetition ordering + CEFR estimation.
            var answers = await LoadAnswersWithLevelAsync(db, userId);
            var summary = BuildLevelSummary(answers);

            var totalAnswers = answers.Count;
            var totalCorrect = answers.Count(a => a.IsCorrect);
            var bySession = answers.GroupBy(a => (a.SessionDate, a.SessionAttempt)).ToList();
            var totalSessions = bySession.Count;
            var bestSessionScore = bySession.Select(g => g.Count(x => x.IsCorrect)).DefaultIfEmpty(0).Max();

            return Results.Ok(new DashboardResponseDto(
                sessionDto,
                totalSessions,
                totalAnswers,
                totalCorrect,
                bestSessionScore,
                summary.Estimated,
                summary.NextLevel,
                summary.NextLevelProgress,
                summary.ByLevel,
                summary.Recommendation));
        });
    }

    private record LeveledAnswer(bool IsCorrect, DateOnly SessionDate, int SessionAttempt, string Level);

    private static async Task<List<LeveledAnswer>> LoadAnswersWithLevelAsync(AppDbContext db, int userId) =>
        await db.Answers
            .AsNoTracking()
            .Where(a => a.UserId == userId)
            .Join(db.Questions,
                a => a.QuestionId,
                q => q.Id,
                (a, q) => new LeveledAnswer(a.IsCorrect, a.SessionDate, a.SessionAttempt, q.Level))
            .ToListAsync();

    private record LevelSummary(
        List<LevelStatDto> ByLevel,
        string? Estimated,
        string? NextLevel,
        NextLevelProgressDto? NextLevelProgress,
        string Recommendation);

    private static LevelSummary BuildLevelSummary(List<LeveledAnswer> answers)
    {
        var byLevel = CefrLevels.Order.Select(level =>
        {
            var attempts = answers.Count(x => x.Level == level);
            var correct = answers.Count(x => x.Level == level && x.IsCorrect);
            var accuracy = attempts > 0 ? (double)correct / attempts : 0.0;
            return new LevelStatDto(level, attempts, correct, accuracy);
        }).ToList();

        var estimated = CefrLevels.Estimate(byLevel.Select(s => (s.Level, s.Attempts, s.Accuracy)));
        var nextLevel = CefrLevels.GetNext(estimated);
        NextLevelProgressDto? progress = null;
        if (!string.IsNullOrEmpty(nextLevel))
        {
            var stat = byLevel.FirstOrDefault(s => s.Level == nextLevel);
            if (stat is not null)
            {
                progress = new NextLevelProgressDto(
                    nextLevel, stat.Attempts, CefrLevels.MinAttempts,
                    stat.Accuracy, CefrLevels.PassThreshold,
                    stat.Attempts >= CefrLevels.MinAttempts,
                    stat.Accuracy >= CefrLevels.PassThreshold);
            }
        }
        return new LevelSummary(byLevel, estimated, nextLevel, progress, CefrLevels.RecommendationFor(estimated));
    }

    private static bool CanPlay(SessionState session, int questionCount) =>
        !session.Ended && (questionCount - session.Answers.Count) > 0;

    private static int GetUserId(HttpContext ctx) =>
        int.Parse(ctx.User.FindFirstValue(ClaimTypes.NameIdentifier)!);
}
