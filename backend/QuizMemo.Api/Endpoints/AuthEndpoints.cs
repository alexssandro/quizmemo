using Google.Apis.Auth;
using Microsoft.EntityFrameworkCore;
using QuizMemo.Api.Auth;
using QuizMemo.Api.Data;
using QuizMemo.Api.Entities;

namespace QuizMemo.Api.Endpoints;

public static class AuthEndpoints
{
    public record GoogleLoginRequest(string IdToken);
    public record TokenResponse(string Token);

    public static void MapAuthEndpoints(this WebApplication app)
    {
        var g = app.MapGroup("/auth");

        g.MapPost("/google", async (
            GoogleLoginRequest req,
            AppDbContext db,
            JwtService jwt,
            IConfiguration config) =>
        {
            var clientId = config["Google:ClientId"]
                ?? throw new InvalidOperationException("Google:ClientId missing in config");

            GoogleJsonWebSignature.Payload payload;
            try
            {
                payload = await GoogleJsonWebSignature.ValidateAsync(
                    req.IdToken,
                    new GoogleJsonWebSignature.ValidationSettings
                    {
                        Audience = new[] { clientId },
                    });
            }
            catch (InvalidJwtException)
            {
                return Results.Unauthorized();
            }

            // Match first by GoogleSubject, then by Email (in case the user signed up
            // locally first and is now linking their Google account).
            var user = await db.Users.FirstOrDefaultAsync(u => u.GoogleSubject == payload.Subject);
            if (user is null && !string.IsNullOrEmpty(payload.Email))
                user = await db.Users.FirstOrDefaultAsync(u => u.Email == payload.Email);

            if (user is null)
            {
                user = new User
                {
                    Username = payload.Email ?? $"google_{payload.Subject}",
                    Email = payload.Email,
                    GoogleSubject = payload.Subject,
                    PasswordHash = null,
                    CreatedAt = DateTime.UtcNow,
                };
                db.Users.Add(user);
            }
            else if (user.GoogleSubject is null)
            {
                user.GoogleSubject = payload.Subject;
                user.Email ??= payload.Email;
            }

            await db.SaveChangesAsync();
            return Results.Ok(new TokenResponse(jwt.CreateToken(user.Id, user.Username)));
        });
    }
}
