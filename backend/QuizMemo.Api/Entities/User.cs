namespace QuizMemo.Api.Entities;

public class User
{
    public int Id { get; set; }
    public string Username { get; set; } = "";

    /// <summary>Null for Google-only accounts.</summary>
    public string? PasswordHash { get; set; }

    public string? Email { get; set; }

    /// <summary>Google "sub" claim — stable unique ID per Google account.</summary>
    public string? GoogleSubject { get; set; }

    public DateTime CreatedAt { get; set; }

    /// <summary>Attempt number to use for new answers on <see cref="SessionAttemptDate"/>.
    /// Auto-reset to 1 when a new date rolls in; bumped by /quiz/session/reset.</summary>
    public int SessionAttempt { get; set; } = 1;
    public DateOnly? SessionAttemptDate { get; set; }

    public List<Answer> Answers { get; set; } = new();
}
