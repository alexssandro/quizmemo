using Microsoft.EntityFrameworkCore;
using QuizMemo.Api.Data;
using QuizMemo.Api.Entities;

namespace QuizMemo.Api.Quiz;

public record SessionState(
    DateOnly Today,
    int Attempt,
    List<Answer> Answers,
    bool Ended,
    int Points)
{
    public HashSet<int> AnsweredQuestionIds() =>
        Answers.Select(a => a.QuestionId).ToHashSet();
}

public static class SessionStateQuery
{
    // Sessions roll over at UTC midnight. Changing this one line changes it everywhere.
    public static DateOnly Today() => DateOnly.FromDateTime(DateTime.UtcNow);

    /// <summary>Current session attempt for the user today. Rolls back to 1 on date change.</summary>
    public static int CurrentAttempt(User user, DateOnly today) =>
        user.SessionAttemptDate == today ? user.SessionAttempt : 1;

    public static async Task<SessionState> LoadAsync(AppDbContext db, int userId)
    {
        var user = await db.Users.AsNoTracking().FirstAsync(u => u.Id == userId);
        var today = Today();
        var attempt = CurrentAttempt(user, today);

        var answers = await db.Answers
            .AsNoTracking()
            .Where(a => a.UserId == userId && a.SessionDate == today && a.SessionAttempt == attempt)
            .ToListAsync();

        var ended = answers.Any(a => !a.IsCorrect);
        var points = answers.Count(a => a.IsCorrect);
        return new SessionState(today, attempt, answers, ended, points);
    }
}
