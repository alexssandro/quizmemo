-- Seed de perguntas de exemplo. Rode manualmente:
--   docker exec -i quizmemo-postgres psql -U quizmemo -d quizmemo < db/seed.sql
-- Os nomes de tabela/coluna seguem a convenção do EF Core ("PascalCase" entre aspas).

BEGIN;

WITH q1 AS (
    INSERT INTO "Questions" ("Text", "Explanation", "CreatedAt")
    VALUES ('Qual é o planeta mais próximo do Sol?', 'Mercúrio orbita a apenas ~58 milhões de km do Sol, bem mais perto que Vênus (~108 milhões de km). É também o menor planeta do sistema solar.', now())
    RETURNING "Id"
)
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect")
SELECT "Id", v.text, v.correct FROM q1, (VALUES
    ('Vênus', false),
    ('Mercúrio', true),
    ('Marte', false),
    ('Terra', false)
) AS v(text, correct);

WITH q2 AS (
    INSERT INTO "Questions" ("Text", "Explanation", "CreatedAt")
    VALUES ('Em que ano o homem pisou na Lua pela primeira vez?', 'A Apollo 11 levou Neil Armstrong e Buzz Aldrin à Lua em 20 de julho de 1969. Armstrong foi o primeiro a pisar na superfície lunar.', now())
    RETURNING "Id"
)
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect")
SELECT "Id", v.text, v.correct FROM q2, (VALUES
    ('1959', false),
    ('1965', false),
    ('1969', true),
    ('1972', false)
) AS v(text, correct);

WITH q3 AS (
    INSERT INTO "Questions" ("Text", "Explanation", "CreatedAt")
    VALUES ('Qual linguagem roda nativamente no navegador?', 'JavaScript é a única linguagem de programação que os navegadores executam nativamente. Python, C# e Ruby precisam de compilação ou um runtime separado.', now())
    RETURNING "Id"
)
INSERT INTO "QuestionOptions" ("QuestionId", "Text", "IsCorrect")
SELECT "Id", v.text, v.correct FROM q3, (VALUES
    ('Python', false),
    ('C#', false),
    ('JavaScript', true),
    ('Ruby', false)
) AS v(text, correct);

COMMIT;
