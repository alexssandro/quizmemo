using Microsoft.EntityFrameworkCore;
using QuizMemo.Api.Entities;

namespace QuizMemo.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();
    public DbSet<Question> Questions => Set<Question>();
    public DbSet<QuestionOption> QuestionOptions => Set<QuestionOption>();
    public DbSet<Answer> Answers => Set<Answer>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>()
            .HasIndex(u => u.Username)
            .IsUnique();

        b.Entity<User>()
            .HasIndex(u => u.GoogleSubject)
            .IsUnique();

        b.Entity<QuestionOption>()
            .HasOne(o => o.Question)
            .WithMany(q => q.Options)
            .HasForeignKey(o => o.QuestionId)
            .OnDelete(DeleteBehavior.Cascade);

        b.Entity<Answer>()
            .HasIndex(a => new { a.UserId, a.SessionDate, a.SessionAttempt });

        b.Entity<Answer>()
            .HasIndex(a => new { a.UserId, a.QuestionId });
    }
}
