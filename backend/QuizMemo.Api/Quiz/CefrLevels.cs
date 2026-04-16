namespace QuizMemo.Api.Quiz;

public static class CefrLevels
{
    public static readonly string[] Order = { "A1", "A2", "B1", "B2", "C1", "C2" };

    public const int MinAttempts = 3;
    public const double PassThreshold = 0.7;

    public static string? GetNext(string? level) => level switch
    {
        null or "" => "A1",
        "A1" => "A2",
        "A2" => "B1",
        "B1" => "B2",
        "B2" => "C1",
        "C1" => "C2",
        "C2" => null,
        _ => "A1",
    };

    /// <summary>
    /// Estimates a user's level as the highest band where they have ≥ MinAttempts attempts
    /// AND ≥ PassThreshold accuracy, AND every lower band is also passed. Stops at the first
    /// failure — a user strong at C1 but untested at B2 still caps at B1.
    /// </summary>
    public static string? Estimate(IEnumerable<(string Level, int Attempts, double Accuracy)> stats)
    {
        var byLevel = stats.ToDictionary(s => s.Level);
        string? estimated = null;
        foreach (var level in Order)
        {
            if (!byLevel.TryGetValue(level, out var s)) break;
            if (s.Attempts >= MinAttempts && s.Accuracy >= PassThreshold)
                estimated = level;
            else
                break;
        }
        return estimated;
    }

    public static string RecommendationFor(string? current)
    {
        return current switch
        {
            null or "" =>
                "Not enough data yet. Answer at least 3 A1 questions with 70%+ accuracy to unlock your first level estimate. " +
                "Start by watching subject-verb agreement, basic articles (a/an/the), and simple prepositions (in/on/at).",
            "A1" =>
                "To reach A2: don't drop subjects (\"I believe...\" not just \"believe...\"), watch agreement with " +
                "\"there is/are anything\", and drop the preposition in time phrases like \"the same day\" (not \"in the same day\").",
            "A2" =>
                "To reach B1: master separable phrasal verbs and pronoun placement (\"back itself up\", not \"back up itself\"), " +
                "always use gerund after \"end up\"/\"stop\"/\"keep\", and watch out for false cognates — \"notice\" in English " +
                "does NOT mean \"avisar\".",
            "B1" =>
                "To reach B2: stop using literal Portuguese calques like \"gain time\" (→ buy time), \"put the password\" (→ enter), " +
                "\"as a repair\" (→ to make up for it). Learn the make/do/take/have collocation families, and the difference between " +
                "\"trust\" (confiar) and \"assume\" (assumir).",
            "B2" =>
                "To reach C1: learn native hedging and softeners (\"I was wondering if...\", \"would you mind...\", \"could you\" " +
                "instead of \"is it possible to\"). Master advanced phrasal verbs (walk through, run into, come up with, " +
                "make up for), and focus on register — when to be casual, diplomatic, or formal.",
            "C1" =>
                "To reach C2: polish subtle register shifts, culture-bound idioms, and precision between near-synonyms " +
                "(assume vs. suppose vs. reckon vs. figure; handle vs. deal with vs. address vs. tackle). Consume native media, " +
                "and pay attention to the rare misses.",
            "C2" =>
                "You're at the top of the scale. Keep consuming native media, pay attention to idiomatic fine-tuning, " +
                "and focus on domain-specific register (legal, technical, casual) since that's where the final polish lives.",
            _ => "",
        };
    }
}
