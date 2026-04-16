namespace QuizMemo.Api.Entities;

public class QuestionOption
{
    public int Id { get; set; }
    public int QuestionId { get; set; }
    public string Text { get; set; } = "";
    public bool IsCorrect { get; set; }

    public Question Question { get; set; } = null!;
}
