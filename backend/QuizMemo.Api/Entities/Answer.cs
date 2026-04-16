namespace QuizMemo.Api.Entities;

public class Answer
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public int QuestionId { get; set; }
    public int SelectedOptionId { get; set; }
    public bool IsCorrect { get; set; }
    public DateOnly SessionDate { get; set; }
    public int SessionAttempt { get; set; } = 1;
    public DateTime AnsweredAt { get; set; }

    public User User { get; set; } = null!;
    public Question Question { get; set; } = null!;
}
