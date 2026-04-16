using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace QuizMemo.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddSessionAttempt : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_Answers_UserId_SessionDate",
                table: "Answers");

            migrationBuilder.AddColumn<int>(
                name: "SessionAttempt",
                table: "Users",
                type: "integer",
                nullable: false,
                defaultValue: 1);

            migrationBuilder.AddColumn<DateOnly>(
                name: "SessionAttemptDate",
                table: "Users",
                type: "date",
                nullable: true);

            migrationBuilder.AddColumn<int>(
                name: "SessionAttempt",
                table: "Answers",
                type: "integer",
                nullable: false,
                defaultValue: 1);

            migrationBuilder.CreateIndex(
                name: "IX_Answers_UserId_SessionDate_SessionAttempt",
                table: "Answers",
                columns: new[] { "UserId", "SessionDate", "SessionAttempt" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_Answers_UserId_SessionDate_SessionAttempt",
                table: "Answers");

            migrationBuilder.DropColumn(
                name: "SessionAttempt",
                table: "Users");

            migrationBuilder.DropColumn(
                name: "SessionAttemptDate",
                table: "Users");

            migrationBuilder.DropColumn(
                name: "SessionAttempt",
                table: "Answers");

            migrationBuilder.CreateIndex(
                name: "IX_Answers_UserId_SessionDate",
                table: "Answers",
                columns: new[] { "UserId", "SessionDate" });
        }
    }
}
