namespace QuizMemo.Api.Entities;

public class Question
{
    public int Id { get; set; }
    public string Text { get; set; } = "";
    public DateTime CreatedAt { get; set; }

    /// <summary>CEFR level: A1, A2, B1, B2, C1, C2.</summary>
    public string Level { get; set; } = "B1";

    /// <summary>Optional explanation shown after the user answers (especially useful on wrong answers).</summary>
    public string? Explanation { get; set; }

    public List<QuestionOption> Options { get; set; } = new();
}
